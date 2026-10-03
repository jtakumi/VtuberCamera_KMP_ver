package com.example.vtubercamera_kmp_ver.camera.uivisibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 操作 UI を隠す「UI 非表示モード」の ON / OFF を保持し、切り替え操作を一元管理する。
class CameraUiVisibilityController {
    private val _state = MutableStateFlow(CameraUiVisibilityUiState())
    val state: StateFlow<CameraUiVisibilityUiState> = _state.asStateFlow()

    // 非表示ボタンからの操作で、UI 非表示モードを ON にする。すでに ON のときは何も変えない。
    fun onHideUi() {
        _state.update { visibilityState -> visibilityState.copy(isHidden = true) }
    }

    // UI 非表示モード中の画面タップからの操作で、モードを OFF にして操作 UI を再表示する。
    // 表示中に呼ばれても何も変えないため、タップと他の操作が重なっても状態が壊れない。
    fun onShowUi() {
        _state.update { visibilityState -> visibilityState.copy(isHidden = false) }
    }
}
