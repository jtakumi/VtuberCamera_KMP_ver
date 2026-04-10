package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable

// 共有の画面ルートへ注入する、プラットフォーム側のカメラ関連リポジトリをまとめる。
data class CameraRepositories(
    val cameraRepository: CameraRepository,
    val permissionRepository: PermissionRepository,
)

@Composable
expect fun rememberCameraRepositories(
    permissionController: CameraPermissionController,
): CameraRepositories
