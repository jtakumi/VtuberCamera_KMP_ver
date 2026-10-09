package com.example.vtubercamera_kmp_ver.camera.photo

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.CameraMessageType
import com.example.vtubercamera_kmp_ver.camera.PhotoCaptureState
import com.example.vtubercamera_kmp_ver.camera.testing.FakeCameraRepository
import com.example.vtubercamera_kmp_ver.camera.toCameraMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class PhotoCaptureControllerTest {
    @Test
    fun capturePhoto_publishesSucceededWithSavedUri() = runTest {
        val controller = PhotoCaptureController(FakeCameraRepository(), controllerScope())

        controller.capturePhoto()
        advanceUntilIdle()

        assertEquals(PhotoCaptureState.Succeeded("fake://photo.jpg"), controller.state.value)
    }

    @Test
    fun succeededState_announcesGallerySaveAsGuide() {
        val message = PhotoCaptureState.Succeeded("fake://photo.jpg").toCameraMessage()

        assertEquals(CameraMessageType.Guide, message?.type)
    }

    @Test
    fun failedState_announcesErrorAndIdleStaysSilent() {
        assertEquals(
            CameraMessageType.Error,
            PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed).toCameraMessage()?.type,
        )
        assertNull(PhotoCaptureState.Idle.toCameraMessage())
        assertNull(PhotoCaptureState.Capturing.toCameraMessage())
    }

    @Test
    fun onOutcomeAcknowledged_resetsSucceededToIdle() = runTest {
        val controller = PhotoCaptureController(FakeCameraRepository(), controllerScope())
        controller.capturePhoto()
        advanceUntilIdle()

        controller.onOutcomeAcknowledged()

        assertEquals(PhotoCaptureState.Idle, controller.state.value)
    }

    @Test
    fun onOutcomeAcknowledged_keepsIdle() = runTest {
        val controller = PhotoCaptureController(FakeCameraRepository(), controllerScope())

        controller.onOutcomeAcknowledged()

        assertEquals(PhotoCaptureState.Idle, controller.state.value)
    }

    private fun TestScope.controllerScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
}
