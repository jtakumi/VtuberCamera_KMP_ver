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
    val photoCapture: PhotoCaptureState = PhotoCaptureState.Idle,
    val photoDeletion: PhotoDeletionState = PhotoDeletionState.Idle,
    val capturedPhotoUri: String? = null,
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

    val isDeletingPhoto: Boolean
        get() = photoDeletion == PhotoDeletionState.Deleting

    // 削除可能な撮影画像があり、かつ削除処理中でないときだけ削除導線を出す。
    val canDeletePhoto: Boolean
        get() = capturedPhotoUri != null && !isDeletingPhoto
}

internal const val DEFAULT_CAMERA_ZOOM_SCALE = 1f
internal const val MIN_CAMERA_ZOOM_SCALE = 1f
internal const val MAX_CAMERA_ZOOM_SCALE = 5f
