package com.example.vtubercamera_kmp_ver

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import com.example.vtubercamera_kmp_ver.camera.AvatarAssetStore
import com.example.vtubercamera_kmp_ver.camera.FilePickerException
import com.example.vtubercamera_kmp_ver.camera.VrmAvatarParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_invalid_format
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_metadata_parse_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_read_failed
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
        val preview = result.preview

        try {
            assertEquals("Sample Avatar", preview.avatarName)
            assertEquals("OpenAI", preview.authorName)
            assertEquals("1.0", preview.vrmVersion)
            assertTrue(preview.thumbnailBytes!!.contentEquals(thumbnailBytes))
            assertEquals(glb.contentHashCode(), result.assetHandle.contentHash)
            assertTrue(assertNotNull(AvatarAssetStore.load(result.assetHandle)).contentEquals(glb))
            assertEquals(VrmSpecVersion.Vrm0, result.runtimeDescriptor.specVersion)
        } finally {
            AvatarAssetStore.remove(result.assetHandle)
        }
    }

    @Test
    fun parseGlbAvatarExtractsMetadataAndThumbnail() {
        val thumbnailBytes = byteArrayOf(5, 6, 7, 8)
        val glb = createGlb(
            json = """
                {
                    "asset": {"version": "2.0"},
                    "extensions": {
                        "VRMC_vrm": {
                            "specVersion": "1.0",
                            "meta": {
                                "name": "Sample GLB Avatar",
                                "authors": ["OpenAI GLB"],
                                "thumbnailImage": 0
                            }
                        }
                    },
                    "images": [{"bufferView": 0, "mimeType": "image/png"}],
                    "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 4}]
                }
            """.trimIndent(),
            binary = thumbnailBytes,
        )

        val result = VrmAvatarParser.parse("sample.glb", glb).getOrThrow()
        val preview = result.preview

        try {
            assertEquals("Sample GLB Avatar", preview.avatarName)
            assertEquals("OpenAI GLB", preview.authorName)
            assertEquals("1.0", preview.vrmVersion)
            assertTrue(preview.thumbnailBytes!!.contentEquals(thumbnailBytes))
            assertEquals(glb.contentHashCode(), result.assetHandle.contentHash)
            assertTrue(assertNotNull(AvatarAssetStore.load(result.assetHandle)).contentEquals(glb))
            assertEquals(VrmSpecVersion.Vrm1, result.runtimeDescriptor.specVersion)
        } finally {
            AvatarAssetStore.remove(result.assetHandle)
        }
    }

    @Test
    fun parseAvatarRejectsNonGlbBasedExtension() {
        val glb = createGlb(
            json = """{"asset":{"version":"2.0"}}""",
            binary = ByteArray(0),
        )

        val exception = VrmAvatarParser.parse("image.png", glb).exceptionOrNull()

        assertNotNull(exception)
        val filePickerException = assertIs<FilePickerException>(exception)
        assertEquals(Res.string.vrm_error_select_file, filePickerException.messageRes)
    }

    @Test
    fun parseAvatarAcceptsUpperCaseExtensionsAndRejectsExtensionlessName() {
        val validGlb = createGlb(
            json = """
                {
                  "asset": {"version": "2.0"},
                  "extensions": {
                    "VRMC_vrm": {
                      "specVersion": "1.0",
                      "meta": {"name": "Uppercase", "authors": ["OpenAI"]}
                    }
                  }
                }
            """.trimIndent(),
            binary = byteArrayOf(),
        )

        val vrmResult = VrmAvatarParser.parse("UPPER.VRM", validGlb).getOrThrow()
        val glbResult = VrmAvatarParser.parse("UPPER.GLB", validGlb).getOrThrow()

        try {
            assertEquals("Uppercase", vrmResult.preview.avatarName)
            assertEquals("Uppercase", glbResult.preview.avatarName)
        } finally {
            AvatarAssetStore.remove(vrmResult.assetHandle)
            AvatarAssetStore.remove(glbResult.assetHandle)
        }

        assertFilePickerErrorRes(
            fileName = "extensionless",
            bytes = validGlb,
            expectedMessageRes = Res.string.vrm_error_select_file,
        )
    }

    @Test
    fun parseAvatarMapsBrokenGlbCasesToPickerErrors() {
        assertFilePickerErrorRes(
            fileName = "broken.vrm",
            bytes = ByteArray(19),
            expectedMessageRes = Res.string.vrm_error_read_failed,
        )
        assertFilePickerErrorRes(
            fileName = "broken.vrm",
            bytes = createGlb(
                json = """{"asset":{"version":"2.0"}}""",
                binary = byteArrayOf(),
                magic = 0x00000000,
            ),
            expectedMessageRes = Res.string.vrm_error_select_file,
        )
        assertFilePickerErrorRes(
            fileName = "broken.vrm",
            bytes = createGlb(
                json = """{"asset":{"version":"2.0"}}""",
                binary = byteArrayOf(),
                declaredLengthOverride = 999_999,
            ),
            expectedMessageRes = Res.string.vrm_error_invalid_format,
        )
        assertFilePickerErrorRes(
            fileName = "broken.vrm",
            bytes = createGlbWithoutJsonChunk(binary = byteArrayOf()),
            expectedMessageRes = Res.string.vrm_error_metadata_parse_failed,
        )
    }

    @Test
    fun parseAvatarTreatsGlbWithoutVrmExtensionAsMetadataFailure() {
        val glbWithoutVrmExtension = createGlb(
            json = """
                {
                  "asset": {"version": "2.0"},
                  "nodes": [{"name": "No VRM Extension"}]
                }
            """.trimIndent(),
            binary = byteArrayOf(),
        )

        assertFilePickerErrorRes(
            fileName = "plain.glb",
            bytes = glbWithoutVrmExtension,
            expectedMessageRes = Res.string.vrm_error_metadata_parse_failed,
        )
    }

    @Test
    fun parseAvatarReturnsNullThumbnailWhenThumbnailSourceIsInvalid() {
        val outOfRangeThumbnail = createGlb(
            json = """
                {
                  "asset": {"version": "2.0"},
                  "extensions": {
                    "VRMC_vrm": {
                      "specVersion": "1.0",
                      "meta": {
                        "name": "Out Of Range Thumbnail",
                        "authors": ["OpenAI"],
                        "thumbnailImage": 99
                      }
                    }
                  },
                  "images": [{"bufferView": 0, "mimeType": "image/png"}],
                  "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 4}]
                }
            """.trimIndent(),
            binary = byteArrayOf(1, 2, 3, 4),
        )
        val nonImageMimeTypeThumbnail = createGlb(
            json = """
                {
                  "asset": {"version": "2.0"},
                  "extensions": {
                    "VRMC_vrm": {
                      "specVersion": "1.0",
                      "meta": {
                        "name": "Non Image Thumbnail",
                        "authors": ["OpenAI"],
                        "thumbnailImage": 0
                      }
                    }
                  },
                  "images": [{"bufferView": 0, "mimeType": "application/octet-stream"}],
                  "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 4}]
                }
            """.trimIndent(),
            binary = byteArrayOf(1, 2, 3, 4),
        )
        val invalidBufferViewThumbnail = createGlb(
            json = """
                {
                  "asset": {"version": "2.0"},
                  "extensions": {
                    "VRMC_vrm": {
                      "specVersion": "1.0",
                      "meta": {
                        "name": "Invalid Buffer View",
                        "authors": ["OpenAI"],
                        "thumbnailImage": 0
                      }
                    }
                  },
                  "images": [{"bufferView": 3, "mimeType": "image/png"}],
                  "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 4}]
                }
            """.trimIndent(),
            binary = byteArrayOf(1, 2, 3, 4),
        )

        val outOfRangeResult = VrmAvatarParser.parse("out_of_range.glb", outOfRangeThumbnail).getOrThrow()
        val nonImageResult = VrmAvatarParser.parse("non_image.glb", nonImageMimeTypeThumbnail).getOrThrow()
        val invalidBufferViewResult =
            VrmAvatarParser.parse("invalid_buffer_view.glb", invalidBufferViewThumbnail).getOrThrow()

        try {
            assertNull(outOfRangeResult.preview.thumbnailBytes)
            assertNull(nonImageResult.preview.thumbnailBytes)
            assertNull(invalidBufferViewResult.preview.thumbnailBytes)
        } finally {
            AvatarAssetStore.remove(outOfRangeResult.assetHandle)
            AvatarAssetStore.remove(nonImageResult.assetHandle)
            AvatarAssetStore.remove(invalidBufferViewResult.assetHandle)
        }
    }

    @Test
    fun parseAvatarAppliesMetadataFallbacks() {
        val fallbackMetadataGlb = createGlb(
            json = """
                {
                  "asset": {"version": "2.9"},
                  "extensions": {
                    "VRMC_vrm": {
                      "specVersion": "1.7",
                      "meta": {
                        "authors": [],
                        "thumbnailImage": 0
                      }
                    }
                  },
                  "images": [{"bufferView": 0, "mimeType": "image/png"}],
                  "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 4}]
                }
            """.trimIndent(),
            binary = byteArrayOf(1, 2, 3, 4),
        )
        val fallbackAssetVersionGlb = createGlb(
            json = """
                {
                  "asset": {"version": "2.5"},
                  "extensions": {
                    "VRM": {
                      "meta": {
                        "title": "VRM 0 Fallback",
                        "author": ""
                      }
                    }
                  }
                }
            """.trimIndent(),
            binary = byteArrayOf(),
        )

        val rawSpecFallbackResult = VrmAvatarParser.parse("fallback_name.glb", fallbackMetadataGlb).getOrThrow()
        val assetVersionFallbackResult = VrmAvatarParser.parse("fallback_asset.vrm", fallbackAssetVersionGlb).getOrThrow()

        try {
            assertEquals("fallback_name", rawSpecFallbackResult.preview.avatarName)
            assertNull(rawSpecFallbackResult.preview.authorName)
            assertEquals("1.7", rawSpecFallbackResult.preview.vrmVersion)

            assertEquals("VRM 0 Fallback", assetVersionFallbackResult.preview.avatarName)
            assertNull(assetVersionFallbackResult.preview.authorName)
            assertEquals("2.5", assetVersionFallbackResult.preview.vrmVersion)
        } finally {
            AvatarAssetStore.remove(rawSpecFallbackResult.assetHandle)
            AvatarAssetStore.remove(assetVersionFallbackResult.assetHandle)
        }
    }

    private fun assertFilePickerErrorRes(
        fileName: String,
        bytes: ByteArray,
        expectedMessageRes: org.jetbrains.compose.resources.StringResource,
    ) {
        val exception = VrmAvatarParser.parse(fileName, bytes).exceptionOrNull()
        assertNotNull(exception)
        val filePickerException = assertIs<FilePickerException>(exception)
        assertEquals(expectedMessageRes, filePickerException.messageRes)
    }

    private fun createGlb(
        json: String,
        binary: ByteArray,
        magic: Int = 0x46546C67,
        declaredLengthOverride: Int? = null,
    ): ByteArray {
        val jsonBytes = json.encodeToByteArray().padTo4Bytes(0x20)
        val binaryBytes = binary.padTo4Bytes(0x00)
        val totalLength = declaredLengthOverride ?: (12 + 8 + jsonBytes.size + 8 + binaryBytes.size)
        return buildList<Byte>() {
            addIntLE(magic)
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

    private fun createGlbWithoutJsonChunk(binary: ByteArray): ByteArray {
        val binaryBytes = binary.padTo4Bytes(0x00)
        val totalLength = 12 + 8 + binaryBytes.size
        return buildList<Byte> {
            addIntLE(0x46546C67)
            addIntLE(2)
            addIntLE(totalLength)
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
