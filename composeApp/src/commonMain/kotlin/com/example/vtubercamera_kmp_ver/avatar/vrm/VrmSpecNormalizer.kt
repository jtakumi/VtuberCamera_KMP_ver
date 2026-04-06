package com.example.vtubercamera_kmp_ver.avatar.vrm

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion

internal object VrmSpecNormalizer {
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

    private fun ParsedVrmMeta.toRuntimeMeta(): VrmRuntimeMeta = VrmRuntimeMeta(
        avatarName = avatarName,
        authors = authors,
        version = version,
    )

    private fun ParsedHumanoidBone.toRuntimeBinding(): VrmHumanoidBoneBinding = VrmHumanoidBoneBinding(
        boneName = normalizeHumanoidBoneName(name),
        nodeIndex = nodeIndex,
    )

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

    private fun ParsedMorphTargetBind.toRuntimeBind(): VrmMorphTargetBind = VrmMorphTargetBind(
        nodeIndex = nodeIndex,
        morphTargetIndex = morphTargetIndex,
        weight = weight,
    )

    private fun ParsedLookAt.toRuntimeLookAt(): VrmLookAtDescriptor = VrmLookAtDescriptor(
        type = type,
        offsetFromHeadBone = offsetFromHeadBone?.toRuntimeFloat3(),
    )

    private fun ParsedFirstPerson.toRuntimeFirstPerson(): VrmFirstPersonDescriptor = VrmFirstPersonDescriptor(
        firstPersonBone = firstPersonBone,
        meshAnnotationIndices = meshAnnotationIndices,
    )

    private fun ParsedFloat3.toRuntimeFloat3(): VrmFloat3 = VrmFloat3(
        x = x,
        y = y,
        z = z,
    )

    private fun asciiLowercaseChar(char: Char): Char = when (char) {
        in 'A'..'Z' -> (char.code + ('a'.code - 'A'.code)).toChar()
        else -> char
    }

    private fun asciiLowercase(value: String): String = buildString(value.length) {
        value.forEach { char -> append(asciiLowercaseChar(char)) }
    }

    private fun lowercaseAsciiFirstChar(value: String): String {
        if (value.isEmpty()) return value
        val firstChar = asciiLowercaseChar(value[0])
        if (firstChar == value[0]) return value
        return buildString(value.length) {
            append(firstChar)
            append(value, 1, value.length)
        }
    }

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