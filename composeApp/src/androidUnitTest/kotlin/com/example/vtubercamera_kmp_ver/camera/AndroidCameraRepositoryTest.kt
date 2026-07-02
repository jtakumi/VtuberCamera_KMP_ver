package com.example.vtubercamera_kmp_ver.camera

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
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

    private class FakeCameraLensAvailability(
        private val availableLens: Set<CameraLensFacing>,
    ) : CameraLensAvailability {
        override fun hasCamera(lensFacing: CameraLensFacing): Boolean {
            return lensFacing in availableLens
        }
    }
}
