package com.example.vtubercamera_kmp_ver.camera

import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal interface CameraLensAvailability {
    fun hasCamera(lensFacing: CameraLensFacing): Boolean
}

internal class AndroidCameraRepository(
    private val cameraAvailabilityProvider: suspend () -> CameraLensAvailability,
    private val previewState: MutableStateFlow<PreviewState> = MutableStateFlow(PreviewState.Preparing),
) : CameraRepository {
    private var pendingLensFacing: CameraLensFacing? = null

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
