package com.example.vtubercamera_kmp_ver.camera.facetracking

import com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus
import com.example.vtubercamera_kmp_ver.camera.NormalizedFaceFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FaceTrackingPresenterTest {

    @Test
    fun onFaceTrackingFrameChanged_withFrame_storesRawFrameAndMarksTracking() {
        val presenter = FaceTrackingPresenter()
        val frame = NormalizedFaceFrame(
            timestampMillis = 100L,
            trackingConfidence = 0.9f,
            headYawDegrees = 12.4f,
            headPitchDegrees = -8.6f,
            headRollDegrees = 30.2f,
            leftEyeBlink = -0.1f,
            rightEyeBlink = 0.453f,
            jawOpen = 1.3f,
            mouthSmile = 0.995f,
        )

        presenter.onFaceTrackingFrameChanged(frame)

        assertEquals(frame, presenter.state.value.faceTracking.frame)
        assertEquals(true, presenter.state.value.faceTracking.isTracking)
    }

    @Test
    fun onFaceTrackingFrameChanged_withNullFrame_clearsDisplayAndMarksNotTracking() {
        val presenter = FaceTrackingPresenter()
        presenter.onFaceTrackingFrameChanged(
            NormalizedFaceFrame(
                timestampMillis = 1L,
                trackingConfidence = 0.9f,
                headYawDegrees = 0f,
                headPitchDegrees = 0f,
                headRollDegrees = 0f,
                leftEyeBlink = 0f,
                rightEyeBlink = 0f,
                jawOpen = 0f,
                mouthSmile = 0f,
            ),
        )

        presenter.onFaceTrackingFrameChanged(null)

        assertFalse(presenter.state.value.faceTracking.isTracking)
        assertNull(presenter.state.value.faceTracking.frame)
        assertEquals(
            AvatarTrackingStatus.NotTracked,
            presenter.state.value.avatarRender.trackingStatus,
        )
    }

    @Test
    fun onFaceTrackingFrameChanged_lowConfidence_transitionsToLost() {
        val presenter = FaceTrackingPresenter()
        presenter.onFaceTrackingFrameChanged(
            NormalizedFaceFrame(
                timestampMillis = 100L,
                trackingConfidence = 0.95f,
                headYawDegrees = 5f,
                headPitchDegrees = 5f,
                headRollDegrees = 5f,
                leftEyeBlink = 0f,
                rightEyeBlink = 0f,
                jawOpen = 0f,
                mouthSmile = 0f,
            ),
        )
        assertEquals(
            AvatarTrackingStatus.Tracking,
            presenter.state.value.avatarRender.trackingStatus,
        )

        presenter.onFaceTrackingFrameChanged(
            NormalizedFaceFrame(
                timestampMillis = 101L,
                trackingConfidence = 0.2f,
                headYawDegrees = 5f,
                headPitchDegrees = 5f,
                headRollDegrees = 5f,
                leftEyeBlink = 0f,
                rightEyeBlink = 0f,
                jawOpen = 0f,
                mouthSmile = 0f,
            ),
        )

        assertEquals(
            AvatarTrackingStatus.Lost,
            presenter.state.value.avatarRender.trackingStatus,
        )
    }
}
