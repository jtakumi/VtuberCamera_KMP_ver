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
                headTranslationX = 0.5f,
                headTranslationZ = -0.4f,
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
        assertEquals(18f, mapped.rig.bodySwayDegrees)
        assertEquals(-11.2f, mapped.rig.bodyLeanDegrees)
        assertEquals(1f, mapped.expressions.leftEyeBlink)
        assertEquals(0f, mapped.expressions.rightEyeBlink)
        assertEquals(0.4f, mapped.expressions.jawOpen)
        assertEquals(1f, mapped.expressions.mouthSmile)
        assertEquals(100L, mapped.sourceTimestampMillis)
    }

    @Test
    fun lowConfidenceFrameReturnsAvatarTowardFront() {
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
        assertEquals(0f, mapped.rig.headRollDegrees)
        assertEquals(0f, mapped.rig.bodySwayDegrees)
        assertEquals(0f, mapped.rig.bodyLeanDegrees)
        assertEquals(0.3f, mapped.expressions.leftEyeBlink)
        assertEquals(0.1f, mapped.expressions.rightEyeBlink)
        assertEquals(0.4f, mapped.expressions.jawOpen)
        assertEquals(0.25f, mapped.expressions.mouthSmile)
        assertEquals(120L, mapped.sourceTimestampMillis)
    }

    @Test
    fun mapTrackingFrameUsesHeadPoseForBodyMotionWhenTranslationIsUnavailable() {
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
                trackingConfidence = 1f,
                headYawDegrees = 30f,
                headPitchDegrees = -20f,
                headRollDegrees = 0f,
                leftEyeBlink = 0f,
                rightEyeBlink = 0f,
                jawOpen = 0f,
                mouthSmile = 0f,
            ),
        )

        assertEquals(expected = 15f, actual = mapped.rig.bodySwayDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = -8f, actual = mapped.rig.bodyLeanDegrees, absoluteTolerance = 0.0001f)
    }

    @Test
    fun firstTrackedFrameFacesMeasuredDirectionInsteadOfFront() {
        val mapper = FaceToAvatarMapper()

        val mapped = mapper.map(
            frame = NormalizedFaceFrame(
                timestampMillis = 100L,
                trackingConfidence = 1f,
                headYawDegrees = 32f,
                headPitchDegrees = -14f,
                headRollDegrees = 9f,
                leftEyeBlink = 0f,
                rightEyeBlink = 0f,
                jawOpen = 0f,
                mouthSmile = 0f,
            ),
            previousState = AvatarRenderState.Neutral,
        )

        // 認識開始時にニュートラルを基準にせず、最初のフレームからその場の顔の向きを再現する。
        assertEquals(AvatarTrackingStatus.Tracking, mapped.trackingStatus)
        assertEquals(32f, mapped.rig.headYawDegrees)
        assertEquals(-14f, mapped.rig.headPitchDegrees)
        assertEquals(9f, mapped.rig.headRollDegrees)
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
