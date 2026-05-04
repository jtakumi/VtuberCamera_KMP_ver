package com.example.vtubercamera_kmp_ver.camera

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class IOSFaceTrackingSupportTest {
    @Test
    fun didAddDidUpdateDidRemoveAnchors_emitFramesAndClearTracking() {
        var nowSeconds = 0.0
        val emittedFrames = mutableListOf<NormalizedFaceFrame?>()
        val delegateCore = createDelegateCore(
            currentTimeSeconds = { nowSeconds },
            emittedFrames = emittedFrames,
        )

        delegateCore.didAddAnchors(listOf(TestFaceAnchor(yawDegrees = 10f)))
        nowSeconds = 0.05
        delegateCore.didUpdateAnchors(listOf(TestFaceAnchor(yawDegrees = 20f)))
        delegateCore.didRemoveAnchors(listOf(TestFaceAnchor(yawDegrees = 30f)))

        assertEquals(listOf(10f, 20f, null), emittedFrames.map { it?.headYawDegrees })
    }

    @Test
    fun didChangeTrackingState_updatesConfidenceAndClearsWhenUnavailable() {
        var nowSeconds = 0.0
        val emittedFrames = mutableListOf<NormalizedFaceFrame?>()
        val delegateCore = createDelegateCore(
            currentTimeSeconds = { nowSeconds },
            emittedFrames = emittedFrames,
        )

        delegateCore.didAddAnchors(listOf(TestFaceAnchor(yawDegrees = 10f)))
        delegateCore.didChangeTrackingState(IOSFaceTrackingState.Limited)
        nowSeconds = 0.05
        delegateCore.didUpdateAnchors(listOf(TestFaceAnchor(yawDegrees = 20f)))
        delegateCore.didChangeTrackingState(IOSFaceTrackingState.Normal)
        nowSeconds = 0.1
        delegateCore.didUpdateAnchors(listOf(TestFaceAnchor(yawDegrees = 30f)))
        delegateCore.didChangeTrackingState(IOSFaceTrackingState.Unavailable)

        assertEquals(4, emittedFrames.size)
        assertEquals(1f, emittedFrames[0]?.trackingConfidence, absoluteTolerance = 0.0001f)
        assertEquals(
            IOS_LIMITED_TRACKING_CONFIDENCE,
            emittedFrames[1]?.trackingConfidence,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(1f, emittedFrames[2]?.trackingConfidence, absoluteTolerance = 0.0001f)
        assertEquals(null, emittedFrames[3])
    }

    @Test
    fun didFailOrInterrupt_clearsTrackedFaceAndResetsThrottle() {
        var nowSeconds = 0.0
        val emittedFrames = mutableListOf<NormalizedFaceFrame?>()
        val delegateCore = createDelegateCore(
            currentTimeSeconds = { nowSeconds },
            emittedFrames = emittedFrames,
        )

        delegateCore.didAddAnchors(listOf(TestFaceAnchor(yawDegrees = 10f)))
        delegateCore.didFailOrInterrupt()
        nowSeconds = 0.01
        delegateCore.didAddAnchors(listOf(TestFaceAnchor(yawDegrees = 20f)))

        assertEquals(listOf(10f, null, 20f), emittedFrames.map { it?.headYawDegrees })
    }

    @Test
    fun frameDispatchThrottle_suppressesFramesInsideThirtyFpsWindow() {
        var nowSeconds = 0.0
        val emittedFrames = mutableListOf<NormalizedFaceFrame?>()
        val delegateCore = createDelegateCore(
            currentTimeSeconds = { nowSeconds },
            emittedFrames = emittedFrames,
        )

        delegateCore.didAddAnchors(listOf(TestFaceAnchor(yawDegrees = 10f)))
        nowSeconds = 0.01
        delegateCore.didUpdateAnchors(listOf(TestFaceAnchor(yawDegrees = 20f)))
        nowSeconds = 0.04
        delegateCore.didUpdateAnchors(listOf(TestFaceAnchor(yawDegrees = 30f)))

        assertEquals(listOf(10f, 30f), emittedFrames.map { it?.headYawDegrees })
    }

    @Test
    fun iosHeadPoseDegreesFromRotationMatrix_convertsYawPitchAndRollToDegrees() {
        val yawRadians = 30f * PI.toFloat() / 180f
        val pitchRadians = 10f * PI.toFloat() / 180f
        val rollRadians = -20f * PI.toFloat() / 180f
        val pose = iosHeadPoseDegreesFromRotationMatrix(
            matrix02 = -sin(pitchRadians),
            matrix12 = sin(rollRadians) * cos(pitchRadians),
            matrix22 = cos(rollRadians) * cos(pitchRadians),
            matrix01 = sin(yawRadians) * cos(pitchRadians),
            matrix00 = cos(yawRadians) * cos(pitchRadians),
        )

        assertEquals(expected = 30f, actual = pose.yawDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 10f, actual = pose.pitchDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = -20f, actual = pose.rollDegrees, absoluteTolerance = 0.0001f)
    }

    @Test
    fun toNormalizedFaceFrame_clampsBlendShapesAndAppliesSmoothing() {
        val previousFrame = NormalizedFaceFrame(
            timestampMillis = 10L,
            trackingConfidence = 0.9f,
            headYawDegrees = 0f,
            headPitchDegrees = 0f,
            headRollDegrees = 0f,
            leftEyeBlink = 0.2f,
            rightEyeBlink = 0.8f,
            jawOpen = 0.3f,
            mouthSmile = 0.1f,
        )

        val smoothedFrame = IOSHeadPoseDegrees(
            yawDegrees = 10f,
            pitchDegrees = 4f,
            rollDegrees = 6f,
        ).toNormalizedFaceFrame(
            timestampMillis = 42L,
            trackingConfidence = 0.65f,
            blendShapes = IOSFaceTrackingBlendShapeValues(
                leftEyeBlink = 1.2f,
                rightEyeBlink = -0.4f,
                jawOpen = 1.4f,
                smileLeft = 0.8f,
                smileRight = -0.3f,
            ),
            previousFrame = previousFrame,
        )

        assertEquals(expected = -4.5f, actual = smoothedFrame.headYawDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 1.8f, actual = smoothedFrame.headPitchDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = -2.4f, actual = smoothedFrame.headRollDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.64f, actual = smoothedFrame.leftEyeBlink, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.576f, actual = smoothedFrame.rightEyeBlink, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.706f, actual = smoothedFrame.jawOpen, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.205f, actual = smoothedFrame.mouthSmile, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.65f, actual = smoothedFrame.trackingConfidence, absoluteTolerance = 0.0001f)
    }

    private fun createDelegateCore(
        currentTimeSeconds: () -> Double,
        emittedFrames: MutableList<NormalizedFaceFrame?>,
    ): IOSFaceTrackingDelegateCore<TestFaceAnchor> {
        return IOSFaceTrackingDelegateCore(
            firstFaceAnchor = { anchors -> anchors.firstNotNullOfOrNull { it as? TestFaceAnchor } },
            convertFaceAnchor = { anchor, trackingConfidence, previousFrame ->
                anchor.toNormalizedFaceFrame(
                    trackingConfidence = trackingConfidence,
                    previousFrame = previousFrame,
                )
            },
            currentTimeSeconds = currentTimeSeconds,
            onFaceTrackingFrameChanged = { frame -> emittedFrames += frame },
        )
    }

    private fun TestFaceAnchor.toNormalizedFaceFrame(
        trackingConfidence: Float,
        previousFrame: NormalizedFaceFrame?,
    ): NormalizedFaceFrame {
        return NormalizedFaceFrame(
            timestampMillis = (previousFrame?.timestampMillis ?: 0L) + 1L,
            trackingConfidence = trackingConfidence,
            headYawDegrees = yawDegrees,
            headPitchDegrees = 0f,
            headRollDegrees = 0f,
            leftEyeBlink = 0f,
            rightEyeBlink = 0f,
            jawOpen = 0f,
            mouthSmile = 0f,
        )
    }

    private data class TestFaceAnchor(
        val yawDegrees: Float,
    )
}
