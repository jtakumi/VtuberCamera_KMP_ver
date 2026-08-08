package com.example.vtubercamera_kmp_ver.camera.gesture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ピンチ操作の割り当て先を保持し、カメラズームとアバター表示倍率の切り替えを一元管理する。
class PinchGestureController {
    private val _state = MutableStateFlow(PinchGestureTarget.CameraZoom)
    val state: StateFlow<PinchGestureTarget> = _state.asStateFlow()

    // 切り替えボタンからの操作で、ピンチ操作の割り当て先をもう一方へ入れ替える。
    fun onTogglePinchTarget() {
        _state.update { target -> target.toggled() }
    }
}
