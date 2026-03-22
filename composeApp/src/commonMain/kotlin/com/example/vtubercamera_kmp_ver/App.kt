package com.example.vtubercamera_kmp_ver

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.vtubercamera_kmp_ver.camera.CameraRoute
import com.example.vtubercamera_kmp_ver.theme.VtuberCameraTheme

@Composable
@Preview
fun App() {
    VtuberCameraTheme {
        CameraRoute()
    }
}
