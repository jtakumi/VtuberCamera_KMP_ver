package com.example.vtubercamera_kmp_ver.camera.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraBackgroundControllerTest {
    @Test
    fun initialState_showsCameraImage() {
        val controller = CameraBackgroundController()

        assertEquals(DEFAULT_CAMERA_BACKGROUND_MODE, controller.state.value.mode)
        assertFalse(controller.state.value.hidesCameraImage)
    }

    @Test
    fun onToggleBackgroundMode_walksThroughEveryPresetAndReturnsToCamera() {
        val controller = CameraBackgroundController()
        val expectedModes = CameraBackgroundMode.entries.drop(1) + CameraBackgroundMode.Camera

        val visitedModes = expectedModes.map {
            controller.onToggleBackgroundMode()
            controller.state.value.mode
        }

        assertEquals(expectedModes, visitedModes)
    }

    @Test
    fun hidesCameraImage_isTrueForEverySolidColorPreset() {
        val controller = CameraBackgroundController()

        // Camera 以外のプリセットは、カメラ映像を単色で覆って顔が映らない状態にする。
        repeat(CameraBackgroundMode.entries.size - 1) {
            controller.onToggleBackgroundMode()
            assertTrue(controller.state.value.hidesCameraImage)
        }

        controller.onToggleBackgroundMode()
        assertFalse(controller.state.value.hidesCameraImage)
    }
}
