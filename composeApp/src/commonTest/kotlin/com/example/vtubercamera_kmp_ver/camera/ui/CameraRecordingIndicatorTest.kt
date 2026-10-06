package com.example.vtubercamera_kmp_ver.camera.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraRecordingIndicatorTest {
    @Test
    fun toElapsedTimeLabel_padsSecondsAndMinutes() {
        assertEquals("00:00", 0L.toElapsedTimeLabel())
        assertEquals("00:07", 7L.toElapsedTimeLabel())
        assertEquals("01:05", 65L.toElapsedTimeLabel())
    }

    @Test
    fun toElapsedTimeLabel_keepsMinutesBeyondOneHour() {
        assertEquals("61:01", 3_661L.toElapsedTimeLabel())
    }

    @Test
    fun toElapsedTimeLabel_treatsNegativeValueAsZero() {
        assertEquals("00:00", (-3L).toElapsedTimeLabel())
    }
}
