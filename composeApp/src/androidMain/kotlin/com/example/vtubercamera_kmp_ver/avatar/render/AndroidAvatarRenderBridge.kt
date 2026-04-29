package com.example.vtubercamera_kmp_ver.avatar.render

import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.tracking.AndroidFaceTrackingToAvatarMapper
import com.example.vtubercamera_kmp_ver.camera.AvatarAssetStore
import com.example.vtubercamera_kmp_ver.camera.AvatarSelectionData
import com.google.android.filament.Engine
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.FilamentAsset
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

    fun prepareFrame() {
        currentAsset?.instance?.animator?.updateBoneMatrices()
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(
        avatarSelection: AvatarSelectionData,
        avatarRenderState: AvatarRenderState,
        onAvatarLoadFailure: (AvatarAssetLoadException) -> Unit,
    ) {
        onRenderStateChanged(renderStateMapper.map(avatarRenderState))

        val nextAssetKey = AvatarAssetKey(
            assetId = avatarSelection.assetHandle.assetId,
            byteHash = avatarSelection.assetHandle.contentHash,
        )
        if (nextAssetKey == currentAssetKey) {
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
                    scene.addEntities(nextAsset.entities)
                    onSceneFramingChanged(nextAsset.toSceneFraming())
                }.onSuccess {
                    val previousAsset = currentAsset
                    currentAsset = nextAsset
                    currentAssetKey = nextAssetKey
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
    }

    private companion object {
        private const val DEFAULT_CAMERA_DISTANCE = 4.0
        private const val MIN_MODEL_HALF_EXTENT = 0.75f
        private const val MODEL_FIT_DISTANCE_MULTIPLIER = 2.8
        private const val SCENE_LAYER_MASK = 0xff
        private const val SCENE_LAYER_VISIBLE = 0x1
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
