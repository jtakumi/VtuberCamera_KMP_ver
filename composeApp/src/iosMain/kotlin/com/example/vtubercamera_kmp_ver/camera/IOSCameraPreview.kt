package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun rememberCameraPermissionController(): CameraPermissionController {
    return remember {
        CameraPermissionController(
            isGranted = false,
            isChecking = false,
            requestPermission = {},
        )
    }
}

@Composable
actual fun CameraPreviewHost(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("iOS camera preview is hosted by the native iOS app.")
    }
}
