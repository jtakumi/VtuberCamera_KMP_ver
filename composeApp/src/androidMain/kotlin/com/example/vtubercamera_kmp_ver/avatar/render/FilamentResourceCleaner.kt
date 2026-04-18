package com.example.vtubercamera_kmp_ver.avatar.render

import com.google.android.filament.Scene
import com.google.android.filament.gltfio.FilamentAsset

internal class FilamentResourceCleaner {
    fun destroyAsset(
        scene: Scene,
        assetLoader: AndroidVrmAssetLoader,
        asset: FilamentAsset?,
    ) {
        asset ?: return
        scene.removeEntities(asset.entities)
        assetLoader.destroyAsset(asset)
    }
}
