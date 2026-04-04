package com.example.vtubercamera_kmp_ver.avatar.vrm

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion

data class VrmRuntimeAssetDescriptor(
    val specVersion: VrmSpecVersion,
    val rawSpecVersion: String?,
    val assetVersion: String?,
    val meta: VrmRuntimeMeta,
    val thumbnailImageIndex: Int?,
    val humanoidBones: List<VrmHumanoidBoneBinding> = emptyList(),
    val expressions: List<VrmExpressionDescriptor> = emptyList(),
    val lookAt: VrmLookAtDescriptor? = null,
    val firstPerson: VrmFirstPersonDescriptor? = null,
) {
    val availableExpressionNames: Set<String>
        get() = expressions.mapTo(linkedSetOf()) { expression -> expression.runtimeName }
}

data class VrmRuntimeMeta(
    val avatarName: String?,
    val authors: List<String> = emptyList(),
    val version: String?,
)

data class VrmHumanoidBoneBinding(
    val boneName: String,
    val nodeIndex: Int,
)

data class VrmExpressionDescriptor(
    val runtimeName: String,
    val displayName: String,
    val presetName: String?,
    val isBinary: Boolean,
    val morphTargetBinds: List<VrmMorphTargetBind> = emptyList(),
    val overrideBlink: String? = null,
    val overrideLookAt: String? = null,
    val overrideMouth: String? = null,
)

data class VrmMorphTargetBind(
    val nodeIndex: Int,
    val morphTargetIndex: Int,
    val weight: Float,
)

data class VrmLookAtDescriptor(
    val type: String?,
    val offsetFromHeadBone: VrmFloat3? = null,
)

data class VrmFirstPersonDescriptor(
    val firstPersonBone: Int? = null,
    val meshAnnotationIndices: List<Int> = emptyList(),
)

data class VrmFloat3(
    val x: Float,
    val y: Float,
    val z: Float,
)