package com.example.vtubercamera_kmp_ver.camera

data class CameraZoomUiState(
    val currentCameraZoomRatio: Float = 1.0f,
    val minCameraZoomRatio:Float = 1.0f,
    val maxCameraZoomRatio:Float = 1.0f,
){
    val canZoom: Boolean
        get() = maxCameraZoomRatio > minCameraZoomRatio
}
