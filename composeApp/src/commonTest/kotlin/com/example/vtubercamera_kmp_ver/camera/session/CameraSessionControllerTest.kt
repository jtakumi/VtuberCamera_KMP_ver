package com.example.vtubercamera_kmp_ver.camera.session

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.CameraLensFacing
import com.example.vtubercamera_kmp_ver.camera.CameraMessageType
import com.example.vtubercamera_kmp_ver.camera.CameraRepositoryException
import com.example.vtubercamera_kmp_ver.camera.PreviewState
import com.example.vtubercamera_kmp_ver.camera.testing.FakeCameraRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CameraSessionControllerTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun start_whenResolveAndStartSucceed_updatesLensFacingAndShowsPreview() = runTest {
        val cameraRepository = FakeCameraRepository(
            resolveInitialLensResult = Result.success(CameraLensFacing.Front),
            startPreviewResult = Result.success(CameraLensFacing.Front),
        )
        val controller = CameraSessionController(cameraRepository, backgroundScope)
        advanceUntilIdle()

        controller.start()
        advanceUntilIdle()

        assertEquals(CameraLensFacing.Front, controller.state.value.lensFacing)
        assertEquals(PreviewState.Showing, controller.state.value.previewState)
        assertNull(controller.state.value.errorState)
        assertEquals(1, cameraRepository.resolveInitialLensCallCount)
        assertEquals(1, cameraRepository.startPreviewCallCount)
    }

    @Test
    fun start_whenResolveInitialLensFails_setsCameraUnavailableError() = runTest {
        val cameraRepository = FakeCameraRepository(
            resolveInitialLensResult = Result.failure(IllegalStateException("no camera")),
        )
        val controller = CameraSessionController(cameraRepository, backgroundScope)
        advanceUntilIdle()

        controller.start()
        advanceUntilIdle()

        assertEquals(CameraError.CameraUnavailable, controller.state.value.errorState)
        val previewState = assertIs<PreviewState.Error>(controller.state.value.previewState)
        assertEquals(CameraError.CameraUnavailable, previewState.error)
        assertEquals(0, cameraRepository.startPreviewCallCount)
    }

    @Test
    fun onRetryPreview_whenStartPreviewFailsWithCameraRepositoryException_preservesErrorType() =
        runTest {
            val cameraRepository = FakeCameraRepository(
                startPreviewResult = Result.failure(
                    CameraRepositoryException(CameraError.CameraUnavailable),
                ),
            )
            val controller = CameraSessionController(cameraRepository, backgroundScope)
            advanceUntilIdle()

            controller.onRetryPreview()
            advanceUntilIdle()

            assertEquals(CameraError.CameraUnavailable, controller.state.value.errorState)
            val previewState = assertIs<PreviewState.Error>(controller.state.value.previewState)
            assertEquals(CameraError.CameraUnavailable, previewState.error)
        }

    @Test
    fun onRetryPreview_whenStartPreviewFailsWithGenericException_fallsBackToPreviewInitializationFailed() =
        runTest {
            val cameraRepository = FakeCameraRepository(
                startPreviewResult = Result.failure(IllegalStateException("boom")),
            )
            val controller = CameraSessionController(cameraRepository, backgroundScope)
            advanceUntilIdle()

            controller.onRetryPreview()
            advanceUntilIdle()

            assertEquals(
                CameraError.PreviewInitializationFailed,
                controller.state.value.errorState,
            )
        }

    @Test
    fun onToggleLensFacing_whenSwitchSucceeds_updatesLensFacingAndClearsError() = runTest {
        val cameraRepository = FakeCameraRepository(
            switchLensResult = Result.success(CameraLensFacing.Front),
        )
        val controller = CameraSessionController(cameraRepository, backgroundScope)
        advanceUntilIdle()
        assertEquals(CameraLensFacing.Back, controller.state.value.lensFacing)

        controller.onToggleLensFacing()
        advanceUntilIdle()

        assertEquals(CameraLensFacing.Front, controller.state.value.lensFacing)
        assertNull(controller.state.value.errorState)
        assertEquals(1, cameraRepository.switchLensCallCount)
        assertEquals(listOf(CameraLensFacing.Back), cameraRepository.switchLensRequests)
    }

    @Test
    fun onToggleLensFacing_whenSwitchFails_setsLensSwitchFailedError() = runTest {
        val cameraRepository = FakeCameraRepository(
            switchLensResult = Result.failure(
                CameraRepositoryException(CameraError.LensSwitchFailed),
            ),
        )
        val controller = CameraSessionController(cameraRepository, backgroundScope)
        advanceUntilIdle()

        controller.onToggleLensFacing()
        advanceUntilIdle()

        assertEquals(CameraError.LensSwitchFailed, controller.state.value.errorState)
        val previewState = assertIs<PreviewState.Error>(controller.state.value.previewState)
        assertEquals(CameraError.LensSwitchFailed, previewState.error)
    }

    @Test
    fun observePreviewState_syncsErrorAndMessageFromRepository() = runTest {
        val cameraRepository = FakeCameraRepository()
        val controller = CameraSessionController(cameraRepository, backgroundScope)
        advanceUntilIdle()

        cameraRepository.emitPreviewState(PreviewState.Error(CameraError.CameraUnavailable))
        advanceUntilIdle()

        val errored = controller.state.value
        assertEquals(CameraError.CameraUnavailable, errored.errorState)
        val previewState = assertIs<PreviewState.Error>(errored.previewState)
        assertEquals(CameraError.CameraUnavailable, previewState.error)
        val message = assertNotNull(errored.message)
        assertEquals(CameraMessageType.Error, message.type)

        cameraRepository.emitPreviewState(PreviewState.Showing)
        advanceUntilIdle()

        assertEquals(PreviewState.Showing, controller.state.value.previewState)
        assertNull(controller.state.value.errorState)
        assertNull(controller.state.value.message)
    }

    @Test
    fun clearErrorAndPrepare_clearsErrorAndSetsPreviewPreparing() = runTest {
        val controller = CameraSessionController(FakeCameraRepository(), backgroundScope)
        advanceUntilIdle()
        controller.setError(CameraError.PermissionDenied)

        controller.clearErrorAndPrepare()

        assertEquals(PreviewState.Preparing, controller.state.value.previewState)
        assertNull(controller.state.value.errorState)
        assertNull(controller.state.value.message)
    }

    @Test
    fun resetPreviewIfErrored_clearsErrorOnlyWhenInErrorState() = runTest {
        val controller = CameraSessionController(FakeCameraRepository(), backgroundScope)
        advanceUntilIdle()

        controller.resetPreviewIfErrored()
        assertEquals(PreviewState.Preparing, controller.state.value.previewState)

        controller.setError(CameraError.PermissionDenied)
        controller.resetPreviewIfErrored()

        assertEquals(PreviewState.Preparing, controller.state.value.previewState)
        assertNull(controller.state.value.errorState)
    }
}
