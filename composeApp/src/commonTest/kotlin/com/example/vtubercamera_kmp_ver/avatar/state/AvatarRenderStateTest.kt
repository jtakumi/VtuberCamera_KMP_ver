package com.example.vtubercamera_kmp_ver.avatar.state

import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarRenderStateTest {

    @Test
    fun neutralStateStartsFromZeroAndNotTracked() {
        val state = AvatarRenderState.Neutral

        assertEquals(AvatarRigState(), state.rig)
        assertEquals(AvatarExpressionWeights(), state.expressions)
        assertEquals(AvatarTrackingStatus.NotTracked, state.trackingStatus)
        assertEquals(0f, state.trackingConfidence)
        assertEquals(null, state.sourceTimestampMillis)
        assertFalse(state.isTracking)
    }

    @Test
    fun lostTrackingStateCanKeepLastAvatarPoseForDecay() {
        val state = AvatarRenderState(
            rig = AvatarRigState(
                headYawDegrees = 10f,
                headPitchDegrees = -5f,
                headRollDegrees = 3f,
            ),
            expressions = AvatarExpressionWeights(
                leftEyeBlink = 0.8f,
                rightEyeBlink = 0.75f,
                jawOpen = 0.4f,
                mouthSmile = 0.6f,
            ),
            trackingStatus = AvatarTrackingStatus.Lost,
            trackingConfidence = 0.35f,
            sourceTimestampMillis = 1234L,
        )

        assertEquals(AvatarTrackingStatus.Lost, state.trackingStatus)
        assertEquals(10f, state.rig.headYawDegrees)
        assertEquals(0.8f, state.expressions.leftEyeBlink)
        assertEquals(1234L, state.sourceTimestampMillis)
        assertFalse(state.isTracking)
    }

    @Test
    fun trackingStateReportsActiveTracking() {
        val state = AvatarRenderState(trackingStatus = AvatarTrackingStatus.Tracking)

        assertTrue(state.isTracking)
    }
}
