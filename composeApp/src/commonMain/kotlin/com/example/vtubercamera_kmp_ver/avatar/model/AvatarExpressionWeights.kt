package com.example.vtubercamera_kmp_ver.avatar.model

import androidx.compose.runtime.Immutable

@Immutable
data class AvatarExpressionWeights(
    val leftEyeBlink: Float = 0f,
    val rightEyeBlink: Float = 0f,
    val jawOpen: Float = 0f,
    val mouthSmile: Float = 0f,
)
