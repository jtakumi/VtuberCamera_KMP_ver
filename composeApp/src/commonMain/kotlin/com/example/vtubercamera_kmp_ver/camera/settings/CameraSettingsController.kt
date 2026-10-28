package com.example.vtubercamera_kmp_ver.camera.settings

import com.example.vtubercamera_kmp_ver.camera.VideoRecordingState
import com.example.vtubercamera_kmp_ver.camera.video.RecordingBgm
import com.example.vtubercamera_kmp_ver.camera.video.VideoRecordingAudioOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_bgm_play_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_load_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_save_failed

/**
 * カメラ画面の設定（BGM・背景画像・雑音抑制）を保持し、変更の保存と BGM の再生制御を担う。
 *
 * 設定は生成時に 1 度だけ同期で読み込む。保存済みの値は数個のプリミティブとファイル名だけで小さく、
 * 画面の初回描画から正しい背景を出すために、非同期の読み込み待ちを作らない。
 * BGM の再生は録画の状態と連動するため、[onRecordingStateChanged] で録画の開始 / 終了を受け取る。
 */
class CameraSettingsController(
    private val repository: CameraSettingsRepository,
    private val bgmPlayer: BgmPlayer,
) {
    private val _state = MutableStateFlow(CameraSettingsUiState())
    val state: StateFlow<CameraSettingsUiState> = _state.asStateFlow()

    // 録画の開始に合わせて BGM を流し始めたか。録画の終了で止めるのは、この連動で流したときだけにする。
    private var isBgmStartedByRecording = false
    private var wasRecording = false

    init {
        bgmPlayer.onPlaybackFailed = ::onBgmPlaybackFailed
        repository.load()
            .onSuccess { settings -> _state.update { it.copy(settings = settings) } }
            .onFailure { _state.update { it.copy(messageRes = Res.string.settings_error_load_failed) } }
    }

    // 選んだ BGM に差し替える。再生中の BGM は止め、置き換えられた古いファイルは削除する。
    fun onBgmPicked(media: StoredMedia) {
        val previous = _state.value.settings.bgmTrack
        stopBgmPlayback()
        updateSettings { it.copy(bgmTrack = media) }
        previous?.let(::discardMedia)
    }

    // BGM の設定を外し、再生中なら止めて取り込み済みのファイルを削除する。
    fun onClearBgm() {
        val previous = _state.value.settings.bgmTrack ?: return
        stopBgmPlayback()
        updateSettings { it.copy(bgmTrack = null) }
        discardMedia(previous)
    }

    // 設定画面の再生 / 停止ボタンからの操作で、BGM を頭から流す、または止める。BGM 未設定のときは何もしない。
    fun onToggleBgmPlayback() {
        val settings = _state.value.settings
        val track = settings.bgmTrack ?: return
        if (_state.value.isBgmPlaying) {
            stopBgmPlayback()
            return
        }
        isBgmStartedByRecording = false
        startBgmPlayback(track, settings.bgmVolume, startPositionMs = 0L)
    }

    // スライダーの操作中に呼ばれる。再生中の音へ即時に反映し、保存は操作の確定時にまとめて行う。
    fun onBgmVolumeChanged(volume: Float) {
        val coercedVolume = volume.coerceIn(MIN_BGM_VOLUME, MAX_BGM_VOLUME)
        _state.update { it.copy(settings = it.settings.copy(bgmVolume = coercedVolume)) }
        bgmPlayer.setVolume(coercedVolume)
    }

    // スライダー操作の確定で、変更した音量を保存する。
    fun onBgmVolumeChangeFinished() {
        persist()
    }

    // 録画の開始と同時に BGM を流すかどうかを反転する。
    fun onToggleBgmStartsWithRecording() {
        updateSettings { it.copy(isBgmStartedWithRecording = !it.isBgmStartedWithRecording) }
    }

    // 雑音抑制の有効 / 無効を反転する。反映されるのは次に始める録画から。
    fun onToggleNoiseSuppression() {
        updateSettings { it.copy(isNoiseSuppressionEnabled = !it.isNoiseSuppressionEnabled) }
    }

    // 選んだ画像を背景に設定する。映す範囲は中央の全体表示へ戻し、置き換えられた古いファイルは削除する。
    fun onBackgroundImagePicked(media: StoredMedia) {
        val previous = _state.value.settings.backgroundImage.image
        updateSettings { it.copy(backgroundImage = BackgroundImageSettings(image = media)) }
        previous?.let(::discardMedia)
    }

    // 背景画像の設定を外して単色の背景プリセットへ戻し、取り込み済みのファイルを削除する。
    fun onClearBackgroundImage() {
        val previous = _state.value.settings.backgroundImage.image ?: return
        updateSettings { it.copy(backgroundImage = BackgroundImageSettings()) }
        discardMedia(previous)
    }

    // ドラッグ / ピンチの操作中に呼ばれる。見た目へ即時に反映し、保存は操作の確定時にまとめて行う。
    fun onBackgroundImageCropChanged(crop: BackgroundImageCrop) {
        _state.update {
            it.copy(
                settings = it.settings.copy(
                    backgroundImage = it.settings.backgroundImage.copy(crop = crop),
                ),
            )
        }
    }

    // 範囲の調整操作の確定で、変更した範囲を保存する。
    fun onBackgroundImageCropChangeFinished() {
        persist()
    }

    // ファイルピッカーが失敗したとき、その理由を設定画面へ案内する。
    fun onMediaPickFailed(messageRes: StringResource) {
        _state.update { it.copy(messageRes = messageRes) }
    }

    // 設定の保存や BGM 再生失敗の案内を閉じる。
    fun onDismissMessage() {
        _state.update { it.copy(messageRes = null) }
    }

    /**
     * 録画の状態を受け取り、録画の開始に合わせて BGM を頭から流し、終了で止める。
     *
     * 連動は [CameraSettings.isBgmStartedWithRecording] が true で BGM が設定済みのときだけ。連動で流した
     * BGM だけを録画の終了で止め、利用者が設定画面で流している BGM には触れない。
     */
    fun onRecordingStateChanged(recordingState: VideoRecordingState) {
        val isRecording = recordingState == VideoRecordingState.Recording
        val didStart = isRecording && !wasRecording
        val didStop = !isRecording && wasRecording
        wasRecording = isRecording

        if (didStart) {
            val settings = _state.value.settings
            val track = settings.bgmTrack
            if (settings.isBgmStartedWithRecording && track != null) {
                isBgmStartedByRecording = true
                startBgmPlayback(track, settings.bgmVolume, startPositionMs = 0L)
            }
        } else if (didStop && isBgmStartedByRecording) {
            isBgmStartedByRecording = false
            stopBgmPlayback()
        }
    }

    /**
     * 録画の開始時点の音声構成を返す。
     *
     * 録画と連動して流す設定ならば、BGM は頭から混ぜる。連動しない設定でも BGM がすでに流れていれば、
     * その再生位置から混ぜる。流れていなければ BGM は混ぜない。
     */
    fun recordingAudioOptions(): VideoRecordingAudioOptions {
        val settings = _state.value.settings
        val track = settings.bgmTrack
        val bgm = when {
            track == null -> null
            settings.isBgmStartedWithRecording -> RecordingBgm(track, settings.bgmVolume, startPositionMs = 0L)
            _state.value.isBgmPlaying -> RecordingBgm(track, settings.bgmVolume, bgmPlayer.positionMs)
            else -> null
        }
        return VideoRecordingAudioOptions(
            bgm = bgm,
            isNoiseSuppressionEnabled = settings.isNoiseSuppressionEnabled,
        )
    }

    // アプリが背面へ回ったとき、設定画面で流している BGM を止める。録画中は録画の音声なので止めない。
    fun onAppBackgrounded() {
        if (!wasRecording) {
            stopBgmPlayback()
        }
    }

    // 画面の破棄時に BGM を止めてプレーヤーを解放する。
    fun release() {
        bgmPlayer.onPlaybackFailed = null
        bgmPlayer.stop()
        bgmPlayer.release()
        _state.update { it.copy(isBgmPlaying = false) }
    }

    // BGM を流し始める。開始できないときは再生中扱いにせず、失敗の案内を出す。
    private fun startBgmPlayback(track: StoredMedia, volume: Float, startPositionMs: Long) {
        bgmPlayer.play(track, volume, startPositionMs)
            .onSuccess { _state.update { it.copy(isBgmPlaying = true) } }
            .onFailure {
                isBgmStartedByRecording = false
                _state.update {
                    it.copy(isBgmPlaying = false, messageRes = Res.string.settings_error_bgm_play_failed)
                }
            }
    }

    private fun stopBgmPlayback() {
        bgmPlayer.stop()
        _state.update { it.copy(isBgmPlaying = false) }
    }

    // プレーヤーが再生の途中で失敗した通知。再生中の表示を落とし、失敗の案内を出す。
    private fun onBgmPlaybackFailed() {
        isBgmStartedByRecording = false
        _state.update { it.copy(isBgmPlaying = false, messageRes = Res.string.settings_error_bgm_play_failed) }
    }

    private fun updateSettings(transform: (CameraSettings) -> CameraSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
        persist()
    }

    // 現在の設定を保存する。失敗しても画面上の設定は有効なままなので、保存できなかったことだけを案内する。
    private fun persist() {
        repository.save(_state.value.settings)
            .onFailure { _state.update { it.copy(messageRes = Res.string.settings_error_save_failed) } }
    }

    // 設定から外れたファイルを削除する。削除に失敗しても設定の動作には影響せず残るのは未参照のファイルだけで、
    // 失敗の記録はストアの実装が行うため、ここでは利用者へ案内しない。
    private fun discardMedia(media: StoredMedia) {
        repository.deleteMedia(media)
    }
}
