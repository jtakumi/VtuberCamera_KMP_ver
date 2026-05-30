package com.example.vtubercamera_kmp_ver.camera.avatar

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeMeta
import com.example.vtubercamera_kmp_ver.camera.AvatarAssetStore
import com.example.vtubercamera_kmp_ver.camera.AvatarPreviewData
import com.example.vtubercamera_kmp_ver.camera.AvatarSelectionData
import com.example.vtubercamera_kmp_ver.camera.FilePickerResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_unknown
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_select_file

class AvatarSelectionControllerTest {

    @Test
    fun onFilePicked_success_replacesSelectionAndRemovesPreviousHandle() {
        val controller = AvatarSelectionController()
        val first = createSelection("first.vrm", "Avatar A", byteArrayOf(1, 2, 3))
        val second = createSelection("second.vrm", "Avatar B", byteArrayOf(4, 5, 6))

        try {
            controller.onFilePicked(FilePickerResult.Success(first))
            assertEquals(first, controller.state.value.avatarSelection)
            assertNotNull(AvatarAssetStore.load(first.assetHandle))

            controller.onFilePicked(FilePickerResult.Success(second))
            assertEquals(second, controller.state.value.avatarSelection)
            assertNull(AvatarAssetStore.load(first.assetHandle))
            assertNotNull(AvatarAssetStore.load(second.assetHandle))
        } finally {
            AvatarAssetStore.remove(first.assetHandle)
            AvatarAssetStore.remove(second.assetHandle)
        }
    }

    @Test
    fun onFilePicked_error_setsFilePickerErrorMessageRes() {
        val controller = AvatarSelectionController()

        controller.onFilePicked(FilePickerResult.Error(Res.string.vrm_error_select_file))

        assertEquals(
            Res.string.vrm_error_select_file,
            controller.state.value.filePickerErrorMessageRes,
        )
    }

    @Test
    fun onFilePicked_cancelled_keepsStateUnchanged() {
        val controller = AvatarSelectionController()
        val stateBefore = controller.state.value

        controller.onFilePicked(FilePickerResult.Cancelled)

        assertEquals(stateBefore, controller.state.value)
    }

    @Test
    fun onAvatarRenderLoadFailed_whenHandleMatches_clearsSelectionAndRemovesAsset() {
        val controller = AvatarSelectionController()
        val selection = createSelection("active.vrm", "Avatar C", byteArrayOf(7, 8, 9))

        try {
            controller.onFilePicked(FilePickerResult.Success(selection))
            assertNotNull(AvatarAssetStore.load(selection.assetHandle))

            controller.onAvatarRenderLoadFailed(
                failedAssetHandle = selection.assetHandle,
                messageRes = Res.string.camera_error_unknown,
            )

            assertNull(controller.state.value.avatarSelection)
            assertEquals(
                Res.string.camera_error_unknown,
                controller.state.value.filePickerErrorMessageRes,
            )
            assertNull(AvatarAssetStore.load(selection.assetHandle))
        } finally {
            AvatarAssetStore.remove(selection.assetHandle)
        }
    }

    @Test
    fun onAvatarRenderLoadFailed_whenHandleStale_keepsCurrentSelection() {
        val controller = AvatarSelectionController()
        val stale = createSelection("stale.vrm", "Avatar Stale", byteArrayOf(11, 12))
        val current = createSelection("current.vrm", "Avatar Current", byteArrayOf(13, 14))

        try {
            controller.onFilePicked(FilePickerResult.Success(stale))
            controller.onFilePicked(FilePickerResult.Success(current))
            assertNull(AvatarAssetStore.load(stale.assetHandle))
            assertNotNull(AvatarAssetStore.load(current.assetHandle))

            controller.onAvatarRenderLoadFailed(
                failedAssetHandle = stale.assetHandle,
                messageRes = Res.string.camera_error_unknown,
            )

            assertEquals(current, controller.state.value.avatarSelection)
            assertNull(controller.state.value.filePickerErrorMessageRes)
            assertNotNull(AvatarAssetStore.load(current.assetHandle))
        } finally {
            AvatarAssetStore.remove(stale.assetHandle)
            AvatarAssetStore.remove(current.assetHandle)
        }
    }

    @Test
    fun onDismissFilePickerError_clearsErrorMessage() {
        val controller = AvatarSelectionController()
        controller.onFilePicked(FilePickerResult.Error(Res.string.vrm_error_select_file))

        controller.onDismissFilePickerError()

        assertNull(controller.state.value.filePickerErrorMessageRes)
    }

    @Test
    fun release_removesCurrentAvatarAssetHandle() {
        val controller = AvatarSelectionController()
        val selection = createSelection("active.vrm", "Avatar D", byteArrayOf(21, 22))

        try {
            controller.onFilePicked(FilePickerResult.Success(selection))
            assertNotNull(AvatarAssetStore.load(selection.assetHandle))

            controller.release()

            assertNull(AvatarAssetStore.load(selection.assetHandle))
        } finally {
            AvatarAssetStore.remove(selection.assetHandle)
        }
    }

    private fun createSelection(
        fileName: String,
        avatarName: String,
        assetBytes: ByteArray,
    ): AvatarSelectionData {
        val handle = AvatarAssetStore.store(assetBytes)
        return AvatarSelectionData(
            preview = AvatarPreviewData(
                fileName = fileName,
                avatarName = avatarName,
                authorName = "Unit Test",
                vrmVersion = "1.0",
                thumbnailBytes = byteArrayOf(1, 9, 9, 0),
            ),
            assetHandle = handle,
            runtimeDescriptor = VrmRuntimeAssetDescriptor(
                specVersion = VrmSpecVersion.Vrm1,
                rawSpecVersion = "1.0",
                assetVersion = "2.0",
                meta = VrmRuntimeMeta(
                    avatarName = avatarName,
                    authors = listOf("Unit Test"),
                    version = "1.0",
                ),
                thumbnailImageIndex = null,
            ),
        )
    }
}
