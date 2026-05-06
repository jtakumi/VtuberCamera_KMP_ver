package com.example.vtubercamera_kmp_ver.avatar.render

import com.example.vtubercamera_kmp_ver.avatar.mapping.AvatarExpressionId
import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmExpressionMap
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmExpressionDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor

internal object VrmMorphBindingResolver {
    fun resolve(
        runtimeDescriptor: VrmRuntimeAssetDescriptor,
        entities: IntArray,
    ): List<ResolvedExpressionMorphBinding> {
        val availableNames = runtimeDescriptor.availableExpressionNames
        return AvatarExpressionId.entries.mapNotNull { expressionId ->
            val runtimeName = VrmExpressionMap.resolve(
                expression = expressionId,
                specVersion = runtimeDescriptor.specVersion,
                availableNames = availableNames,
            ) ?: return@mapNotNull null
            val expressionDescriptor = runtimeDescriptor.expressions.firstOrNull { expression ->
                expression.runtimeName == runtimeName
            } ?: return@mapNotNull null
            expressionDescriptor.toResolvedBinding(expressionId, entities)
        }
    }

    private fun VrmExpressionDescriptor.toResolvedBinding(
        expressionId: AvatarExpressionId,
        entities: IntArray,
    ): ResolvedExpressionMorphBinding? {
        val morphBinds = morphTargetBinds.mapNotNull { bind ->
            val entity = entities.getOrNull(bind.nodeIndex) ?: return@mapNotNull null
            ResolvedMorphBind(
                entity = entity,
                morphTargetIndex = bind.morphTargetIndex,
                weight = bind.weight,
            )
        }
        if (morphBinds.isEmpty()) {
            return null
        }
        return ResolvedExpressionMorphBinding(
            expressionId = expressionId,
            morphBinds = morphBinds,
        )
    }
}

internal data class ResolvedExpressionMorphBinding(
    val expressionId: AvatarExpressionId,
    val morphBinds: List<ResolvedMorphBind>,
)

internal data class ResolvedMorphBind(
    val entity: Int,
    val morphTargetIndex: Int,
    val weight: Float,
)
