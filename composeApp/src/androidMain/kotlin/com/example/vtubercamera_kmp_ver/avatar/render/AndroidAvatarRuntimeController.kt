package com.example.vtubercamera_kmp_ver.avatar.render

import com.example.vtubercamera_kmp_ver.avatar.mapping.AvatarExpressionId
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import kotlin.math.cos
import kotlin.math.sin

internal class AndroidAvatarRuntimeController private constructor(
    private val engine: Engine,
    private val poseBindings: List<PoseBinding>,
    private val armPoseBindings: List<ArmPoseBinding>,
    private val morphTargets: Map<Int, FloatArray>,
    private val expressionBindings: List<ExpressionBinding>,
) {
    // 描画ループ内で毎フレーム行列を組み立てるため、確保済みバッファを使い回して GC 負荷を避ける。
    // apply / prepareFrame はいずれも Compose の main thread から呼ばれるので共有して問題ない。
    private val rotationScratch = FloatArray(MATRIX_SIZE)
    private val transformScratch = FloatArray(MATRIX_SIZE)

    init {
        // 脱力させた腕の姿勢は定数なので、毎フレーム計算せず生成時に一度だけ適用する。
        applyRelaxedArmPose()
    }

    fun apply(renderState: AvatarRenderState) {
        applyHeadPose(renderState)
        applyExpressions(renderState)
    }

    /**
     * 現在 rig に適用されている姿勢を診断用に読み戻す。
     *
     * TransformManager への JNI 読み出しと診断オブジェクト生成を伴うため、
     * 毎フレームではなくログ出力が実際に必要なタイミングでのみ呼ぶこと。
     */
    fun applicationTargets(): AvatarRenderApplicationResult {
        val transformManager = engine.transformManager
        val poseMatrices = poseBindings.map { binding ->
            val localTransform = transformManager.getTransform(
                binding.transformInstance,
                FloatArray(MATRIX_SIZE),
            )
            AvatarPoseBindingDiagnostic(
                boneName = binding.boneName,
                m00 = localTransform[0],
                m02 = localTransform[8],
                m20 = localTransform[2],
                m22 = localTransform[10],
            )
        }
        return AvatarRenderApplicationResult(
            poseBindingCount = poseBindings.size,
            expressionBindingCount = expressionBindings.size,
            morphTargetEntityCount = morphTargets.size,
            poseMatrices = poseMatrices,
        )
    }

    private fun applyHeadPose(renderState: AvatarRenderState) {
        if (poseBindings.isEmpty()) return
        val transformManager = engine.transformManager
        // 描画ループ内のためイテレータを確保しない index ベースで回す。
        for (index in poseBindings.indices) {
            val binding = poseBindings[index]
            writeRotationMatrix(
                yawDegrees = renderState.rig.headYawDegrees * binding.rotationWeight +
                    renderState.rig.bodySwayDegrees * binding.swayWeight,
                pitchDegrees = renderState.rig.headPitchDegrees * binding.rotationWeight +
                    renderState.rig.bodyLeanDegrees * binding.swayWeight,
                rollDegrees = renderState.rig.headRollDegrees * binding.rotationWeight -
                    renderState.rig.bodySwayDegrees * binding.swayWeight * 0.35f,
                destination = rotationScratch,
            )
            multiplyColumnMajor(
                left = binding.baseLocalTransform,
                right = rotationScratch,
                destination = transformScratch,
            )
            transformManager.setTransform(binding.transformInstance, transformScratch)
        }
    }

    /**
     * Keeps imported VRM avatars out of their bind/T-pose while tracking is active.
     *
     * 適用する角度は binding が持つ定数なので結果は不変であり、生成時の一度だけ呼べばよい。
     */
    private fun applyRelaxedArmPose() {
        if (armPoseBindings.isEmpty()) return
        val transformManager = engine.transformManager
        for (index in armPoseBindings.indices) {
            val binding = armPoseBindings[index]
            writeRotationMatrix(
                yawDegrees = 0f,
                pitchDegrees = 0f,
                rollDegrees = binding.rollDegrees,
                destination = rotationScratch,
            )
            multiplyColumnMajor(
                left = binding.baseLocalTransform,
                right = rotationScratch,
                destination = transformScratch,
            )
            transformManager.setTransform(binding.transformInstance, transformScratch)
        }
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

    /**
     * yaw → pitch → roll の合成回転を column-major 行列として [destination] へ書き込む。
     *
     * 中間行列を組み立てて掛け合わせる代わりに積を展開しているため、呼び出しごとの確保が発生しない。
     * [destination] は 16 要素すべてが上書きされるので、呼び出し側で初期化する必要はない。
     */
    private fun writeRotationMatrix(
        yawDegrees: Float,
        pitchDegrees: Float,
        rollDegrees: Float,
        destination: FloatArray,
    ) {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val roll = Math.toRadians(rollDegrees.toDouble())

        val cy = cos(yaw).toFloat()
        val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()
        val cr = cos(roll).toFloat()
        val sr = sin(roll).toFloat()

        destination[0] = cy * cr + sy * sp * sr
        destination[1] = cp * sr
        destination[2] = -sy * cr + cy * sp * sr
        destination[3] = 0f
        destination[4] = -cy * sr + sy * sp * cr
        destination[5] = cp * cr
        destination[6] = sy * sr + cy * sp * cr
        destination[7] = 0f
        destination[8] = sy * cp
        destination[9] = -sp
        destination[10] = cy * cp
        destination[11] = 0f
        destination[12] = 0f
        destination[13] = 0f
        destination[14] = 0f
        destination[15] = 1f
    }

    private data class PoseBinding(
        val boneName: String,
        val transformInstance: Int,
        val baseLocalTransform: FloatArray,
        val rotationWeight: Float,
        val swayWeight: Float,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PoseBinding

            if (transformInstance != other.transformInstance) return false
            if (rotationWeight != other.rotationWeight) return false
            if (swayWeight != other.swayWeight) return false
            if (boneName != other.boneName) return false
            if (!baseLocalTransform.contentEquals(other.baseLocalTransform)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = transformInstance
            result = 31 * result + rotationWeight.hashCode()
            result = 31 * result + swayWeight.hashCode()
            result = 31 * result + boneName.hashCode()
            result = 31 * result + baseLocalTransform.contentHashCode()
            return result
        }
    }

    private data class ArmPoseBinding(
        val transformInstance: Int,
        val baseLocalTransform: FloatArray,
        val rollDegrees: Float,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ArmPoseBinding

            if (transformInstance != other.transformInstance) return false
            if (rollDegrees != other.rollDegrees) return false
            if (!baseLocalTransform.contentEquals(other.baseLocalTransform)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = transformInstance
            result = 31 * result + rollDegrees.hashCode()
            result = 31 * result + baseLocalTransform.contentHashCode()
            return result
        }
    }

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
            val nodeEntityResolver = runtimeDescriptor.nodeEntityResolver(asset)
            val poseBindings = createPoseBindings(engine, runtimeDescriptor, nodeEntityResolver)
            val armPoseBindings = createArmPoseBindings(engine, runtimeDescriptor, nodeEntityResolver)
            val morphTargets = createMorphTargets(engine, asset)
            val expressionBindings = createExpressionBindings(nodeEntityResolver, runtimeDescriptor)
            return AndroidAvatarRuntimeController(
                engine = engine,
                poseBindings = poseBindings,
                armPoseBindings = armPoseBindings,
                morphTargets = morphTargets,
                expressionBindings = expressionBindings,
            )
        }

        private fun createPoseBindings(
            engine: Engine,
            runtimeDescriptor: VrmRuntimeAssetDescriptor,
            nodeEntityResolver: (Int) -> Int?,
        ): List<PoseBinding> {
            val transformManager = engine.transformManager
            val nodes = runtimeDescriptor.humanoidBones.associate { it.boneName to it.nodeIndex }
            val specs = listOf(
                BoneWeight(HEAD_BONE_NAME, 0.82f, 0f),
                BoneWeight(NECK_BONE_NAME, 0.18f, 0.32f),
                BoneWeight(CHEST_BONE_NAME, 0f, 0.72f),
                BoneWeight(SPINE_BONE_NAME, 0f, 0.55f),
            )
            val available = specs.filter { nodes[it.name]?.let(nodeEntityResolver) != null }
            if (available.none { it.name == HEAD_BONE_NAME }) return emptyList()
            val missingRotationWeight = specs.filterNot { it in available }.sumOf { it.rotationWeight.toDouble() }.toFloat()
            return available.mapNotNull { spec ->
                val entity = nodes[spec.name]?.let(nodeEntityResolver) ?: return@mapNotNull null
                val transformInstance = transformManager.getInstance(entity)
                if (transformInstance == 0) return@mapNotNull null
                PoseBinding(
                    boneName = spec.name,
                    transformInstance = transformInstance,
                    baseLocalTransform = transformManager.getTransform(
                        transformInstance,
                        FloatArray(MATRIX_SIZE),
                    ).copyOf(),
                    rotationWeight = spec.rotationWeight +
                        if (spec.name == HEAD_BONE_NAME) missingRotationWeight else 0f,
                    swayWeight = spec.swayWeight,
                )
            }
        }

        private fun createArmPoseBindings(
            engine: Engine,
            runtimeDescriptor: VrmRuntimeAssetDescriptor,
            nodeEntityResolver: (Int) -> Int?,
        ): List<ArmPoseBinding> {
            val transformManager = engine.transformManager
            val nodes = runtimeDescriptor.humanoidBones.associate { it.boneName to it.nodeIndex }
            return listOf(
                ArmPoseSpec(LEFT_UPPER_ARM_BONE_NAME, RELAXED_LEFT_ARM_ROLL_DEGREES),
                ArmPoseSpec(RIGHT_UPPER_ARM_BONE_NAME, RELAXED_RIGHT_ARM_ROLL_DEGREES),
            ).mapNotNull { spec ->
                val entity = nodes[spec.name]?.let(nodeEntityResolver) ?: return@mapNotNull null
                val transformInstance = transformManager.getInstance(entity)
                if (transformInstance == 0) return@mapNotNull null
                ArmPoseBinding(
                    transformInstance = transformInstance,
                    baseLocalTransform = transformManager.getTransform(
                        transformInstance,
                        FloatArray(MATRIX_SIZE),
                    ).copyOf(),
                    rollDegrees = spec.rollDegrees,
                )
            }
        }

        private data class BoneWeight(
            val name: String,
            val rotationWeight: Float,
            val swayWeight: Float,
        )

        private data class ArmPoseSpec(
            val name: String,
            val rollDegrees: Float,
        )

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
            nodeEntityResolver: (Int) -> Int?,
            runtimeDescriptor: VrmRuntimeAssetDescriptor,
        ): List<ExpressionBinding> {
            return VrmMorphBindingResolver.resolve(
                runtimeDescriptor = runtimeDescriptor,
                nodeEntityResolver = nodeEntityResolver,
            ).mapNotNull { binding ->
                val weightProvider = when (binding.expressionId) {
                    AvatarExpressionId.BlinkLeft -> { state: AvatarRenderState -> state.expressions.leftEyeBlink }
                    AvatarExpressionId.BlinkRight -> { state: AvatarRenderState -> state.expressions.rightEyeBlink }
                    AvatarExpressionId.JawOpen -> { state: AvatarRenderState -> state.expressions.jawOpen }
                    AvatarExpressionId.Smile -> { state: AvatarRenderState -> state.expressions.mouthSmile }
                }
                ExpressionBinding(
                    weightProvider = weightProvider,
                    morphBinds = binding.morphBinds.map { bind ->
                        MorphBind(
                            entity = bind.entity,
                            morphTargetIndex = bind.morphTargetIndex,
                            weight = bind.weight,
                        )
                    },
                )
            }
        }

        /**
         * column-major 4x4 行列の積 `left * right` を [destination] へ書き込む。
         *
         * [destination] は書き込みながら [left] / [right] を読むため、これらと同じ配列を渡してはならない。
         */
        private fun multiplyColumnMajor(
            left: FloatArray,
            right: FloatArray,
            destination: FloatArray,
        ) {
            for (column in 0 until MATRIX_EDGE) {
                for (row in 0 until MATRIX_EDGE) {
                    var value = 0f
                    for (index in 0 until MATRIX_EDGE) {
                        value += left[index * MATRIX_EDGE + row] * right[column * MATRIX_EDGE + index]
                    }
                    destination[column * MATRIX_EDGE + row] = value
                }
            }
        }

        private const val HEAD_BONE_NAME = "head"
        private const val NECK_BONE_NAME = "neck"
        private const val CHEST_BONE_NAME = "chest"
        private const val SPINE_BONE_NAME = "spine"
        private const val LEFT_UPPER_ARM_BONE_NAME = "leftUpperArm"
        private const val RIGHT_UPPER_ARM_BONE_NAME = "rightUpperArm"
        private const val RELAXED_LEFT_ARM_ROLL_DEGREES = -75f
        private const val RELAXED_RIGHT_ARM_ROLL_DEGREES = 75f
        private const val MATRIX_EDGE = 4
        private const val MATRIX_SIZE = MATRIX_EDGE * MATRIX_EDGE

        private fun VrmRuntimeAssetDescriptor.nodeEntityResolver(
            asset: FilamentAsset,
        ): (Int) -> Int? = { nodeIndex ->
            nodeNames.getOrNull(nodeIndex)
                ?.let(asset::getEntitiesByName)
                ?.firstOrNull()
        }
    }
}

/** Summary of the rig targets that accepted a render-state update. */
internal data class AvatarRenderApplicationResult(
    val poseBindingCount: Int,
    val expressionBindingCount: Int,
    val morphTargetEntityCount: Int,
    val poseMatrices: List<AvatarPoseBindingDiagnostic>,
)

/** Read-back of the TransformManager matrix applied to a VRM humanoid joint. */
internal data class AvatarPoseBindingDiagnostic(
    val boneName: String,
    val m00: Float,
    val m02: Float,
    val m20: Float,
    val m22: Float,
)
