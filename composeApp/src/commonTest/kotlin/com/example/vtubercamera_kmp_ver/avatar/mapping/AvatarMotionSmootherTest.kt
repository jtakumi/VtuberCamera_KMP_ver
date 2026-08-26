package com.example.vtubercamera_kmp_ver.avatar.mapping

import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus
import kotlin.test.Test
import kotlin.test.assertTrue

class AvatarMotionSmootherTest {
    @Test
    fun oneEuroFilterRespondsMoreStronglyToFastMotion() {
        val smoother = AvatarMotionSmoother(
            AvatarMotionSmoothingConfig(minCutoff = 0.5f, beta = 0.2f),
        )
        var state = trackingState(0f, 0L)
        state = smoother.smooth(AvatarRenderState.Neutral, state)

        val slow = smoother.smooth(state, trackingState(1f, 100L))
        val fast = smoother.smooth(slow, trackingState(30f, 110L))

        val slowProgress = slow.rig.headYawDegrees
        val fastProgress = fast.rig.headYawDegrees - slow.rig.headYawDegrees
        assertTrue(fastProgress > slowProgress)
    }

    @Test
    fun notTrackedDecaysBodyPoseAlongWithHead() {
        val smoother = AvatarMotionSmoother(
            AvatarMotionSmoothingConfig(lostAlpha = 0.25f),
        )
        val previous = AvatarRenderState(
            rig = AvatarRigState(bodySwayDegrees = 8f, bodyLeanDegrees = 4f),
            trackingStatus = AvatarTrackingStatus.Tracking,
        )

        val result = smoother.smooth(
            previous,
            AvatarRenderState(trackingStatus = AvatarTrackingStatus.NotTracked),
        )

        assertTrue(result.rig.bodySwayDegrees == 6f)
        assertTrue(result.rig.bodyLeanDegrees == 3f)
    }

    @Test
    fun lostTrackingFollowsMeasuredPoseAndDecaysOnlyExpressions() {
        val smoother = AvatarMotionSmoother(
            AvatarMotionSmoothingConfig(trackingAlpha = 1f, lostAlpha = 0.25f),
        )
        val previous = AvatarRenderState(
            rig = AvatarRigState(headYawDegrees = 0f),
            expressions = AvatarExpressionWeights(jawOpen = 0.8f),
            trackingStatus = AvatarTrackingStatus.Tracking,
        )

        val result = smoother.smooth(
            previous,
            AvatarRenderState(
                rig = AvatarRigState(headYawDegrees = 24f),
                trackingStatus = AvatarTrackingStatus.Lost,
                sourceTimestampMillis = 100L,
            ),
        )

        assertTrue(result.rig.headYawDegrees == 24f)
        assertTrue(result.expressions.jawOpen == 0.6f)
    }

    private fun trackingState(yaw: Float, timestamp: Long) = AvatarRenderState(
        rig = AvatarRigState(headYawDegrees = yaw),
        trackingStatus = AvatarTrackingStatus.Tracking,
        sourceTimestampMillis = timestamp,
    )
}
