package com.example.vtubercamera_kmp_ver.camera.gesture

import kotlin.test.Test
import kotlin.test.assertEquals

class PinchGestureControllerTest {
    @Test
    fun initialState_targetsCameraZoom() {
        val controller = PinchGestureController()

        assertEquals(PinchGestureTarget.CameraZoom, controller.state.value)
    }

    @Test
    fun onTogglePinchTarget_switchesBetweenCameraZoomAndAvatarScale() {
        val controller = PinchGestureController()

        controller.onTogglePinchTarget()
        assertEquals(PinchGestureTarget.AvatarScale, controller.state.value)

        controller.onTogglePinchTarget()
        assertEquals(PinchGestureTarget.CameraZoom, controller.state.value)
    }
}
