package com.example.vtubercamera_kmp_ver.camera.background

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// カメラ映像を覆う背景モードを保持し、切り替え操作を一元管理する。
class CameraBackgroundController {
    private val _state = MutableStateFlow(CameraBackgroundUiState())
    val state: StateFlow<CameraBackgroundUiState> = _state.asStateFlow()

    // 切り替えチップからの操作で、背景モードを宣言順の次へ巡回させる。
    fun onToggleBackgroundMode() {
        _state.update { backgroundState ->
            backgroundState.copy(mode = backgroundState.mode.next())
        }
    }
}
