package com.example.vtubercamera_kmp_ver.camera.photo

import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.PhotoDeletionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 撮影画像の削除リクエストと削除状態の監視をまとめる controller。
class PhotoDeletionController(
    private val cameraRepository: CameraRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PhotoDeletionState>(PhotoDeletionState.Idle)
    val state: StateFlow<PhotoDeletionState> = _state.asStateFlow()

    init {
        scope.launch {
            cameraRepository.observePhotoDeletionState().collect { deletionState ->
                _state.value = deletionState
            }
        }
    }

    fun deletePhoto(uri: String) {
        if (_state.value == PhotoDeletionState.Deleting) {
            return
        }
        scope.launch {
            cameraRepository.deletePhoto(uri)
        }
    }
}
