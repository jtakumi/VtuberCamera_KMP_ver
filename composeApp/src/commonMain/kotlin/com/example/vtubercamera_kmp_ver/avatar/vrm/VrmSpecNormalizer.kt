package com.example.vtubercamera_kmp_ver.avatar.vrm

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion

internal object VrmSpecNormalizer {
    // 解析済み VRM 拡張情報をプレビュー向けの共通ディスクリプタへ正規化する。
    fun normalizePreview(
        extension: ParsedVrmExtension,
        assetVersion: String?,
    ): VrmPreviewAssetDescriptor = when (extension) {
        is ParsedVrm0Extension -> VrmPreviewAssetDescriptor(
            specVersion = VrmSpecVersion.Vrm0,
            rawSpecVersion = extension.rawSpecVersion,
            assetVersion = assetVersion,
            meta = extension.meta.toRuntimeMeta(),
            thumbnailImageIndex = extension.thumbnailImageIndex,
        )

        is ParsedVrm1Extension -> VrmPreviewAssetDescriptor(
            specVersion = VrmSpecVersion.Vrm1,
            rawSpecVersion = extension.rawSpecVersion,
            assetVersion = assetVersion,
            meta = extension.meta.toRuntimeMeta(),
            thumbnailImageIndex = extension.thumbnailImageIndex,
        )
    }

    // 解析済み VRM 拡張情報を実行時利用向けの共通ディスクリプタへ正規化する。
    fun normalize(
        extension: ParsedVrmExtension,
        assetVersion: String?,
    ): VrmRuntimeAssetDescriptor = when (extension) {
        is ParsedVrm0Extension -> VrmRuntimeAssetDescriptor(
            specVersion = VrmSpecVersion.Vrm0,
            rawSpecVersion = extension.rawSpecVersion,
            assetVersion = assetVersion,
            meta = extension.meta.toRuntimeMeta(),
            thumbnailImageIndex = extension.thumbnailImageIndex,
            humanoidBones = extension.humanoidBones.map { bone -> bone.toRuntimeBinding() },
            expressions = extension.expressions.map { expression -> expression.toRuntimeExpression() },
            lookAt = extension.lookAt?.toRuntimeLookAt(),
            firstPerson = extension.firstPerson?.toRuntimeFirstPerson(),
        )

        is ParsedVrm1Extension -> VrmRuntimeAssetDescriptor(
            specVersion = VrmSpecVersion.Vrm1,
            rawSpecVersion = extension.rawSpecVersion,
            assetVersion = assetVersion,
            meta = extension.meta.toRuntimeMeta(),
            thumbnailImageIndex = extension.thumbnailImageIndex,
            humanoidBones = extension.humanoidBones.map { bone -> bone.toRuntimeBinding() },
            expressions = extension.expressions.map { expression -> expression.toRuntimeExpression() },
            lookAt = extension.lookAt?.toRuntimeLookAt(),
            firstPerson = extension.firstPerson?.toRuntimeFirstPerson(),
        )
    }

    // VRM メタ情報をランタイム用メタモデルへ変換する。
    private fun ParsedVrmMeta.toRuntimeMeta(): VrmRuntimeMeta = VrmRuntimeMeta(
        avatarName = avatarName,
        authors = authors,
        version = version,
    )

    // ヒューマノイドボーン定義を正規化済みのバインディングへ変換する。
    private fun ParsedHumanoidBone.toRuntimeBinding(): VrmHumanoidBoneBinding = VrmHumanoidBoneBinding(
        boneName = normalizeHumanoidBoneName(name),
        nodeIndex = nodeIndex,
    )

    // 表情定義をランタイムで扱う表情記述へ変換する。
    private fun ParsedVrmExpression.toRuntimeExpression(): VrmExpressionDescriptor = VrmExpressionDescriptor(
        runtimeName = runtimeName,
        displayName = displayName,
        presetName = presetName,
        isBinary = isBinary,
        morphTargetBinds = morphTargetBinds.map { bind -> bind.toRuntimeBind() },
        overrideBlink = overrideBlink,
        overrideLookAt = overrideLookAt,
        overrideMouth = overrideMouth,
    )

    // モーフターゲットのバインド情報を実行時形式へ変換する。
    private fun ParsedMorphTargetBind.toRuntimeBind(): VrmMorphTargetBind = VrmMorphTargetBind(
        nodeIndex = nodeIndex,
        morphTargetIndex = morphTargetIndex,
        weight = weight,
    )

    // LookAt 設定をランタイム用の視線追従定義へ変換する。
    private fun ParsedLookAt.toRuntimeLookAt(): VrmLookAtDescriptor = VrmLookAtDescriptor(
        type = type,
        offsetFromHeadBone = offsetFromHeadBone?.toRuntimeFloat3(),
    )

    // 一人称表示設定をランタイム用ディスクリプタへ変換する。
    private fun ParsedFirstPerson.toRuntimeFirstPerson(): VrmFirstPersonDescriptor = VrmFirstPersonDescriptor(
        firstPersonBone = firstPersonBone,
        meshAnnotationIndices = meshAnnotationIndices,
    )

    // パース済み 3 次元ベクトルを共通 Float3 モデルへ詰め替える。
    private fun ParsedFloat3.toRuntimeFloat3(): VrmFloat3 = VrmFloat3(
        x = x,
        y = y,
        z = z,
    )

    // ASCII の英大文字を英小文字へ変換する。
    private fun asciiLowercaseChar(char: Char): Char = when (char) {
        in 'A'..'Z' -> (char.code + ('a'.code - 'A'.code)).toChar()
        else -> char
    }

    // 文字列全体を ASCII ベースで小文字化する。
    private fun asciiLowercase(value: String): String = buildString(value.length) {
        value.forEach { char -> append(asciiLowercaseChar(char)) }
    }

    // 文字列先頭だけを ASCII ベースで小文字化する。
    private fun lowercaseAsciiFirstChar(value: String): String {
        if (value.isEmpty()) return value
        val firstChar = asciiLowercaseChar(value[0])
        if (firstChar == value[0]) return value
        return buildString(value.length) {
            append(firstChar)
            append(value, 1, value.length)
        }
    }

    // VRM ごとの差異を吸収してヒューマノイドボーン名を正規化する。
    private fun normalizeHumanoidBoneName(rawName: String): String {
        val trimmedName = rawName.trim()
        if (trimmedName.isEmpty()) return trimmedName
        return humanoidBoneAliases[asciiLowercase(trimmedName)] ?: lowercaseAsciiFirstChar(trimmedName)
    }

    private val humanoidBoneAliases: Map<String, String> = mapOf(
        "hips" to "hips",
        "spine" to "spine",
        "chest" to "chest",
        "upperchest" to "upperChest",
        "neck" to "neck",
        "head" to "head",
        "jaw" to "jaw",
        "lefteye" to "leftEye",
        "righteye" to "rightEye",
        "leftshoulder" to "leftShoulder",
        "rightshoulder" to "rightShoulder",
        "leftupperarm" to "leftUpperArm",
        "rightupperarm" to "rightUpperArm",
        "leftlowerarm" to "leftLowerArm",
        "rightlowerarm" to "rightLowerArm",
        "lefthand" to "leftHand",
        "righthand" to "rightHand",
        "leftupperleg" to "leftUpperLeg",
        "rightupperleg" to "rightUpperLeg",
        "leftlowerleg" to "leftLowerLeg",
        "rightlowerleg" to "rightLowerLeg",
        "leftfoot" to "leftFoot",
        "rightfoot" to "rightFoot",
        "lefttoes" to "leftToes",
        "righttoes" to "rightToes",
    )
}
