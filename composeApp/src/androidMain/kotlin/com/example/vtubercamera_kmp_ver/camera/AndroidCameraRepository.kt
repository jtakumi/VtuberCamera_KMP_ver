package com.example.vtubercamera_kmp_ver.camera

import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

internal interface CameraLensAvailability {
    fun hasCamera(lensFacing: CameraLensFacing): Boolean
}

internal class AndroidCameraRepository(
    private val cameraAvailabilityProvider: suspend () -> CameraLensAvailability,
    private val previewState: MutableStateFlow<PreviewState> = MutableStateFlow(PreviewState.Preparing),
    private val photoCaptureState: MutableStateFlow<PhotoCaptureState> = MutableStateFlow(PhotoCaptureState.Idle),
    private val zoomUiState: MutableStateFlow<CameraZoomUiState> = MutableStateFlow(CameraZoomUiState())
) : CameraRepository {
    private var pendingLensFacing: CameraLensFacing? = null
    private var cameraControl: CameraControl? = null
    private var imageCapture: ImageCapture? = null

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

    override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
        val cameraAvailability = cameraAvailabilityProvider()
        previewState.value = PreviewState.Preparing
        val targetLens = current.toggled()
        if (!cameraAvailability.hasCamera(targetLens)) {
            previewState.value = PreviewState.Error(CameraError.LensSwitchFailed)
            return Result.failure(CameraRepositoryException(CameraError.LensSwitchFailed))
        }
        pendingLensFacing = targetLens
        return Result.success(targetLens)
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
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val outputFile = File.createTempFile("vtuber-camera-", ".jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                capture.takePicture(
                    outputOptions,
                    Runnable::run,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val uri = outputFileResults.savedUri?.toString() ?: outputFile.toURI().toString()
                            photoCaptureState.value = PhotoCaptureState.Succeeded(uri)
                            continuation.resume(Result.success(uri))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            photoCaptureState.value = PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed)
                            continuation.resume(
                                Result.failure(CameraRepositoryException(CameraError.PhotoCaptureFailed)),
                            )
                        }
                    },
                )
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
