package com.example.vtubercamera_kmp_ver.camera.settings

import androidx.compose.runtime.Stable

/**
 * 設定画面が呼び出す操作をまとめる。
 *
 * 画面の階層を通して個別の callback を渡し回さずに済むよう、1 つの値として束ねる。
 * [onPickBgm] と [onPickBackgroundImage] はプラットフォームのファイルピッカーを開く。
 */
@Stable
data class CameraSettingsActions(
    val supportsMicrophoneGain: Boolean,
    val onPickBgm: () -> Unit,
    val onClearBgm: () -> Unit,
    val onToggleBgmPlayback: () -> Unit,
    val onBgmVolumeChanged: (Float) -> Unit,
    val onBgmVolumeChangeFinished: () -> Unit,
    val onMicrophoneVolumeChanged: (Float) -> Unit,
    val onMicrophoneVolumeChangeFinished: () -> Unit,
    val onToggleBgmStartsWithRecording: () -> Unit,
    val onToggleNoiseSuppression: () -> Unit,
    val onPickBackgroundImage: () -> Unit,
    val onClearBackgroundImage: () -> Unit,
    val onBackgroundImageCropChanged: (BackgroundImageCrop) -> Unit,
    val onBackgroundImageCropChangeFinished: () -> Unit,
    val onDismissMessage: () -> Unit,
)
