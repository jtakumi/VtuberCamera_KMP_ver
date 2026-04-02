package com.example.vtubercamera_kmp_ver.camera

import CameraUiState
import androidx.lifecycle.ViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CameraViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun onLensFacingChanged(lensFacing: CameraLensFacing) {
        _uiState.update { currentState ->
            currentState.copy(lensFacing = lensFacing)
        }
    }

    fun onPermissionStateChanged(
        isGranted: Boolean,
        isChecking: Boolean,
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                isPermissionGranted = isGranted,
                isPermissionChecking = isChecking,
            )
        }
    }

    fun onToggleLensFacing() {
        _uiState.update { currentState ->
            currentState.copy(lensFacing = currentState.lensFacing.toggled())
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
