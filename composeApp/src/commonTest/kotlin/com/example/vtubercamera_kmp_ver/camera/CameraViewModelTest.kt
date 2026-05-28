package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeMeta
import com.example.vtubercamera_kmp_ver.camera.testing.FakeCameraRepository
import com.example.vtubercamera_kmp_ver.camera.testing.FakePermissionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_unknown
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_select_file
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit test stubs for [CameraViewModel] state-machine logic.
 *
 * # Test infrastructure note
 * This file uses fake [CameraRepository] / [PermissionRepository] implementations plus
 * kotlinx-coroutines-test dispatcher control to validate shared state-machine transitions.
 *
 * # Manual / E2E test checklist
 * The following scenarios require a real device or simulator and cannot be automated in commonTest:
 *
 * ## Permission flow (Android & iOS)
 * 1. **Denied → grant from Settings → preview recovery**
 *    - Steps: launch app → deny camera permission → navigate to Settings → grant permission →
 *             return to app.
 *    - Expected: CameraScreen transitions from the PermissionDenied error overlay to a live
 *                camera preview without any user-visible retry action.
 *
 * 2. **Checking (Unknown) → denied → preview not started**
 *    - Steps: while the OS permission dialog is open (isChecking=true), dismiss it by tapping
 *             "Don't Allow".
 *    - Expected: previewState == Error(PermissionDenied), retry button is visible.
 *
 * 3. **前回起動時は許可済み → 次回起動時に OS 設定で拒否へ変更済み**
 *    - Steps: app launch #1 で許可してプレビュー表示を確認 → アプリを終了 →
 *             端末の設定画面でカメラ権限を拒否へ変更 → app launch #2。
 *    - Expected: initialize() 直後に permissionState == Denied,
 *                previewState == Error(PermissionDenied),
 *                CameraRepository.startPreview は呼ばれない。
 *
 * ## iOS fallback lens
 * 4. **Both lenses unavailable → CameraUnavailable error shown**
 *    - Steps: run on an iOS Simulator where AVFoundation returns no camera device for both front
 *             and back; trigger startPreview() or resolveInitialLens().
 *    - Expected: previewState == Error(CameraUnavailable), the UI shows the CameraUnavailable
 *                error message, not PreviewInitializationFailed.
 *
 * 5. **Requested lens unavailable, fallback lens succeeds → preview starts on fallback**
 *    - Steps: request front lens on a device that has only a back camera.
 *    - Expected: preview starts on the back camera; lensFacing in uiState is updated to Back;
 *                no error is shown.
 *
 * 6. **Fallback lens resolves but session canAddInput fails → error propagates correctly**
 *    - Steps: inject a stubbed IOSCameraSessionManager where canAddInput always returns false.
 *    - Expected: onPlatformPreviewError is called with the *resolved* (fallback) lens, repository
 *                guard passes, and previewState transitions to Error with the correct CameraError.
 *
 * ## Android error display differentiation
 * 7. **CameraUnavailable shows correct string, distinct from PreviewInitializationFailed**
 *    - Steps: trigger CameraUnavailable (e.g. hasCameraSafely returns false for all selectors)
 *             and separately trigger PreviewInitializationFailed (e.g. bindToLifecycle throws).
 *    - Expected: the two error states display different user-facing strings; neither falls back to
 *                the generic "unknown error" copy.
 *
 * ## iOS file selection
 * 8. **File selection success → avatarPreview is updated**
 *    - Steps: tap the file-picker button → select a valid .vrm or .glb file.
 *    - Expected: CameraViewModel.onFilePicked(FilePickerResult.Success(...)) is called;
 *                uiState.avatarPreview is populated with the parsed metadata / thumbnail;
 *                filePickerErrorMessageRes is null.
 *
 * 9. **File selection cancelled → state unchanged**
 *    - Steps: tap the file-picker button → dismiss the picker without selecting a file.
 *    - Expected: CameraViewModel.onFilePicked(FilePickerResult.Cancelled) is called;
 *                uiState is unchanged (no error, no new avatarPreview).
 *
 * 10. **File selection fails (unsupported format / IO error) → error message shown**
 *    - Steps: tap the file-picker button → select a file that cannot be parsed as VRM/GLB.
 *    - Expected: CameraViewModel.onFilePicked(FilePickerResult.Error(...)) is called;
 *                uiState.avatarSelection.filePickerErrorMessageRes is set to the appropriate StringResource;
 *                onDismissFilePickerError() clears it.
 */
