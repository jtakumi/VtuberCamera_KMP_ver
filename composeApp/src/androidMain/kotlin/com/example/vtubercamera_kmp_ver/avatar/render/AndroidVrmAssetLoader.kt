package com.example.vtubercamera_kmp_ver.avatar.render

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer

internal class AndroidVrmAssetLoader(
    engine: Engine,
) {
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)

    fun loadAsset(bytes: ByteArray): Result<FilamentAsset> = runCatching {
        val asset = assetLoader.createAsset(ByteBuffer.wrap(bytes))
            ?: throw AvatarAssetLoadException(
                kind = AvatarAssetLoadFailureKind.InvalidAsset,
                message = "Unable to create a Filament asset from the selected VRM/GLB bytes.",
            )

        try {
            resourceLoader.loadResources(asset)
            resourceLoader.evictResourceData()
            asset.releaseSourceData()
            asset
        } catch (throwable: Throwable) {
            destroyAsset(asset)
            throw AvatarAssetLoadException(
                kind = AvatarAssetLoadFailureKind.ResourceLoadFailed,
                message = "Unable to load Filament resources for the selected VRM/GLB asset.",
                cause = throwable,
            )
        }
    }

    fun destroyAsset(asset: FilamentAsset) {
        assetLoader.destroyAsset(asset)
    }

    fun destroy() {
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroy()
    }
}

internal enum class AvatarAssetLoadFailureKind {
    AssetUnavailable,
    InvalidAsset,
    ResourceLoadFailed,
    SceneSetupFailed,
}

internal class AvatarAssetLoadException(
    val kind: AvatarAssetLoadFailureKind,
    message: String? = null,
    cause: Throwable? = null,
) : IllegalStateException(message ?: cause?.message, cause)
