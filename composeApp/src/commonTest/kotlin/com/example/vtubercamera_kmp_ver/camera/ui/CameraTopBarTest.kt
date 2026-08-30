package com.example.vtubercamera_kmp_ver.camera.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraTopBarTest {

    @Test
    fun toRatioLabel_keepsOneDecimalForExactRatio() {
        assertEquals("1.0x", 1.0f.toRatioLabel())
    }

    @Test
    fun toRatioLabel_roundsToNearestTenth() {
        assertEquals("1.3x", 1.25f.toRatioLabel())
        assertEquals("2.0x", 2.04f.toRatioLabel())
    }

    @Test
    fun toRatioLabel_keepsWholePartForRatioOverTen() {
        assertEquals("10.0x", 10.0f.toRatioLabel())
    }
}
