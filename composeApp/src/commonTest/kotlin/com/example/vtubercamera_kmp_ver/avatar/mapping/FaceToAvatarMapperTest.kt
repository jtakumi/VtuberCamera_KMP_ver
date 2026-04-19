package com.example.vtubercamera_kmp_ver.avatar.mapping

import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus
import com.example.vtubercamera_kmp_ver.camera.NormalizedFaceFrame
import kotlin.test.Test
import kotlin.test.assertEquals

class FaceToAvatarMapperTest {

    @Test
    fun mapTrackingFrameClampsAndSmoothsToRigAndExpressions() {
        val mapper = FaceToAvatarMapper(
            FaceToAvatarMapperConfig(
                smoothing = AvatarMotionSmoothingConfig(
                    trackingAlpha = 1f,
                    lostAlpha = 1f,
                ),
            ),
        )

        val mapped = mapper.map(
            frame = NormalizedFaceFrame(
                timestampMillis = 100L,
                trackingConfidence = 0.95f,
                headYawDegrees = 120f,
                headPitchDegrees = -40f,
                headRollDegrees = 35f,
                leftEyeBlink = 1.5f,
                rightEyeBlink = -0.2f,
                jawOpen = 0.4f,
                mouthSmile = 1.2f,
            ),
            previousState = AvatarRenderState.Neutral,
        )

        assertEquals(AvatarTrackingStatus.Tracking, mapped.trackingStatus)
        assertEquals(40f, mapped.rig.headYawDegrees)
        assertEquals(-25f, mapped.rig.headPitchDegrees)
        assertEquals(30f, mapped.rig.headRollDegrees)
        assertEquals(1f, mapped.expressions.leftEyeBlink)
        assertEquals(0f, mapped.expressions.rightEyeBlink)
        assertEquals(0.4f, mapped.expressions.jawOpen)
        assertEquals(1f, mapped.expressions.mouthSmile)
        assertEquals(100L, mapped.sourceTimestampMillis)
    }

    @Test
    fun lowConfidenceFrameMovesToLostAndDecaysTowardNeutral() {
        val mapper = FaceToAvatarMapper(
            FaceToAvatarMapperConfig(
                trackingConfidenceThreshold = 0.7f,
                smoothing = AvatarMotionSmoothingConfig(
                    trackingAlpha = 1f,
                    lostAlpha = 0.5f,
                ),
            ),
        )

        val previous = AvatarRenderState(
            trackingStatus = AvatarTrackingStatus.Tracking,
            rig = AvatarRigState(
                headYawDegrees = 20f,
                headPitchDegrees = -10f,
            ),
            expressions = AvatarExpressionWeights(
                leftEyeBlink = 0.6f,
                rightEyeBlink = 0.2f,
                jawOpen = 0.8f,
                mouthSmile = 0.5f,
            ),
            sourceTimestampMillis = 50L,
            trackingConfidence = 1f,
        )

        val mapped = mapper.map(
            frame = NormalizedFaceFrame(
                timestampMillis = 120L,
                trackingConfidence = 0.3f,
                headYawDegrees = 15f,
                headPitchDegrees = 10f,
                headRollDegrees = 4f,
                leftEyeBlink = 0.1f,
                rightEyeBlink = 0.2f,
                jawOpen = 0.7f,
                mouthSmile = 0.4f,
            ),
            previousState = previous,
        )

        assertEquals(AvatarTrackingStatus.Lost, mapped.trackingStatus)
        assertEquals(10f, mapped.rig.headYawDegrees)
        assertEquals(-5f, mapped.rig.headPitchDegrees)
        assertEquals(0.3f, mapped.expressions.leftEyeBlink)
        assertEquals(0.1f, mapped.expressions.rightEyeBlink)
        assertEquals(0.4f, mapped.expressions.jawOpen)
        assertEquals(0.25f, mapped.expressions.mouthSmile)
        assertEquals(120L, mapped.sourceTimestampMillis)
    }

    @Test
    fun nullFrameTransitionsToNotTrackedAndContinuesDecay() {
        val mapper = FaceToAvatarMapper(
            FaceToAvatarMapperConfig(
                smoothing = AvatarMotionSmoothingConfig(
                    trackingAlpha = 1f,
                    lostAlpha = 0.25f,
                ),
            ),
        )

        val previous = AvatarRenderState(
            trackingStatus = AvatarTrackingStatus.Lost,
            rig = AvatarRigState(
                headYawDegrees = 8f,
                headRollDegrees = 12f,
            ),
            expressions = AvatarExpressionWeights(
                leftEyeBlink = 0.5f,
                jawOpen = 0.4f,
                mouthSmile = 0.8f,
            ),
            sourceTimestampMillis = 777L,
            trackingConfidence = 0.2f,
        )

        val mapped = mapper.map(frame = null, previousState = previous)

        assertEquals(AvatarTrackingStatus.NotTracked, mapped.trackingStatus)
        assertEquals(6f, mapped.rig.headYawDegrees)
        assertEquals(9f, mapped.rig.headRollDegrees)
        assertEquals(0.375f, mapped.expressions.leftEyeBlink)
        assertEquals(0.3f, mapped.expressions.jawOpen)
        assertEquals(0.6f, mapped.expressions.mouthSmile)
        assertEquals(777L, mapped.sourceTimestampMillis)
        assertEquals(0f, mapped.trackingConfidence)
    }
}
