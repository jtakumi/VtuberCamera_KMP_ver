package com.example.vtubercamera_kmp_ver.camera.capturemode

// シャッターボタンが担う撮影の種類を表す。[Photo] は静止画、[Video] は動画録画。
enum class CameraCaptureMode {
    Photo,
    Video,
    ;

    // トグル用に、もう一方のモードを返す。
    fun toggled(): CameraCaptureMode = when (this) {
        Photo -> Video
        Video -> Photo
    }
}
