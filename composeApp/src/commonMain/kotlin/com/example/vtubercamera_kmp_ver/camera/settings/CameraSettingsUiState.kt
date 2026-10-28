package com.example.vtubercamera_kmp_ver.camera.settings

import org.jetbrains.compose.resources.StringResource

/**
 * 設定画面と、設定を参照する各レイヤーが描画に使う状態。
 *
 * [isBgmPlaying] は設定画面や録画連動で BGM を再生中かどうか。[messageRes] は設定の保存や BGM の再生に
 * 失敗したときに設定画面へ出す案内で、利用者が閉じるまで残す。
 */
data class CameraSettingsUiState(
    val settings: CameraSettings = CameraSettings(),
    val isBgmPlaying: Boolean = false,
    val messageRes: StringResource? = null,
)
