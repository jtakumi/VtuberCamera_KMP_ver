package com.example.vtubercamera_kmp_ver.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import vtubercamera_kmp_ver.composeapp.generated.resources.Res

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
 *                uiState.filePickerErrorMessageRes is set to the appropriate StringResource;
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
     * [CameraRepository.startPreview], leaving [CameraUiState.previewState] as
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
        assertEquals(PermissionState.Granted, uiState.permissionState)
        assertEquals(CameraLensFacing.Front, uiState.lensFacing)
        assertEquals(PreviewState.Showing, uiState.previewState)
        assertNull(uiState.errorState)
        assertNull(uiState.message)
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
        assertEquals(PermissionState.Unknown, uiState.permissionState)
        assertEquals(PreviewState.Preparing, uiState.previewState)
        assertNull(uiState.errorState)
        assertNull(uiState.message)
        assertEquals(0, cameraRepository.resolveInitialLensCallCount)
        assertEquals(0, cameraRepository.startPreviewCallCount)
    }

    /**
     * When [PermissionRepository.checkCameraPermission] returns [PermissionState.Denied],
     * [CameraViewModel.initialize] must set [CameraUiState.errorState] to
     * [CameraError.PermissionDenied] and [CameraUiState.previewState] to
     * [PreviewState.Error(CameraError.PermissionDenied)].
     * [CameraRepository.startPreview] must NOT be called.
     */
    @Test
    fun initialize_whenPermissionDenied_setsPermissionDeniedErrorAndDoesNotStartPreview() = runTest {
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
        assertEquals(PermissionState.Denied, uiState.permissionState)
        assertEquals(CameraError.PermissionDenied, uiState.errorState)
        val previewState = assertIs<PreviewState.Error>(uiState.previewState)
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
     *   - [CameraViewModel.initialize] は [CameraUiState.permissionState] を Denied に更新し、
     *     [CameraUiState.errorState] / [CameraUiState.previewState] を PermissionDenied に設定する。
     *   - [CameraRepository.startPreview] は呼び出されない。
     */
    @Test
    fun initialize_whenPreviouslyGrantedButNowDenied_setsPermissionDeniedAndDoesNotStartPreview() = runTest {
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

        assertEquals(PermissionState.Granted, firstLaunchViewModel.uiState.value.permissionState)
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
        assertEquals(PermissionState.Denied, uiState.permissionState)
        assertEquals(CameraError.PermissionDenied, uiState.errorState)
        val previewState = assertIs<PreviewState.Error>(uiState.previewState)
        assertEquals(CameraError.PermissionDenied, previewState.error)
        assertEquals(0, secondLaunchCameraRepository.startPreviewCallCount)
    }

    // ---------------------------------------------------------------------------
    // onPermissionStateChanged() – permission denied → granted (Settings recovery)
    // ---------------------------------------------------------------------------

    /**
     * When permission transitions from [PermissionState.Denied] to [PermissionState.Granted],
     * [CameraViewModel.onPermissionStateChanged] must:
     *   1. Reset [CameraUiState.previewState] to [PreviewState.Preparing].
     *   2. Clear [CameraUiState.errorState] and [CameraUiState.message].
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
        assertEquals(PermissionState.Denied, deniedState.permissionState)
        assertEquals(CameraError.PermissionDenied, deniedState.errorState)
        assertIs<PreviewState.Error>(deniedState.previewState)

        viewModel.onPermissionStateChanged(isGranted = true, isChecking = false)
        val recoveringState = viewModel.uiState.value
        assertEquals(PermissionState.Granted, recoveringState.permissionState)
        assertEquals(PreviewState.Preparing, recoveringState.previewState)
        assertNull(recoveringState.errorState)
        assertNull(recoveringState.message)
        assertEquals(0, cameraRepository.startPreviewCallCount)

        advanceUntilIdle()

        val recoveredState = viewModel.uiState.value
        assertEquals(PermissionState.Granted, recoveredState.permissionState)
        assertEquals(PreviewState.Showing, recoveredState.previewState)
        assertNull(recoveredState.errorState)
        assertNull(recoveredState.message)
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
        assertEquals(PermissionState.Granted, uiState.permissionState)
        assertEquals(PreviewState.Preparing, uiState.previewState)
        assertEquals(1, cameraRepository.resolveInitialLensCallCount)
        assertEquals(1, cameraRepository.startPreviewCallCount)
    }

    /**
     * When permission transitions to [PermissionState.Unknown] from a [PreviewState.Error] state,
     * [CameraUiState.previewState] must be reset to [PreviewState.Preparing] and
     * [CameraUiState.errorState] must be cleared.
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
        assertEquals(CameraError.PermissionDenied, viewModel.uiState.value.errorState)
        assertIs<PreviewState.Error>(viewModel.uiState.value.previewState)

        viewModel.onPermissionStateChanged(isGranted = false, isChecking = true)

        val uiState = viewModel.uiState.value
        assertEquals(PermissionState.Unknown, uiState.permissionState)
        assertEquals(PreviewState.Preparing, uiState.previewState)
        assertNull(uiState.errorState)
        assertNull(uiState.message)
        assertEquals(0, cameraRepository.startPreviewCallCount)
    }

    @Test
    fun onPermissionStateChanged_fromUnknownToDenied_setsPermissionDeniedAndDoesNotStartPreview() = runTest {
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
        assertEquals(PermissionState.Denied, uiState.permissionState)
        assertEquals(CameraError.PermissionDenied, uiState.errorState)
        val previewState = assertIs<PreviewState.Error>(uiState.previewState)
        assertEquals(CameraError.PermissionDenied, previewState.error)
        assertEquals(CameraMessageType.Error, uiState.message?.type)
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

        assertEquals(PreviewState.Showing, viewModel.uiState.value.previewState)
        assertNull(viewModel.uiState.value.errorState)
        assertNull(viewModel.uiState.value.message)

        cameraRepository.emitPreviewState(PreviewState.Error(CameraError.CameraUnavailable))
        advanceUntilIdle()

        val errorState = viewModel.uiState.value
        assertIs<PreviewState.Error>(errorState.previewState)
        assertEquals(CameraError.CameraUnavailable, errorState.errorState)
        assertEquals(CameraMessageType.Error, errorState.message?.type)

        cameraRepository.emitPreviewState(PreviewState.Preparing)
        advanceUntilIdle()

        val preparingState = viewModel.uiState.value
        assertEquals(PreviewState.Preparing, preparingState.previewState)
        assertNull(preparingState.errorState)
        assertNull(preparingState.message)
    }

    // ---------------------------------------------------------------------------
    // onRetryPreview() – error type preserved from repository
    // ---------------------------------------------------------------------------

    /**
     * When [CameraRepository.startPreview] fails with
     * [CameraRepositoryException(CameraError.CameraUnavailable)],
     * [CameraViewModel.onRetryPreview] must set [CameraUiState.errorState] to
     * [CameraError.CameraUnavailable] — NOT the generic [CameraError.PreviewInitializationFailed].
     *
     * Verifies that [CameraRepositoryException.error] is extracted and not overwritten.
     */
    @Test
    fun onRetryPreview_whenRepositoryThrowsCameraUnavailable_setsCameraUnavailableError() = runTest {
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
        assertEquals(CameraError.CameraUnavailable, uiState.errorState)
        val previewState = assertIs<PreviewState.Error>(uiState.previewState)
        assertEquals(CameraError.CameraUnavailable, previewState.error)
        assertEquals(1, cameraRepository.startPreviewCallCount)
    }

    /**
     * When [CameraRepository.startPreview] fails with a plain (non-[CameraRepositoryException])
     * throwable, [CameraViewModel.onRetryPreview] must fall back to
     * [CameraError.PreviewInitializationFailed].
     */
    @Test
    fun onRetryPreview_whenRepositoryThrowsGenericException_setsPreviewInitializationFailedError() = runTest {
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
        assertEquals(CameraError.PreviewInitializationFailed, uiState.errorState)
        val previewState = assertIs<PreviewState.Error>(uiState.previewState)
        assertEquals(CameraError.PreviewInitializationFailed, previewState.error)
        assertEquals(1, cameraRepository.startPreviewCallCount)
    }

    // ---------------------------------------------------------------------------
    // onToggleLensFacing() – error type preserved from repository
    // ---------------------------------------------------------------------------

    /**
     * When [CameraRepository.switchLens] fails with
     * [CameraRepositoryException(CameraError.LensSwitchFailed)],
     * [CameraViewModel.onToggleLensFacing] must set [CameraUiState.errorState] to
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
        assertEquals(CameraLensFacing.Back, viewModel.uiState.value.lensFacing)

        viewModel.onToggleLensFacing()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(CameraLensFacing.Front, uiState.lensFacing)
        assertNull(uiState.errorState)
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
        assertEquals(CameraError.LensSwitchFailed, uiState.errorState)
        val previewState = assertIs<PreviewState.Error>(uiState.previewState)
        assertEquals(CameraError.LensSwitchFailed, previewState.error)
        assertEquals(1, cameraRepository.switchLensCallCount)
    }

    // ---------------------------------------------------------------------------
    // onFilePicked()
    // ---------------------------------------------------------------------------

    /**
     * When [CameraViewModel.onFilePicked] receives [FilePickerResult.Success],
     * [CameraUiState.avatarPreview] must be updated with the parsed avatar data and
     * [CameraUiState.filePickerErrorMessageRes] must be null.
     */
    @Test
    fun onFilePicked_success_updatesAvatarPreviewAndClearsError() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val firstSelection = createAvatarSelectionData("first.vrm", "Avatar A", byteArrayOf(1, 2, 3))
        val secondSelection = createAvatarSelectionData("second.vrm", "Avatar B", byteArrayOf(4, 5, 6))

        try {
            viewModel.onFilePicked(FilePickerResult.Success(firstSelection))
            advanceUntilIdle()
            assertEquals(firstSelection.preview, viewModel.uiState.value.avatarPreview)
            assertNull(viewModel.uiState.value.filePickerErrorMessageRes)
            assertNotNull(AvatarAssetStore.load(firstSelection.assetHandle))

            viewModel.onFilePicked(FilePickerResult.Error(Res.string.vrm_error_select_file))
            advanceUntilIdle()
            assertEquals(Res.string.vrm_error_select_file, viewModel.uiState.value.filePickerErrorMessageRes)

            viewModel.onFilePicked(FilePickerResult.Success(secondSelection))
            advanceUntilIdle()
            val uiState = viewModel.uiState.value
            assertEquals(secondSelection.preview, uiState.avatarPreview)
            assertNull(uiState.filePickerErrorMessageRes)
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
     * [CameraUiState.filePickerErrorMessageRes] must be set to the error's [StringResource].
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
        assertEquals(Res.string.vrm_error_select_file, viewModel.uiState.value.filePickerErrorMessageRes)

        viewModel.onDismissFilePickerError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.filePickerErrorMessageRes)
    }

    @Test
    fun onAvatarRenderLoadFailed_whenCurrentHandle_matchesClearsSelectionAndRemovesAsset() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val selection = createAvatarSelectionData("active.vrm", "Avatar C", byteArrayOf(7, 8, 9))

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
            assertNull(uiState.avatarSelection)
            assertEquals(Res.string.camera_error_unknown, uiState.filePickerErrorMessageRes)
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
        val staleSelection = createAvatarSelectionData("stale.vrm", "Avatar Stale", byteArrayOf(11, 12))
        val currentSelection = createAvatarSelectionData("current.vrm", "Avatar Current", byteArrayOf(13, 14))

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
            assertEquals(currentSelection, uiState.avatarSelection)
            assertNull(uiState.filePickerErrorMessageRes)
            assertNotNull(AvatarAssetStore.load(currentSelection.assetHandle))
        } finally {
            AvatarAssetStore.remove(staleSelection.assetHandle)
            AvatarAssetStore.remove(currentSelection.assetHandle)
        }
    }

    @Test
    fun onCleared_removesCurrentAvatarAssetHandle() = runTest {
        val viewModel = CameraViewModel(
            cameraRepository = FakeCameraRepository(),
            permissionRepository = FakePermissionRepository(PermissionState.Unknown),
        )
        val selection = createAvatarSelectionData("active.vrm", "Avatar D", byteArrayOf(21, 22))

        try {
            viewModel.onFilePicked(FilePickerResult.Success(selection))
            advanceUntilIdle()
            assertNotNull(AvatarAssetStore.load(selection.assetHandle))

            val onCleared = CameraViewModel::class.java.getDeclaredMethod("onCleared")
            onCleared.isAccessible = true
            onCleared.invoke(viewModel)

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

    private class FakeCameraRepository(
        private val resolveInitialLensResult: Result<CameraLensFacing> = Result.success(CameraLensFacing.Back),
        private val startPreviewResult: Result<CameraLensFacing>? = null,
        private val switchLensResult: Result<CameraLensFacing> = Result.success(CameraLensFacing.Front),
    ) : CameraRepository {
        private val previewState = MutableStateFlow<PreviewState>(PreviewState.Preparing)

        var startPreviewCallCount: Int = 0
            private set

        var resolveInitialLensCallCount: Int = 0
            private set

        var switchLensCallCount: Int = 0
            private set

        val startPreviewRequests = mutableListOf<CameraLensFacing>()
        val resolveInitialLensRequests = mutableListOf<CameraLensFacing>()
        val switchLensRequests = mutableListOf<CameraLensFacing>()

        override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
            startPreviewCallCount += 1
            startPreviewRequests += lensFacing
            val result = startPreviewResult ?: Result.success(lensFacing)
            if (result.isSuccess) {
                previewState.value = PreviewState.Showing
            }
            return result
        }

        override suspend fun stopPreview() {
            previewState.value = PreviewState.Preparing
        }

        override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
            switchLensCallCount += 1
            switchLensRequests += current
            val result = switchLensResult
            if (result.isSuccess) {
                previewState.value = PreviewState.Showing
            }
            return result
        }

        override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
            resolveInitialLensCallCount += 1
            resolveInitialLensRequests += preferred
            return resolveInitialLensResult
        }

        override fun observePreviewState(): Flow<PreviewState> {
            return previewState.asStateFlow()
        }

        override fun onPlatformPreviewStarted(lensFacing: CameraLensFacing) {
            previewState.value = PreviewState.Showing
        }

        override fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError) {
            previewState.value = PreviewState.Error(error)
        }

        fun emitPreviewState(state: PreviewState) {
            previewState.value = state
        }
    }

    private class FakePermissionRepository(
        private val checkCameraPermissionResult: PermissionState,
    ) : PermissionRepository {
        override suspend fun checkCameraPermission(): PermissionState {
            return checkCameraPermissionResult
        }

        override suspend fun requestCameraPermission(): PermissionState {
            return checkCameraPermissionResult
        }
    }
}
