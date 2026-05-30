package com.example.vtubercamera_kmp_ver.camera.session

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.CameraLensFacing
import com.example.vtubercamera_kmp_ver.camera.CameraMessage
import com.example.vtubercamera_kmp_ver.camera.PreviewState

// プレビュー / レンズ / セッションエラー / 案内メッセージなどカメラセッション固有の UI 状態。
data class CameraSessionUiState(
    val lensFacing: CameraLensFacing = CameraLensFacing.Back,
    val previewState: PreviewState = PreviewState.Preparing,
    val errorState: CameraError? = null,
    val message: CameraMessage? = null,
)
