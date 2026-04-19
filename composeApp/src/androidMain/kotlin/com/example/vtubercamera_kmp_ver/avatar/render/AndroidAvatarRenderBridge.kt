package com.example.vtubercamera_kmp_ver.avatar.render

import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.camera.AvatarAssetStore
import com.example.vtubercamera_kmp_ver.camera.AvatarSelectionData
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.FilamentAsset
import kotlin.math.max

internal class AndroidAvatarRenderBridge(
    private val scene: Scene,
    private val assetLoader: AndroidVrmAssetLoader,
    private val resourceCleaner: FilamentResourceCleaner,
    private val onSceneFramingChanged: (AvatarSceneFraming) -> Unit,
) {
    private var currentAsset: FilamentAsset? = null
    private var currentAssetKey: AvatarAssetKey? = null

    @Suppress("UNUSED_PARAMETER")
    fun update(
        avatarSelection: AvatarSelectionData,
        avatarRenderState: AvatarRenderState,
        onAvatarLoadFailure: (AvatarAssetLoadException) -> Unit,
    ) {
        // TODO: Apply avatarRenderState to the loaded model here once expression / pose mapping is
        // wired into the Android runtime renderer.

        val nextAssetKey = AvatarAssetKey(
            assetId = avatarSelection.assetHandle.assetId,
            byteHash = avatarSelection.assetHandle.contentHash,
        )
        if (nextAssetKey == currentAssetKey) {
            return
        }

        val assetBytes = AvatarAssetStore.load(avatarSelection.assetHandle)
            ?: return onAvatarLoadFailure(
                AvatarAssetLoadException(AvatarAssetLoadFailureKind.AssetUnavailable),
            )

        assetLoader.loadAsset(assetBytes)
            .onSuccess { nextAsset ->
                runCatching {
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
                    onAvatarLoadFailure(throwable.toAvatarLoadException(AvatarAssetLoadFailureKind.SceneSetupFailed))
                }
            }
            .onFailure { throwable ->
                onAvatarLoadFailure(throwable.toAvatarLoadException(AvatarAssetLoadFailureKind.ResourceLoadFailed))
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

    private companion object {
        private const val DEFAULT_CAMERA_DISTANCE = 4.0
        private const val MIN_MODEL_HALF_EXTENT = 0.75f
        private const val MODEL_FIT_DISTANCE_MULTIPLIER = 2.8
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
