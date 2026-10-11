package com.example.vtubercamera_kmp_ver.camera.video

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.VideoRecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 動画録画の開始 / 停止操作を repository へ中継し、録画状態を UI へ公開する。
// 録画は画面そのものを撮るため、開始前に [VideoRecordingState.Starting] を公開して操作 UI を先に隠させ、
// UI が消えるのを待ってから録画を始める。UI の映り込みを避けるための順序である。
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

    // 録画ボタンまたは録画中の画面タップからの操作で、待機中なら録画を開始し、録画中なら停止する。
    // 開始準備中と保存中は二重操作になるため何もしない。
    fun onToggleRecording() {
        when (_state.value) {
            VideoRecordingState.Recording -> scope.launch { cameraRepository.stopVideoRecording() }
            VideoRecordingState.Starting,
            VideoRecordingState.Finalizing,
            -> Unit
            VideoRecordingState.Idle,
            is VideoRecordingState.Succeeded,
            is VideoRecordingState.Failed,
            -> startRecording()
        }
    }

    // 操作 UI を隠させてから録画を開始する。repository の状態が変わらないまま失敗した場合
    // （直前の失敗と同じ値で失敗したときなど）は、準備中のまま固まらないよう失敗へ遷移させる。
    private fun startRecording() {
        _state.value = VideoRecordingState.Starting
        scope.launch {
            delay(UI_HIDE_SETTLE_MILLIS)
            val startResult = cameraRepository.startVideoRecording()
            if (startResult.isFailure && _state.value == VideoRecordingState.Starting) {
                _state.value = VideoRecordingState.Failed(CameraError.VideoRecordFailed)
            }
        }
    }

    // 完了 / 失敗の結果を通知済みとして [VideoRecordingState.Idle] へ戻す。
    // 結果が残ったままだと、別の操作の最中に古い結果バナーが再表示されてしまう。
    // 進行中の状態は守るため何もしない。
    fun onOutcomeAcknowledged() {
        _state.update { recordingState ->
            when (recordingState) {
                is VideoRecordingState.Succeeded,
                is VideoRecordingState.Failed,
                -> VideoRecordingState.Idle
                VideoRecordingState.Idle,
                VideoRecordingState.Starting,
                VideoRecordingState.Recording,
                VideoRecordingState.Finalizing,
                -> recordingState
            }
        }
    }
}

// 操作 UI が recomposition で画面から消えるのを待つ時間。これより短いと最初の数フレームへ UI が映り込む。
private const val UI_HIDE_SETTLE_MILLIS = 300L
