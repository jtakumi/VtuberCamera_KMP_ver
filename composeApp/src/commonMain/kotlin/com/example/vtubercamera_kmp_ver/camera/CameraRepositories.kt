package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable

// Groups the platform camera and permission repositories injected into the shared route.
data class CameraRepositories(
    val cameraRepository: CameraRepository,
    val permissionRepository: PermissionRepository,
)

@Composable
expect fun rememberCameraRepositories(
    permissionController: CameraPermissionController,
): CameraRepositories
