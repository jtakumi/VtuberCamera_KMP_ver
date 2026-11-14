package com.example.vtubercamera_kmp_ver.camera.avatar

// ドラッグ操作によるアバター表示位置を保持し、renderer へ渡す位置を可動域内に収める。
class AvatarPositionController {
    var state: AvatarPositionUiState = AvatarPositionUiState()
        private set

    // ドラッグの移動量(画面の幅・高さに対する割合)を現在位置へ加算し、可動域を超えないよう clamp する。
    // 非数や無限大の移動量は不正入力として無視し、位置が壊れないようにする。
    fun onAvatarDragged(deltaX: Float, deltaY: Float) {
        if (!deltaX.isFiniteValue() || !deltaY.isFiniteValue()) {
            return
        }
        val max = state.maxOffset
        state = state.copy(
            offset = AvatarOffset(
                x = (state.offset.x + deltaX).coerceIn(-max, max),
                y = (state.offset.y + deltaY).coerceIn(-max, max),
            ),
        )
    }

    private fun Float.isFiniteValue(): Boolean = !isNaN() && !isInfinite()
}
