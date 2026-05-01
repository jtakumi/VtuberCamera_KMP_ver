package com.example.vtubercamera_kmp_ver.avatar.render

import com.example.vtubercamera_kmp_ver.avatar.mapping.AvatarExpressionId
import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmExpressionMap
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmExpressionDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import kotlin.math.cos
import kotlin.math.sin

internal class AndroidAvatarRuntimeController private constructor(
    private val engine: Engine,
    private val headBinding: HeadBinding?,
    private val morphTargets: Map<Int, FloatArray>,
    private val expressionBindings: List<ExpressionBinding>,
) {
    fun apply(renderState: AvatarRenderState) {
        applyHeadPose(renderState)
        applyExpressions(renderState)
    }

    private fun applyHeadPose(renderState: AvatarRenderState) {
        val binding = headBinding ?: return
        val transformManager = engine.transformManager
        val rotation = rotationMatrix(
            yawDegrees = renderState.rig.headYawDegrees,
            pitchDegrees = renderState.rig.headPitchDegrees,
            rollDegrees = renderState.rig.headRollDegrees,
        )
        transformManager.setTransform(
            binding.transformInstance,
            multiplyColumnMajor(binding.baseLocalTransform, rotation),
        )
    }

    private fun applyExpressions(renderState: AvatarRenderState) {
        if (morphTargets.isEmpty()) {
            return
        }

        morphTargets.values.forEach { weights ->
            weights.fill(0f)
        }

        expressionBindings.forEach { binding ->
            val expressionWeight = binding.weightProvider(renderState).coerceIn(0f, 1f)
            if (expressionWeight <= 0f) {
                return@forEach
            }
            binding.morphBinds.forEach { morphBind ->
                val weights = morphTargets[morphBind.entity] ?: return@forEach
                if (morphBind.morphTargetIndex in weights.indices) {
                    weights[morphBind.morphTargetIndex] =
                        (weights[morphBind.morphTargetIndex] + expressionWeight * morphBind.weight).coerceIn(0f, 1f)
                }
            }
        }

        val renderableManager = engine.renderableManager
        morphTargets.forEach { (entity, weights) ->
            val renderableInstance = renderableManager.getInstance(entity)
            if (renderableInstance != 0) {
                renderableManager.setMorphWeights(renderableInstance, weights, 0)
            }
        }
    }

    private fun rotationMatrix(
        yawDegrees: Float,
        pitchDegrees: Float,
        rollDegrees: Float,
    ): FloatArray {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val roll = Math.toRadians(rollDegrees.toDouble())

        val cy = cos(yaw).toFloat()
        val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()
        val cr = cos(roll).toFloat()
        val sr = sin(roll).toFloat()

        val yawMatrix = floatArrayOf(
            cy, 0f, -sy, 0f,
            0f, 1f, 0f, 0f,
            sy, 0f, cy, 0f,
            0f, 0f, 0f, 1f,
        )
        val pitchMatrix = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, cp, sp, 0f,
            0f, -sp, cp, 0f,
            0f, 0f, 0f, 1f,
        )
        val rollMatrix = floatArrayOf(
            cr, sr, 0f, 0f,
            -sr, cr, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )

        return multiplyColumnMajor(yawMatrix, multiplyColumnMajor(pitchMatrix, rollMatrix))
    }

    private data class HeadBinding(
        val transformInstance: Int,
        val baseLocalTransform: FloatArray,
    )

    private data class ExpressionBinding(
        val weightProvider: (AvatarRenderState) -> Float,
        val morphBinds: List<MorphBind>,
    )

    private data class MorphBind(
        val entity: Int,
        val morphTargetIndex: Int,
        val weight: Float,
    )

    companion object {
        fun create(
            engine: Engine,
            asset: FilamentAsset,
            runtimeDescriptor: VrmRuntimeAssetDescriptor,
        ): AndroidAvatarRuntimeController {
            val entities = asset.entities
            val headBinding = createHeadBinding(engine, entities, runtimeDescriptor)
            val morphTargets = createMorphTargets(engine, asset)
            val expressionBindings = createExpressionBindings(entities, runtimeDescriptor)
            return AndroidAvatarRuntimeController(
                engine = engine,
                headBinding = headBinding,
                morphTargets = morphTargets,
                expressionBindings = expressionBindings,
            )
        }

        private fun createHeadBinding(
            engine: Engine,
            entities: IntArray,
            runtimeDescriptor: VrmRuntimeAssetDescriptor,
        ): HeadBinding? {
            val headNodeIndex = runtimeDescriptor.humanoidBones
                .firstOrNull { bone -> bone.boneName == HEAD_BONE_NAME }
                ?.nodeIndex
                ?: return null
            val headEntity = entities.getOrNull(headNodeIndex) ?: return null
            val transformManager = engine.transformManager
            val transformInstance = transformManager.getInstance(headEntity)
            if (transformInstance == 0) {
                return null
            }
            return HeadBinding(
                transformInstance = transformInstance,
                baseLocalTransform = transformManager.getTransform(transformInstance, FloatArray(MATRIX_SIZE)).copyOf(),
            )
        }

        private fun createMorphTargets(
            engine: Engine,
            asset: FilamentAsset,
        ): Map<Int, FloatArray> {
            val renderableManager = engine.renderableManager
            val morphTargets = mutableMapOf<Int, FloatArray>()
            asset.renderableEntities.forEach { entity ->
                val renderableInstance = renderableManager.getInstance(entity)
                if (renderableInstance == 0) {
                    return@forEach
                }
                val morphTargetCount = renderableManager.getMorphTargetCount(renderableInstance)
                if (morphTargetCount > 0) {
                    morphTargets[entity] = FloatArray(morphTargetCount)
                }
            }
            return morphTargets
        }

        private fun createExpressionBindings(
            entities: IntArray,
            runtimeDescriptor: VrmRuntimeAssetDescriptor,
        ): List<ExpressionBinding> {
            val availableNames = runtimeDescriptor.availableExpressionNames
            return listOfNotNull(
                createExpressionBinding(
                    expressionId = AvatarExpressionId.BlinkLeft,
                    runtimeDescriptor = runtimeDescriptor,
                    availableNames = availableNames,
                    entities = entities,
                    weightProvider = { state -> state.expressions.leftEyeBlink },
                ),
                createExpressionBinding(
                    expressionId = AvatarExpressionId.BlinkRight,
                    runtimeDescriptor = runtimeDescriptor,
                    availableNames = availableNames,
                    entities = entities,
                    weightProvider = { state -> state.expressions.rightEyeBlink },
                ),
                createExpressionBinding(
                    expressionId = AvatarExpressionId.JawOpen,
                    runtimeDescriptor = runtimeDescriptor,
                    availableNames = availableNames,
                    entities = entities,
                    weightProvider = { state -> state.expressions.jawOpen },
                ),
                createExpressionBinding(
                    expressionId = AvatarExpressionId.Smile,
                    runtimeDescriptor = runtimeDescriptor,
                    availableNames = availableNames,
                    entities = entities,
                    weightProvider = { state -> state.expressions.mouthSmile },
                ),
            )
        }

        private fun createExpressionBinding(
            expressionId: AvatarExpressionId,
            runtimeDescriptor: VrmRuntimeAssetDescriptor,
            availableNames: Set<String>,
            entities: IntArray,
            weightProvider: (AvatarRenderState) -> Float,
        ): ExpressionBinding? {
            val runtimeName = VrmExpressionMap.resolve(
                expression = expressionId,
                specVersion = runtimeDescriptor.specVersion,
                availableNames = availableNames,
            ) ?: return null
            val descriptor = runtimeDescriptor.expressions.firstOrNull { expression ->
                expression.runtimeName == runtimeName
            } ?: return null
            val morphBinds = descriptor.toMorphBinds(entities)
            if (morphBinds.isEmpty()) {
                return null
            }
            return ExpressionBinding(
                weightProvider = weightProvider,
                morphBinds = morphBinds,
            )
        }

        private fun VrmExpressionDescriptor.toMorphBinds(entities: IntArray): List<MorphBind> {
            return morphTargetBinds.mapNotNull { bind ->
                val entity = entities.getOrNull(bind.nodeIndex) ?: return@mapNotNull null
                MorphBind(
                    entity = entity,
                    morphTargetIndex = bind.morphTargetIndex,
                    weight = bind.weight,
                )
            }
        }

        private fun multiplyColumnMajor(
            left: FloatArray,
            right: FloatArray,
        ): FloatArray {
            val result = FloatArray(MATRIX_SIZE)
            for (column in 0 until MATRIX_EDGE) {
                for (row in 0 until MATRIX_EDGE) {
                    var value = 0f
                    for (index in 0 until MATRIX_EDGE) {
                        value += left[index * MATRIX_EDGE + row] * right[column * MATRIX_EDGE + index]
                    }
                    result[column * MATRIX_EDGE + row] = value
                }
            }
            return result
        }

        private const val HEAD_BONE_NAME = "head"
        private const val MATRIX_EDGE = 4
        private const val MATRIX_SIZE = MATRIX_EDGE * MATRIX_EDGE
    }
}
