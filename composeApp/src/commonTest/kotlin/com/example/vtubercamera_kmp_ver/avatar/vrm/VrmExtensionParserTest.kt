package com.example.vtubercamera_kmp_ver.avatar.vrm

import com.example.vtubercamera_kmp_ver.avatar.mapping.VrmSpecVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class VrmExtensionParserTest {

    @Test
    fun parseRuntimeAssetDescriptorNormalizesVrm0Extension() {
        val glb = createGlb(
            json = """
                {
                  "asset": {"version": "2.0"},
                  "extensions": {
                    "VRM": {
                      "meta": {
                        "title": "Preview Avatar",
                        "author": "OpenAI",
                        "version": "0.99",
                        "texture": 0
                      },
                      "humanoid": {
                        "humanBones": [
                          {"bone": "head", "node": 3},
                          {"bone": "leftupperarm", "node": 4}
                        ]
                      },
                      "blendShapeMaster": {
                        "blendShapeGroups": [
                          {
                            "name": "Joy",
                            "presetName": "joy",
                            "isBinary": false,
                            "binds": [
                              {"mesh": 5, "index": 1, "weight": 100}
                            ]
                          }
                        ]
                      },
                      "firstPerson": {
                        "firstPersonBone": 3,
                        "lookAtTypeName": "Bone",
                        "meshAnnotations": [
                          {"mesh": 7}
                        ]
                      }
                    }
                  },
                  "textures": [{"source": 0}],
                  "images": [{"bufferView": 0, "mimeType": "image/png"}],
                  "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 4}]
                }
            """.trimIndent(),
            binary = byteArrayOf(1, 2, 3, 4),
        )

        val descriptor = VrmExtensionParser.parseRuntimeAssetDescriptor(glb).getOrThrow()

        assertEquals(VrmSpecVersion.Vrm0, descriptor.specVersion)
        assertEquals("Preview Avatar", descriptor.meta.avatarName)
        assertEquals(listOf("OpenAI"), descriptor.meta.authors)
        assertEquals("0.99", descriptor.meta.version)
        assertEquals(0, descriptor.thumbnailImageIndex)
        assertEquals("head", descriptor.humanoidBones.first().boneName)
        assertEquals("leftUpperArm", descriptor.humanoidBones[1].boneName)
        assertEquals("joy", descriptor.expressions.single().runtimeName)
        assertEquals(1f, descriptor.expressions.single().morphTargetBinds.single().weight)
        assertEquals("Bone", descriptor.lookAt?.type)
        assertEquals(3, descriptor.firstPerson?.firstPersonBone)
        assertEquals(listOf(7), descriptor.firstPerson?.meshAnnotationIndices)
    }

    @Test
    fun parseRuntimeAssetDescriptorNormalizesVrm1Extension() {
        val glb = createGlb(
            json = """
                {
                  "asset": {"version": "2.0"},
                  "extensions": {
                    "VRMC_vrm": {
                      "specVersion": "1.0",
                      "meta": {
                        "name": "Runtime Avatar",
                        "authors": ["OpenAI", "Copilot"],
                        "version": "1.2.3",
                        "thumbnailImage": 1
                      },
                      "humanoid": {
                        "humanBones": {
                          "head": {"node": 8},
                          "neck": {"node": 9}
                        }
                      },
                      "expressions": {
                        "preset": {
                          "happy": {
                            "isBinary": false,
                            "morphTargetBinds": [
                              {"node": 10, "index": 2, "weight": 0.75}
                            ],
                            "overrideBlink": "blend"
                          },
                          "blinkLeft": {
                            "isBinary": true,
                            "morphTargetBinds": [
                              {"node": 11, "index": 3, "weight": 1.0}
                            ]
                          }
                        },
                        "custom": {
                          "surprisedCustom": {
                            "name": "Surprised",
                            "morphTargetBinds": [
                              {"node": 12, "index": 4, "weight": 0.5}
                            ]
                          }
                        }
                      },
                      "lookAt": {
                        "type": "expression",
                        "offsetFromHeadBone": {"x": 0.0, "y": 0.2, "z": 0.1}
                      },
                      "firstPerson": {
                        "meshAnnotations": [
                          {"node": 13}
                        ]
                      }
                    }
                  },
                  "images": [
                    {"bufferView": 0, "mimeType": "image/png"},
                    {"bufferView": 1, "mimeType": "image/png"}
                  ],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 4},
                    {"buffer": 0, "byteOffset": 4, "byteLength": 4}
                  ]
                }
            """.trimIndent(),
            binary = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
        )

        val descriptor = VrmExtensionParser.parseRuntimeAssetDescriptor(glb).getOrThrow()

        assertEquals(VrmSpecVersion.Vrm1, descriptor.specVersion)
        assertEquals("1.0", descriptor.rawSpecVersion)
        assertEquals("Runtime Avatar", descriptor.meta.avatarName)
        assertEquals(listOf("OpenAI", "Copilot"), descriptor.meta.authors)
        assertEquals(1, descriptor.thumbnailImageIndex)
        assertEquals(setOf("happy", "blinkLeft", "surprisedCustom"), descriptor.availableExpressionNames)
        assertEquals("happy", descriptor.expressions.first().runtimeName)
        assertEquals(0.75f, descriptor.expressions.first().morphTargetBinds.single().weight)
        assertEquals("blend", descriptor.expressions.first().overrideBlink)
        assertEquals("expression", descriptor.lookAt?.type)
        assertNotNull(descriptor.lookAt?.offsetFromHeadBone)
        assertEquals(listOf(13), descriptor.firstPerson?.meshAnnotationIndices)
    }

    @Test
    fun parseDocumentReturnsMalformedJsonAsMetadataFailed() {
        val glb = createGlb(
            json = "{ this is not valid json !!!",
            binary = byteArrayOf(),
        )

        val result = VrmExtensionParser.parseDocument(glb)
        val exception = assertFailsWith<VrmAssetParseException> { result.getOrThrow() }
        assertEquals(VrmAssetParseFailureKind.MetadataFailed, exception.kind)
    }

    private fun createGlb(json: String, binary: ByteArray): ByteArray {
        val jsonBytes = json.encodeToByteArray().padTo4Bytes(0x20)
        val binaryBytes = binary.padTo4Bytes(0x00)
        val totalLength = 12 + 8 + jsonBytes.size + 8 + binaryBytes.size
        return buildList<Byte> {
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