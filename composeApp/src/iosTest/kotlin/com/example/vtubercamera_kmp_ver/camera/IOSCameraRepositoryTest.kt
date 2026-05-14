package com.example.vtubercamera_kmp_ver.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
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
