package com.example.vtubercamera_kmp_ver.camera.photo

import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.PhotoCaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}
