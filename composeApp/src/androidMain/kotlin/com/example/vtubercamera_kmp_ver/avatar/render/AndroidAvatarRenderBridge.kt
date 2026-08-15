package com.example.vtubercamera_kmp_ver.avatar.render

import android.os.SystemClock
import android.util.Log
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus
import com.example.vtubercamera_kmp_ver.avatar.tracking.AndroidFaceTrackingToAvatarMapper
import com.example.vtubercamera_kmp_ver.camera.AvatarAssetStore
import com.example.vtubercamera_kmp_ver.camera.AvatarSelectionData
import com.google.android.filament.Engine
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.FilamentAsset
import kotlin.math.abs
import kotlin.math.max

internal class AndroidAvatarRenderBridge(
    private val engine: Engine,
    private val scene: Scene,
    private val assetLoader: AndroidVrmAssetLoader,
    private val resourceCleaner: FilamentResourceCleaner,
    private val onSceneFramingChanged: (AvatarSceneFraming) -> Unit,
    private val onRenderStateChanged: (AvatarRenderState) -> Unit,
) {
    private val renderStateMapper = AndroidFaceTrackingToAvatarMapper()
    private var currentAsset: FilamentAsset? = null
    private var currentAssetKey: AvatarAssetKey? = null
    private var currentRuntimeController: AndroidAvatarRuntimeController? = null
    private var latestAppliedRenderState = AvatarRenderState.Neutral
    private var previousTrackingRenderState: AvatarRenderState? = null
    private var lastMotionLogElapsedMillis = Long.MIN_VALUE
    private var lastTrackingStatus: AvatarTrackingStatus? = null

    fun prepareFrame() {
        // updateBoneMatrices copies the current TransformManager pose into the skinning matrices.
        // Face tracking therefore has to run first for its rotation to reach the rendered mesh.
        currentRuntimeController?.apply(latestAppliedRenderState)
        currentAsset?.instance?.animator?.updateBoneMatrices()
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(
        avatarSelection: AvatarSelectionData,
        avatarRenderState: AvatarRenderState,
        onAvatarLoadFailure: (AvatarAssetLoadException) -> Unit,
    ) {
        val trackingRenderState = avatarRenderState
        val appliedRenderState = renderStateMapper.map(trackingRenderState)
        latestAppliedRenderState = appliedRenderState
        onRenderStateChanged(appliedRenderState)

        val nextAssetKey = AvatarAssetKey(
            assetId = avatarSelection.assetHandle.assetId,
            byteHash = avatarSelection.assetHandle.contentHash,
        )
        if (nextAssetKey == currentAssetKey) {
            // 姿勢の反映は次の prepareFrame が latestAppliedRenderState を使って行うため、ここでは記録だけに留める。
            currentRuntimeController?.let { controller ->
                logMotion(
                    controller = controller,
                    trackingRenderState = trackingRenderState,
                    appliedRenderState = appliedRenderState,
                )
            }
            return
        }

        val assetBytes = AvatarAssetStore.load(avatarSelection.assetHandle)
            ?: run {
                clearCurrentAsset()
                return onAvatarLoadFailure(
                    AvatarAssetLoadException(AvatarAssetLoadFailureKind.AssetUnavailable),
                )
            }

        assetLoader.loadAsset(assetBytes)
            .onSuccess { nextAsset ->
                runCatching {
                    nextAsset.configureRenderables()
                    nextAsset.instance.animator.updateBoneMatrices()
                    val runtimeController = AndroidAvatarRuntimeController.create(
                        engine = engine,
                        asset = nextAsset,
                        runtimeDescriptor = avatarSelection.runtimeDescriptor,
                    )
                    // 診断ログが適用後の姿勢を読み戻せるよう、apply を先に済ませてから記録する。
                    runtimeController.apply(latestAppliedRenderState)
                    logMotion(
                        controller = runtimeController,
                        trackingRenderState = trackingRenderState,
                        appliedRenderState = appliedRenderState,
                    )
                    scene.addEntities(nextAsset.entities)
                    onSceneFramingChanged(nextAsset.toSceneFraming())
                    runtimeController
                }.onSuccess { runtimeController ->
                    val previousAsset = currentAsset
                    currentAsset = nextAsset
                    currentAssetKey = nextAssetKey
                    currentRuntimeController = runtimeController
                    resourceCleaner.destroyAsset(
                        scene = scene,
                        assetLoader = assetLoader,
                        asset = previousAsset,
                    )
                }.onFailure { throwable ->
                    resourceCleaner.destroyAsset(
                        scene = scene,
                        assetLoader = assetLoader,
                        asset = nextAsset,
                    )
                    clearCurrentAsset()
                    onAvatarLoadFailure(throwable.toAvatarLoadException(AvatarAssetLoadFailureKind.SceneSetupFailed))
                }
            }
            .onFailure { throwable ->
                clearCurrentAsset()
                onAvatarLoadFailure(
                    if (throwable is AvatarAssetLoadException) {
                        throwable
                    } else {
                        throwable.toAvatarLoadException(AvatarAssetLoadFailureKind.ResourceLoadFailed)
                    },
                )
            }
    }

    private fun FilamentAsset.configureRenderables() {
        val renderableManager = engine.renderableManager
        renderableEntities.forEach { entity ->
            val renderable = renderableManager.getInstance(entity)
            renderableManager.setLayerMask(renderable, SCENE_LAYER_MASK, SCENE_LAYER_VISIBLE)
            renderableManager.setCulling(renderable, false)
            repeat(renderableManager.getPrimitiveCount(renderable)) { primitiveIndex ->
                renderableManager.getMaterialInstanceAt(renderable, primitiveIndex).setDoubleSided(true)
            }
        }
    }

    fun destroy() {
        resourceCleaner.destroyAsset(
            scene = scene,
            assetLoader = assetLoader,
            asset = currentAsset,
        )
        currentAsset = null
        currentAssetKey = null
        currentRuntimeController = null
        latestAppliedRenderState = AvatarRenderState.Neutral
        previousTrackingRenderState = null
        lastTrackingStatus = null
    }

    private fun FilamentAsset.toSceneFraming(): AvatarSceneFraming {
        val bounds = boundingBox
        val center = bounds.center
        val halfExtent = bounds.halfExtent
        val maxHalfExtent = max(
            max(halfExtent[0], halfExtent[1]),
            max(halfExtent[2], MIN_MODEL_HALF_EXTENT),
        )

        return AvatarSceneFraming(
            targetX = center[0].toDouble(),
            targetY = center[1].toDouble(),
            targetZ = center[2].toDouble(),
            cameraDistance = max(
                DEFAULT_CAMERA_DISTANCE,
                maxHalfExtent.toDouble() * MODEL_FIT_DISTANCE_MULTIPLIER,
            ),
        )
    }

    private data class AvatarAssetKey(
        val assetId: Long,
        val byteHash: Int,
    )

    private fun Throwable.toAvatarLoadException(
        fallbackKind: AvatarAssetLoadFailureKind,
    ): AvatarAssetLoadException = this as? AvatarAssetLoadException
        ?: AvatarAssetLoadException(
            kind = fallbackKind,
            cause = this,
        )

    private fun clearCurrentAsset() {
        resourceCleaner.destroyAsset(
            scene = scene,
            assetLoader = assetLoader,
            asset = currentAsset,
        )
        currentAsset = null
        currentAssetKey = null
        currentRuntimeController = null
        latestAppliedRenderState = AvatarRenderState.Neutral
        previousTrackingRenderState = null
        lastTrackingStatus = null
    }

    /**
     * tracking / 適用済み render state を診断ログへ記録する。
     *
     * rig への姿勢反映は行わない。読み戻しを伴う [AndroidAvatarRuntimeController.applicationTargets] は
     * 実際にログを出力するときだけ呼び、毎フレームの JNI 往復と一時オブジェクト生成を避ける。
     */
    private fun logMotion(
        controller: AndroidAvatarRuntimeController,
        trackingRenderState: AvatarRenderState,
        appliedRenderState: AvatarRenderState,
    ) {
        val previousState = previousTrackingRenderState
        val trackingChanged = previousState == null || trackingRenderState.differsVisiblyFrom(previousState)
        val statusChanged = appliedRenderState.trackingStatus != lastTrackingStatus
        val now = SystemClock.elapsedRealtime()
        if (statusChanged || now - lastMotionLogElapsedMillis >= MOTION_LOG_INTERVAL_MILLIS) {
            val application = controller.applicationTargets()
            Log.d(
                AVATAR_MOTION_LOG_TAG,
                "sourceTs=${trackingRenderState.sourceTimestampMillis ?: -1L} " +
                    "faceTracking(status=${trackingRenderState.trackingStatus},confidence=${trackingRenderState.trackingConfidence.format(2)},changed=$trackingChanged," +
                    "head=${trackingRenderState.rig.poseLog()},body=${trackingRenderState.rig.bodyLog()},expression=${trackingRenderState.expressions.expressionLog()}) " +
                    "avatar(status=${appliedRenderState.trackingStatus},head=${appliedRenderState.rig.poseLog()}," +
                    "body=${appliedRenderState.rig.bodyLog()},expression=${appliedRenderState.expressions.expressionLog()}) " +
                    "poseBindings=${application.poseBindingCount} " +
                    "expressionBindings=${application.expressionBindingCount} " +
                    "morphTargetEntities=${application.morphTargetEntityCount} " +
                    "jointMatrix=${application.poseMatrices.joinToString { it.toLogString() }}",
            )
            lastMotionLogElapsedMillis = now
        }
        previousTrackingRenderState = trackingRenderState
        lastTrackingStatus = appliedRenderState.trackingStatus
    }

    private fun com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState.poseLog(): String =
        "(y=${headYawDegrees.format(1)},p=${headPitchDegrees.format(1)},r=${headRollDegrees.format(1)})"

    private fun com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState.bodyLog(): String =
        "(sway=${bodySwayDegrees.format(1)},lean=${bodyLeanDegrees.format(1)})"

    private fun com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights.expressionLog(): String =
        "(blinkL=${leftEyeBlink.format(2)},blinkR=${rightEyeBlink.format(2)},jaw=${jawOpen.format(2)},smile=${mouthSmile.format(2)})"

    private fun AvatarRenderState.differsVisiblyFrom(other: AvatarRenderState): Boolean =
        abs(rig.headYawDegrees - other.rig.headYawDegrees) > MOTION_EPSILON_DEGREES ||
            abs(rig.headPitchDegrees - other.rig.headPitchDegrees) > MOTION_EPSILON_DEGREES ||
            abs(rig.headRollDegrees - other.rig.headRollDegrees) > MOTION_EPSILON_DEGREES ||
            abs(rig.bodySwayDegrees - other.rig.bodySwayDegrees) > MOTION_EPSILON_DEGREES ||
            abs(rig.bodyLeanDegrees - other.rig.bodyLeanDegrees) > MOTION_EPSILON_DEGREES ||
            abs(expressions.leftEyeBlink - other.expressions.leftEyeBlink) > EXPRESSION_EPSILON ||
            abs(expressions.rightEyeBlink - other.expressions.rightEyeBlink) > EXPRESSION_EPSILON ||
            abs(expressions.jawOpen - other.expressions.jawOpen) > EXPRESSION_EPSILON ||
            abs(expressions.mouthSmile - other.expressions.mouthSmile) > EXPRESSION_EPSILON

    private fun Float.format(decimalPlaces: Int): String =
        "%1$.${decimalPlaces}f".format(java.util.Locale.US, this)

    private fun AvatarPoseBindingDiagnostic.toLogString(): String =
        "$boneName[m00=${m00.format(2)},m02=${m02.format(2)},m20=${m20.format(2)},m22=${m22.format(2)}]"

    private companion object {
        private const val DEFAULT_CAMERA_DISTANCE = 4.0
        private const val MIN_MODEL_HALF_EXTENT = 0.75f
        private const val MODEL_FIT_DISTANCE_MULTIPLIER = 2.8
        private const val SCENE_LAYER_MASK = 0xff
        private const val SCENE_LAYER_VISIBLE = 0x1
        private const val AVATAR_MOTION_LOG_TAG = "AvatarMotion"
        private const val MOTION_LOG_INTERVAL_MILLIS = 1_000L
        private const val MOTION_EPSILON_DEGREES = 0.1f
        private const val EXPRESSION_EPSILON = 0.01f
    }
}

internal data class AvatarSceneFraming(
    val targetX: Double,
    val targetY: Double,
    val targetZ: Double,
    val cameraDistance: Double,
) {
    companion object {
        val Default = AvatarSceneFraming(
            targetX = 0.0,
            targetY = 0.0,
            targetZ = 0.0,
            cameraDistance = 4.0,
        )
    }
}
