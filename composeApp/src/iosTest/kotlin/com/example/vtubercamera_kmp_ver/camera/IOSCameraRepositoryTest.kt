package com.example.vtubercamera_kmp_ver.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class IOSCameraRepositoryTest {
    @Test
    fun startPreview_fallsBackToAvailableLensWhenRequestedLensIsMissing() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Front))

        val result = repository.startPreview(CameraLensFacing.Back)

        assertEquals(CameraLensFacing.Front, result.getOrNull())
        assertEquals(PreviewState.Preparing, repository.observePreviewState().first())
    }

    @Test
    fun resolveInitialLens_returnsCameraUnavailableWhenNoLensIsAvailable() = runTest {
        val repository = createRepository(availableLens = emptySet())

        val result = repository.resolveInitialLens(CameraLensFacing.Front)

        val exception = assertIs<CameraRepositoryException>(result.exceptionOrNull())
        assertEquals(CameraError.CameraUnavailable, exception.error)
    }

    @Test
    fun switchLens_returnsLensSwitchFailedWhenTargetLensIsMissing() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))

        val result = repository.switchLens(CameraLensFacing.Back)

        val exception = assertIs<CameraRepositoryException>(result.exceptionOrNull())
        assertEquals(CameraError.LensSwitchFailed, exception.error)
        assertEquals(
            PreviewState.Error(CameraError.LensSwitchFailed),
            repository.observePreviewState().first(),
        )
    }

    @Test
    fun capturePhoto_returnsPhotoCaptureFailedWhenCapturerIsNotReady() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))

        val result = repository.capturePhoto()

        val exception = assertIs<CameraRepositoryException>(result.exceptionOrNull())
        assertEquals(CameraError.PhotoCaptureFailed, exception.error)
        assertEquals(
            PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed),
            repository.observePhotoCaptureState().first(),
        )
    }

    @Test
    fun capturePhoto_doesNotInvokeCapturerUntilPreviewIsShowing() = runTest {
        var captureInvoked = false
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformPhotoCapturerReady(
            IOSPhotoCapturer { captureInvoked = true },
        )

        val result = repository.capturePhoto()

        val exception = assertIs<CameraRepositoryException>(result.exceptionOrNull())
        assertEquals(CameraError.PhotoCaptureFailed, exception.error)
        assertFalse(captureInvoked)
        assertEquals(
            PhotoCaptureState.Failed(CameraError.PhotoCaptureFailed),
            repository.observePhotoCaptureState().first(),
        )
    }

    @Test
    fun startVideoRecording_returnsVideoRecordFailedWhenRecorderIsNotReady() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformPreviewStarted(CameraLensFacing.Back)

        val result = repository.startVideoRecording()

        val exception = assertIs<CameraRepositoryException>(result.exceptionOrNull())
        assertEquals(CameraError.VideoRecordFailed, exception.error)
        assertEquals(
            VideoRecordingState.Failed(CameraError.VideoRecordFailed),
            repository.observeVideoRecordingState().first(),
        )
    }

    @Test
    fun startVideoRecording_doesNotInvokeRecorderUntilPreviewIsShowing() = runTest {
        val recorder = FakeVideoRecorder()
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformVideoRecorderReady(recorder)

        val result = repository.startVideoRecording()

        assertTrue(result.isFailure)
        assertEquals(0, recorder.startCount)
        assertEquals(
            VideoRecordingState.Failed(CameraError.VideoRecordFailed),
            repository.observeVideoRecordingState().first(),
        )
    }

    @Test
    fun startVideoRecording_returnsVideoRecordFailedWhenRecorderThrows() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformPreviewStarted(CameraLensFacing.Back)
        repository.onPlatformVideoRecorderReady(
            IOSVideoRecorder { throw IllegalStateException("screen recording is unavailable") },
        )

        val result = repository.startVideoRecording()

        val exception = assertIs<CameraRepositoryException>(result.exceptionOrNull())
        assertEquals(CameraError.VideoRecordFailed, exception.error)
        assertEquals(
            VideoRecordingState.Failed(CameraError.VideoRecordFailed),
            repository.observeVideoRecordingState().first(),
        )
    }

    @Test
    fun startVideoRecording_publishesRecordingAndRejectsSecondStart() = runTest {
        val recorder = FakeVideoRecorder()
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformPreviewStarted(CameraLensFacing.Back)
        repository.onPlatformVideoRecorderReady(recorder)

        val first = repository.startVideoRecording()
        val second = repository.startVideoRecording()

        assertTrue(first.isSuccess)
        assertTrue(second.isFailure)
        assertEquals(1, recorder.startCount)
        assertEquals(VideoRecordingState.Recording, repository.observeVideoRecordingState().first())
    }

    @Test
    fun stopVideoRecording_withoutActiveRecording_failsWithoutChangingState() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))

        val result = repository.stopVideoRecording()

        assertTrue(result.isFailure)
        assertEquals(VideoRecordingState.Idle, repository.observeVideoRecordingState().first())
    }

    @Test
    fun stopVideoRecording_waitsForFinalizeAndPublishesSucceededUri() = runTest {
        val recorder = FakeVideoRecorder()
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformPreviewStarted(CameraLensFacing.Back)
        repository.onPlatformVideoRecorderReady(recorder)
        repository.startVideoRecording()

        val stopResult = async { repository.stopVideoRecording() }
        advanceUntilIdle()

        assertEquals(1, recorder.stopCount)
        assertEquals(VideoRecordingState.Finalizing, repository.observeVideoRecordingState().first())
        assertTrue(stopResult.isActive)

        recorder.finalize(uri = "file:///tmp/vtuber-camera.mov", error = null)
        advanceUntilIdle()

        assertEquals("file:///tmp/vtuber-camera.mov", stopResult.await().getOrNull())
        assertEquals(
            VideoRecordingState.Succeeded("file:///tmp/vtuber-camera.mov"),
            repository.observeVideoRecordingState().first(),
        )
    }

    @Test
    fun stopVideoRecording_whenSaveFails_publishesFailed() = runTest {
        val recorder = FakeVideoRecorder()
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformPreviewStarted(CameraLensFacing.Back)
        repository.onPlatformVideoRecorderReady(recorder)
        repository.startVideoRecording()

        val stopResult = async { repository.stopVideoRecording() }
        advanceUntilIdle()
        recorder.finalize(uri = null, error = IllegalStateException("Photo library access was not granted"))
        advanceUntilIdle()

        val exception = assertIs<CameraRepositoryException>(stopResult.await().exceptionOrNull())
        assertEquals(CameraError.VideoRecordFailed, exception.error)
        assertEquals(
            VideoRecordingState.Failed(CameraError.VideoRecordFailed),
            repository.observeVideoRecordingState().first(),
        )
    }

    @Test
    fun startVideoRecording_afterStartPermissionDenied_allowsNextRecording() = runTest {
        val recorder = FakeVideoRecorder()
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformPreviewStarted(CameraLensFacing.Back)
        repository.onPlatformVideoRecorderReady(recorder)
        repository.startVideoRecording()
        // 停止要求なしに録画側から終了（ReplayKit の権限拒否など）しても、次の録画を始められる状態へ戻る。
        recorder.finalize(uri = null, error = IllegalStateException("User declined screen recording"))

        val result = repository.startVideoRecording()

        assertTrue(result.isSuccess)
        assertEquals(2, recorder.startCount)
    }

    @Test
    fun onPlatformPreviewStarted_ignoresStaleCallbackWhileSwitchIsPending() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back, CameraLensFacing.Front))

        repository.startPreview(CameraLensFacing.Back)
        repository.switchLens(CameraLensFacing.Back)

        repository.onPlatformPreviewStarted(CameraLensFacing.Back)
        assertEquals(PreviewState.Preparing, repository.observePreviewState().first())

        repository.onPlatformPreviewStarted(CameraLensFacing.Front)
        assertEquals(PreviewState.Showing, repository.observePreviewState().first())
    }

    @Test
    fun onPlatformPreviewError_ignoresStaleCallbackWhileSwitchIsPending() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back, CameraLensFacing.Front))

        repository.startPreview(CameraLensFacing.Back)
        repository.switchLens(CameraLensFacing.Back)

        repository.onPlatformPreviewError(CameraLensFacing.Back, CameraError.CameraUnavailable)
        assertEquals(PreviewState.Preparing, repository.observePreviewState().first())

        repository.onPlatformPreviewError(CameraLensFacing.Front, CameraError.CameraUnavailable)
        assertEquals(
            PreviewState.Error(CameraError.CameraUnavailable),
            repository.observePreviewState().first(),
        )
    }

    @Test
    fun deletePhoto_whenFileDeleterSucceeds_publishesSucceeded() = runTest {
        val deletedUris = mutableListOf<String>()
        val repository = IOSCameraRepository(
            hasLens = { true },
            photoFileDeleter = { uri ->
                deletedUris += uri
                true
            },
        )

        val result = repository.deletePhoto("file:///tmp/vtuber-camera.jpg")

        assertTrue(result.isSuccess)
        assertEquals(listOf("file:///tmp/vtuber-camera.jpg"), deletedUris)
        assertEquals(
            PhotoDeletionState.Succeeded,
            repository.observePhotoDeletionState().first(),
        )
    }

    @Test
    fun deletePhoto_whenFileDeleterFails_returnsPhotoDeleteFailed() = runTest {
        val repository = IOSCameraRepository(
            hasLens = { true },
            photoFileDeleter = { false },
        )

        val result = repository.deletePhoto("file:///tmp/missing.jpg")

        val exception = assertIs<CameraRepositoryException>(result.exceptionOrNull())
        assertEquals(CameraError.PhotoDeleteFailed, exception.error)
        assertEquals(
            PhotoDeletionState.Failed(CameraError.PhotoDeleteFailed),
            repository.observePhotoDeletionState().first(),
        )
    }

    @Test
    fun onPlatformCameraControlReady_publishesCurrentZoomState() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))

        repository.onPlatformCameraControlReady(
            FakeCameraControl(
                initialZoomState = CameraZoomUiState(
                    currentCameraZoomRatio = 2f,
                    minCameraZoomRatio = 1f,
                    maxCameraZoomRatio = 6f,
                ),
            ),
        )

        assertEquals(
            CameraZoomUiState(
                currentCameraZoomRatio = 2f,
                minCameraZoomRatio = 1f,
                maxCameraZoomRatio = 6f,
            ),
            repository.observeZoomState().first(),
        )
    }

    @Test
    fun setZoomRatio_updatesZoomStateFromCameraControl() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        val cameraControl = FakeCameraControl(
            initialZoomState = CameraZoomUiState(
                currentCameraZoomRatio = 1f,
                minCameraZoomRatio = 1f,
                maxCameraZoomRatio = 5f,
            ),
        )
        repository.onPlatformCameraControlReady(cameraControl)

        repository.setZoomRatio(3f)

        assertEquals(3f, cameraControl.setZoomRatioRequests.last())
        assertEquals(
            CameraZoomUiState(
                currentCameraZoomRatio = 3f,
                minCameraZoomRatio = 1f,
                maxCameraZoomRatio = 5f,
            ),
            repository.observeZoomState().first(),
        )
    }

    @Test
    fun onPlatformCameraControlReady_resetsZoomStateWhenControlIsCleared() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformCameraControlReady(
            FakeCameraControl(
                initialZoomState = CameraZoomUiState(
                    currentCameraZoomRatio = 2f,
                    minCameraZoomRatio = 1f,
                    maxCameraZoomRatio = 4f,
                ),
            ),
        )

        repository.onPlatformCameraControlReady(null)

        assertEquals(CameraZoomUiState(), repository.observeZoomState().first())
    }

    @Test
    fun startIosFaceTrackingPreview_returnsUnsupportedErrorWithoutPreparingSession() {
        var didPrepareTracking = false
        var didRunSession = false
        var completedLens: CameraLensFacing? = null
        var completedError: Throwable? = null

        startIosFaceTrackingPreview(
            isSupported = false,
            prepareTracking = { didPrepareTracking = true },
            runSession = {
                didRunSession = true
                null
            },
            onComplete = { lensFacing, error ->
                completedLens = lensFacing
                completedError = error
            },
        )

        assertFalse(didPrepareTracking)
        assertFalse(didRunSession)
        assertEquals(CameraLensFacing.Front, completedLens)
        assertIs<IllegalStateException>(completedError)
    }

    @Test
    fun resolveAvailableLens_returnsNullWhenNeitherLensExists() {
        val resolvedLens = resolveAvailableLens(
            requested = CameraLensFacing.Front,
            hasLens = { false },
        )

        assertNull(resolvedLens)
    }

    private fun createRepository(availableLens: Set<CameraLensFacing>): IOSCameraRepository {
        return IOSCameraRepository(hasLens = { lensFacing -> lensFacing in availableLens })
    }

    // AVFoundation を使わずに録画の開始 / 停止 / 書き出し完了を再現する recorder。
    private class FakeVideoRecorder : IOSVideoRecorder {
        var startCount = 0
            private set
        var stopCount = 0
            private set
        private var onFinalized: ((uri: String?, error: Throwable?) -> Unit)? = null

        override fun startRecording(
            onFinalized: (uri: String?, error: Throwable?) -> Unit,
        ): IOSVideoRecording {
            startCount += 1
            this.onFinalized = onFinalized
            return IOSVideoRecording { stopCount += 1 }
        }

        fun finalize(uri: String?, error: Throwable?) {
            onFinalized?.invoke(uri, error)
        }
    }

    private class FakeCameraControl(
        initialZoomState: CameraZoomUiState,
    ) : CameraControl {
        val setZoomRatioRequests = mutableListOf<Float>()
        private var zoomState = initialZoomState

        override fun zoomState(): CameraZoomUiState {
            return zoomState
        }

        override fun setZoomRatio(updatedZoomRatio: Float): CameraZoomUiState {
            setZoomRatioRequests += updatedZoomRatio
            zoomState = zoomState.copy(currentCameraZoomRatio = updatedZoomRatio)
            return zoomState
        }
    }
}
