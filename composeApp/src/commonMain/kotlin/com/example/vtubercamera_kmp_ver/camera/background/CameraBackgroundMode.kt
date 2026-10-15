package com.example.vtubercamera_kmp_ver.camera.background

import androidx.compose.ui.graphics.Color

// カメラ映像の上に重ねる背景の種類を表す。
// [Camera] 以外は不透明な単色でカメラ映像を覆い、実際の顔が写らない状態にする。
// face tracking は背景の裏で動き続けるため、どのモードでもアバターは追従する。
enum class CameraBackgroundMode {
    Camera,
    Black,
    White,
    Green,
    Blue,
    ;

    // 単色で覆うモードかどうか。カメラ映像をそのまま見せる [Camera] だけ false になる。
    val hidesCameraImage: Boolean
        get() = this != Camera

    // 単色モードで画面全体を覆う不透明な色。[Camera] は映像をそのまま見せるため null を返す。
    // 合成用途で背景を差し替えやすいよう、緑・青はクロマキー標準色に合わせる。
    val overlayColor: Color?
        get() = when (this) {
            Camera -> null
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
