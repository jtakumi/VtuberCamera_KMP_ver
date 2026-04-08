package com.example.vtubercamera_kmp_ver.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(
    private val cameraRepository: CameraRepository,
    private val permissionRepository: PermissionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePreviewState()
        }
    }

    fun initialize() {
        viewModelScope.launch {
            val permissionState = permissionRepository.checkCameraPermission()
            _uiState.update { it.copy(permissionState = permissionState) }

            if (permissionState == PermissionState.Granted) {
                val initialLensResult = cameraRepository.resolveInitialLens(_uiState.value.lensFacing)
                if (initialLensResult.isFailure) {
                    setError(CameraError.CameraUnavailable)
                    return@launch
                }

                val initialLens = initialLensResult.getOrDefault(_uiState.value.lensFacing)
                _uiState.update { it.copy(lensFacing = initialLens) }
                val startResult = cameraRepository.startPreview(initialLens)
                if (startResult.isFailure) {
                    setError(CameraError.PreviewInitializationFailed)
                }
            } else if (permissionState == PermissionState.Denied) {
                setError(CameraError.PermissionDenied)
            }
        }
    }

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

    fun onRetryPreview() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewState = PreviewState.Preparing,
                    errorState = null,
                    message = CameraMessage(
                        type = CameraMessageType.Guide,
                        text = "Retrying camera preview...",
                    ),
                )
            }
            val result = cameraRepository.startPreview(_uiState.value.lensFacing)
            if (result.isFailure) {
                setError(CameraError.PreviewInitializationFailed)
            } else {
                _uiState.update { it.copy(message = null) }
            }
        }
    }

    fun onLensFacingChanged(lensFacing: CameraLensFacing) {
        _uiState.update { currentState ->
            currentState.copy(
                lensFacing = lensFacing,
                previewState = PreviewState.Showing,
                errorState = null,
                message = null,
            )
        }
    }

    fun onPermissionStateChanged(
        isGranted: Boolean,
        isChecking: Boolean,
    ) {
        val permissionState = when {
            isChecking -> PermissionState.Unknown
            isGranted -> PermissionState.Granted
            else -> PermissionState.Denied
        }
        _uiState.update { currentState ->
            currentState.copy(
                permissionState = permissionState,
                errorState = if (permissionState == PermissionState.Denied) {
                    CameraError.PermissionDenied
                } else {
                    null
                },
                previewState = if (permissionState == PermissionState.Denied) {
                    PreviewState.Error(CameraError.PermissionDenied)
                } else {
                    currentState.previewState
                },
                message = if (permissionState == PermissionState.Denied) {
                    CameraMessage(
                        type = CameraMessageType.Error,
                        text = "Camera permission is denied",
                    )
                } else {
                    null
                },
            )
        }
    }

    fun onToggleLensFacing() {
        viewModelScope.launch {
            val currentLens = _uiState.value.lensFacing
            _uiState.update { state ->
                state.copy(
                    previewState = PreviewState.Preparing,
                    message = CameraMessage(
                        type = CameraMessageType.Guide,
                        text = "Switching camera lens...",
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

    fun onFaceTrackingFrameChanged(frame: NormalizedFaceFrame?) {
        _uiState.update { currentState ->
            currentState.copy(
                faceTracking = FaceTrackingUiState(
                    isTracking = frame != null,
                    frame = frame,
                    display = frame?.toDisplayState(),
                ),
            )
        }
    }

    fun onFilePicked(result: FilePickerResult) {
        _uiState.update { currentState ->
            when (result) {
                is FilePickerResult.Success -> currentState.copy(
                    avatarPreview = result.avatarPreview,
                    filePickerErrorMessageRes = null,
                )
                is FilePickerResult.Error -> currentState.copy(
                    filePickerErrorMessageRes = result.messageRes,
                )
                FilePickerResult.Cancelled -> currentState
            }
        }
    }

    fun onDismissFilePickerError() {
        _uiState.update { currentState ->
            currentState.copy(filePickerErrorMessageRes = null)
        }
    }

    private suspend fun observePreviewState() {
        cameraRepository.observePreviewState().collect { previewState ->
            _uiState.update { currentState ->
                val error = (previewState as? PreviewState.Error)?.error
                currentState.copy(
                    previewState = previewState,
                    errorState = error ?: currentState.errorState,
                    message = error?.toMessage() ?: currentState.message,
                )
            }
        }
    }

    private fun setError(error: CameraError) {
        _uiState.update { currentState ->
            currentState.copy(
                previewState = PreviewState.Error(error),
                errorState = error,
                message = error.toMessage(),
            )
        }
    }
}

private fun CameraError.toMessage(): CameraMessage {
    val text = when (this) {
        CameraError.PermissionDenied -> "Camera permission is denied"
        CameraError.CameraUnavailable -> "Camera device is unavailable"
        CameraError.PreviewInitializationFailed -> "Failed to initialize camera preview"
        CameraError.LensSwitchFailed -> "Failed to switch camera lens"
        CameraError.Unknown -> "Unknown camera error"
    }
    return CameraMessage(
        type = CameraMessageType.Error,
        text = text,
    )
}

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

private fun Float.asPercentLabel(): String = "${(coerceIn(0f, 1f) * 100).roundToInt()}%"
