package com.example.vtubercamera_kmp_ver.camera.permission

import com.example.vtubercamera_kmp_ver.camera.PermissionState
import com.example.vtubercamera_kmp_ver.camera.testing.FakePermissionRepository
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
class CameraPermissionCoordinatorTest {
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
    fun initialize_whenGranted_emitsGrantedEntered() = runTest {
        val coordinator = CameraPermissionCoordinator(
            permissionRepository = FakePermissionRepository(PermissionState.Granted),
            scope = this,
        )
        val events = mutableListOf<PermissionChange>()
        coordinator.onPermissionChanged = { events += it }

        coordinator.initialize()
        advanceUntilIdle()

        assertEquals(PermissionState.Granted, coordinator.state.value.permissionState)
        assertEquals(listOf(PermissionChange.GrantedEntered), events)
    }

    @Test
    fun initialize_whenDenied_emitsDeniedReceived() = runTest {
        val coordinator = CameraPermissionCoordinator(
            permissionRepository = FakePermissionRepository(PermissionState.Denied),
            scope = this,
        )
        val events = mutableListOf<PermissionChange>()
        coordinator.onPermissionChanged = { events += it }

        coordinator.initialize()
        advanceUntilIdle()

        assertEquals(PermissionState.Denied, coordinator.state.value.permissionState)
        assertEquals(listOf(PermissionChange.DeniedReceived), events)
    }

    @Test
    fun onPermissionStateChanged_fromDeniedToGranted_emitsGrantedEntered() = runTest {
        val coordinator = CameraPermissionCoordinator(
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
            scope = this,
        )
        val events = mutableListOf<PermissionChange>()
        coordinator.onPermissionChanged = { events += it }

        coordinator.onPermissionStateChanged(isGranted = false, isChecking = false)
        coordinator.onPermissionStateChanged(isGranted = true, isChecking = false)

        assertEquals(PermissionState.Granted, coordinator.state.value.permissionState)
        assertEquals(
            listOf(PermissionChange.DeniedReceived, PermissionChange.GrantedEntered),
            events,
        )
    }

    @Test
    fun onPermissionStateChanged_grantedAgain_emitsGrantedRefreshed() = runTest {
        val coordinator = CameraPermissionCoordinator(
            permissionRepository = FakePermissionRepository(PermissionState.Granted),
            scope = this,
        )
        val events = mutableListOf<PermissionChange>()
        coordinator.onPermissionChanged = { events += it }
        coordinator.initialize()
        advanceUntilIdle()

        coordinator.onPermissionStateChanged(isGranted = true, isChecking = false)

        assertEquals(
            listOf(PermissionChange.GrantedEntered, PermissionChange.GrantedRefreshed),
            events,
        )
    }

    @Test
    fun onPermissionStateChanged_isChecking_emitsUnknownReceived() = runTest {
        val coordinator = CameraPermissionCoordinator(
            permissionRepository = FakePermissionRepository(PermissionState.Denied),
            scope = this,
        )
        val events = mutableListOf<PermissionChange>()
        coordinator.onPermissionChanged = { events += it }

        coordinator.onPermissionStateChanged(isGranted = false, isChecking = true)

        assertEquals(PermissionState.Unknown, coordinator.state.value.permissionState)
        assertEquals(listOf(PermissionChange.UnknownReceived), events)
    }

    @Test
    fun onRequestPermission_setsUnknown_andInvokesRequestedCallback() = runTest {
        val coordinator = CameraPermissionCoordinator(
            permissionRepository = FakePermissionRepository(PermissionState.Granted),
            scope = this,
        )
        coordinator.onPermissionStateChanged(isGranted = false, isChecking = false)
        assertEquals(PermissionState.Denied, coordinator.state.value.permissionState)

        var requestedCount = 0
        coordinator.onPermissionRequested = { requestedCount += 1 }

        coordinator.onRequestPermission()

        assertEquals(PermissionState.Unknown, coordinator.state.value.permissionState)
        assertEquals(1, requestedCount)
    }
}
