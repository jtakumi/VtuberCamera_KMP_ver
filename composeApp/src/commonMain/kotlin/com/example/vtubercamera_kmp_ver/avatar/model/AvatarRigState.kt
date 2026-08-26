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

/**
 * Converts camera-space tracking directions into mirrored avatar-rendering directions.
 * VRM 0.x and VRM 1.0 are normalized at asset load time, so this correction is shared by both.
 */
fun AvatarRigState.mirroredForAvatarRenderer(): AvatarRigState = copy(
    headYawDegrees = -headYawDegrees,
    headPitchDegrees = -headPitchDegrees,
    headRollDegrees = -headRollDegrees,
    bodySwayDegrees = -bodySwayDegrees,
    bodyLeanDegrees = -bodyLeanDegrees,
)
