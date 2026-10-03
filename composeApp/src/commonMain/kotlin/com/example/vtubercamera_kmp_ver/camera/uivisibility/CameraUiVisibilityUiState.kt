package com.example.vtubercamera_kmp_ver.camera.uivisibility

// 操作 UI を隠す「UI 非表示モード」が ON かどうかを保持する。ON の間は上部バーと操作バーを描かない。
data class CameraUiVisibilityUiState(
    val isHidden: Boolean = false,
)
