package com.example.vtubercamera_kmp_ver.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AvatarPreviewDataTest {

    @Test
    fun equals_usesThumbnailBufferIdentity() {
        val thumbnail = byteArrayOf(1, 2, 3)
        val preview = createPreview(thumbnail)

        assertEquals(preview, createPreview(thumbnail))
        assertEquals(preview.hashCode(), createPreview(thumbnail).hashCode())
        assertNotEquals(preview, createPreview(byteArrayOf(1, 2, 3)))
    }

    private fun createPreview(thumbnailBytes: ByteArray) = AvatarPreviewData(
        fileName = "avatar.vrm",
        avatarName = "Avatar",
        authorName = "Author",
        vrmVersion = "1.0",
        thumbnailBytes = thumbnailBytes,
    )
}
