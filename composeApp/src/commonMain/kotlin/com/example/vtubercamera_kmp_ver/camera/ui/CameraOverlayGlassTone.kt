package com.example.vtubercamera_kmp_ver.camera.ui

import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundMode
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassTone

/**
 * 背景プリセットの明るさに合わせて、オーバーレイ UI が使う Liquid Glass の明暗を決める。
 *
 * ガラスは背後を透過するため、白系プリセットの上では明るいガラス + 暗い前景に切り替えないと
 * 文字とアイコンが読めなくなる。カメラ映像は明るさが一定しないので、常に読める暗いガラスを使う。
 */
internal val CameraBackgroundMode.overlayGlassTone: LiquidGlassTone
    get() = when (this) {
        CameraBackgroundMode.White -> LiquidGlassTone.Light
        CameraBackgroundMode.Camera,
        CameraBackgroundMode.Black,
        CameraBackgroundMode.Green,
        CameraBackgroundMode.Blue,
        -> LiquidGlassTone.Dark
    }
