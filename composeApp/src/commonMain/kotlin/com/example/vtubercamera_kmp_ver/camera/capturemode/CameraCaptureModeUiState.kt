package com.example.vtubercamera_kmp_ver.camera.capturemode

// 現在選択している撮影モードを保持する。起動時は従来どおり写真撮影から始める。
data class CameraCaptureModeUiState(
    val mode: CameraCaptureMode = CameraCaptureMode.Photo,
)
