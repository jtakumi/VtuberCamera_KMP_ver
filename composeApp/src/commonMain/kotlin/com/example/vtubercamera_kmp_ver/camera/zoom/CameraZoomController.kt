package com.example.vtubercamera_kmp_ver.camera.zoom

import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.CameraZoomUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// カメラズーム倍率の取得 / 反映を担い、repository の zoom state を UI へ同期する。
class CameraZoomController(
    private val cameraRepository: CameraRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CameraZoomUiState())
    val state: StateFlow<CameraZoomUiState> = _state.asStateFlow()

    init {
        scope.launch { observeZoomState() }
    }

    // ピンチ操作によるズーム倍率の変化を repository へ反映する。
    fun onCameraZoomChanged(scaleChange: Float) {
        val zoomState = _state.value
        cameraRepository.setZoomRatio(
            (zoomState.currentCameraZoomRatio * scaleChange).coerceIn(
                zoomState.minCameraZoomRatio,
                zoomState.maxCameraZoomRatio,
            ),
        )
    }

    private suspend fun observeZoomState() {
        cameraRepository.observeZoomState().collect { zoomUiState ->
            _state.value = zoomUiState
        }
    }
}
