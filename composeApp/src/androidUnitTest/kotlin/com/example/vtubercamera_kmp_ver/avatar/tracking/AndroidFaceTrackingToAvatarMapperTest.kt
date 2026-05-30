package com.example.vtubercamera_kmp_ver.avatar.tracking

import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidFaceTrackingToAvatarMapperTest {

    @Test
    fun map_trackingStateAppliesAndroidRendererGainAndExpressionEmphasis() {
        val mapped = AndroidFaceTrackingToAvatarMapper().map(
            AvatarRenderState(
                rig = AvatarRigState(
                    headYawDegrees = 50f,
                    headPitchDegrees = -40f,
                    headRollDegrees = 40f,
                ),
                expressions = AvatarExpressionWeights(
                    leftEyeBlink = 0.92f,
                    rightEyeBlink = 0.51f,
                    jawOpen = 0.6f,
                    mouthSmile = 0.43f,
                ),
                trackingStatus = AvatarTrackingStatus.Tracking,
                trackingConfidence = 0.95f,
                sourceTimestampMillis = 100L,
            ),
        )

        assertEquals(expected = 45f, actual = mapped.rig.headYawDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = -30f, actual = mapped.rig.headPitchDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 35f, actual = mapped.rig.headRollDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 1f, actual = mapped.expressions.leftEyeBlink, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.5f, actual = mapped.expressions.rightEyeBlink, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.72f, actual = mapped.expressions.jawOpen, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.5f, actual = mapped.expressions.mouthSmile, absoluteTolerance = 0.0001f)
        assertEquals(AvatarTrackingStatus.Tracking, mapped.trackingStatus)
        assertEquals(0.95f, mapped.trackingConfidence)
        assertEquals(100L, mapped.sourceTimestampMillis)
    }

    @Test
    fun map_nonTrackingStatePassesThroughNeutralDecayUntouched() {
        val state = AvatarRenderState(
            rig = AvatarRigState(
                headYawDegrees = 8f,
                headPitchDegrees = -4f,
                headRollDegrees = 3f,
            ),
            expressions = AvatarExpressionWeights(
                leftEyeBlink = 0.3f,
                rightEyeBlink = 0.2f,
                jawOpen = 0.4f,
                mouthSmile = 0.5f,
            ),
            trackingStatus = AvatarTrackingStatus.Lost,
            trackingConfidence = 0.2f,
            sourceTimestampMillis = 120L,
        )

        val mapped = AndroidFaceTrackingToAvatarMapper().map(state)

        assertEquals(state, mapped)
    }
}
