package com.example.vtubercamera_kmp_ver.camera.photo

import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.PhotoCaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoCaptureController(
    private val cameraRepository: CameraRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PhotoCaptureState>(PhotoCaptureState.Idle)
    val state: StateFlow<PhotoCaptureState> = _state.asStateFlow()

    init {
        scope.launch {
            cameraRepository.observePhotoCaptureState().collect { captureState ->
                _state.value = captureState
            }
        }
    }

    fun capturePhoto() {
        if (_state.value == PhotoCaptureState.Capturing) {
            return
        }
        scope.launch {
            cameraRepository.capturePhoto()
        }
    }

    // 完了 / 失敗の結果を通知済みとして [PhotoCaptureState.Idle] へ戻す。
    // 結果が残ったままだと、動画録画など別の操作の通知を古い写真の結果が隠してしまう。
    // 撮影中の状態は守るため何もしない。
    fun onOutcomeAcknowledged() {
        _state.update { captureState ->
            when (captureState) {
                is PhotoCaptureState.Succeeded,
                is PhotoCaptureState.Failed,
                -> PhotoCaptureState.Idle
                PhotoCaptureState.Idle,
                PhotoCaptureState.Capturing,
                -> captureState
            }
        }
    }
}
