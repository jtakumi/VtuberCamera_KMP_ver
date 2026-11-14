package com.example.vtubercamera_kmp_ver.camera.avatar

import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarPositionControllerTest {
    @Test
    fun initialState_startsAtDefaultPosition() {
        val controller = AvatarPositionController()

        assertEquals(AvatarOffset.Zero, controller.state.offset)
        assertEquals(MAX_AVATAR_OFFSET, controller.state.maxOffset)
    }

    @Test
    fun onAvatarDragged_addsDeltaToCurrentOffset() {
        val controller = AvatarPositionController()

        controller.onAvatarDragged(deltaX = 0.1f, deltaY = -0.2f)

        assertEquals(AvatarOffset(0.1f, -0.2f), controller.state.offset)
    }

    @Test
    fun onAvatarDragged_accumulatesAcrossGestureUpdates() {
        val controller = AvatarPositionController()

        controller.onAvatarDragged(0.1f, 0.05f)
        controller.onAvatarDragged(0.1f, 0.05f)

        assertEquals(0.2f, controller.state.offset.x, absoluteTolerance = 1e-5f)
        assertEquals(0.1f, controller.state.offset.y, absoluteTolerance = 1e-5f)
    }

    @Test
    fun onAvatarDragged_clampsToMaxOffsetOnEachAxis() {
        val controller = AvatarPositionController()

        controller.onAvatarDragged(deltaX = 10f, deltaY = -10f)

        assertEquals(AvatarOffset(MAX_AVATAR_OFFSET, -MAX_AVATAR_OFFSET), controller.state.offset)
    }

    @Test
    fun onAvatarDragged_ignoresNonFiniteDelta() {
        val controller = AvatarPositionController()
        controller.onAvatarDragged(0.1f, 0.1f)

        controller.onAvatarDragged(Float.NaN, 0.2f)
        controller.onAvatarDragged(0.2f, Float.POSITIVE_INFINITY)

        assertEquals(AvatarOffset(0.1f, 0.1f), controller.state.offset)
    }
}
