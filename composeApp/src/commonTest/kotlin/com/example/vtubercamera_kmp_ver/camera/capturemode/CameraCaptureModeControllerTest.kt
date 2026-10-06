package com.example.vtubercamera_kmp_ver.camera.capturemode

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraCaptureModeControllerTest {
    @Test
    fun initialState_startsInPhotoMode() {
        val controller = CameraCaptureModeController()

        assertEquals(CameraCaptureMode.Photo, controller.state.value.mode)
    }

    @Test
    fun onToggleCaptureMode_switchesFromPhotoToVideo() {
        val controller = CameraCaptureModeController()

        controller.onToggleCaptureMode()

        assertEquals(CameraCaptureMode.Video, controller.state.value.mode)
    }

    @Test
    fun onToggleCaptureMode_returnsToPhotoWhenToggledTwice() {
        val controller = CameraCaptureModeController()

        controller.onToggleCaptureMode()
        controller.onToggleCaptureMode()

        assertEquals(CameraCaptureMode.Photo, controller.state.value.mode)
    }
}
