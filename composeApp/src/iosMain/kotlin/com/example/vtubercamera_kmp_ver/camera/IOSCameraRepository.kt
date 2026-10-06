package com.example.vtubercamera_kmp_ver.camera

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import kotlin.coroutines.resume

internal typealias IOSCameraLensAvailability = (CameraLensFacing) -> Boolean

internal interface CameraControl {
    fun zoomState(): CameraZoomUiState

    fun setZoomRatio(updatedZoomRatio: Float): CameraZoomUiState?
}

internal fun interface IOSPhotoCapturer {
    fun capturePhoto(onComplete: (uri: String?, error: Throwable?) -> Unit)
}

// アプリ画面の録画を repository から切り離すための録画開始口。repository のテストでは fake へ差し替える。
internal fun interface IOSVideoRecorder {
    // 録画を開始する。[onFinalized] は録画の終了と Photos への保存が済んだ時点で main thread から
    // 1 度だけ呼ばれ、成功なら保存先の識別子、失敗なら原因を受け取る。失敗した録画の一時ファイルは、
    // 実装側が片付けてから通知する。開始できないときは例外を投げる。
    fun startRecording(onFinalized: (uri: String?, error: Throwable?) -> Unit): IOSVideoRecording
}

// 開始済みの録画への停止要求。停止後の保存完了は [IOSVideoRecorder.startRecording] の callback で届く。
internal fun interface IOSVideoRecording {
    fun stop()
}

