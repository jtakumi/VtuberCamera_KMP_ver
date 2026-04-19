package com.example.vtubercamera_kmp_ver.camera

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

actual object AvatarAssetStore {
    private val nextAssetId = AtomicLong(0L)
    private val assets = ConcurrentHashMap<Long, ByteArray>()

    actual fun store(bytes: ByteArray): AvatarAssetHandle {
        val assetId = nextAssetId.getAndIncrement()
        assets[assetId] = bytes
        return AvatarAssetHandle(
            assetId = assetId,
            contentHash = bytes.contentHashCode(),
        )
    }

    actual fun load(assetHandle: AvatarAssetHandle): ByteArray? = assets[assetHandle.assetId]

    actual fun remove(assetHandle: AvatarAssetHandle) {
        assets.remove(assetHandle.assetId)
    }
}
