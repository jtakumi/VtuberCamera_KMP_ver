package com.example.vtubercamera_kmp_ver.camera.avatar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ピンチ操作によるアバター表示倍率を保持し、renderer へ渡す倍率を可変域内に収める。
class AvatarScaleController {
    private val _state = MutableStateFlow(AvatarScaleUiState())
    val state: StateFlow<AvatarScaleUiState> = _state.asStateFlow()

    // ピンチ操作の相対倍率を現在値へ乗算し、可変域を超えないよう clamp する。
    // 0 以下や非数の倍率変化は不正入力として無視し、倍率が壊れないようにする。
    fun onAvatarScaleChanged(scaleChange: Float) {
        if (!scaleChange.isValidScaleChange()) {
            return
        }
        _state.update { scaleState ->
            scaleState.copy(
                currentAvatarScale = (scaleState.currentAvatarScale * scaleChange).coerceIn(
                    scaleState.minAvatarScale,
                    scaleState.maxAvatarScale,
                ),
            )
        }
    }

    private fun Float.isValidScaleChange(): Boolean = this > 0f && !isNaN() && !isInfinite()
}
