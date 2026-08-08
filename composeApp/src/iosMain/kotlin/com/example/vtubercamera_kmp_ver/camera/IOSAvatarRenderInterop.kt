package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.camera.avatar.DEFAULT_AVATAR_SCALE
import com.example.vtubercamera_kmp_ver.camera.avatar.MAX_AVATAR_SCALE
import com.example.vtubercamera_kmp_ver.camera.avatar.MIN_AVATAR_SCALE
import platform.Foundation.NSLog
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.create

/**
 * Publishes selected avatar assets and render-state updates so the iOS host renderer can stay in
 * sync with the shared Compose camera state without introducing a reverse dependency on iosApp.
 */
internal object IOSAvatarRenderInterop {
    const val avatarSelectionDidChangeNotification =
        "com.example.vtubercamera_kmp_ver.avatar.selectionDidChange"
    const val avatarSelectionDidClearNotification =
        "com.example.vtubercamera_kmp_ver.avatar.selectionDidClear"
    const val avatarRenderStateDidChangeNotification =
        "com.example.vtubercamera_kmp_ver.avatar.renderStateDidChange"

    const val assetIdKey = "assetId"
    const val contentHashKey = "contentHash"
    const val fileNameKey = "fileName"
    const val assetBytesKey = "assetBytes"
    const val headYawDegreesKey = "headYawDegrees"
    const val headPitchDegreesKey = "headPitchDegrees"
    const val headRollDegreesKey = "headRollDegrees"
    const val leftEyeBlinkKey = "leftEyeBlink"
    const val rightEyeBlinkKey = "rightEyeBlink"
    const val jawOpenKey = "jawOpen"
    const val mouthSmileKey = "mouthSmile"
    const val avatarScaleKey = "avatarScale"

    // Publishes the currently selected avatar asset once so the native renderer can load it.
    fun publishSelectedAvatar(avatarSelection: AvatarSelectionData): Boolean {
        val assetBytes = AvatarAssetStore.load(avatarSelection.assetHandle) ?: run {
            NSLog(
                "Failed to load avatar bytes for assetId=${avatarSelection.assetHandle.assetId} " +
                    "contentHash=${avatarSelection.assetHandle.contentHash}",
            )
            return false
        }
        NSNotificationCenter.defaultCenter.postNotificationName(
            avatarSelectionDidChangeNotification,
            null,
            mapOf(
                assetIdKey to avatarSelection.assetHandle.assetId,
                contentHashKey to avatarSelection.assetHandle.contentHash,
                fileNameKey to avatarSelection.preview.fileName,
                assetBytesKey to assetBytes.toNSData(),
            ),
        )
        return true
    }

    // Publishes render-state updates independently so tracking changes do not resend the full asset.
    // The pinch-driven avatar scale rides along here because the native renderer applies it to the
    // same frame as the tracking values.
    fun publishRenderState(avatarRenderState: AvatarRenderState, avatarScale: Float) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            avatarRenderStateDidChangeNotification,
            null,
            mapOf(
                avatarScaleKey to avatarScale.sanitizedAvatarScale(),
                headYawDegreesKey to avatarRenderState.rig.headYawDegrees,
                headPitchDegreesKey to avatarRenderState.rig.headPitchDegrees,
                headRollDegreesKey to avatarRenderState.rig.headRollDegrees,
                leftEyeBlinkKey to avatarRenderState.expressions.leftEyeBlink,
                rightEyeBlinkKey to avatarRenderState.expressions.rightEyeBlink,
                jawOpenKey to avatarRenderState.expressions.jawOpen,
                mouthSmileKey to avatarRenderState.expressions.mouthSmile,
            ),
        )
    }

    // Clears the native renderer when the current avatar selection leaves composition.
    fun publishClearedAvatar() {
        NSNotificationCenter.defaultCenter.postNotificationName(avatarSelectionDidClearNotification, null, null)
    }

    // Keeps NaN or out-of-range scales from reaching the native renderer, which would otherwise
    // receive an unusable transform for the avatar.
    private fun Float.sanitizedAvatarScale(): Float = when {
        isNaN() -> DEFAULT_AVATAR_SCALE
        else -> coerceIn(MIN_AVATAR_SCALE, MAX_AVATAR_SCALE)
    }

    // Copies avatar bytes into NSData so the host app can consume them via NotificationCenter.
    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) {
            return NSData.create(bytes = null, length = 0u)
        }

        // Pin the ByteArray while Foundation copies from its backing memory into NSData. This
        // copy is synchronous and thread-safe for the provided ByteArray contents, but it still
        // assumes the selected asset fits in memory. Asset-store misses are converted into the
        // existing render-load callback, while native allocation failures from NSData.create are
        // allowed to surface immediately instead of being swallowed here.
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
}
