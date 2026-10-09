package com.example.vtubercamera_kmp_ver.camera.capturemode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 写真 / 動画の撮影モードを保持し、切り替え操作を一元管理する。
// 録画中に切り替えてよいかの判断は、録画状態を知っている呼び出し側（ViewModel）が行う。
class CameraCaptureModeController {
    private val _state = MutableStateFlow(CameraCaptureModeUiState())
    val state: StateFlow<CameraCaptureModeUiState> = _state.asStateFlow()

    // トグルからの操作で、撮影モードを写真と動画のあいだで切り替える。
    fun onToggleCaptureMode() {
        _state.update { captureModeState ->
            captureModeState.copy(mode = captureModeState.mode.toggled())
        }
    }
}
