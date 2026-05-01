package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.avatar.mapping.AvatarExpressionId
import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmExpressionMap
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
    const val runtimeSpecVersionKey = "runtimeSpecVersion"
    const val headBoneNodeIndexKey = "headBoneNodeIndex"
    const val expressionBindingsKey = "expressionBindings"
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
                runtimeSpecVersionKey to avatarSelection.runtimeDescriptor.specVersion.name,
                headBoneNodeIndexKey to avatarSelection.runtimeDescriptor.headBoneNodeIndex(),
                expressionBindingsKey to avatarSelection.runtimeDescriptor.toExpressionBindingPayloads(),
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

    private fun VrmRuntimeAssetDescriptor.headBoneNodeIndex(): Int? {
        return humanoidBones.firstOrNull { bone -> bone.boneName == HEAD_BONE_NAME }?.nodeIndex
    }

    private fun VrmRuntimeAssetDescriptor.toExpressionBindingPayloads(): List<Map<String, Any>> {
        val availableNames = availableExpressionNames
        return listOfNotNull(
            toExpressionBindingPayload(
                channel = EXPRESSION_CHANNEL_BLINK_LEFT,
                expressionId = AvatarExpressionId.BlinkLeft,
                availableNames = availableNames,
            ),
            toExpressionBindingPayload(
                channel = EXPRESSION_CHANNEL_BLINK_RIGHT,
                expressionId = AvatarExpressionId.BlinkRight,
                availableNames = availableNames,
            ),
            toExpressionBindingPayload(
                channel = EXPRESSION_CHANNEL_JAW_OPEN,
                expressionId = AvatarExpressionId.JawOpen,
                availableNames = availableNames,
            ),
            toExpressionBindingPayload(
                channel = EXPRESSION_CHANNEL_SMILE,
                expressionId = AvatarExpressionId.Smile,
                availableNames = availableNames,
            ),
        )
    }

    private fun VrmRuntimeAssetDescriptor.toExpressionBindingPayload(
        channel: String,
        expressionId: AvatarExpressionId,
        availableNames: Set<String>,
    ): Map<String, Any>? {
        val runtimeName = VrmExpressionMap.resolve(
            expression = expressionId,
            specVersion = specVersion,
            availableNames = availableNames,
        ) ?: return null
        val descriptor = expressions.firstOrNull { expression ->
            expression.runtimeName == runtimeName
        } ?: return null
        return descriptor.toExpressionBindingPayload(channel)
    }

    private fun VrmExpressionDescriptor.toExpressionBindingPayload(channel: String): Map<String, Any> {
        return mapOf(
            EXPRESSION_CHANNEL_KEY to channel,
            EXPRESSION_RUNTIME_NAME_KEY to runtimeName,
            EXPRESSION_MORPH_BINDS_KEY to morphTargetBinds.map { bind ->
                mapOf(
                    MORPH_BIND_NODE_INDEX_KEY to bind.nodeIndex,
                    MORPH_BIND_MORPH_TARGET_INDEX_KEY to bind.morphTargetIndex,
                    MORPH_BIND_WEIGHT_KEY to bind.weight,
                )
            },
        )
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

    private const val HEAD_BONE_NAME = "head"
    private const val EXPRESSION_CHANNEL_KEY = "channel"
    private const val EXPRESSION_RUNTIME_NAME_KEY = "runtimeName"
    private const val EXPRESSION_MORPH_BINDS_KEY = "morphBinds"
    private const val EXPRESSION_CHANNEL_BLINK_LEFT = "blinkLeft"
    private const val EXPRESSION_CHANNEL_BLINK_RIGHT = "blinkRight"
    private const val EXPRESSION_CHANNEL_JAW_OPEN = "jawOpen"
    private const val EXPRESSION_CHANNEL_SMILE = "smile"
    private const val MORPH_BIND_NODE_INDEX_KEY = "nodeIndex"
    private const val MORPH_BIND_MORPH_TARGET_INDEX_KEY = "morphTargetIndex"
    private const val MORPH_BIND_WEIGHT_KEY = "weight"
}
