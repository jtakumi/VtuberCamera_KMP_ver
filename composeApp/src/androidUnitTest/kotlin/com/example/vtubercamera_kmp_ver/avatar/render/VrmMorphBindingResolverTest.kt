package com.example.vtubercamera_kmp_ver.avatar.render

import com.example.vtubercamera_kmp_ver.avatar.mapping.AvatarExpressionId
import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmExpressionDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmMorphTargetBind
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VrmMorphBindingResolverTest {

    @Test
    fun resolve_prefersVrm0AliasesAndMapsNodesToEntities() {
        val resolved = VrmMorphBindingResolver.resolve(
            runtimeDescriptor = descriptor(
                specVersion = VrmSpecVersion.Vrm0,
                expressions = listOf(
                    expression("happy", 0, 1, 0.5f),
                    expression("joy", 1, 2, 0.75f),
                    expression("aa", 2, 3, 1f),
                ),
            ),
            entities = intArrayOf(10, 20, 30),
        )

        val smile = resolved.single { binding -> binding.expressionId == AvatarExpressionId.Smile }
        val jawOpen = resolved.single { binding -> binding.expressionId == AvatarExpressionId.JawOpen }

        assertEquals(20, smile.morphBinds.single().entity)
        assertEquals(2, smile.morphBinds.single().morphTargetIndex)
        assertEquals(0.75f, smile.morphBinds.single().weight)
        assertEquals(30, jawOpen.morphBinds.single().entity)
    }

    @Test
    fun resolve_prefersVrm1Aliases() {
        val resolved = VrmMorphBindingResolver.resolve(
            runtimeDescriptor = descriptor(
                specVersion = VrmSpecVersion.Vrm1,
                expressions = listOf(
                    expression("joy", 0, 0, 1f),
                    expression("happy", 1, 1, 1f),
                    expression("a", 2, 2, 1f),
                    expression("aa", 3, 3, 1f),
                ),
            ),
            entities = intArrayOf(10, 20, 30, 40),
        )

        val smile = resolved.single { binding -> binding.expressionId == AvatarExpressionId.Smile }
        val jawOpen = resolved.single { binding -> binding.expressionId == AvatarExpressionId.JawOpen }

        assertEquals(20, smile.morphBinds.single().entity)
        assertEquals(40, jawOpen.morphBinds.single().entity)
    }

    @Test
    fun resolve_dropsInvalidNodesAndMissingAliases() {
        val resolved = VrmMorphBindingResolver.resolve(
            runtimeDescriptor = descriptor(
                specVersion = VrmSpecVersion.Vrm1,
                expressions = listOf(
                    expression("neutral", 0, 0, 1f),
                    expression("happy", 99, 1, 1f),
                ),
            ),
            entities = intArrayOf(10),
        )

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun resolve_preservesMultipleMorphBinds() {
        val resolved = VrmMorphBindingResolver.resolve(
            runtimeDescriptor = descriptor(
                specVersion = VrmSpecVersion.Vrm1,
                expressions = listOf(
                    VrmExpressionDescriptor(
                        runtimeName = "blinkLeft",
                        displayName = "Blink Left",
                        presetName = "blinkLeft",
                        isBinary = false,
                        morphTargetBinds = listOf(
                            VrmMorphTargetBind(nodeIndex = 0, morphTargetIndex = 1, weight = 0.25f),
                            VrmMorphTargetBind(nodeIndex = 1, morphTargetIndex = 2, weight = 0.5f),
                        ),
                    ),
                ),
            ),
            entities = intArrayOf(10, 20),
        )

        val blinkLeft = resolved.single()
        assertEquals(AvatarExpressionId.BlinkLeft, blinkLeft.expressionId)
        assertEquals(listOf(10, 20), blinkLeft.morphBinds.map { bind -> bind.entity })
        assertEquals(listOf(1, 2), blinkLeft.morphBinds.map { bind -> bind.morphTargetIndex })
    }

    private fun descriptor(
        specVersion: VrmSpecVersion,
        expressions: List<VrmExpressionDescriptor>,
    ): VrmRuntimeAssetDescriptor = VrmRuntimeAssetDescriptor(
        specVersion = specVersion,
        rawSpecVersion = null,
        assetVersion = null,
        meta = VrmRuntimeMeta(
            avatarName = null,
            authors = emptyList(),
            version = null,
        ),
        thumbnailImageIndex = null,
        expressions = expressions,
    )

    private fun expression(
        runtimeName: String,
        nodeIndex: Int,
        morphTargetIndex: Int,
        weight: Float,
    ): VrmExpressionDescriptor = VrmExpressionDescriptor(
        runtimeName = runtimeName,
        displayName = runtimeName,
        presetName = runtimeName,
        isBinary = false,
        morphTargetBinds = listOf(
            VrmMorphTargetBind(
                nodeIndex = nodeIndex,
                morphTargetIndex = morphTargetIndex,
                weight = weight,
            ),
        ),
    )
}