// Collects pending unit-test scenarios for the shared camera state machine.
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @kotlin.test.BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @kotlin.test.AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // initialize()
    // ---------------------------------------------------------------------------

    /**
     * When [PermissionRepository.checkCameraPermission] returns [PermissionState.Granted],
     * [CameraViewModel.initialize] must resolve the initial lens and call
     * [CameraRepository.startPreview], leaving [CameraUiState.session.previewState] as
     * [PreviewState.Preparing] (and eventually [PreviewState.Showing] once the platform signals
     * success via [CameraRepository.onPlatformPreviewStarted]).
     */
    @Test
    fun initialize_whenPermissionGranted_startsCameraPreview() = runTest {
        val cameraRepository = FakeCameraRepository(
            resolveInitialLensResult = Result.success(CameraLensFacing.Front),
            startPreviewResult = Result.success(CameraLensFacing.Front),
        )
        val permissionRepository = FakePermissionRepository(
            checkCameraPermissionResult = PermissionState.Granted,
        )
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = permissionRepository,
        )

        viewModel.initialize()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(PermissionState.Granted, uiState.permission.permissionState)
        assertEquals(CameraLensFacing.Front, uiState.session.lensFacing)
        assertEquals(PreviewState.Showing, uiState.session.previewState)
        assertNull(uiState.session.errorState)
        assertNull(uiState.session.message)
        assertEquals(1, cameraRepository.resolveInitialLensCallCount)
        assertEquals(listOf(CameraLensFacing.Back), cameraRepository.resolveInitialLensRequests)
        assertEquals(1, cameraRepository.startPreviewCallCount)
        assertEquals(listOf(CameraLensFacing.Front), cameraRepository.startPreviewRequests)
    }

    @Test
    fun initialize_whenPermissionUnknown_keepsWaitingAndDoesNotStartPreview() = runTest {
        val cameraRepository = FakeCameraRepository()
        val permissionRepository = FakePermissionRepository(
            checkCameraPermissionResult = PermissionState.Unknown,
        )
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = permissionRepository,
        )

        viewModel.initialize()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(PermissionState.Unknown, uiState.permission.permissionState)
        assertEquals(PreviewState.Preparing, uiState.session.previewState)
        assertNull(uiState.session.errorState)
        assertNull(uiState.session.message)
        assertEquals(0, cameraRepository.resolveInitialLensCallCount)
        assertEquals(0, cameraRepository.startPreviewCallCount)
    }

    /**
     * When [PermissionRepository.checkCameraPermission] returns [PermissionState.Denied],
     * [CameraViewModel.initialize] must set [CameraUiState.session.errorState] to
     * [CameraError.PermissionDenied] and [CameraUiState.session.previewState] to
     * [PreviewState.Error(CameraError.PermissionDenied)].
     * [CameraRepository.startPreview] must NOT be called.
     */
    @Test
    fun initialize_whenPermissionDenied_setsPermissionDeniedErrorAndDoesNotStartPreview() =
        runTest {
            val cameraRepository = FakeCameraRepository()
            val permissionRepository = FakePermissionRepository(
                checkCameraPermissionResult = PermissionState.Denied,
            )
            val viewModel = CameraViewModel(
                cameraRepository = cameraRepository,
                permissionRepository = permissionRepository,
            )

            viewModel.initialize()
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(PermissionState.Denied, uiState.permission.permissionState)
            assertEquals(CameraError.PermissionDenied, uiState.session.errorState)
            val previewState = assertIs<PreviewState.Error>(uiState.session.previewState)
            assertEquals(CameraError.PermissionDenied, previewState.error)
            assertEquals(0, cameraRepository.startPreviewCallCount)
        }

    /**
     * 「前回起動時は許可済みだったが、次回起動時までに OS 設定で拒否へ変更された」ケース。
     *
     * Given:
     *   - previous session では [PermissionState.Granted] で運用されていた。
     *   - app 再起動後の [PermissionRepository.checkCameraPermission] は
     *     [PermissionState.Denied] を返す。
     *
     * Then:
     *   - [CameraViewModel.initialize] は [CameraUiState.permission.permissionState] を Denied に更新し、
     *     [CameraUiState.session.errorState] / [CameraUiState.session.previewState] を PermissionDenied に設定する。
     *   - [CameraRepository.startPreview] は呼び出されない。
     */
    @Test
    fun initialize_whenPreviouslyGrantedButNowDenied_setsPermissionDeniedAndDoesNotStartPreview() =
        runTest {
            val firstLaunchCameraRepository = FakeCameraRepository()
            val firstLaunchPermissionRepository = FakePermissionRepository(
                checkCameraPermissionResult = PermissionState.Granted,
            )
            val firstLaunchViewModel = CameraViewModel(
                cameraRepository = firstLaunchCameraRepository,
                permissionRepository = firstLaunchPermissionRepository,
            )
            firstLaunchViewModel.initialize()
            advanceUntilIdle()

            assertEquals(
                PermissionState.Granted,
                firstLaunchViewModel.uiState.value.permission.permissionState
            )
            assertEquals(1, firstLaunchCameraRepository.startPreviewCallCount)

            val secondLaunchCameraRepository = FakeCameraRepository()
            val secondLaunchPermissionRepository = FakePermissionRepository(
                checkCameraPermissionResult = PermissionState.Denied,
            )
            val secondLaunchViewModel = CameraViewModel(
                cameraRepository = secondLaunchCameraRepository,
                permissionRepository = secondLaunchPermissionRepository,
            )
            secondLaunchViewModel.initialize()
            advanceUntilIdle()

            val uiState = secondLaunchViewModel.uiState.value
            assertEquals(PermissionState.Denied, uiState.permission.permissionState)
            assertEquals(CameraError.PermissionDenied, uiState.session.errorState)
            val previewState = assertIs<PreviewState.Error>(uiState.session.previewState)
            assertEquals(CameraError.PermissionDenied, previewState.error)
            assertEquals(0, secondLaunchCameraRepository.startPreviewCallCount)
        }

    // ---------------------------------------------------------------------------
    // onPermissionStateChanged() – permission denied → granted (Settings recovery)
    // ---------------------------------------------------------------------------

    /**
     * When permission transitions from [PermissionState.Denied] to [PermissionState.Granted],
     * [CameraViewModel.onPermissionStateChanged] must:
     *   1. Reset [CameraUiState.session.previewState] to [PreviewState.Preparing].
     *   2. Clear [CameraUiState.session.errorState] and [CameraUiState.session.message].
     *   3. Launch [CameraRepository.resolveInitialLens] + [CameraRepository.startPreview]
     *      (mirroring the initialize() flow).
     *
     * This covers the "denied → grant from Settings → preview recovery" scenario.
     */
    @Test
    fun onPermissionStateChanged_fromDeniedToGranted_resetsErrorAndStartsPreview() = runTest {
        val cameraRepository = FakeCameraRepository()
        val permissionRepository = FakePermissionRepository(
            checkCameraPermissionResult = PermissionState.Unknown,
        )
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = permissionRepository,
        )
        advanceUntilIdle()

        viewModel.onPermissionStateChanged(isGranted = false, isChecking = false)
        val deniedState = viewModel.uiState.value
        assertEquals(PermissionState.Denied, deniedState.permission.permissionState)
        assertEquals(CameraError.PermissionDenied, deniedState.session.errorState)
        assertIs<PreviewState.Error>(deniedState.session.previewState)

        viewModel.onPermissionStateChanged(isGranted = true, isChecking = false)
        val recoveringState = viewModel.uiState.value
        assertEquals(PermissionState.Granted, recoveringState.permission.permissionState)
        assertEquals(PreviewState.Preparing, recoveringState.session.previewState)
        assertNull(recoveringState.session.errorState)
        assertNull(recoveringState.session.message)
        assertEquals(0, cameraRepository.startPreviewCallCount)

        advanceUntilIdle()

        val recoveredState = viewModel.uiState.value
        assertEquals(PermissionState.Granted, recoveredState.permission.permissionState)
        assertEquals(PreviewState.Showing, recoveredState.session.previewState)
        assertNull(recoveredState.session.errorState)
        assertNull(recoveredState.session.message)
        assertEquals(1, cameraRepository.resolveInitialLensCallCount)
        assertEquals(1, cameraRepository.startPreviewCallCount)
    }

    /**
     * When [onPermissionStateChanged] is called with [PermissionState.Granted] while permission
     * is already [PermissionState.Granted], [CameraRepository.startPreview] must NOT be called
     * again (guard: previousPermissionState == Granted prevents duplicate preview starts).
     */
    @Test
    fun onPermissionStateChanged_alreadyGranted_doesNotStartPreviewAgain() = runTest {
        val cameraRepository = FakeCameraRepository()
        val permissionRepository = FakePermissionRepository(
            checkCameraPermissionResult = PermissionState.Granted,
        )
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = permissionRepository,
        )
        viewModel.initialize()
        advanceUntilIdle()
        assertEquals(1, cameraRepository.startPreviewCallCount)

        viewModel.onPermissionStateChanged(isGranted = true, isChecking = false)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(PermissionState.Granted, uiState.permission.permissionState)
        assertEquals(PreviewState.Preparing, uiState.session.previewState)
        assertEquals(1, cameraRepository.resolveInitialLensCallCount)
        assertEquals(1, cameraRepository.startPreviewCallCount)
    }

    /**
     * When permission transitions to [PermissionState.Unknown] from a [PreviewState.Error] state,
     * [CameraUiState.session.previewState] must be reset to [PreviewState.Preparing] and
     * [CameraUiState.session.errorState] must be cleared.
     */
    @Test
    fun onPermissionStateChanged_toUnknownFromError_resetsPreviewStateToPreparing() = runTest {
        val cameraRepository = FakeCameraRepository()
        val permissionRepository = FakePermissionRepository(
            checkCameraPermissionResult = PermissionState.Unknown,
        )
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = permissionRepository,
        )
        advanceUntilIdle()

        viewModel.onPermissionStateChanged(isGranted = false, isChecking = false)
        assertEquals(CameraError.PermissionDenied, viewModel.uiState.value.session.errorState)
        assertIs<PreviewState.Error>(viewModel.uiState.value.session.previewState)

        viewModel.onPermissionStateChanged(isGranted = false, isChecking = true)

        val uiState = viewModel.uiState.value
        assertEquals(PermissionState.Unknown, uiState.permission.permissionState)
        assertEquals(PreviewState.Preparing, uiState.session.previewState)
        assertNull(uiState.session.errorState)
        assertNull(uiState.session.message)
        assertEquals(0, cameraRepository.startPreviewCallCount)
    }

    @Test
    fun onPermissionStateChanged_fromUnknownToDenied_setsPermissionDeniedAndDoesNotStartPreview() =
        runTest {
            val cameraRepository = FakeCameraRepository()
            val permissionRepository = FakePermissionRepository(
                checkCameraPermissionResult = PermissionState.Unknown,
            )
            val viewModel = CameraViewModel(
                cameraRepository = cameraRepository,
                permissionRepository = permissionRepository,
            )
            advanceUntilIdle()

            viewModel.onPermissionStateChanged(isGranted = false, isChecking = false)
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(PermissionState.Denied, uiState.permission.permissionState)
            assertEquals(CameraError.PermissionDenied, uiState.session.errorState)
            val previewState = assertIs<PreviewState.Error>(uiState.session.previewState)
            assertEquals(CameraError.PermissionDenied, previewState.error)
            assertEquals(CameraMessageType.Error, uiState.session.message?.type)
            assertEquals(0, cameraRepository.resolveInitialLensCallCount)
            assertEquals(0, cameraRepository.startPreviewCallCount)
        }

    @Test
    fun observePreviewState_syncsPreparingShowingAndErrorToUiState() = runTest {
        val cameraRepository = FakeCameraRepository()
        val permissionRepository = FakePermissionRepository(
            checkCameraPermissionResult = PermissionState.Unknown,
        )
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = permissionRepository,
        )
        advanceUntilIdle()

        cameraRepository.emitPreviewState(PreviewState.Showing)
        advanceUntilIdle()

        assertEquals(PreviewState.Showing, viewModel.uiState.value.session.previewState)
        assertNull(viewModel.uiState.value.session.errorState)
        assertNull(viewModel.uiState.value.session.message)

        cameraRepository.emitPreviewState(PreviewState.Error(CameraError.CameraUnavailable))
        advanceUntilIdle()

        val errorState = viewModel.uiState.value
        assertIs<PreviewState.Error>(errorState.session.previewState)
        assertEquals(CameraError.CameraUnavailable, errorState.session.errorState)
        assertEquals(CameraMessageType.Error, errorState.session.message?.type)

        cameraRepository.emitPreviewState(PreviewState.Preparing)
        advanceUntilIdle()

        val preparingState = viewModel.uiState.value
        assertEquals(PreviewState.Preparing, preparingState.session.previewState)
        assertNull(preparingState.session.errorState)
        assertNull(preparingState.session.message)
    }

    // ---------------------------------------------------------------------------
    // onRetryPreview() – error type preserved from repository
    // ---------------------------------------------------------------------------

    /**
     * When [CameraRepository.startPreview] fails with
     * [CameraRepositoryException(CameraError.CameraUnavailable)],
     * [CameraViewModel.onRetryPreview] must set [CameraUiState.session.errorState] to
     * [CameraError.CameraUnavailable] — NOT the generic [CameraError.PreviewInitializationFailed].
     *
     * Verifies that [CameraRepositoryException.error] is extracted and not overwritten.
     */
    @Test
    fun onRetryPreview_whenRepositoryThrowsCameraUnavailable_setsCameraUnavailableError() =
        runTest {
            val cameraRepository = FakeCameraRepository(
                startPreviewResult = Result.failure(
                    CameraRepositoryException(CameraError.CameraUnavailable),
                ),
            )
            val viewModel = CameraViewModel(
                cameraRepository = cameraRepository,
                permissionRepository = FakePermissionRepository(PermissionState.Unknown),
            )
            advanceUntilIdle()

            viewModel.onRetryPreview()
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(CameraError.CameraUnavailable, uiState.session.errorState)
            val previewState = assertIs<PreviewState.Error>(uiState.session.previewState)
            assertEquals(CameraError.CameraUnavailable, previewState.error)
            assertEquals(1, cameraRepository.startPreviewCallCount)
        }

    /**
     * When [CameraRepository.startPreview] fails with a plain (non-[CameraRepositoryException])
     * throwable, [CameraViewModel.onRetryPreview] must fall back to
     * [CameraError.PreviewInitializationFailed].
     */
    @Test
    fun onRetryPreview_whenRepositoryThrowsGenericException_setsPreviewInitializationFailedError() =
        runTest {
            val cameraRepository = FakeCameraRepository(
                startPreviewResult = Result.failure(IllegalStateException("boom")),
            )
            val viewModel = CameraViewModel(
                cameraRepository = cameraRepository,
                permissionRepository = FakePermissionRepository(PermissionState.Unknown),
            )
            advanceUntilIdle()

            viewModel.onRetryPreview()
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(CameraError.PreviewInitializationFailed, uiState.session.errorState)
            val previewState = assertIs<PreviewState.Error>(uiState.session.previewState)
            assertEquals(CameraError.PreviewInitializationFailed, previewState.error)
            assertEquals(1, cameraRepository.startPreviewCallCount)
        }

    // ---------------------------------------------------------------------------
    // onToggleLensFacing() – error type preserved from repository
    // ---------------------------------------------------------------------------

    /**
     * When [CameraRepository.switchLens] fails with
     * [CameraRepositoryException(CameraError.LensSwitchFailed)],
     * [CameraViewModel.onToggleLensFacing] must set [CameraUiState.session.errorState] to
     * [CameraError.LensSwitchFailed].
     */
    @Test
    fun onToggleLensFacing_whenSwitchSucceeds_updatesLensFacing() = runTest {
        val cameraRepository = FakeCameraRepository(
            switchLensResult = Result.success(CameraLensFacing.Front),
        )
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        advanceUntilIdle()
        assertEquals(CameraLensFacing.Back, viewModel.uiState.value.session.lensFacing)

        viewModel.onToggleLensFacing()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(CameraLensFacing.Front, uiState.session.lensFacing)
        assertNull(uiState.session.errorState)
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
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        advanceUntilIdle()

        viewModel.onToggleLensFacing()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(CameraError.LensSwitchFailed, uiState.session.errorState)
        val previewState = assertIs<PreviewState.Error>(uiState.session.previewState)
        assertEquals(CameraError.LensSwitchFailed, previewState.error)
        assertEquals(1, cameraRepository.switchLensCallCount)
    }

    @Test
    fun onFaceTrackingFrameChanged_formatsAngleAndClampsPercentLabels() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val frame = createFaceFrame(
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

        viewModel.onFaceTrackingFrameChanged(frame)
        advanceUntilIdle()

        val display = assertNotNull(viewModel.uiState.value.faceTracking.display)
        assertEquals("12 deg", display.headYawLabel)
        assertEquals("-9 deg", display.headPitchLabel)
        assertEquals("30 deg", display.headRollLabel)
        assertEquals("0%", display.leftEyeBlinkLabel)
        assertEquals("45%", display.rightEyeBlinkLabel)
        assertEquals("100%", display.jawOpenLabel)
        assertEquals("100%", display.mouthSmileLabel)
    }

    @Test
    fun onFaceTrackingFrameChanged_whenFrameIsNull_setsNotTrackingAndClearsDisplay() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        viewModel.onFaceTrackingFrameChanged(createFaceFrame())
        advanceUntilIdle()

        viewModel.onFaceTrackingFrameChanged(null)
        advanceUntilIdle()

        val faceTracking = viewModel.uiState.value.faceTracking
        assertEquals(false, faceTracking.isTracking)
        assertNull(faceTracking.frame)
        assertNull(faceTracking.display)
    }

    @Test
    fun onFaceTrackingFrameChanged_whenConfidenceLow_transitionsAvatarStateToLost() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val highConfidenceFrame = createFaceFrame(
            timestampMillis = 200L,
            trackingConfidence = 0.92f,
            headYawDegrees = 9f,
            headPitchDegrees = 1f,
            headRollDegrees = -4f,
        )
        val lowConfidenceFrame = createFaceFrame(
            timestampMillis = 201L,
            trackingConfidence = 0.2f,
            headYawDegrees = 3f,
            headPitchDegrees = 2f,
            headRollDegrees = 1f,
        )

        viewModel.onFaceTrackingFrameChanged(highConfidenceFrame)
        advanceUntilIdle()
        viewModel.onFaceTrackingFrameChanged(lowConfidenceFrame)
        advanceUntilIdle()

        val avatarState = viewModel.uiState.value.avatarRender
        assertEquals(AvatarTrackingStatus.Lost, avatarState.trackingStatus)
        assertEquals(0.2f, avatarState.trackingConfidence)
        assertEquals(201L, avatarState.sourceTimestampMillis)
    }

    @Test
    fun onFaceTrackingFrameChanged_usesPreviousAvatarStateAcrossSequentialFrames() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val firstFrame = createFaceFrame(
            timestampMillis = 300L,
            trackingConfidence = 0.95f,
            headYawDegrees = 10f,
        )
        val secondFrame = createFaceFrame(
            timestampMillis = 301L,
            trackingConfidence = 0.1f,
            headYawDegrees = 8f,
        )

        viewModel.onFaceTrackingFrameChanged(firstFrame)
        advanceUntilIdle()
        val trackedState = viewModel.uiState.value.avatarRender
        assertEquals(AvatarTrackingStatus.Tracking, trackedState.trackingStatus)
        assertEquals(300L, trackedState.sourceTimestampMillis)

        viewModel.onFaceTrackingFrameChanged(secondFrame)
        advanceUntilIdle()
        val lostState = viewModel.uiState.value.avatarRender
        assertEquals(AvatarTrackingStatus.Lost, lostState.trackingStatus)
        assertEquals(301L, lostState.sourceTimestampMillis)

        viewModel.onFaceTrackingFrameChanged(null)
        advanceUntilIdle()
        val notTrackedState = viewModel.uiState.value.avatarRender
        assertEquals(AvatarTrackingStatus.NotTracked, notTrackedState.trackingStatus)
        assertEquals(301L, notTrackedState.sourceTimestampMillis)
    }

    // ---------------------------------------------------------------------------
    // onFilePicked()
    // ---------------------------------------------------------------------------

    /**
     * When [CameraViewModel.onFilePicked] receives [FilePickerResult.Success],
     * [CameraUiState.avatarPreview] must be updated with the parsed avatar data and
     * [CameraUiState.avatarSelection.filePickerErrorMessageRes] must be null.
     */
    @Test
    fun onFilePicked_success_updatesAvatarPreviewAndClearsError() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val firstSelection =
            createAvatarSelectionData("first.vrm", "Avatar A", byteArrayOf(1, 2, 3))
        val secondSelection =
            createAvatarSelectionData("second.vrm", "Avatar B", byteArrayOf(4, 5, 6))

        try {
            viewModel.onFilePicked(FilePickerResult.Success(firstSelection))
            advanceUntilIdle()
            assertEquals(firstSelection.preview, viewModel.uiState.value.avatarPreview)
            assertNull(viewModel.uiState.value.avatarSelection.filePickerErrorMessageRes)
            assertNotNull(AvatarAssetStore.load(firstSelection.assetHandle))

            viewModel.onFilePicked(FilePickerResult.Error(Res.string.vrm_error_select_file))
            advanceUntilIdle()
            assertEquals(
                Res.string.vrm_error_select_file,
                viewModel.uiState.value.avatarSelection.filePickerErrorMessageRes
            )

            viewModel.onFilePicked(FilePickerResult.Success(secondSelection))
            advanceUntilIdle()
            val uiState = viewModel.uiState.value
            assertEquals(secondSelection.preview, uiState.avatarPreview)
            assertNull(uiState.avatarSelection.filePickerErrorMessageRes)
            assertNull(AvatarAssetStore.load(firstSelection.assetHandle))
            assertNotNull(AvatarAssetStore.load(secondSelection.assetHandle))
        } finally {
            AvatarAssetStore.remove(firstSelection.assetHandle)
            AvatarAssetStore.remove(secondSelection.assetHandle)
        }
    }

    /**
     * When [CameraViewModel.onFilePicked] receives [FilePickerResult.Cancelled],
     * [CameraUiState] must remain entirely unchanged.
     */
    @Test
    fun onFilePicked_cancelled_doesNotChangeState() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        advanceUntilIdle()
        val stateBefore = viewModel.uiState.value

        viewModel.onFilePicked(FilePickerResult.Cancelled)
        advanceUntilIdle()

        assertEquals(stateBefore, viewModel.uiState.value)
    }

    /**
     * When [CameraViewModel.onFilePicked] receives [FilePickerResult.Error],
     * [CameraUiState.avatarSelection.filePickerErrorMessageRes] must be set to the error's [StringResource].
     * A subsequent call to [CameraViewModel.onDismissFilePickerError] must set it back to null.
     */
    @Test
    fun onFilePicked_error_setsFilePickerErrorMessageRes_andDismissClears() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )

        viewModel.onFilePicked(FilePickerResult.Error(Res.string.vrm_error_select_file))
        advanceUntilIdle()
        assertEquals(
            Res.string.vrm_error_select_file,
            viewModel.uiState.value.avatarSelection.filePickerErrorMessageRes
        )

        viewModel.onDismissFilePickerError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.avatarSelection.filePickerErrorMessageRes)
    }

    @Test
    fun onAvatarRenderLoadFailed_whenCurrentHandle_matchesClearsSelectionAndRemovesAsset() =
        runTest {
            val viewModel = CameraViewModel(
                cameraRepository = FakeCameraRepository(),
                permissionRepository = FakePermissionRepository(PermissionState.Unknown),
            )
            val selection =
                createAvatarSelectionData("active.vrm", "Avatar C", byteArrayOf(7, 8, 9))

            try {
                viewModel.onFilePicked(FilePickerResult.Success(selection))
                advanceUntilIdle()
                assertNotNull(AvatarAssetStore.load(selection.assetHandle))

                viewModel.onAvatarRenderLoadFailed(
                    failedAssetHandle = selection.assetHandle,
                    messageRes = Res.string.camera_error_unknown,
                )
                advanceUntilIdle()

                val uiState = viewModel.uiState.value
                assertNull(uiState.avatarSelection.avatarSelection)
                assertEquals(Res.string.camera_error_unknown, uiState.avatarSelection.filePickerErrorMessageRes)
                assertNull(AvatarAssetStore.load(selection.assetHandle))
            } finally {
                AvatarAssetStore.remove(selection.assetHandle)
            }
        }

    @Test
    fun onAvatarRenderLoadFailed_whenHandleIsStale_ignoresAndKeepsCurrentSelection() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val staleSelection =
            createAvatarSelectionData("stale.vrm", "Avatar Stale", byteArrayOf(11, 12))
        val currentSelection =
            createAvatarSelectionData("current.vrm", "Avatar Current", byteArrayOf(13, 14))

        try {
            viewModel.onFilePicked(FilePickerResult.Success(staleSelection))
            viewModel.onFilePicked(FilePickerResult.Success(currentSelection))
            advanceUntilIdle()
            assertNull(AvatarAssetStore.load(staleSelection.assetHandle))
            assertNotNull(AvatarAssetStore.load(currentSelection.assetHandle))

            viewModel.onAvatarRenderLoadFailed(
                failedAssetHandle = staleSelection.assetHandle,
                messageRes = Res.string.camera_error_unknown,
            )
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(currentSelection, uiState.avatarSelection.avatarSelection)
            assertNull(uiState.avatarSelection.filePickerErrorMessageRes)
            assertNotNull(AvatarAssetStore.load(currentSelection.assetHandle))
        } finally {
            AvatarAssetStore.remove(staleSelection.assetHandle)
            AvatarAssetStore.remove(currentSelection.assetHandle)
        }
    }

    // ---------------------------------------------------------------------------
    // onCameraZoomChanged()
    // ---------------------------------------------------------------------------

    @Test
    fun onCameraZoomChanged_appliesScaleChangeWithinBounds() = runTest {
        val cameraRepository = FakeCameraRepository()
        val viewModel = CameraViewModel(
            cameraRepository = cameraRepository,
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        advanceUntilIdle()
        assertEquals(1f, viewModel.uiState.value.zoom.currentCameraZoomRatio)

        viewModel.onCameraZoomChanged(2f)
        assertEquals(2f, cameraRepository.setZoomRatioRequests.last())

        viewModel.onCameraZoomChanged(10f)
        assertEquals(5f, cameraRepository.setZoomRatioRequests.last())

        viewModel.onCameraZoomChanged(0.1f)
        assertEquals(1f, cameraRepository.setZoomRatioRequests.last())
    }

    @Test
    fun releaseCurrentAvatarAsset_removesCurrentAvatarAssetHandle() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val selection = createAvatarSelectionData("active.vrm", "Avatar D", byteArrayOf(21, 22))

        try {
            viewModel.onFilePicked(FilePickerResult.Success(selection))
            advanceUntilIdle()
            assertNotNull(AvatarAssetStore.load(selection.assetHandle))

            viewModel.releaseCurrentAvatarAsset()

            assertNull(AvatarAssetStore.load(selection.assetHandle))
        } finally {
            AvatarAssetStore.remove(selection.assetHandle)
        }
    }

    private fun createAvatarSelectionData(
        fileName: String,
        avatarName: String,
        assetBytes: ByteArray,
    ): AvatarSelectionData {
        val handle = AvatarAssetStore.store(assetBytes)
        return AvatarSelectionData(
            preview = AvatarPreviewData(
                fileName = fileName,
                avatarName = avatarName,
                authorName = "Unit Test",
                vrmVersion = "1.0",
                thumbnailBytes = byteArrayOf(1, 9, 9, 0),
            ),
            assetHandle = handle,
            runtimeDescriptor = VrmRuntimeAssetDescriptor(
                specVersion = VrmSpecVersion.Vrm1,
                rawSpecVersion = "1.0",
                assetVersion = "2.0",
                meta = VrmRuntimeMeta(
                    avatarName = avatarName,
                    authors = listOf("Unit Test"),
                    version = "1.0",
                ),
                thumbnailImageIndex = null,
            ),
        )
    }

    private fun createFaceFrame(
        timestampMillis: Long = 42L,
        trackingConfidence: Float = 0.8f,
        headYawDegrees: Float = 0f,
        headPitchDegrees: Float = 0f,
        headRollDegrees: Float = 0f,
        leftEyeBlink: Float = 0f,
        rightEyeBlink: Float = 0f,
        jawOpen: Float = 0f,
        mouthSmile: Float = 0f,
    ): NormalizedFaceFrame = NormalizedFaceFrame(
        timestampMillis = timestampMillis,
        trackingConfidence = trackingConfidence,
        headYawDegrees = headYawDegrees,
        headPitchDegrees = headPitchDegrees,
        headRollDegrees = headRollDegrees,
        leftEyeBlink = leftEyeBlink,
        rightEyeBlink = rightEyeBlink,
        jawOpen = jawOpen,
        mouthSmile = mouthSmile,
    )

}
