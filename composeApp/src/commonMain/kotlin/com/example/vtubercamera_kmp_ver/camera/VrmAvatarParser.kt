package com.example.vtubercamera_kmp_ver.camera

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

object VrmAvatarParser {
    private const val glbHeaderMagic = 0x46546C67
    private const val jsonChunkType = 0x4E4F534A
    private const val binaryChunkType = 0x004E4942

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(fileName: String, bytes: ByteArray): Result<AvatarPreviewData> {
        if (!fileName.lowercase().endsWith(".vrm")) {
            return Result.failure(IllegalArgumentException("VRMファイルを選択してください。"))
        }
        if (bytes.size < 20) {
            return Result.failure(IllegalArgumentException("VRMファイルの読み込みに失敗しました。"))
        }

        val magic = bytes.readIntLE(0)
        if (magic != glbHeaderMagic) {
            return Result.failure(IllegalArgumentException("VRMファイルを選択してください。"))
        }

        val declaredLength = bytes.readIntLE(8)
        if (declaredLength > bytes.size || declaredLength < 20) {
            return Result.failure(IllegalArgumentException("VRMファイルの形式が不正です。"))
        }

        var offset = 12
        var jsonChunk: String? = null
        var binaryChunk: ByteArray? = null
        while (offset + 8 <= declaredLength) {
            val chunkLength = bytes.readIntLE(offset)
            val chunkType = bytes.readIntLE(offset + 4)
            val chunkStart = offset + 8
            val chunkEnd = chunkStart + chunkLength
            if (chunkLength < 0 || chunkEnd > declaredLength) {
                return Result.failure(IllegalArgumentException("VRMファイルの形式が不正です。"))
            }
            when (chunkType) {
                jsonChunkType -> jsonChunk = bytes.decodeToString(chunkStart, chunkEnd)
                binaryChunkType -> binaryChunk = bytes.copyOfRange(chunkStart, chunkEnd)
            }
            offset = chunkEnd
        }

        val root = jsonChunk
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: return Result.failure(IllegalArgumentException("VRMメタデータの解析に失敗しました。"))

        val meta = root.vrm0Meta() ?: root.vrm1Meta()
        val avatarName = meta?.string("title")
            ?: meta?.string("name")
            ?: fileName.substringBeforeLast('.')
        val authorName = meta?.string("author") ?: meta?.stringArray("authors")?.firstOrNull()
        val vrmVersion = meta?.string("version")
            ?: root.jsonObject("extensions")?.jsonObject("VRMC_vrm")?.string("specVersion")
            ?: root.jsonObject("asset")?.string("version")

        val thumbnailIndex = root.resolveThumbnailImageIndex()
        val thumbnailBytes = if (thumbnailIndex != null && binaryChunk != null) {
            root.extractImageBytes(thumbnailIndex, binaryChunk)
        } else {
            null
        }

        return Result.success(
            AvatarPreviewData(
                fileName = fileName,
                avatarName = avatarName,
                authorName = authorName,
                vrmVersion = vrmVersion,
                thumbnailBytes = thumbnailBytes,
            ),
        )
    }

    private fun JsonObject.vrm0Meta(): JsonObject? =
        jsonObject("extensions")?.jsonObject("VRM")?.jsonObject("meta")

    private fun JsonObject.vrm1Meta(): JsonObject? =
        jsonObject("extensions")?.jsonObject("VRMC_vrm")?.jsonObject("meta")

    private fun JsonObject.resolveThumbnailImageIndex(): Int? {
        val vrm0Meta = vrm0Meta()
        val vrm0TextureIndex = vrm0Meta?.int("texture")
        if (vrm0TextureIndex != null) {
            val imageIndex = jsonArray("textures")
                ?.getOrNull(vrm0TextureIndex)
                ?.jsonObjectOrNull()
                ?.int("source")
            if (imageIndex != null) return imageIndex
        }

        return vrm1Meta()?.int("thumbnailImage")
    }

    private fun JsonObject.extractImageBytes(imageIndex: Int, binaryChunk: ByteArray): ByteArray? {
        val image = jsonArray("images")
            ?.getOrNull(imageIndex)
            ?.jsonObjectOrNull()
            ?: return null
        val bufferViewIndex = image.int("bufferView") ?: return null
        val mimeType = image.string("mimeType") ?: return null
        if (!mimeType.startsWith("image/")) return null

        val bufferView = jsonArray("bufferViews")
            ?.getOrNull(bufferViewIndex)
            ?.jsonObjectOrNull()
            ?: return null
        val byteOffset = bufferView.int("byteOffset") ?: 0
        val byteLength = bufferView.int("byteLength") ?: return null
        val end = byteOffset + byteLength
        if (byteOffset < 0 || end > binaryChunk.size) return null
        return binaryChunk.copyOfRange(byteOffset, end)
    }

    private fun JsonObject.jsonObject(key: String): JsonObject? = get(key)?.jsonObjectOrNull()

    private fun JsonObject.jsonArray(key: String): JsonArray? = get(key)?.jsonArrayOrNull()

    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitiveOrNull()?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitiveOrNull()?.intOrNull

    private fun JsonObject.stringArray(key: String): List<String>? {
        return jsonArray(key)
            ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull?.takeIf { value -> value.isNotBlank() } }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun ByteArray.readIntLE(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }
}
