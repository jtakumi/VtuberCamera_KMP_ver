package com.example.vtubercamera_kmp_ver.camera.background

import androidx.compose.ui.graphics.Color

// カメラ映像を隠す不透明な背景の種類を表す。
// face tracking は背景の裏で動き続けるため、どのモードでもアバターは追従する。
enum class CameraBackgroundMode {
    Black,
    White,
    Green,
    Blue,
    ;

    // すべての背景モードでカメラ映像を隠す。
    val hidesCameraImage: Boolean
        get() = true

    // 画面全体を覆う不透明な色。
    // 合成用途で背景を差し替えやすいよう、緑・青はクロマキー標準色に合わせる。
    val overlayColor: Color
        get() = when (this) {
            Black -> Color(0xFF000000)
            White -> Color(0xFFFFFFFF)
            Green -> Color(0xFF00B140)
            Blue -> Color(0xFF0047BB)
        }

    // 切り替えチップ用に、宣言順で次のモードを返す。末尾からは先頭へ巡回する。
    fun next(): CameraBackgroundMode {
        val modes = entries
        return modes[(ordinal + 1) % modes.size]
    }
}
