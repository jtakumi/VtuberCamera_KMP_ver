package com.example.vtubercamera_kmp_ver.camera.ui

import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundMode
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassTone
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraOverlayGlassToneTest {

    @Test
    fun overlayGlassTone_usesLightGlassOnWhiteBackground() {
        assertEquals(LiquidGlassTone.Light, CameraBackgroundMode.White.overlayGlassTone)
    }

    @Test
    fun overlayGlassTone_usesDarkGlassOnCameraImage() {
        assertEquals(LiquidGlassTone.Dark, CameraBackgroundMode.Camera.overlayGlassTone)
    }

    @Test
    fun overlayGlassTone_usesDarkGlassOnRemainingPresets() {
        val darkGlassModes = listOf(
            CameraBackgroundMode.Black,
            CameraBackgroundMode.Green,
            CameraBackgroundMode.Blue,
        )

        for (mode in darkGlassModes) {
            assertEquals(LiquidGlassTone.Dark, mode.overlayGlassTone, "unexpected tone for $mode")
        }
    }

    @Test
    fun overlayGlassTone_coversEveryBackgroundMode() {
        val tones = CameraBackgroundMode.entries.map { it.overlayGlassTone }

        assertEquals(CameraBackgroundMode.entries.size, tones.size)
    }
}
