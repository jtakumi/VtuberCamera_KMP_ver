package com.example.vtubercamera_kmp_ver.camera

import kotlinx.coroutines.flow.Flow

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
    val text: String,
)

interface CameraRepository {
    suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing>

    suspend fun stopPreview()

    suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing>

    suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing>

    fun observePreviewState(): Flow<PreviewState>
}

interface PermissionRepository {
    suspend fun checkCameraPermission(): PermissionState

    suspend fun requestCameraPermission(): PermissionState
}
