package com.example.vtubercamera_kmp_ver.camera.video

import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.VideoRecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 動画録画の開始 / 停止操作を repository へ中継し、録画状態を UI へ公開する。
// 失敗は repository が [VideoRecordingState.Failed] として状態へ載せるため、ここでは結果を再送しない。
class VideoRecordingController(
    private val cameraRepository: CameraRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<VideoRecordingState>(VideoRecordingState.Idle)
    val state: StateFlow<VideoRecordingState> = _state.asStateFlow()

    init {
        scope.launch {
            cameraRepository.observeVideoRecordingState().collect { recordingState ->
                _state.value = recordingState
            }
        }
    }

    // 録画ボタンからの操作で、待機中なら録画を開始し、録画中なら停止する。
    // 書き出し中は二重操作になるため何もしない。
    fun onToggleRecording() {
        when (_state.value) {
            VideoRecordingState.Recording -> scope.launch { cameraRepository.stopVideoRecording() }
            VideoRecordingState.Finalizing -> Unit
            VideoRecordingState.Idle,
            is VideoRecordingState.Succeeded,
            is VideoRecordingState.Failed,
            -> scope.launch { cameraRepository.startVideoRecording() }
        }
    }

    // 完了 / 失敗の結果を通知済みとして [VideoRecordingState.Idle] へ戻す。
    // 結果が残ったままだと、別の操作の最中に古い結果バナーが再表示されてしまう。
    // 録画中と書き出し中は進行中の状態を守るため何もしない。
    fun onOutcomeAcknowledged() {
        _state.update { recordingState ->
            when (recordingState) {
                is VideoRecordingState.Succeeded,
                is VideoRecordingState.Failed,
                -> VideoRecordingState.Idle
                VideoRecordingState.Idle,
                VideoRecordingState.Recording,
                VideoRecordingState.Finalizing,
                -> recordingState
            }
        }
    }
}
