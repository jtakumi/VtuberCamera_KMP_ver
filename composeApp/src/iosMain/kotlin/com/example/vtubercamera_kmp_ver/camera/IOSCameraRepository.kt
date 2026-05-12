package com.example.vtubercamera_kmp_ver.camera

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.AVFoundation.AVCaptureDevice

internal typealias IOSCameraLensAvailability = (CameraLensFacing) -> Boolean

internal class IOSCameraRepository(
    private val hasLens: IOSCameraLensAvailability,
    private val previewState: MutableStateFlow<PreviewState> = MutableStateFlow(PreviewState.Preparing),
    private val zoomState: MutableStateFlow<CameraZoomUiState> = MutableStateFlow(CameraZoomUiState()),
) : CameraRepository {
    private var pendingLensFacing: CameraLensFacing? = null
    private var currentDevice: AVCaptureDevice? = null

    // プレビュー開始前の状態を整え、利用可能なレンズを解決する。
    override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
        val resolvedLens = resolveAvailableLens(requested = lensFacing, hasLens = hasLens)
            ?: return Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
        pendingLensFacing = resolvedLens
        previewState.value = PreviewState.Preparing
        return Result.success(resolvedLens)
    }

    // プレビュー停止に合わせて内部状態を初期化する。
    override suspend fun stopPreview() {
        pendingLensFacing = null
        previewState.value = PreviewState.Preparing
    }

    // 現在と反対側のレンズへ切り替え可能か確認して反映する。
    override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
        previewState.value = PreviewState.Preparing
        val targetLens = current.toggled()
        if (!hasLens(targetLens)) {
            previewState.value = PreviewState.Error(CameraError.LensSwitchFailed)
            return Result.failure(CameraRepositoryException(CameraError.LensSwitchFailed))
        }
        pendingLensFacing = targetLens
        return Result.success(targetLens)
    }

    // 初回表示時に利用できるレンズを決定する。
    override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
        val resolvedLens = resolveAvailableLens(requested = preferred, hasLens = hasLens)
            ?: return Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
        return Result.success(resolvedLens)
    }

    // プレビュー状態の変更を監視する Flow を返す。
    override fun observePreviewState(): Flow<PreviewState> = previewState

    // ネイティブ側でプレビュー開始が完了したことを状態へ反映する。
    override fun onPlatformPreviewStarted(lensFacing: CameraLensFacing) {
        if (pendingLensFacing == null || pendingLensFacing == lensFacing) {
            pendingLensFacing = lensFacing
            previewState.value = PreviewState.Showing
        }
    }

    // ネイティブ側のプレビュー開始失敗を状態へ反映する。
    override fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError) {
        if (pendingLensFacing == lensFacing) {
            pendingLensFacing = null
            previewState.value = PreviewState.Error(error)
        }
    }

    override fun observeZoomState(): Flow<CameraZoomUiState> = zoomState

    override fun onPlatformZoomStateChanged(zoomUiState: CameraZoomUiState) {
        zoomState.value = zoomUiState
    }

    // AVCaptureDevice.videoZoomFactor を使って実際のカメラズームを適用する。
    override fun setZoomRatio(updatedZoomRatio: Float) {
        val device = currentDevice ?: return
        val clamped = updatedZoomRatio.toDouble().coerceIn(
            device.minAvailableVideoZoomFactor,
            device.maxAvailableVideoZoomFactor,
        )
        if (device.lockForConfiguration(null)) {
            device.videoZoomFactor = clamped
            device.unlockForConfiguration()
            zoomState.value = CameraZoomUiState(
                currentCameraZoomRatio = clamped.toFloat(),
                minCameraZoomRatio = device.minAvailableVideoZoomFactor.toFloat(),
                maxCameraZoomRatio = device.maxAvailableVideoZoomFactor.toFloat(),
            )
        }
    }

    // AVFoundation セッション開始後にデバイスを登録し、ズーム能力を公開する。
    // ARKit 使用時は device=null を渡してズーム状態を初期値に戻す。
    fun onPlatformCameraDeviceReady(device: AVCaptureDevice?) {
        currentDevice = device
        zoomState.value = if (device != null) {
            CameraZoomUiState(
                currentCameraZoomRatio = device.videoZoomFactor.toFloat(),
                minCameraZoomRatio = device.minAvailableVideoZoomFactor.toFloat(),
                maxCameraZoomRatio = device.maxAvailableVideoZoomFactor.toFloat(),
            )
        } else {
            CameraZoomUiState()
        }
    }
}

// 指定レンズが使えない場合に代替レンズを含めて利用可否を解決する。
internal fun resolveAvailableLens(
    requested: CameraLensFacing,
    hasLens: IOSCameraLensAvailability,
): CameraLensFacing? {
    if (hasLens(requested)) {
        return requested
    }

    val fallback = requested.toggled()
    return fallback.takeIf(hasLens)
}
