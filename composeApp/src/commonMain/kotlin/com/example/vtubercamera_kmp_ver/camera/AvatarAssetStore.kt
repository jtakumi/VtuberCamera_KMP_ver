package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Immutable

// renderer が利用する avatar bytes への軽量ハンドルを保持する。
@Immutable
data class AvatarAssetHandle(
    val assetId: Long,
    val contentHash: Int,
)

// 選択済み avatar の raw bytes を UI state の外で保持する platform store。
expect object AvatarAssetStore {
    fun store(bytes: ByteArray): AvatarAssetHandle

    fun load(assetHandle: AvatarAssetHandle): ByteArray?

    fun remove(assetHandle: AvatarAssetHandle)
}
