package com.example.vtubercamera_kmp_ver.camera

import org.jetbrains.compose.resources.StringResource

data class CameraUiState(
    val lensFacing: CameraLensFacing = CameraLensFacing.Back,
    val permissionState: PermissionState = PermissionState.Unknown,
    val previewState: PreviewState = PreviewState.Preparing,
    val errorState: CameraError? = null,
    val message: CameraMessage? = null,
    val faceTracking: FaceTrackingUiState = FaceTrackingUiState(),
    val avatarPreview: AvatarPreviewData? = null,
    val filePickerErrorMessageRes: StringResource? = null,
) {
    val isPermissionGranted: Boolean
        get() = permissionState == PermissionState.Granted

    val isPermissionChecking: Boolean
        get() = permissionState == PermissionState.Unknown

    val hasError: Boolean
        get() = errorState != null || previewState is PreviewState.Error
}
