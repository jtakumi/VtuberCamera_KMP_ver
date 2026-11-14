package com.example.vtubercamera_kmp_ver.camera.avatar

// アバターの表示位置を、既定位置からのずれとして画面の幅・高さに対する割合で表す。
// x は右向き、y は下向きが正で、(0, 0) が既定位置。画面サイズに依存しないため、
// Compose と各 platform の renderer が同じ値をそのまま解釈できる。
data class AvatarOffset(
    val x: Float = 0f,
    val y: Float = 0f,
) {
    companion object {
        val Zero = AvatarOffset()
    }
}

// ドラッグ操作で決まったアバターの表示位置を保持する。
data class AvatarPositionUiState(
    val offset: AvatarOffset = AvatarOffset.Zero,
    val maxOffset: Float = MAX_AVATAR_OFFSET,
)

// 既定位置から画面半分まで動かせる。これを超えるとアバターの中心が画面外へ出て見失いやすくなる。
const val MAX_AVATAR_OFFSET = 0.5f
