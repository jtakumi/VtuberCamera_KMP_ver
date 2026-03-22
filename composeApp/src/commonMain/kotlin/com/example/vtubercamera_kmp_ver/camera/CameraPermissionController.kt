package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

@Immutable
data class CameraPermissionController(
    val isGranted: Boolean,
    val isChecking: Boolean,
    val requestPermission: () -> Unit,
)

@Immutable
data class FilePickerLauncher(
    val launch: () -> Unit,
)

@Composable
expect fun rememberCameraPermissionController(): CameraPermissionController

@Composable
expect fun rememberFilePickerLauncher(): FilePickerLauncher

@Composable
expect fun CameraPreviewHost(
    modifier: Modifier = Modifier,
    lensFacing: CameraLensFacing,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
)
