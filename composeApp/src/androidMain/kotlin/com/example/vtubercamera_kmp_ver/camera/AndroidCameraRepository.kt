package com.example.vtubercamera_kmp_ver.camera

import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.example.vtubercamera_kmp_ver.avatar.render.AndroidAvatarFrameProvider
import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundMode
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageSettings
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia
import com.example.vtubercamera_kmp_ver.camera.video.VideoRecordingAudioOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import kotlin.coroutines.resume

internal fun Bitmap.withExifOrientation(file: File): Bitmap {
    val orientation = runCatching {
        ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun savePhotoBitmapToTempFile(bitmap: Bitmap): String? {
    val outputFile = runCatching { File.createTempFile("vtuber-camera-composed-", ".jpg") }
        .getOrNull() ?: return null
    return runCatching {
        val saved = outputFile.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, stream)
        }
        if (saved) outputFile.toURI().toString() else null
    }.getOrNull().also { uri ->
        if (uri == null) outputFile.delete()
    }
}

internal interface CameraLensAvailability {
    fun hasCamera(lensFacing: CameraLensFacing): Boolean
}

internal class AndroidCameraRepository(
    private val cameraAvailabilityProvider: suspend () -> CameraLensAvailability,
    private val previewState: MutableStateFlow<PreviewState> = MutableStateFlow(PreviewState.Preparing),
    private val photoCaptureState: MutableStateFlow<PhotoCaptureState> = MutableStateFlow(PhotoCaptureState.Idle),
    private val photoDeletionState: MutableStateFlow<PhotoDeletionState> = MutableStateFlow(PhotoDeletionState.Idle),
    private val videoRecordingState: MutableStateFlow<VideoRecordingState> = MutableStateFlow(VideoRecordingState.Idle),
    private val zoomUiState: MutableStateFlow<CameraZoomUiState> = MutableStateFlow(CameraZoomUiState()),
    // 既定では撮影で書き出したローカルファイルを削除する。テストや content:// 対応で差し替え可能にする。
    private val photoFileDeleter: (String) -> Boolean = ::deletePhotoFile,
    // 合成前の写真を一時ファイルへ撮影し、合成後の画像は photoBitmapSaver で保存する。
    private val photoOutputFactory: () -> PhotoCaptureOutput = ::createTempFilePhotoOutput,
    private val photoBitmapSaver: (Bitmap) -> String? = ::savePhotoBitmapToTempFile,
    // 取り込み済みの背景画像や BGM を実体のファイルへ解決する。写真の合成と録画の BGM 混合で使う。
    private val storedMediaFileResolver: (StoredMedia) -> File? = { null },
) : CameraRepository {
    private var pendingLensFacing: CameraLensFacing? = null
    private var cameraControl: CameraControl? = null
    private var imageCapture: ImageCapture? = null
    private var videoRecorder: AndroidVideoRecorder? = null
    private var photoBackgroundColor: Int = CAMERA_BACKGROUND_GREEN
    private var photoBackgroundImage: BackgroundImageSettings = BackgroundImageSettings()

    // 録画セッションの管理。いずれも main thread からだけ触る（呼び出し元と recorder の完了通知が main）。
    private var activeRecording: AndroidVideoRecording? = null
    private var pendingStopResult: CompletableDeferred<Result<String?>>? = null

    override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
        val cameraAvailability = cameraAvailabilityProvider()
        val resolvedLens = cameraAvailability.resolveLensFacing(lensFacing)
        if (!cameraAvailability.hasCamera(resolvedLens)) {
            previewState.value = PreviewState.Error(CameraError.CameraUnavailable)
            return Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
        }
        pendingLensFacing = resolvedLens
        previewState.value = PreviewState.Preparing
        return Result.success(resolvedLens)
    }

    override suspend fun stopPreview() {
        pendingLensFacing = null
        previewState.value = PreviewState.Preparing
    }

    override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
        val cameraAvailability = cameraAvailabilityProvider()
        val resolvedLens = cameraAvailability.resolveLensFacing(preferred)
        return if (cameraAvailability.hasCamera(resolvedLens)) {
            Result.success(resolvedLens)
        } else {
            Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
        }
    }

    override fun observePreviewState(): Flow<PreviewState> = previewState

    override fun observePhotoCaptureState(): Flow<PhotoCaptureState> = photoCaptureState

    override suspend fun capturePhoto(): Result<String?> {
        val capture = imageCapture
            ?: return Result.failure<String?>(
                CameraRepositoryException(CameraError.PhotoCaptureFailed),
            ).also {
                photoCaptureState.value = PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed)
            }
        photoCaptureState.value = PhotoCaptureState.Capturing
        val output = runCatching { photoOutputFactory() }.getOrElse {
            return failPhotoCapture()
        }
        val avatarProviderAvailable = AndroidAvatarFrameProvider.hasProvider()
        val avatarBitmap = withContext(Dispatchers.Main) {
            AndroidAvatarFrameProvider.capture()
        }
        if (avatarProviderAvailable && avatarBitmap == null) {
            output.discard()
            return failPhotoCapture()
        }

        val captureResult = runCatching {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { output.discard() }
                    runCatching {
                        capture.takePicture(
                            output.options,
                            Runnable::run,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    if (continuation.isActive) continuation.resume(Result.failure(exception))
                                }
                            },
                        )
                    }.onFailure { error ->
                        if (continuation.isActive) continuation.resume(Result.failure(error))
                    }
                }
            }
        }.getOrElse { Result.failure(it) }

        if (captureResult.isFailure) {
            output.discard()
            avatarBitmap?.recycle()
            return failPhotoCapture()
        }

        val backgroundImageSettings = photoBackgroundImage
        val photoUri = runCatching {
            withContext(Dispatchers.IO) {
                val backgroundBitmap = backgroundImageSettings.image?.let(::decodeStoredBackgroundBitmap)
                val cameraFile = output.file
                val cameraBitmap = BitmapFactory.decodeFile(cameraFile.absolutePath)
                    ?: error("Captured photo could not be decoded")
                val uprightBitmap = cameraBitmap.withExifOrientation(cameraFile)
                if (uprightBitmap !== cameraBitmap) cameraBitmap.recycle()

                try {
                    val composedBitmap = avatarBitmap?.let {
                        compositePhoto(
                            cameraBitmap = uprightBitmap,
                            avatarBitmap = it,
                            backgroundColor = photoBackgroundColor,
                            backgroundImage = backgroundBitmap,
                            backgroundImageCrop = backgroundImageSettings.crop,
                        )
                    } ?: uprightBitmap
                    try {
                        photoBitmapSaver(composedBitmap)
                    } finally {
                        if (composedBitmap !== uprightBitmap) composedBitmap.recycle()
                    }
                } finally {
                    uprightBitmap.recycle()
                    avatarBitmap?.recycle()
                    backgroundBitmap?.recycle()
                }
            }
        }.getOrNull()
        output.discard()

        return if (photoUri != null) {
            photoCaptureState.value = PhotoCaptureState.Succeeded(photoUri)
            Result.success(photoUri)
        } else {
            failPhotoCapture()
        }
    }

    private fun failPhotoCapture(): Result<String?> {
        photoCaptureState.value = PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed)
        return Result.failure(CameraRepositoryException(CameraError.PhotoCaptureFailed))
    }

    override fun observeVideoRecordingState(): Flow<VideoRecordingState> = videoRecordingState

    // アプリ画面の録画を開始する。録画中、recorder が未準備、開始に失敗したときは
    // [CameraError.VideoRecordFailed] で失敗する。すでに録画中の呼び出しは状態を変えずに失敗だけ返す。
    override suspend fun startVideoRecording(audioOptions: VideoRecordingAudioOptions): Result<Unit> {
        if (activeRecording != null) {
            return Result.failure(CameraRepositoryException(CameraError.VideoRecordFailed))
        }
        val recorder = videoRecorder ?: return failVideoRecordingStart()
        val recording = runCatching { recorder.startRecording(audioOptions, ::onVideoRecordingFinalized) }
            .getOrElse { return failVideoRecordingStart() }
        activeRecording = recording
        videoRecordingState.value = VideoRecordingState.Recording
        return Result.success(Unit)
    }

    // 録画の停止を要求し、ギャラリーへの保存が完了するまで待つ。録画中でなければ状態を変えずに失敗だけ返す。
    // 保存に失敗したときは [VideoRecordingState.Failed] へ遷移する（出力の片付けは recorder が行う）。
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

    // 録画の終了を受けて状態を確定する。停止要求への応答だけでなく、ウィンドウの破棄など
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
        return withContext(Dispatchers.IO) {
            val deleted = runCatching { photoFileDeleter(uri) }.getOrDefault(false)
            if (deleted) {
                photoDeletionState.value = PhotoDeletionState.Succeeded
                Result.success(Unit)
            } else {
                photoDeletionState.value = PhotoDeletionState.Failed(CameraError.PhotoDeleteFailed)
                Result.failure(CameraRepositoryException(CameraError.PhotoDeleteFailed))
            }
        }
    }

    override fun onPlatformPreviewStarted(lensFacing: CameraLensFacing) {
        if (pendingLensFacing == null || pendingLensFacing == lensFacing) {
            pendingLensFacing = lensFacing
            previewState.value = PreviewState.Showing
        }
    }

    override fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError) {
        if (pendingLensFacing == lensFacing) {
            pendingLensFacing = null
            previewState.value = PreviewState.Error(error)
        }
    }

    override fun observeZoomState():Flow<CameraZoomUiState> = zoomUiState

    override fun onPlatformZoomStateChanged(zoomUiState: CameraZoomUiState) {
        this.zoomUiState.value = zoomUiState
    }

    override fun setZoomRatio(updatedZoomRatio: Float){
        cameraControl?.setZoomRatio(updatedZoomRatio)
    }

    fun onPlatformCameraControlReady(cameraControl: CameraControl) {
        this.cameraControl = cameraControl
    }

    fun onPlatformImageCaptureReady(imageCapture: ImageCapture?) {
        this.imageCapture = imageCapture
    }

    fun onPlatformBackgroundModeChanged(backgroundMode: CameraBackgroundMode) {
        photoBackgroundColor = when (backgroundMode) {
            CameraBackgroundMode.Black -> CAMERA_BACKGROUND_BLACK
            CameraBackgroundMode.White -> CAMERA_BACKGROUND_WHITE
            CameraBackgroundMode.Green -> CAMERA_BACKGROUND_GREEN
            CameraBackgroundMode.Blue -> CAMERA_BACKGROUND_BLUE
        }
    }

    /** 取り込み済みのメディアの実体を返す。解決できないときは null を返す。 */
    fun resolveStoredMediaFile(media: StoredMedia): File? = storedMediaFileResolver(media)

    // 取り込み済みの背景画像を Bitmap へ読み込む。読めないときは null を返し、写真の合成は単色の背景で続ける。
    private fun decodeStoredBackgroundBitmap(media: StoredMedia): Bitmap? =
        storedMediaFileResolver(media)?.let { file -> BitmapFactory.decodeFile(file.path) }

    // 設定した背景画像を、撮影画像へ合成する背景として保持する。画像が無いときは単色の背景だけを合成する。
    fun onPlatformBackgroundImageChanged(backgroundImage: BackgroundImageSettings) {
        photoBackgroundImage = backgroundImage
    }

    // 画面録画の開始口。画面が破棄されるときは null に戻す。
    fun onPlatformVideoRecorderReady(videoRecorder: AndroidVideoRecorder?) {
        this.videoRecorder = videoRecorder
    }
}

