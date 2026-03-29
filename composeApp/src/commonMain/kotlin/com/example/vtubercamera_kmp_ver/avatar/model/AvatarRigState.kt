package com.example.vtubercamera_kmp_ver.avatar.model

import androidx.compose.runtime.Immutable

@Immutable
data class AvatarRigState(
    val headYawDegrees: Float = 0f,
    val headPitchDegrees: Float = 0f,
    val headRollDegrees: Float = 0f,
)
