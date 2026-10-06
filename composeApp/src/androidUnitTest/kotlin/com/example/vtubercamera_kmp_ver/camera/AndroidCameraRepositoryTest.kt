package com.example.vtubercamera_kmp_ver.camera

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class AndroidCameraRepositoryTest {
    @Test
    fun startPreview_fallsBackToAvailableLensWhenRequestedLensIsMissing() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Front))

        val result = repository.startPreview(CameraLensFacing.Back)

        assertEquals(CameraLensFacing.Front, result.getOrNull())
        assertEquals(PreviewState.Preparing, repository.observePreviewState().first())
    }

    @Test
    fun resolveInitialLens_fallsBackToAvailableLensWhenPreferredLensIsMissing() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Front))

        val result = repository.resolveInitialLens(CameraLensFacing.Back)

        assertEquals(CameraLensFacing.Front, result.getOrNull())
    }

    @Test
    fun resolveInitialLens_returnsCameraUnavailableWhenNoLensIsAvailable() = runTest {
        val repository = createRepository(availableLens = emptySet())

        val result = repository.resolveInitialLens(CameraLensFacing.Back)

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
    fun capturePhoto_returnsPhotoCaptureFailedWhenImageCaptureIsNotReady() = runTest {
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
    fun startVideoRecording_returnsVideoRecordFailedWhenRecorderIsNotReady() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))

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
        repository.onPlatformVideoRecorderReady(recorder)

        val first = repository.startVideoRecording()
        val second = repository.startVideoRecording()

        assertTrue(first.isSuccess)
        assertTrue(second.isFailure)
        assertEquals(1, recorder.startCount)
        assertEquals(VideoRecordingState.Recording, repository.observeVideoRecordingState().first())
        recorder.outputFile?.delete()
    }

    @Test
    fun startVideoRecording_returnsVideoRecordFailedWhenRecorderThrows() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformVideoRecorderReady(
            AndroidVideoRecorder { _, _ -> throw IllegalStateException("camera closed") },
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
        repository.onPlatformVideoRecorderReady(recorder)
        repository.startVideoRecording()

        val stopResult = async { repository.stopVideoRecording() }
        advanceUntilIdle()

        assertEquals(1, recorder.stopCount)
        assertEquals(VideoRecordingState.Finalizing, repository.observeVideoRecordingState().first())
        assertTrue(stopResult.isActive)

        recorder.finalize(error = null)
        advanceUntilIdle()

        val outputFile = assertNotNull(recorder.outputFile)
        val expectedUri = outputFile.toURI().toString()
        assertEquals(expectedUri, stopResult.await().getOrNull())
        assertEquals(
            VideoRecordingState.Succeeded(expectedUri),
            repository.observeVideoRecordingState().first(),
        )
        assertTrue(outputFile.exists())
        outputFile.delete()
    }

    @Test
    fun stopVideoRecording_whenFinalizeFails_deletesOutputAndPublishesFailed() = runTest {
        val recorder = FakeVideoRecorder()
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformVideoRecorderReady(recorder)
        repository.startVideoRecording()

        val stopResult = async { repository.stopVideoRecording() }
        advanceUntilIdle()
        recorder.finalize(error = VideoRecordingException(errorCode = 8, cause = null))
        advanceUntilIdle()

        val exception = assertIs<CameraRepositoryException>(stopResult.await().exceptionOrNull())
        assertEquals(CameraError.VideoRecordFailed, exception.error)
        assertEquals(
            VideoRecordingState.Failed(CameraError.VideoRecordFailed),
            repository.observeVideoRecordingState().first(),
        )
        assertFalse(assertNotNull(recorder.outputFile).exists())
    }

    @Test
    fun startVideoRecording_afterFinalize_allowsNextRecording() = runTest {
        val recorder = FakeVideoRecorder()
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        repository.onPlatformVideoRecorderReady(recorder)
        repository.startVideoRecording()
        // 録画側から終了（unbind やストレージ不足）した場合も、次の録画を始められる状態へ戻る。
        recorder.finalize(error = VideoRecordingException(errorCode = 2, cause = null))

        val result = repository.startVideoRecording()

        assertTrue(result.isSuccess)
        assertEquals(2, recorder.startCount)
        assertEquals(VideoRecordingState.Recording, repository.observeVideoRecordingState().first())
        recorder.outputFile?.delete()
    }

    @Test
    fun deletePhoto_removesCapturedFileAndPublishesSucceeded() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))
        val file = File.createTempFile("vtuber-camera-test-", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val result = repository.deletePhoto(file.toURI().toString())

        assertTrue(result.isSuccess)
        assertFalse(file.exists())
        assertEquals(
            PhotoDeletionState.Succeeded,
            repository.observePhotoDeletionState().first(),
        )
    }

    @Test
    fun deletePhoto_returnsPhotoDeleteFailedWhenUriIsNotADeletableFile() = runTest {
        val repository = createRepository(availableLens = setOf(CameraLensFacing.Back))

        val result = repository.deletePhoto("content://media/external/images/media/42")

        val exception = assertIs<CameraRepositoryException>(result.exceptionOrNull())
        assertEquals(CameraError.PhotoDeleteFailed, exception.error)
        assertEquals(
            PhotoDeletionState.Failed(CameraError.PhotoDeleteFailed),
            repository.observePhotoDeletionState().first(),
        )
    }

    @Test
    fun onPlatformPreviewStarted_ignoresStaleCallbackWhileSwitchIsPending() = runTest {
        val repository = createRepository(
            availableLens = setOf(CameraLensFacing.Back, CameraLensFacing.Front),
        )

        repository.startPreview(CameraLensFacing.Back)
        repository.switchLens(CameraLensFacing.Back)

        repository.onPlatformPreviewStarted(CameraLensFacing.Back)
        assertEquals(PreviewState.Preparing, repository.observePreviewState().first())

        repository.onPlatformPreviewStarted(CameraLensFacing.Front)
        assertEquals(PreviewState.Showing, repository.observePreviewState().first())
    }

    @Test
    fun onPlatformPreviewError_ignoresStaleCallbackWhileSwitchIsPending() = runTest {
        val repository = createRepository(
            availableLens = setOf(CameraLensFacing.Back, CameraLensFacing.Front),
        )

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

    private fun createRepository(availableLens: Set<CameraLensFacing>): AndroidCameraRepository {
        return AndroidCameraRepository(
            cameraAvailabilityProvider = {
                FakeCameraLensAvailability(availableLens)
            },
        )
    }

    // CameraX を使わずに録画の開始 / 停止 / 書き出し完了を再現する recorder。
    private class FakeVideoRecorder : AndroidVideoRecorder {
        var startCount = 0
            private set
        var stopCount = 0
            private set
        var outputFile: File? = null
            private set
        private var onFinalized: ((Throwable?) -> Unit)? = null

        override fun startRecording(
            outputFile: File,
            onFinalized: (error: Throwable?) -> Unit,
        ): AndroidVideoRecording {
            startCount += 1
            this.outputFile = outputFile
            this.onFinalized = onFinalized
            return AndroidVideoRecording { stopCount += 1 }
        }

        fun finalize(error: Throwable?) {
            onFinalized?.invoke(error)
        }
    }

    private class FakeCameraLensAvailability(
        private val availableLens: Set<CameraLensFacing>,
    ) : CameraLensAvailability {
        override fun hasCamera(lensFacing: CameraLensFacing): Boolean {
            return lensFacing in availableLens
        }
    }
}
