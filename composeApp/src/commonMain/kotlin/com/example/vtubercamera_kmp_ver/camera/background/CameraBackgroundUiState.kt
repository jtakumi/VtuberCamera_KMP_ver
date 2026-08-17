package com.example.vtubercamera_kmp_ver.camera.background

import com.example.vtubercamera_kmp_ver.camera.CameraUiDefaults

// 現在選択している背景モードを保持する。
data class CameraBackgroundUiState(
    val mode: CameraBackgroundMode = DEFAULT_CAMERA_BACKGROUND_MODE,
) {
    // カメラ映像を単色で覆っているかどうか。overlay を描くかの判定に使う。
    val hidesCameraImage: Boolean
        get() = mode.hidesCameraImage
}

val DEFAULT_CAMERA_BACKGROUND_MODE = CameraUiDefaults.initialBackgroundMode
