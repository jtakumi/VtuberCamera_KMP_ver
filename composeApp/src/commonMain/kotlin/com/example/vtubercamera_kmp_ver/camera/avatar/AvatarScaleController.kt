package com.example.vtubercamera_kmp_ver.camera.avatar

// ピンチ操作によるアバター表示倍率を保持し、renderer へ渡す倍率を可変域内に収める。
class AvatarScaleController {
    var state: AvatarScaleUiState = AvatarScaleUiState()
        private set

    // ピンチ操作の相対倍率を現在値へ乗算し、可変域を超えないよう clamp する。
    // 0 以下や非数の倍率変化は不正入力として無視し、倍率が壊れないようにする。
    fun onAvatarScaleChanged(scaleChange: Float) {
        if (!scaleChange.isValidScaleChange()) {
            return
        }
        state = state.copy(
            currentAvatarScale = (state.currentAvatarScale * scaleChange).coerceIn(
                state.minAvatarScale,
                state.maxAvatarScale,
            ),
        )
    }

    private fun Float.isValidScaleChange(): Boolean = this > 0f && !isNaN() && !isInfinite()
}
