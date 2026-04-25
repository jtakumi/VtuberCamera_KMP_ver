package com.example.vtubercamera_kmp_ver.camera

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Unit test stubs for [CameraViewModel] state-machine logic.
 *
 * # Test infrastructure note
 * This file includes both:
 * - Executable unit tests that use a fake [CameraRepository] / [PermissionRepository] and
 *   kotlinx-coroutines-test dispatcher control.
 * - Pending scenarios still marked with @Ignore + TODO until dedicated fixtures are added.
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
    @Ignore
    @Test
    fun initialize_whenPermissionGranted_startsCameraPreview() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
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
        assertIs<PreviewState.Error>(uiState.previewState)
        assertEquals(CameraError.PermissionDenied, (uiState.previewState as PreviewState.Error).error)
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
        assertIs<PreviewState.Error>(uiState.previewState)
        assertEquals(CameraError.PermissionDenied, (uiState.previewState as PreviewState.Error).error)
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
    @Ignore
    @Test
    fun onPermissionStateChanged_fromDeniedToGranted_resetsErrorAndStartsPreview() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
    }

    /**
     * When [onPermissionStateChanged] is called with [PermissionState.Granted] while permission
     * is already [PermissionState.Granted], [CameraRepository.startPreview] must NOT be called
     * again (guard: previousPermissionState == Granted prevents duplicate preview starts).
     */
    @Ignore
    @Test
    fun onPermissionStateChanged_alreadyGranted_doesNotStartPreviewAgain() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
    }

    /**
     * When permission transitions to [PermissionState.Unknown] from a [PreviewState.Error] state,
     * [CameraUiState.previewState] must be reset to [PreviewState.Preparing] and
     * [CameraUiState.errorState] must be cleared.
     */
    @Ignore
    @Test
    fun onPermissionStateChanged_toUnknownFromError_resetsPreviewStateToPreparing() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
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
    @Ignore
    @Test
    fun onRetryPreview_whenRepositoryThrowsCameraUnavailable_setsCameraUnavailableError() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
    }

    /**
     * When [CameraRepository.startPreview] fails with a plain (non-[CameraRepositoryException])
     * throwable, [CameraViewModel.onRetryPreview] must fall back to
     * [CameraError.PreviewInitializationFailed].
     */
    @Ignore
    @Test
    fun onRetryPreview_whenRepositoryThrowsGenericException_setsPreviewInitializationFailedError() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
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
    @Ignore
    @Test
    fun onToggleLensFacing_whenSwitchFails_setsLensSwitchFailedError() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
    }

    // ---------------------------------------------------------------------------
    // onFilePicked()
    // ---------------------------------------------------------------------------

    /**
     * When [CameraViewModel.onFilePicked] receives [FilePickerResult.Success],
     * [CameraUiState.avatarPreview] must be updated with the parsed avatar data and
     * [CameraUiState.filePickerErrorMessageRes] must be null.
     */
    @Ignore
    @Test
    fun onFilePicked_success_updatesAvatarPreviewAndClearsError() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
    }

    /**
     * When [CameraViewModel.onFilePicked] receives [FilePickerResult.Cancelled],
     * [CameraUiState] must remain entirely unchanged.
     */
    @Ignore
    @Test
    fun onFilePicked_cancelled_doesNotChangeState() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
    }

    /**
     * When [CameraViewModel.onFilePicked] receives [FilePickerResult.Error],
     * [CameraUiState.filePickerErrorMessageRes] must be set to the error's [StringResource].
     * A subsequent call to [CameraViewModel.onDismissFilePickerError] must set it back to null.
     */
    @Ignore
    @Test
    fun onFilePicked_error_setsFilePickerErrorMessageRes_andDismissClears() {
        TODO("Requires fake repositories + TestCoroutineScheduler")
    }

    private class FakeCameraRepository : CameraRepository {
        private val previewState = MutableStateFlow<PreviewState>(PreviewState.Preparing)

        var startPreviewCallCount: Int = 0
            private set

        override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
            startPreviewCallCount += 1
            previewState.value = PreviewState.Showing
            return Result.success(lensFacing)
        }

        override suspend fun stopPreview() {
            previewState.value = PreviewState.Preparing
        }

        override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
            return Result.success(current)
        }

        override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
            return Result.success(preferred)
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
