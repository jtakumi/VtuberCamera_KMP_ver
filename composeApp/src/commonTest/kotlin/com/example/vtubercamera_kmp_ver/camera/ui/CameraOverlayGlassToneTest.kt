package com.example.vtubercamera_kmp_ver.camera.ui

import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundMode
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassTone
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraOverlayGlassToneTest {

    @Test
    fun overlayGlassTone_usesLightGlassOnlyForTheWhitePreset() {
        val expectedTones = mapOf(
            CameraBackgroundMode.Camera to LiquidGlassTone.Dark,
            CameraBackgroundMode.Black to LiquidGlassTone.Dark,
            CameraBackgroundMode.White to LiquidGlassTone.Light,
            CameraBackgroundMode.Green to LiquidGlassTone.Dark,
            CameraBackgroundMode.Blue to LiquidGlassTone.Dark,
        )

        // 背景プリセットが増えたときに期待表の更新漏れへ気付けるよう、網羅も同時に確認する。
        assertEquals(CameraBackgroundMode.entries.toSet(), expectedTones.keys)

        for ((mode, expectedTone) in expectedTones) {
            assertEquals(expectedTone, mode.overlayGlassTone, "unexpected tone for $mode")
        }
    }
}
