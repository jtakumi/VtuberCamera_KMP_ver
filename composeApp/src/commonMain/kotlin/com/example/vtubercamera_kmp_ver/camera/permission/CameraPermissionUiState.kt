package com.example.vtubercamera_kmp_ver.camera.permission

import com.example.vtubercamera_kmp_ver.camera.PermissionState

// カメラ権限の確認・要求の進行状態を保持する。
data class CameraPermissionUiState(
    val permissionState: PermissionState = PermissionState.Unknown,
)
