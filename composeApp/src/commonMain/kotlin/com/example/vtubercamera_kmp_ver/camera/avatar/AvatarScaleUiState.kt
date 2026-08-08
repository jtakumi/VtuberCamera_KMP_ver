package com.example.vtubercamera_kmp_ver.camera.avatar

// アバター表示倍率の現在値と可変域を保持する。
data class AvatarScaleUiState(
    val currentAvatarScale: Float = DEFAULT_AVATAR_SCALE,
    val minAvatarScale: Float = MIN_AVATAR_SCALE,
    val maxAvatarScale: Float = MAX_AVATAR_SCALE,
) {
    val canScale: Boolean
        get() = maxAvatarScale > minAvatarScale
}

const val DEFAULT_AVATAR_SCALE = 1.0f
const val MIN_AVATAR_SCALE = 0.5f
const val MAX_AVATAR_SCALE = 3.0f
