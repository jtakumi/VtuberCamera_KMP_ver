package com.example.vtubercamera_kmp_ver.camera.uivisibility

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraUiVisibilityControllerTest {
    @Test
    fun initialState_showsUi() {
        val controller = CameraUiVisibilityController()

        assertFalse(controller.state.value.isHidden)
    }

    @Test
    fun onHideUi_turnsHiddenModeOn() {
        val controller = CameraUiVisibilityController()

        controller.onHideUi()

        assertTrue(controller.state.value.isHidden)
    }

    @Test
    fun onHideUi_keepsHiddenModeOnWhenCalledAgain() {
        val controller = CameraUiVisibilityController()

        controller.onHideUi()
        controller.onHideUi()

        assertTrue(controller.state.value.isHidden)
    }

    @Test
    fun onShowUi_turnsHiddenModeOffSoUiCanBeRevealed() {
        val controller = CameraUiVisibilityController()
        controller.onHideUi()

        controller.onShowUi()

        assertFalse(controller.state.value.isHidden)
    }

    @Test
    fun onShowUi_keepsUiVisibleWhenAlreadyVisible() {
        val controller = CameraUiVisibilityController()

        controller.onShowUi()

        assertFalse(controller.state.value.isHidden)
    }

    @Test
    fun hideThenShow_canBeRepeated() {
        val controller = CameraUiVisibilityController()

        repeat(times = 3) {
            controller.onHideUi()
            assertTrue(controller.state.value.isHidden)

            controller.onShowUi()
            assertFalse(controller.state.value.isHidden)
        }
    }
}
