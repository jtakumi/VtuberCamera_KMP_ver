package com.example.vtubercamera_kmp_ver.camera.video

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.CameraRepositoryException
import com.example.vtubercamera_kmp_ver.camera.VideoRecordingState
import com.example.vtubercamera_kmp_ver.camera.testing.FakeCameraRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class VideoRecordingControllerTest {
    @Test
    fun initialState_isIdle() = runTest {
        val controller = createController(FakeCameraRepository())

        assertEquals(VideoRecordingState.Idle, controller.state.value)
    }

    @Test
    fun onToggleRecording_whenIdle_startsRecording() = runTest {
        val repository = FakeCameraRepository()
        val controller = createController(repository)

        controller.onToggleRecording()
        advanceUntilIdle()

        assertEquals(1, repository.startVideoRecordingCallCount)
        assertEquals(VideoRecordingState.Recording, controller.state.value)
    }

    @Test
    fun onToggleRecording_whenRecording_stopsAndPublishesSucceededUri() = runTest {
        val repository = FakeCameraRepository()
        val controller = createController(repository)
        controller.onToggleRecording()
        advanceUntilIdle()

        controller.onToggleRecording()
        advanceUntilIdle()

        assertEquals(1, repository.stopVideoRecordingCallCount)
        assertEquals(VideoRecordingState.Succeeded("fake://video.mp4"), controller.state.value)
    }

    @Test
    fun onToggleRecording_afterSucceeded_startsNextRecording() = runTest {
        val repository = FakeCameraRepository()
        val controller = createController(repository)
        controller.onToggleRecording()
        advanceUntilIdle()
        controller.onToggleRecording()

        controller.onToggleRecording()
        advanceUntilIdle()

        assertEquals(2, repository.startVideoRecordingCallCount)
        assertEquals(VideoRecordingState.Recording, controller.state.value)
    }

    @Test
    fun onToggleRecording_whenStartFails_publishesFailedAndAllowsRetry() = runTest {
        val repository = FakeCameraRepository(
            startVideoRecordingResult = Result.failure(
                CameraRepositoryException(CameraError.VideoRecordFailed),
            ),
        )
        val controller = createController(repository)

        controller.onToggleRecording()
        advanceUntilIdle()
        assertEquals(
            VideoRecordingState.Failed(CameraError.VideoRecordFailed),
            controller.state.value,
        )

        controller.onToggleRecording()
        advanceUntilIdle()
        assertEquals(2, repository.startVideoRecordingCallCount)
    }

    @Test
    fun onToggleRecording_whenStopFails_publishesFailed() = runTest {
        val repository = FakeCameraRepository(
            stopVideoRecordingResult = Result.failure(
                CameraRepositoryException(CameraError.VideoRecordFailed),
            ),
        )
        val controller = createController(repository)
        controller.onToggleRecording()
        advanceUntilIdle()

        controller.onToggleRecording()
        advanceUntilIdle()

        assertEquals(
            VideoRecordingState.Failed(CameraError.VideoRecordFailed),
            controller.state.value,
        )
    }

    @Test
    fun onOutcomeAcknowledged_resetsSucceededToIdle() = runTest {
        val controller = createController(FakeCameraRepository())
        controller.onToggleRecording()
        advanceUntilIdle()
        controller.onToggleRecording()

        controller.onOutcomeAcknowledged()
        advanceUntilIdle()

        assertEquals(VideoRecordingState.Idle, controller.state.value)
    }

    @Test
    fun onOutcomeAcknowledged_keepsRecordingState() = runTest {
        val controller = createController(FakeCameraRepository())
        controller.onToggleRecording()
        advanceUntilIdle()

        controller.onOutcomeAcknowledged()
        advanceUntilIdle()

        assertEquals(VideoRecordingState.Recording, controller.state.value)
    }

    // 状態の反映を同期的に検証できるよう、controller の collect と launch を Unconfined で動かす。
    private fun TestScope.createController(repository: FakeCameraRepository): VideoRecordingController {
        return VideoRecordingController(
            cameraRepository = repository,
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
    }
}
