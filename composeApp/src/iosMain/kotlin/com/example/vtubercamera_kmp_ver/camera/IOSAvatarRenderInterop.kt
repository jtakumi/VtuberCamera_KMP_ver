package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import platform.Foundation.NSLog
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
    fun publishRenderState(avatarRenderState: AvatarRenderState) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            avatarRenderStateDidChangeNotification,
            null,
            mapOf(
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

    // Copies avatar bytes into NSData so the host app can consume them via NotificationCenter.
    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) {
            return NSData.create()
        }

        // Pin the ByteArray while Foundation copies from its backing memory into NSData. This
        // copy is synchronous and thread-safe for the provided ByteArray contents, but it still
        // assumes the selected asset fits in memory, and an allocation failure will surface back
        // to the caller as a native exception instead of being swallowed here.
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
}
