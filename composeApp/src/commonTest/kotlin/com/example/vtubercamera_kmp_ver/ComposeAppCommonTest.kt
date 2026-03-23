package com.example.vtubercamera_kmp_ver

import com.example.vtubercamera_kmp_ver.camera.VrmAvatarParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_select_file

class ComposeAppCommonTest {

    @Test
    fun parseVrmAvatarExtractsMetadataAndThumbnail() {
        val thumbnailBytes = byteArrayOf(1, 2, 3, 4)
        val glb = createGlb(
            json = """
                {
                  "asset": {"version": "2.0"},
                  "extensions": {
                    "VRM": {
                      "meta": {
                        "title": "Sample Avatar",
                        "author": "OpenAI",
                        "version": "1.0",
                        "texture": 0
                      }
                    }
                  },
                  "textures": [{"source": 0}],
                  "images": [{"bufferView": 0, "mimeType": "image/png"}],
                  "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 4}]
                }
            """.trimIndent(),
            binary = thumbnailBytes,
        )

        val result = VrmAvatarParser.parse("sample.vrm", glb).getOrThrow()

        assertEquals("Sample Avatar", result.avatarName)
        assertEquals("OpenAI", result.authorName)
        assertEquals("1.0", result.vrmVersion)
        assertTrue(result.thumbnailBytes!!.contentEquals(thumbnailBytes))
    }

    @Test
    fun parseVrmAvatarRejectsNonVrmExtension() {
        val glb = createGlb(
            json = """{"asset":{"version":"2.0"}}""",
            binary = ByteArray(0),
        )

        val exception = VrmAvatarParser.parse("image.png", glb).exceptionOrNull()

        assertNotNull(exception)
        val filePickerException = assertIs<com.example.vtubercamera_kmp_ver.camera.FilePickerException>(exception)
        assertEquals(Res.string.vrm_error_select_file, filePickerException.messageRes)
    }

    private fun createGlb(json: String, binary: ByteArray): ByteArray {
        val jsonBytes = json.encodeToByteArray().padTo4Bytes(0x20)
        val binaryBytes = binary.padTo4Bytes(0x00)
        val totalLength = 12 + 8 + jsonBytes.size + 8 + binaryBytes.size
        return buildList<Byte>() {
            addIntLE(0x46546C67)
            addIntLE(2)
            addIntLE(totalLength)
            addIntLE(jsonBytes.size)
            addIntLE(0x4E4F534A)
            addAll(jsonBytes.toList())
            addIntLE(binaryBytes.size)
            addIntLE(0x004E4942)
            addAll(binaryBytes.toList())
        }.toByteArray()
    }

    private fun ByteArray.padTo4Bytes(paddingByte: Int): ByteArray {
        val padding = (4 - size % 4) % 4
        if (padding == 0) return this
        return this + ByteArray(padding) { paddingByte.toByte() }
    }

    private fun MutableList<Byte>.addIntLE(value: Int) {
        add((value and 0xFF).toByte())
        add(((value ushr 8) and 0xFF).toByte())
        add(((value ushr 16) and 0xFF).toByte())
        add(((value ushr 24) and 0xFF).toByte())
    }
}
