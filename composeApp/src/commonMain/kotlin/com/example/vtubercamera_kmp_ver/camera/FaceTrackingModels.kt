package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Immutable

// Platform tracker output normalized into the shared avatar-mapping contract.
@Immutable
data class NormalizedFaceFrame(
    val timestampMillis: Long,
    val trackingConfidence: Float,
    val headYawDegrees: Float,
    val headPitchDegrees: Float,
    val headRollDegrees: Float,
    val leftEyeBlink: Float,
    val rightEyeBlink: Float,
    val jawOpen: Float,
    val mouthSmile: Float,
)

@Immutable
data class FaceTrackingDisplayState(
    val headYawLabel: String,
    val headPitchLabel: String,
    val headRollLabel: String,
    val leftEyeBlinkLabel: String,
    val rightEyeBlinkLabel: String,
    val jawOpenLabel: String,
    val mouthSmileLabel: String,
)

@Immutable
data class FaceTrackingUiState(
    val isTracking: Boolean = false,
    val frame: NormalizedFaceFrame? = null,
    val display: FaceTrackingDisplayState? = null,
)
