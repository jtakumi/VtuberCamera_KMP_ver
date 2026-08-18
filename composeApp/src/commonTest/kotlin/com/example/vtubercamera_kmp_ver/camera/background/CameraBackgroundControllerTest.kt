package com.example.vtubercamera_kmp_ver.camera.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraBackgroundControllerTest {
    @Test
    fun initialState_usesGreenBackground() {
        val controller = CameraBackgroundController()

        assertEquals(DEFAULT_CAMERA_BACKGROUND_MODE, controller.state.value.mode)
        assertTrue(controller.state.value.hidesCameraImage)
    }

    @Test
    fun onToggleBackgroundMode_walksThroughEveryPresetAndReturnsToGreen() {
        val controller = CameraBackgroundController()
        val expectedModes = listOf(
            CameraBackgroundMode.Blue,
            CameraBackgroundMode.Camera,
            CameraBackgroundMode.Black,
            CameraBackgroundMode.White,
            CameraBackgroundMode.Green,
        )

        val visitedModes = expectedModes.map {
            controller.onToggleBackgroundMode()
            controller.state.value.mode
        }

        assertEquals(expectedModes, visitedModes)
    }

    @Test
    fun hidesCameraImage_reflectsTheSelectedPreset() {
        val controller = CameraBackgroundController()

        assertTrue(controller.state.value.hidesCameraImage)
        controller.onToggleBackgroundMode()
        assertTrue(controller.state.value.hidesCameraImage)
        controller.onToggleBackgroundMode()
        kotlin.test.assertFalse(controller.state.value.hidesCameraImage)
    }
}
