package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Immutable

// renderer が利用する avatar bytes への軽量ハンドルを保持する。
@Immutable
data class AvatarAssetHandle(
    val assetId: Long,
    val contentHash: Int,
)

// 選択済み avatar の raw bytes を UI state の外で保持する簡易ストア。
object AvatarAssetStore {
    private var nextAssetId: Long = 0L
    private val assets = mutableMapOf<Long, ByteArray>()

    fun store(bytes: ByteArray): AvatarAssetHandle {
        val assetId = nextAssetId++
        assets[assetId] = bytes
        return AvatarAssetHandle(
            assetId = assetId,
            contentHash = bytes.contentHashCode(),
        )
    }

    fun load(assetHandle: AvatarAssetHandle): ByteArray? = assets[assetHandle.assetId]

    fun remove(assetHandle: AvatarAssetHandle) {
        assets.remove(assetHandle.assetId)
    }
}
