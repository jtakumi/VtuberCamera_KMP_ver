package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmExpressionDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor
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
    const val specVersionKey = "specVersion"
    const val headNodeIndexKey = "headNodeIndex"
    const val expressionsKey = "expressions"
    const val expressionRuntimeNameKey = "runtimeName"
    const val expressionMorphTargetBindsKey = "morphTargetBinds"
    const val morphBindNodeIndexKey = "nodeIndex"
    const val morphBindMorphTargetIndexKey = "morphTargetIndex"
    const val morphBindWeightKey = "weight"
    const val specVersionVrm0Value = "vrm0"
    const val specVersionVrm1Value = "vrm1"
    const val headYawDegreesKey = "headYawDegrees"
    const val headPitchDegreesKey = "headPitchDegrees"
    const val headRollDegreesKey = "headRollDegrees"
    const val leftEyeBlinkKey = "leftEyeBlink"
    const val rightEyeBlinkKey = "rightEyeBlink"
    const val jawOpenKey = "jawOpen"
    const val mouthSmileKey = "mouthSmile"
    const val isTrackingKey = "isTracking"
    const val trackingConfidenceKey = "trackingConfidence"

    // Publishes the currently selected avatar asset once so the native renderer can load it.
    fun publishSelectedAvatar(avatarSelection: AvatarSelectionData): Boolean {
        val assetBytes = AvatarAssetStore.load(avatarSelection.assetHandle) ?: run {
            NSLog(
                "Failed to load avatar bytes for assetId=${avatarSelection.assetHandle.assetId} " +
                    "contentHash=${avatarSelection.assetHandle.contentHash}",
            )
            return false
        }
        val runtimeDescriptor = avatarSelection.runtimeDescriptor
        val userInfo = buildMap {
            put(assetIdKey, avatarSelection.assetHandle.assetId)
            put(contentHashKey, avatarSelection.assetHandle.contentHash)
            put(fileNameKey, avatarSelection.preview.fileName)
            put(assetBytesKey, assetBytes.toNSData())
            put(specVersionKey, runtimeDescriptor.specVersion.toNotificationValue())
            put(expressionsKey, runtimeDescriptor.expressions.toNotificationValue())
            runtimeDescriptor.headNodeIndexOrNull()?.let { headNodeIndex ->
                put(headNodeIndexKey, headNodeIndex)
            }
        }
        NSNotificationCenter.defaultCenter.postNotificationName(
            avatarSelectionDidChangeNotification,
            null,
            userInfo,
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
                isTrackingKey to avatarRenderState.isTracking,
                trackingConfidenceKey to avatarRenderState.trackingConfidence,
            ),
        )
    }

    // Clears the native renderer when the current avatar selection leaves composition.
    fun publishClearedAvatar() {
        NSNotificationCenter.defaultCenter.postNotificationName(avatarSelectionDidClearNotification, null, null)
    }

    // Converts the parsed spec version into the string form shared with the Swift-side decoder.
    private fun VrmSpecVersion.toNotificationValue(): String = when (this) {
        VrmSpecVersion.Vrm0 -> specVersionVrm0Value
        VrmSpecVersion.Vrm1 -> specVersionVrm1Value
    }

    // Flattens expression descriptors into Foundation-bridgeable maps so the host renderer can
    // resolve morph bindings without re-parsing the GLB on the Swift side.
    private fun List<VrmExpressionDescriptor>.toNotificationValue(): List<Map<String, Any>> {
        return map { expression ->
            mapOf(
                expressionRuntimeNameKey to expression.runtimeName,
                expressionMorphTargetBindsKey to expression.morphTargetBinds.map { bind ->
                    mapOf(
                        morphBindNodeIndexKey to bind.nodeIndex,
                        morphBindMorphTargetIndexKey to bind.morphTargetIndex,
                        morphBindWeightKey to bind.weight,
                    )
                },
            )
        }
    }

    // Returns the glTF node index of the humanoid head bone, or null when the asset lacks one.
    private fun VrmRuntimeAssetDescriptor.headNodeIndexOrNull(): Int? {
        return humanoidBones.firstOrNull { bone -> bone.boneName == HEAD_BONE_NAME }?.nodeIndex
    }

    private const val HEAD_BONE_NAME = "head"

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
