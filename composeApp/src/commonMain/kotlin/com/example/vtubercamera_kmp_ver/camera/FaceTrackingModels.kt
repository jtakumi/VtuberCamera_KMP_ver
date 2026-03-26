package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Immutable

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
data class FaceTrackingUiState(
    val isTracking: Boolean = false,
    val frame: NormalizedFaceFrame? = null,
)