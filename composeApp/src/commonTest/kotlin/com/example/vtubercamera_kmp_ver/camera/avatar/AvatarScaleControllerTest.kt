package com.example.vtubercamera_kmp_ver.camera.avatar

import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarScaleControllerTest {
    @Test
    fun initialState_startsAtDefaultAvatarScale() {
        val controller = AvatarScaleController()

        assertEquals(DEFAULT_AVATAR_SCALE, controller.state.value.currentAvatarScale)
        assertEquals(MIN_AVATAR_SCALE, controller.state.value.minAvatarScale)
        assertEquals(MAX_AVATAR_SCALE, controller.state.value.maxAvatarScale)
    }

    @Test
    fun onAvatarScaleChanged_multipliesCurrentScale() {
        val controller = AvatarScaleController()

        controller.onAvatarScaleChanged(1.5f)

        assertEquals(1.5f, controller.state.value.currentAvatarScale)
    }

    @Test
    fun onAvatarScaleChanged_accumulatesAcrossGestureUpdates() {
        val controller = AvatarScaleController()

        controller.onAvatarScaleChanged(1.2f)
        controller.onAvatarScaleChanged(1.5f)

        assertEquals(1.8f, controller.state.value.currentAvatarScale, absoluteTolerance = 1e-5f)
    }

    @Test
    fun onAvatarScaleChanged_clampsAboveMax() {
        val controller = AvatarScaleController()

        controller.onAvatarScaleChanged(10f)

        assertEquals(MAX_AVATAR_SCALE, controller.state.value.currentAvatarScale)
    }

    @Test
    fun onAvatarScaleChanged_clampsBelowMin() {
        val controller = AvatarScaleController()

        controller.onAvatarScaleChanged(0.01f)

        assertEquals(MIN_AVATAR_SCALE, controller.state.value.currentAvatarScale)
    }

    @Test
    fun onAvatarScaleChanged_ignoresNonPositiveOrNonFiniteScaleChange() {
        val controller = AvatarScaleController()
        controller.onAvatarScaleChanged(1.5f)

        controller.onAvatarScaleChanged(0f)
        controller.onAvatarScaleChanged(-2f)
        controller.onAvatarScaleChanged(Float.NaN)
        controller.onAvatarScaleChanged(Float.POSITIVE_INFINITY)

        assertEquals(1.5f, controller.state.value.currentAvatarScale)
    }
}
