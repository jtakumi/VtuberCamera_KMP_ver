package com.example.vtubercamera_kmp_ver.camera

import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_lens_switch_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_permission_denied
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_preview_initialization_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_unavailable
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_unknown

enum class PermissionState {
    Unknown,
    Granted,
    Denied,
}

sealed interface PreviewState {
    data object Preparing : PreviewState

    data object Showing : PreviewState

    data class Error(val error: CameraError) : PreviewState
}

enum class CameraError {
    PermissionDenied,
    CameraUnavailable,
    PreviewInitializationFailed,
    LensSwitchFailed,
    Unknown,
}

enum class CameraMessageType {
    Guide,
    Error,
}

data class CameraMessage(
    val type: CameraMessageType,
    val messageRes: StringResource,
    val formatArgs: List<Any> = emptyList(),
)

fun CameraError.toCameraMessage(): CameraMessage {
    val messageRes = when (this) {
        CameraError.PermissionDenied -> Res.string.camera_error_permission_denied
        CameraError.CameraUnavailable -> Res.string.camera_error_unavailable
        CameraError.PreviewInitializationFailed -> {
            Res.string.camera_error_preview_initialization_failed
        }
        CameraError.LensSwitchFailed -> Res.string.camera_error_lens_switch_failed
        CameraError.Unknown -> Res.string.camera_error_unknown
    }
    return CameraMessage(
        type = CameraMessageType.Error,
        messageRes = messageRes,
    )
}

class CameraRepositoryException(val error: CameraError) : Exception(error.name)

interface CameraRepository {
    suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing>

    suspend fun stopPreview()

    suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing>

    suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing>

    fun observePreviewState(): Flow<PreviewState>

    fun onPlatformPreviewStarted(lensFacing: CameraLensFacing)

    fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError)
}

interface PermissionRepository {
    suspend fun checkCameraPermission(): PermissionState

    suspend fun requestCameraPermission(): PermissionState
}
