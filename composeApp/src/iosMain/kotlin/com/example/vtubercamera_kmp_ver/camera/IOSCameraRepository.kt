package com.example.vtubercamera_kmp_ver.camera

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

internal class IOSCameraRepository(
    private val hasLens: IOSCameraLensAvailability,
    private val previewState: MutableStateFlow<PreviewState> = MutableStateFlow(PreviewState.Preparing),
    private val photoCaptureState: MutableStateFlow<PhotoCaptureState> = MutableStateFlow(PhotoCaptureState.Idle),
    private val photoDeletionState: MutableStateFlow<PhotoDeletionState> = MutableStateFlow(PhotoDeletionState.Idle),
    private val zoomUiState: MutableStateFlow<CameraZoomUiState> = MutableStateFlow(
        CameraZoomUiState()
    ),
    // 既定では撮影で書き出したローカルファイルを削除する。テストで差し替え可能にする。
    private val photoFileDeleter: (String) -> Boolean = ::deletePhotoFile,
) : CameraRepository {
    private var pendingLensFacing: CameraLensFacing? = null

    private var cameraControl: CameraControl? = null
    private var photoCapturer: IOSPhotoCapturer? = null

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
        previewState.value = PreviewState.Preparing
    }

    // 現在と反対側のレンズへ切り替え可能か確認して反映する。
    override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
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
