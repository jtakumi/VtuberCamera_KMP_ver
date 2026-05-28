package com.example.vtubercamera_kmp_ver.camera.zoom

import com.example.vtubercamera_kmp_ver.camera.testing.FakeCameraRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CameraZoomControllerTest {
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
    fun observeZoomState_syncsRepositoryZoomToState() = runTest {
        val cameraRepository = FakeCameraRepository()
        val controller = CameraZoomController(cameraRepository, backgroundScope)
        advanceUntilIdle()

        assertEquals(1f, controller.state.value.currentCameraZoomRatio)
        assertEquals(1f, controller.state.value.minCameraZoomRatio)
        assertEquals(5f, controller.state.value.maxCameraZoomRatio)
    }

    @Test
    fun onCameraZoomChanged_clampsBelowMin() = runTest {
        val cameraRepository = FakeCameraRepository()
        val controller = CameraZoomController(cameraRepository, backgroundScope)
        advanceUntilIdle()

        controller.onCameraZoomChanged(0.1f)

        assertEquals(1f, cameraRepository.setZoomRatioRequests.last())
    }

    @Test
    fun onCameraZoomChanged_clampsAboveMax() = runTest {
        val cameraRepository = FakeCameraRepository()
        val controller = CameraZoomController(cameraRepository, backgroundScope)
        advanceUntilIdle()

        controller.onCameraZoomChanged(10f)

        assertEquals(5f, cameraRepository.setZoomRatioRequests.last())
    }

    @Test
    fun onCameraZoomChanged_appliesScaleWithinBounds() = runTest {
        val cameraRepository = FakeCameraRepository()
        val controller = CameraZoomController(cameraRepository, backgroundScope)
        advanceUntilIdle()

        controller.onCameraZoomChanged(2f)

        assertEquals(2f, cameraRepository.setZoomRatioRequests.last())
    }
}
