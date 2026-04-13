package com.example.vtubercamera_kmp_ver.avatar.vrm

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion

// プレビュー表示向けに必要な VRM メタ情報を保持する。
data class VrmPreviewAssetDescriptor(
    val specVersion: VrmSpecVersion,
    val rawSpecVersion: String?,
    val assetVersion: String?,
    val meta: VrmRuntimeMeta,
    val thumbnailImageIndex: Int?,
)

// VRM アセットから抽出した実行時利用向けの統合情報を保持する。
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
    // 利用可能な表情名を重複なしで列挙する。
    val availableExpressionNames: Set<String>
        get() = expressions.mapTo(linkedSetOf()) { expression -> expression.runtimeName }
}

// ランタイムで参照する VRM メタ情報を保持する。
data class VrmRuntimeMeta(
    val avatarName: String?,
    val authors: List<String> = emptyList(),
    val version: String?,
)

// ヒューマノイドボーン名とノード番号の対応を表す。
data class VrmHumanoidBoneBinding(
    val boneName: String,
    val nodeIndex: Int,
)

// 表情プリセットとモーフ設定をまとめた実行時用定義を表す。
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

// 特定ノードのモーフターゲットに対する重み付けを表す。
data class VrmMorphTargetBind(
    val nodeIndex: Int,
    val morphTargetIndex: Int,
    val weight: Float,
)

// 視線追従に必要な LookAt 設定を保持する。
data class VrmLookAtDescriptor(
    val type: String?,
    val offsetFromHeadBone: VrmFloat3? = null,
)

// 一人称表示で参照するボーンとメッシュ注釈を保持する。
data class VrmFirstPersonDescriptor(
    val firstPersonBone: Int? = null,
    val meshAnnotationIndices: List<Int> = emptyList(),
)

// ランタイムで使う 3 次元ベクトルを表す。
data class VrmFloat3(
    val x: Float,
    val y: Float,
    val z: Float,
)
