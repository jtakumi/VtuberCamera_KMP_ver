package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmExpressionDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmHumanoidBoneBinding
import com.example.vtubercamera_kmp_ver.camera.avatar.DEFAULT_AVATAR_SCALE
import com.example.vtubercamera_kmp_ver.camera.avatar.MAX_AVATAR_SCALE
import com.example.vtubercamera_kmp_ver.camera.avatar.MIN_AVATAR_SCALE
import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundMode
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
    const val avatarBackgroundDidChangeNotification =
        "com.example.vtubercamera_kmp_ver.avatar.backgroundDidChange"

    const val assetIdKey = "assetId"
    const val contentHashKey = "contentHash"
    const val fileNameKey = "fileName"
    const val assetBytesKey = "assetBytes"
    const val specVersionKey = "specVersion"
    const val nodeNamesKey = "nodeNames"
    const val humanoidBonesKey = "humanoidBones"
    const val expressionsKey = "expressions"
    const val boneNameKey = "boneName"
    const val nodeNameKey = "nodeName"
    const val runtimeNameKey = "runtimeName"
    const val morphTargetBindsKey = "morphTargetBinds"
    const val nodeIndexKey = "nodeIndex"
    const val morphTargetIndexKey = "morphTargetIndex"
    const val weightKey = "weight"
    const val headYawDegreesKey = "headYawDegrees"
    const val headPitchDegreesKey = "headPitchDegrees"
    const val headRollDegreesKey = "headRollDegrees"
    const val bodySwayDegreesKey = "bodySwayDegrees"
    const val bodyLeanDegreesKey = "bodyLeanDegrees"
    const val leftEyeBlinkKey = "leftEyeBlink"
    const val rightEyeBlinkKey = "rightEyeBlink"
    const val jawOpenKey = "jawOpen"
    const val mouthSmileKey = "mouthSmile"
    const val avatarScaleKey = "avatarScale"
    const val trackingConfidenceKey = "trackingConfidence"
    const val isTrackingKey = "isTracking"
    const val backgroundModeKey = "backgroundMode"

    const val specVersionVrm0 = "vrm0"
    const val specVersionVrm1 = "vrm1"

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
        val nodeNames = runtimeDescriptor.nodeNames.map { nodeName -> nodeName.orEmpty() }
        NSNotificationCenter.defaultCenter.postNotificationName(
            avatarSelectionDidChangeNotification,
            null,
            mapOf(
                assetIdKey to avatarSelection.assetHandle.assetId,
                contentHashKey to avatarSelection.assetHandle.contentHash,
                fileNameKey to avatarSelection.preview.fileName,
                assetBytesKey to assetBytes.toNSData(),
                specVersionKey to runtimeDescriptor.specVersion.interopName(),
                nodeNamesKey to nodeNames,
                humanoidBonesKey to runtimeDescriptor.humanoidBones.toInteropHumanoidBones(nodeNames),
                expressionsKey to runtimeDescriptor.expressions.toInteropExpressions(),
            ),
        )
        return true
    }

    // gltfio exposes the imported hierarchy by node name, so humanoid bones cross the bridge
    // already resolved to a name. Bones whose node has no usable name are dropped because the
    // native renderer could not bind them anyway.
    private fun List<VrmHumanoidBoneBinding>.toInteropHumanoidBones(
        nodeNames: List<String>,
    ): List<Map<String, Any>> = mapNotNull { bone ->
        val nodeName = nodeNames.getOrNull(bone.nodeIndex).orEmpty()
        if (nodeName.isEmpty()) {
            return@mapNotNull null
        }
        mapOf(
            boneNameKey to bone.boneName,
            nodeNameKey to nodeName,
        )
    }

    // Morph binds stay keyed by node index here; the host app resolves them to Filament
    // entities once the asset is loaded.
    private fun List<VrmExpressionDescriptor>.toInteropExpressions(): List<Map<String, Any>> =
        map { expression ->
            mapOf(
                runtimeNameKey to expression.runtimeName,
                morphTargetBindsKey to expression.morphTargetBinds.map { bind ->
                    mapOf(
                        nodeIndexKey to bind.nodeIndex,
                        morphTargetIndexKey to bind.morphTargetIndex,
                        weightKey to bind.weight,
                    )
                },
            )
        }

    private fun VrmSpecVersion.interopName(): String = when (this) {
        VrmSpecVersion.Vrm0 -> specVersionVrm0
        VrmSpecVersion.Vrm1 -> specVersionVrm1
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
                bodySwayDegreesKey to avatarRenderState.rig.bodySwayDegrees,
                bodyLeanDegreesKey to avatarRenderState.rig.bodyLeanDegrees,
                leftEyeBlinkKey to avatarRenderState.expressions.leftEyeBlink,
                rightEyeBlinkKey to avatarRenderState.expressions.rightEyeBlink,
                jawOpenKey to avatarRenderState.expressions.jawOpen,
                mouthSmileKey to avatarRenderState.expressions.mouthSmile,
                trackingConfidenceKey to avatarRenderState.trackingConfidence,
                isTrackingKey to avatarRenderState.isTracking,
            ),
        )
    }

    // UIKit interop views draw over sibling Compose content on iOS, so the native renderer must
    // clear to the selected solid background instead of relying on the Compose layer behind it.
    fun publishBackgroundMode(backgroundMode: CameraBackgroundMode) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            avatarBackgroundDidChangeNotification,
            null,
            mapOf(backgroundModeKey to backgroundMode.name),
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
