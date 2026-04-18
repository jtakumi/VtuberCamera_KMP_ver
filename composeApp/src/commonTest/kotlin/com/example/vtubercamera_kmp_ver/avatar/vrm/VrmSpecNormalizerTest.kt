package com.example.vtubercamera_kmp_ver.avatar.vrm

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VrmSpecNormalizerTest {

    @Test
    fun normalizeVrm0MapsRuntimeDescriptorAndExpressionOverrides() {
        val descriptor = VrmSpecNormalizer.normalize(
            extension = ParsedVrm0Extension(
                rawSpecVersion = "0.0",
                meta = ParsedVrmMeta(
                    avatarName = "Avatar 0",
                    authors = listOf("Author 0"),
                    version = "0.99",
                ),
                thumbnailImageIndex = 2,
                humanoidBones = listOf(
                    ParsedHumanoidBone(name = "leftupperarm", nodeIndex = 4),
                ),
                expressions = listOf(
                    ParsedVrmExpression(
                        runtimeName = "joy",
                        displayName = "Joy",
                        presetName = "joy",
                        isBinary = false,
                        morphTargetBinds = listOf(
                            ParsedMorphTargetBind(nodeIndex = 5, morphTargetIndex = 1, weight = 1f),
                        ),
                        overrideBlink = "blend",
                        overrideLookAt = "none",
                        overrideMouth = "block",
                    ),
                ),
                lookAt = null,
                firstPerson = null,
            ),
            assetVersion = "2.0",
        )

        assertEquals(VrmSpecVersion.Vrm0, descriptor.specVersion)
        assertEquals("0.0", descriptor.rawSpecVersion)
        assertEquals("Avatar 0", descriptor.meta.avatarName)
        assertEquals("leftUpperArm", descriptor.humanoidBones.single().boneName)

        val expression = descriptor.expressions.single()
        assertEquals("joy", expression.runtimeName)
        assertEquals("Joy", expression.displayName)
        assertEquals("joy", expression.presetName)
        assertEquals("blend", expression.overrideBlink)
        assertEquals("none", expression.overrideLookAt)
        assertEquals("block", expression.overrideMouth)
        assertEquals(1f, expression.morphTargetBinds.single().weight)
    }

    @Test
    fun normalizeVrm1MapsRuntimeDescriptorAndRetainsExpressionNames() {
        val descriptor = VrmSpecNormalizer.normalize(
            extension = ParsedVrm1Extension(
                rawSpecVersion = "1.0",
                meta = ParsedVrmMeta(
                    avatarName = "Avatar 1",
                    authors = listOf("Author 1", "Author 2"),
                    version = "1.2.3",
                ),
                thumbnailImageIndex = 6,
                humanoidBones = listOf(
                    ParsedHumanoidBone(name = "Head", nodeIndex = 8),
                ),
                expressions = listOf(
                    ParsedVrmExpression(
                        runtimeName = "happy",
                        displayName = "Happy",
                        presetName = "happy",
                        isBinary = false,
                        morphTargetBinds = listOf(
                            ParsedMorphTargetBind(nodeIndex = 10, morphTargetIndex = 2, weight = 0.75f),
                        ),
                        overrideBlink = null,
                        overrideLookAt = null,
                        overrideMouth = null,
                    ),
                    ParsedVrmExpression(
                        runtimeName = "surprisedCustom",
                        displayName = "Surprised",
                        presetName = null,
                        isBinary = true,
                        morphTargetBinds = listOf(
                            ParsedMorphTargetBind(nodeIndex = 12, morphTargetIndex = 4, weight = 0.5f),
                        ),
                        overrideBlink = "none",
                        overrideLookAt = "blend",
                        overrideMouth = "block",
                    ),
                ),
                lookAt = ParsedLookAt(
                    type = "expression",
                    offsetFromHeadBone = ParsedFloat3(0f, 0.2f, 0.1f),
                ),
                firstPerson = ParsedFirstPerson(
                    firstPersonBone = 3,
                    meshAnnotationIndices = listOf(13),
                ),
            ),
            assetVersion = "2.0",
        )

        assertEquals(VrmSpecVersion.Vrm1, descriptor.specVersion)
        assertEquals("head", descriptor.humanoidBones.single().boneName)
        assertEquals(setOf("happy", "surprisedCustom"), descriptor.availableExpressionNames)

        val customExpression = descriptor.expressions[1]
        assertEquals("surprisedCustom", customExpression.runtimeName)
        assertNull(customExpression.presetName)
        assertEquals("none", customExpression.overrideBlink)
        assertEquals("blend", customExpression.overrideLookAt)
        assertEquals("block", customExpression.overrideMouth)
        assertEquals(0.5f, customExpression.morphTargetBinds.single().weight)

        assertEquals("expression", descriptor.lookAt?.type)
        assertEquals(13, descriptor.firstPerson?.meshAnnotationIndices?.single())
    }
}
