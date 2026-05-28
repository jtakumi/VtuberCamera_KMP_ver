package com.example.vtubercamera_kmp_ver.camera.testing

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.CameraLensFacing
import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.CameraZoomUiState
import com.example.vtubercamera_kmp_ver.camera.PermissionRepository
import com.example.vtubercamera_kmp_ver.camera.PermissionState
import com.example.vtubercamera_kmp_ver.camera.PreviewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// commonTest 全体で共有するカメラリポジトリのフェイク実装。call 記録 / 結果差し替え / preview state 注入を提供する。
internal class FakeCameraRepository(
    private val resolveInitialLensResult: Result<CameraLensFacing> = Result.success(
        CameraLensFacing.Back,
    ),
    private val startPreviewResult: Result<CameraLensFacing>? = null,
    private val switchLensResult: Result<CameraLensFacing> = Result.success(CameraLensFacing.Front),
) : CameraRepository {
    private val previewState = MutableStateFlow<PreviewState>(PreviewState.Preparing)
    private val zoomUiState = MutableStateFlow(
        CameraZoomUiState(
            currentCameraZoomRatio = 1f,
            minCameraZoomRatio = 1f,
            maxCameraZoomRatio = 5f,
        ),
    )

    var startPreviewCallCount: Int = 0
        private set

    var resolveInitialLensCallCount: Int = 0
        private set

    var switchLensCallCount: Int = 0
        private set

    val startPreviewRequests = mutableListOf<CameraLensFacing>()
    val resolveInitialLensRequests = mutableListOf<CameraLensFacing>()
    val switchLensRequests = mutableListOf<CameraLensFacing>()
    val setZoomRatioRequests = mutableListOf<Float>()

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

    override fun observeZoomState(): Flow<CameraZoomUiState> {
        return zoomUiState.asStateFlow()
    }

    override fun onPlatformZoomStateChanged(zoomUiState: CameraZoomUiState) {
        this.zoomUiState.value = CameraZoomUiState(
            currentCameraZoomRatio = zoomUiState.currentCameraZoomRatio,
            minCameraZoomRatio = zoomUiState.minCameraZoomRatio,
            maxCameraZoomRatio = zoomUiState.maxCameraZoomRatio,
        )
    }

    override fun setZoomRatio(updatedZoomRatio: Float) {
        setZoomRatioRequests += updatedZoomRatio
        zoomUiState.value = zoomUiState.value.copy(currentCameraZoomRatio = updatedZoomRatio)
    }

    fun emitPreviewState(state: PreviewState) {
        previewState.value = state
    }
}

internal class FakePermissionRepository(
    private val checkCameraPermissionResult: PermissionState,
) : PermissionRepository {
    override suspend fun checkCameraPermission(): PermissionState {
        return checkCameraPermissionResult
    }

    override suspend fun requestCameraPermission(): PermissionState {
        return checkCameraPermissionResult
    }
}
