package com.example.vtubercamera_kmp_ver.avatar.model

import androidx.compose.runtime.Immutable
import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion

@Immutable
data class AvatarRigState(
    val headYawDegrees: Float = 0f,
    val headPitchDegrees: Float = 0f,
    val headRollDegrees: Float = 0f,
    val bodySwayDegrees: Float = 0f,
    val bodyLeanDegrees: Float = 0f,
)

/**
 * Converts camera-space tracking directions into the coordinate system of the rendered avatar.
 *
 * Both specifications need mirrored roll. VRM 0.x uses the opposite vertical axis, whereas
 * VRM 1.0 uses the opposite horizontal axis. Apply the same correction to the corresponding
 * body follow-through so head and torso do not move in conflicting directions.
 */
fun AvatarRigState.mirroredForAvatarRenderer(specVersion: VrmSpecVersion): AvatarRigState = when (specVersion) {
    VrmSpecVersion.Vrm0 -> copy(
        headYawDegrees = -headYawDegrees,
        headPitchDegrees = headPitchDegrees,
        headRollDegrees = -headRollDegrees,
        bodySwayDegrees = -bodySwayDegrees,
        bodyLeanDegrees = bodyLeanDegrees,
    )
    VrmSpecVersion.Vrm1 -> copy(
        headYawDegrees = headYawDegrees,
        headPitchDegrees = -headPitchDegrees,
        headRollDegrees = -headRollDegrees,
        bodySwayDegrees = bodySwayDegrees,
        bodyLeanDegrees = -bodyLeanDegrees,
    )
}
