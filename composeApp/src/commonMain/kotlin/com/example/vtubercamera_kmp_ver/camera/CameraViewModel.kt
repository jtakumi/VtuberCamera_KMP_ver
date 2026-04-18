package com.example.vtubercamera_kmp_ver.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vtubercamera_kmp_ver.avatar.mapping.FaceToAvatarMapper
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_permission_denied
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_retrying_preview
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_switching_lens

// 共有のカメラ状態、権限遷移、プレビュー開始、ファイル選択結果をまとめて制御する。
class CameraViewModel(
    private val cameraRepository: CameraRepository,
    private val permissionRepository: PermissionRepository,
) : ViewModel() {
    private val faceToAvatarMapper = FaceToAvatarMapper()
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePreviewState()
        }
    }

    // 画面初期化時に権限状態を確認し、必要ならプレビュー開始やエラー反映を行う。
    fun initialize() {
        viewModelScope.launch {
            val permissionState = permissionRepository.checkCameraPermission()
            _uiState.update { it.copy(permissionState = permissionState) }

            if (permissionState == PermissionState.Granted) {
                startCameraPreview()
            } else if (permissionState == PermissionState.Denied) {
                setError(CameraError.PermissionDenied)
            }
        }
    }

    // カメラ権限の再要求フローを開始し、UI の一時状態を整える。
    fun onRequestPermission() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    permissionState = PermissionState.Unknown,
                    errorState = null,
                    message = null,
                    previewState = state.previewState,
                )
            }
            permissionRepository.requestCameraPermission()
        }
    }

    // プレビュー再試行時の案内メッセージを出しつつ開始処理をやり直す。
    fun onRetryPreview() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewState = PreviewState.Preparing,
                    errorState = null,
                    message = CameraMessage(
                        type = CameraMessageType.Guide,
                        messageRes = Res.string.camera_error_retrying_preview,
                    ),
                )
            }
            cameraRepository.startPreview(_uiState.value.lensFacing)
                .onSuccess { resolvedLens ->
                    _uiState.update { it.copy(lensFacing = resolvedLens, message = null) }
                }
                .onFailure {
                    setError(it.toCameraError(CameraError.PreviewInitializationFailed))
                }
        }
    }

    // 実際に選択されたレンズ向きを UI 状態へ反映する。
    fun onLensFacingChanged(lensFacing: CameraLensFacing) {
        _uiState.update { currentState ->
            currentState.copy(
                lensFacing = lensFacing,
            )
        }
    }

    // ネイティブ側から受け取った権限状態の変化を UI 状態へ反映する。
    fun onPermissionStateChanged(
        isGranted: Boolean,
        isChecking: Boolean,
    ) {
        val permissionState = when {
            isChecking -> PermissionState.Unknown
            isGranted -> PermissionState.Granted
            else -> PermissionState.Denied
        }
        val previousPermissionState = _uiState.value.permissionState
        _uiState.update { currentState ->
            when (permissionState) {
                PermissionState.Denied -> currentState.copy(
                    permissionState = permissionState,
                    errorState = CameraError.PermissionDenied,
                    previewState = PreviewState.Error(CameraError.PermissionDenied),
                    message = CameraMessage(
                        type = CameraMessageType.Error,
                        messageRes = Res.string.camera_error_permission_denied,
                    ),
                )
                PermissionState.Granted -> currentState.copy(
                    permissionState = permissionState,
                    errorState = null,
                    message = null,
                    previewState = PreviewState.Preparing,
                )
                PermissionState.Unknown -> currentState.copy(
                    permissionState = permissionState,
                    errorState = null,
                    message = null,
                    previewState = if (currentState.previewState is PreviewState.Error) {
                        PreviewState.Preparing
                    } else {
                        currentState.previewState
                    },
                )
            }
        }
        if (permissionState == PermissionState.Granted && previousPermissionState != PermissionState.Granted) {
            viewModelScope.launch {
                startCameraPreview()
            }
        }
    }

    // 前後カメラの切り替え要求を発行し、切り替え中の状態を表示する。
    fun onToggleLensFacing() {
        viewModelScope.launch {
            val currentLens = _uiState.value.lensFacing
            _uiState.update { state ->
                state.copy(
                    previewState = PreviewState.Preparing,
                    message = CameraMessage(
                        type = CameraMessageType.Guide,
                        messageRes = Res.string.camera_error_switching_lens,
                    ),
                )
            }

            cameraRepository.switchLens(currentLens)
                .onSuccess { switchedLens ->
                    _uiState.update { state ->
                        state.copy(
                            lensFacing = switchedLens,
                            errorState = null,
                            message = null,
                        )
                    }
                }
                .onFailure {
                    setError(CameraError.LensSwitchFailed)
                }
        }
    }

    // 顔トラッキング結果を画面表示用の状態へ変換して保持する。
    fun onFaceTrackingFrameChanged(frame: NormalizedFaceFrame?) {
        _uiState.update { currentState ->
            val avatarRenderState = faceToAvatarMapper.map(
                frame = frame,
                previousState = currentState.avatarRenderState,
            )
            currentState.copy(
                faceTracking = FaceTrackingUiState(
                    isTracking = frame != null,
                    frame = frame,
                    display = frame?.toDisplayState(),
                ),
                avatarRenderState = avatarRenderState,
            )
        }
    }

    // ファイルピッカーの結果に応じてアバタープレビューやエラーを更新する。
    fun onFilePicked(result: FilePickerResult) {
        _uiState.update { currentState ->
            when (result) {
                is FilePickerResult.Success -> currentState.copy(
                    avatarSelection = result.avatarSelection,
                    filePickerErrorMessageRes = null,
                )
                is FilePickerResult.Error -> currentState.copy(
                    filePickerErrorMessageRes = result.messageRes,
                )
                FilePickerResult.Cancelled -> currentState
            }
        }
    }

    // ファイル選択エラー表示を閉じる。
    fun onDismissFilePickerError() {
        _uiState.update { currentState ->
            currentState.copy(filePickerErrorMessageRes = null)
        }
    }

    // リポジトリのプレビュー状態を監視して UI 状態へ同期する。
    private suspend fun observePreviewState() {
        cameraRepository.observePreviewState().collect { previewState ->
            _uiState.update { currentState ->
                val error = (previewState as? PreviewState.Error)?.error
                currentState.copy(
                    previewState = previewState,
                    errorState = error,
                    message = error?.toCameraMessage(),
                )
            }
        }
    }

    // 利用可能な初期レンズを解決したうえでカメラプレビュー開始を依頼する。
    private suspend fun startCameraPreview() {
        val initialLensResult = cameraRepository.resolveInitialLens(_uiState.value.lensFacing)
        if (initialLensResult.isFailure) {
            setError(initialLensResult.exceptionOrNull().toCameraError(CameraError.CameraUnavailable))
            return
        }
        val initialLens = initialLensResult.getOrDefault(_uiState.value.lensFacing)
        _uiState.update { it.copy(lensFacing = initialLens) }
        cameraRepository.startPreview(initialLens)
            .onSuccess { resolvedLens ->
                _uiState.update { it.copy(lensFacing = resolvedLens) }
            }
            .onFailure {
                setError(it.toCameraError(CameraError.PreviewInitializationFailed))
            }
    }

    // カメラ関連エラーを UI のエラー表示状態へ集約して反映する。
    private fun setError(error: CameraError) {
        _uiState.update { currentState ->
            currentState.copy(
                previewState = PreviewState.Error(error),
                errorState = error,
                message = error.toCameraMessage(),
            )
        }
    }
}

// リポジトリ由来の例外から共通のカメラエラー種別を取り出す。
private fun Throwable?.toCameraError(fallback: CameraError): CameraError =
    (this as? CameraRepositoryException)?.error ?: fallback

// 顔トラッキングの生データを画面表示用ラベル付き状態へ変換する。
private fun NormalizedFaceFrame.toDisplayState(): FaceTrackingDisplayState =
    FaceTrackingDisplayState(
        headYawLabel = "${headYawDegrees.roundToInt()} deg",
        headPitchLabel = "${headPitchDegrees.roundToInt()} deg",
        headRollLabel = "${headRollDegrees.roundToInt()} deg",
        leftEyeBlinkLabel = leftEyeBlink.asPercentLabel(),
        rightEyeBlinkLabel = rightEyeBlink.asPercentLabel(),
        jawOpenLabel = jawOpen.asPercentLabel(),
        mouthSmileLabel = mouthSmile.asPercentLabel(),
    )

// 0 から 1 の値をパーセント表記へ整形する。
private fun Float.asPercentLabel(): String = "${(coerceIn(0f, 1f) * 100).roundToInt()}%"
