package com.example.vtubercamera_kmp_ver.avatar.model

import androidx.compose.runtime.Immutable

@Immutable
data class AvatarRigState(
    val headYawDegrees: Float = 0f,
    val headPitchDegrees: Float = 0f,
    val headRollDegrees: Float = 0f,
    val bodySwayDegrees: Float = 0f,
    val bodyLeanDegrees: Float = 0f,
)