private val CAMERA_BACKGROUND_BLACK: Int = 0xFF000000.toInt()
private val CAMERA_BACKGROUND_WHITE: Int = 0xFFFFFFFF.toInt()
private val CAMERA_BACKGROUND_GREEN: Int = 0xFF00B140.toInt()
private val CAMERA_BACKGROUND_BLUE: Int = 0xFF0047BB.toInt()

// 撮影で書き出したローカルファイルを削除する。既に存在しない場合も削除成功として扱う。
internal fun deletePhotoFile(uri: String): Boolean {
    val file = uri.toPhotoFileOrNull() ?: return false
    return !file.exists() || file.delete()
}

private fun String.toPhotoFileOrNull(): File? {
    return try {
        when {
            startsWith("file:") -> File(URI(this))
            startsWith("/") -> File(this)
            else -> null
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal class ProcessCameraProviderLensAvailability(
    private val cameraProvider: ProcessCameraProvider,
) : CameraLensAvailability {
    override fun hasCamera(lensFacing: CameraLensFacing): Boolean {
        return cameraProvider.hasCameraSafely(lensFacing.toCameraSelector())
    }
}

internal fun CameraLensFacing.toCameraSelector(): CameraSelector {
    return when (this) {
        CameraLensFacing.Back -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraLensFacing.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
    }
}

internal fun CameraLensAvailability.resolveLensFacing(requested: CameraLensFacing): CameraLensFacing {
    if (hasCamera(requested)) {
        return requested
    }

    val fallback = requested.toggled()
    return if (hasCamera(fallback)) {
        fallback
    } else {
        requested
    }
}

internal fun ProcessCameraProvider.resolveLensFacing(requested: CameraLensFacing): CameraLensFacing {
    return ProcessCameraProviderLensAvailability(this).resolveLensFacing(requested)
}

internal fun ProcessCameraProvider.hasCameraSafely(selector: CameraSelector): Boolean {
    return try {
        hasCamera(selector)
    } catch (_: CameraInfoUnavailableException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}
