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

    fun loadAsset(bytes: ByteArray): FilamentAsset {
        val asset = assetLoader.createAsset(ByteBuffer.wrap(bytes))
            ?: throw IllegalArgumentException(
                "Unable to create a Filament asset from the selected VRM/GLB bytes. " +
                    "The file may be corrupted or use unsupported glTF features. " +
                    "Please try a different VRM/GLB file or verify the asset is valid.",
            )

        return runCatching {
            resourceLoader.loadResources(asset)
            resourceLoader.evictResourceData()
            asset.releaseSourceData()
            asset
        }.getOrElse { throwable ->
            destroyAsset(asset)
            throw throwable
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
