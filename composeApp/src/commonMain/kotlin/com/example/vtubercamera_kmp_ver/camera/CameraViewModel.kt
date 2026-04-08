package com.example.vtubercamera_kmp_ver.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                startCameraPreview()
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
                        messageRes = Res.string.camera_error_retrying_preview,
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
        val previousPermissionState = _uiState.value.permissionState
        _uiState.update { currentState ->
            when (permissionState) {
                PermissionState.Denied -> currentState.copy(
                    permissionState = permissionState,
                    errorState = CameraError.PermissionDenied,
                    previewState = PreviewState.Error(CameraError.PermissionDenied),
                    message = CameraMessage(
                        type = CameraMessageType.Error,
<<<<<<< HEAD
                        messageRes = Res.string.camera_error_permission_denied,
                    )
                } else {
                    null
                },
            )
=======
                        text = "Camera permission is denied",
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
>>>>>>> 1fe92214730d9f7ffd73e80dcc3af3a7082ce5c8
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
                    errorState = error,
                    message = error?.toCameraMessage(),
                )
            }
        }
    }

    private suspend fun startCameraPreview() {
        val initialLensResult = cameraRepository.resolveInitialLens(_uiState.value.lensFacing)
        if (initialLensResult.isFailure) {
            setError(CameraError.CameraUnavailable)
            return
        }
        val initialLens = initialLensResult.getOrDefault(_uiState.value.lensFacing)
        _uiState.update { it.copy(lensFacing = initialLens) }
        val startResult = cameraRepository.startPreview(initialLens)
        if (startResult.isFailure) {
            setError(CameraError.PreviewInitializationFailed)
        }
    }

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