internal class IOSCameraRepository(
    private val hasLens: IOSCameraLensAvailability,
    private val previewState: MutableStateFlow<PreviewState> = MutableStateFlow(PreviewState.Preparing),
    private val photoCaptureState: MutableStateFlow<PhotoCaptureState> = MutableStateFlow(PhotoCaptureState.Idle),
    private val photoDeletionState: MutableStateFlow<PhotoDeletionState> = MutableStateFlow(PhotoDeletionState.Idle),
    private val videoRecordingState: MutableStateFlow<VideoRecordingState> = MutableStateFlow(VideoRecordingState.Idle),
    private val zoomUiState: MutableStateFlow<CameraZoomUiState> = MutableStateFlow(
        CameraZoomUiState()
    ),
    // 既定では撮影で書き出したローカルファイルを削除する。テストで差し替え可能にする。
    private val photoFileDeleter: (String) -> Boolean = ::deletePhotoFile,
) : CameraRepository {
    private var pendingLensFacing: CameraLensFacing? = null

    private var cameraControl: CameraControl? = null
    private var photoCapturer: IOSPhotoCapturer? = null
    private var videoRecorder: IOSVideoRecorder? = null

    // 録画セッションの管理。いずれも main thread からだけ触る（呼び出し元と recorder の完了通知が main）。
    private var activeRecording: IOSVideoRecording? = null
    private var pendingStopResult: CompletableDeferred<Result<String?>>? = null

    // プレビュー開始前の状態を整え、利用可能なレンズを解決する。
    override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
        val resolvedLens = resolveAvailableLens(requested = lensFacing, hasLens = hasLens)
            ?: return Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
        pendingLensFacing = resolvedLens
        previewState.value = PreviewState.Preparing
        return Result.success(resolvedLens)
    }

    // プレビュー停止に合わせて内部状態を初期化する。
    override suspend fun stopPreview() {
        pendingLensFacing = null
        photoCapturer = null
        previewState.value = PreviewState.Preparing
    }

    // 現在と反対側のレンズへ切り替え可能か確認して反映する。
    override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
        photoCapturer = null
        previewState.value = PreviewState.Preparing
        val targetLens = current.toggled()
        if (!hasLens(targetLens)) {
            previewState.value = PreviewState.Error(CameraError.LensSwitchFailed)
            return Result.failure(CameraRepositoryException(CameraError.LensSwitchFailed))
        }
        pendingLensFacing = targetLens
        return Result.success(targetLens)
    }

    // 初回表示時に利用できるレンズを決定する。
    override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
        val resolvedLens = resolveAvailableLens(requested = preferred, hasLens = hasLens)
            ?: return Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
        return Result.success(resolvedLens)
    }

    // プレビュー状態の変更を監視する Flow を返す。
    override fun observePreviewState(): Flow<PreviewState> = previewState

    override fun observePhotoCaptureState(): Flow<PhotoCaptureState> = photoCaptureState

    override suspend fun capturePhoto(): Result<String?> {
        if (previewState.value !is PreviewState.Showing) {
            return Result.failure<String?>(
                CameraRepositoryException(CameraError.PhotoCaptureFailed),
            ).also {
                photoCaptureState.value = PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed)
            }
        }
        val capturer = photoCapturer
            ?: return Result.failure<String?>(
                CameraRepositoryException(CameraError.PhotoCaptureFailed),
            ).also {
                photoCaptureState.value = PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed)
            }
        photoCaptureState.value = PhotoCaptureState.Capturing
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            capturer.capturePhoto { uri, error ->
                if (error == null) {
                    photoCaptureState.value = PhotoCaptureState.Succeeded(uri)
                    continuation.resume(Result.success(uri))
                } else {
                    photoCaptureState.value = PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed)
                    continuation.resume(Result.failure(CameraRepositoryException(CameraError.PhotoCaptureFailed)))
                }
            }
        }
    }

    override fun observeVideoRecordingState(): Flow<VideoRecordingState> = videoRecordingState

    // アプリ画面の録画を開始する。録画中、プレビュー未表示、recorder が未準備、開始に失敗したときは
    // [CameraError.VideoRecordFailed] で失敗する。すでに録画中の呼び出しは状態を変えずに失敗だけ返す。
    override suspend fun startVideoRecording(): Result<Unit> {
        if (activeRecording != null) {
            return Result.failure(CameraRepositoryException(CameraError.VideoRecordFailed))
        }
        val recorder = videoRecorder
        if (previewState.value !is PreviewState.Showing || recorder == null) {
            return failVideoRecordingStart()
        }
        val recording = runCatching { recorder.startRecording(::onVideoRecordingFinalized) }
            .getOrElse { return failVideoRecordingStart() }
        activeRecording = recording
        videoRecordingState.value = VideoRecordingState.Recording
        return Result.success(Unit)
    }

    // 録画の停止を要求し、Photos への保存が完了するまで待つ。録画中でなければ状態を変えずに失敗だけ返す。
    // 保存に失敗したときは [VideoRecordingState.Failed] へ遷移する（一時ファイルの片付けは recorder が行う）。
    override suspend fun stopVideoRecording(): Result<String?> {
        val recording = activeRecording
        if (recording == null || pendingStopResult != null) {
            return Result.failure(CameraRepositoryException(CameraError.VideoRecordFailed))
        }
        val stopResult = CompletableDeferred<Result<String?>>()
        pendingStopResult = stopResult
        videoRecordingState.value = VideoRecordingState.Finalizing
        runCatching { recording.stop() }
            .onFailure { onVideoRecordingFinalized(uri = null, error = it) }
        return stopResult.await()
    }

    // 録画の開始前に失敗したことを状態へ反映し、失敗結果を返す。
    private fun failVideoRecordingStart(): Result<Unit> {
        videoRecordingState.value = VideoRecordingState.Failed(CameraError.VideoRecordFailed)
        return Result.failure(CameraRepositoryException(CameraError.VideoRecordFailed))
    }

    // 録画の終了を受けて状態を確定する。停止要求への応答だけでなく、開始時の権限拒否など
    // 録画側から終了した場合も同じ経路で失敗として扱う。
    private fun onVideoRecordingFinalized(uri: String?, error: Throwable?) {
        activeRecording = null
        val result: Result<String?> = if (error == null && uri != null) {
            videoRecordingState.value = VideoRecordingState.Succeeded(uri)
            Result.success(uri)
        } else {
            videoRecordingState.value = VideoRecordingState.Failed(CameraError.VideoRecordFailed)
            Result.failure(CameraRepositoryException(CameraError.VideoRecordFailed))
        }
        pendingStopResult?.complete(result)
        pendingStopResult = null
    }

    override fun observePhotoDeletionState(): Flow<PhotoDeletionState> = photoDeletionState

    override suspend fun deletePhoto(uri: String): Result<Unit> {
        photoDeletionState.value = PhotoDeletionState.Deleting
        val deleted = runCatching { photoFileDeleter(uri) }.getOrDefault(false)
        return if (deleted) {
            photoDeletionState.value = PhotoDeletionState.Succeeded
            Result.success(Unit)
        } else {
            photoDeletionState.value = PhotoDeletionState.Failed(CameraError.PhotoDeleteFailed)
            Result.failure(CameraRepositoryException(CameraError.PhotoDeleteFailed))
        }
    }

    // ネイティブ側でプレビュー開始が完了したことを状態へ反映する。
    override fun onPlatformPreviewStarted(lensFacing: CameraLensFacing) {
        if (pendingLensFacing == null || pendingLensFacing == lensFacing) {
            pendingLensFacing = lensFacing
            previewState.value = PreviewState.Showing
        }
    }

    // ネイティブ側のプレビュー開始失敗を状態へ反映する。
    override fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError) {
        if (pendingLensFacing == lensFacing) {
            pendingLensFacing = null
            previewState.value = PreviewState.Error(error)
        }
    }

    override fun observeZoomState(): Flow<CameraZoomUiState> = zoomUiState


    override fun onPlatformZoomStateChanged(zoomUiState: CameraZoomUiState) {
        this.zoomUiState.value = zoomUiState
    }

    override fun setZoomRatio(updatedZoomRatio: Float) {
        cameraControl?.setZoomRatio(updatedZoomRatio)?.let(::onPlatformZoomStateChanged)
    }

    fun onPlatformCameraControlReady(cameraControl: CameraControl?) {
        this.cameraControl = cameraControl
        onPlatformZoomStateChanged(cameraControl?.zoomState() ?: CameraZoomUiState())
    }

    fun onPlatformPhotoCapturerReady(photoCapturer: IOSPhotoCapturer?) {
        this.photoCapturer = photoCapturer
    }

    fun onPlatformVideoRecorderReady(videoRecorder: IOSVideoRecorder?) {
        this.videoRecorder = videoRecorder
    }
}

// 指定レンズが使えない場合に代替レンズを含めて利用可否を解決する。
internal fun resolveAvailableLens(
    requested: CameraLensFacing,
    hasLens: IOSCameraLensAvailability,
): CameraLensFacing? {
    if (hasLens(requested)) {
        return requested
    }

    val fallback = requested.toggled()
    return fallback.takeIf(hasLens)
}

internal fun Float.coerceInZoomRange(minZoomRatio: Float, maxZoomRatio: Float): Float =
    coerceIn(minZoomRatio, maxZoomRatio)

// 撮影で書き出したローカルファイルを削除する。既に存在しない場合も削除成功として扱う。
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal fun deletePhotoFile(uri: String): Boolean {
    val fileManager = NSFileManager.defaultManager
    val path = uri.toLocalFilePath() ?: return false
    if (!fileManager.fileExistsAtPath(path)) {
        return true
    }
    return fileManager.removeItemAtPath(path, error = null)
}

private fun String.toLocalFilePath(): String? {
    return when {
        startsWith("file:") -> NSURL.URLWithString(this)?.path
        startsWith("/") -> this
        else -> null
    }
}
