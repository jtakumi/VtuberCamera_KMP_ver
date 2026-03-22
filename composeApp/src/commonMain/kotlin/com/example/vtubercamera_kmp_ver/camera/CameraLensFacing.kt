package com.example.vtubercamera_kmp_ver.camera

enum class CameraLensFacing {
    Back,
    Front,
}

fun CameraLensFacing.toggled(): CameraLensFacing {
    return when (this) {
        CameraLensFacing.Back -> CameraLensFacing.Front
        CameraLensFacing.Front -> CameraLensFacing.Back
    }
}
