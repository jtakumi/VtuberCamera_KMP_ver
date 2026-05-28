package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.camera.avatar.AvatarSelectionUiState
import com.example.vtubercamera_kmp_ver.camera.permission.CameraPermissionUiState
import com.example.vtubercamera_kmp_ver.camera.session.CameraSessionUiState

// カメラ画面で描画する共有 UI 状態を、ドメインごとの sub-state を束ねて保持する。
data class CameraUiState(
    val session: CameraSessionUiState = CameraSessionUiState(),
    val permission: CameraPermissionUiState = CameraPermissionUiState(),
    val zoom: CameraZoomUiState = CameraZoomUiState(),
    val faceTracking: FaceTrackingUiState = FaceTrackingUiState(),
    val avatarRender: AvatarRenderState = AvatarRenderState.Neutral,
    val avatarSelection: AvatarSelectionUiState = AvatarSelectionUiState(),
) {
    val isPermissionGranted: Boolean
        get() = permission.permissionState == PermissionState.Granted

    val isPermissionChecking: Boolean
        get() = permission.permissionState == PermissionState.Unknown

    val hasError: Boolean
        get() = session.errorState != null || session.previewState is PreviewState.Error

    val avatarPreview: AvatarPreviewData?
        get() = avatarSelection.avatarSelection?.preview
}

internal const val DEFAULT_CAMERA_ZOOM_SCALE = 1f
internal const val MIN_CAMERA_ZOOM_SCALE = 1f
internal const val MAX_CAMERA_ZOOM_SCALE = 5f
