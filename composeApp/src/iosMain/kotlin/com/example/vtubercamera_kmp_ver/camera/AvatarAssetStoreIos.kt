package com.example.vtubercamera_kmp_ver.camera

import platform.Foundation.NSLock

actual object AvatarAssetStore {
    private val lock = NSLock()
    private var nextAssetId: Long = 0L
    private val assets = mutableMapOf<Long, ByteArray>()

    actual fun store(bytes: ByteArray): AvatarAssetHandle {
        lock.lock()
        return try {
            val assetId = nextAssetId
            nextAssetId += 1
            assets[assetId] = bytes
            AvatarAssetHandle(
                assetId = assetId,
                contentHash = bytes.contentHashCode(),
            )
        } finally {
            lock.unlock()
        }
    }

    actual fun load(assetHandle: AvatarAssetHandle): ByteArray? {
        lock.lock()
        return try {
            assets[assetHandle.assetId]
        } finally {
            lock.unlock()
        }
    }

    actual fun remove(assetHandle: AvatarAssetHandle) {
        lock.lock()
        try {
            assets.remove(assetHandle.assetId)
        } finally {
            lock.unlock()
        }
    }
}
