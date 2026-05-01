package com.example.vtubercamera_kmp_ver.avatar.render

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal class AndroidVrmAssetLoader(
    engine: Engine,
) {
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)

    fun loadAsset(bytes: ByteArray): Result<FilamentAsset> = runCatching {
        val renderBytes = bytes.normalizeJsonEscapedSlashesForGltfio()
        val asset = assetLoader.createAsset(ByteBuffer.wrap(renderBytes))
            ?: throw AvatarAssetLoadException(
                kind = AvatarAssetLoadFailureKind.InvalidAsset,
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

    private fun ByteArray.normalizeJsonEscapedSlashesForGltfio(): ByteArray {
        if (size < GLB_MIN_SIZE || readIntLE(GLB_MAGIC_OFFSET) != GLB_MAGIC) {
            return this
        }

        val declaredLength = readIntLE(GLB_LENGTH_OFFSET)
        if (declaredLength > size || declaredLength < GLB_MIN_SIZE) {
            return this
        }

        val chunks = mutableListOf<GlbChunk>()
        var offset = GLB_HEADER_SIZE
        var changed = false
        while (offset <= declaredLength - GLB_CHUNK_HEADER_SIZE) {
            val chunkLength = readIntLE(offset)
            val chunkType = readIntLE(offset + GLB_CHUNK_TYPE_OFFSET)
            if (chunkLength < 0 || chunkLength > declaredLength - offset - GLB_CHUNK_HEADER_SIZE) {
                return this
            }

            val chunkStart = offset + GLB_CHUNK_HEADER_SIZE
            val chunkEnd = chunkStart + chunkLength
            val payload = copyOfRange(chunkStart, chunkEnd)
            val nextPayload = if (chunkType == GLB_JSON_CHUNK_TYPE) {
                val json = payload.decodeToString().trimEnd(' ', '\u0000', '\t', '\r', '\n')
                val normalizedJson = json.replace("\\/", "/")
                if (normalizedJson != json) {
                    changed = true
                    normalizedJson.encodeToByteArray().padGlbChunk()
                } else {
                    payload
                }
            } else {
                payload
            }
            chunks += GlbChunk(type = chunkType, payload = nextPayload)
            offset = chunkEnd
        }

        if (!changed) {
            return this
        }

        val nextLength = GLB_HEADER_SIZE + chunks.sumOf { GLB_CHUNK_HEADER_SIZE + it.payload.size }
        return ByteArrayOutputStream(nextLength).use { output ->
            output.writeIntLE(GLB_MAGIC)
            output.writeIntLE(GLB_VERSION)
            output.writeIntLE(nextLength)
            chunks.forEach { chunk ->
                output.writeIntLE(chunk.payload.size)
                output.writeIntLE(chunk.type)
                output.write(chunk.payload)
            }
            output.toByteArray()
        }
    }

    private fun ByteArray.padGlbChunk(): ByteArray {
        val padding = (GLB_ALIGNMENT - (size % GLB_ALIGNMENT)) % GLB_ALIGNMENT
        if (padding == 0) {
            return this
        }
        return copyOf(size + padding).also { padded ->
            for (index in size until padded.size) {
                padded[index] = GLB_JSON_PADDING_BYTE
            }
        }
    }

    private fun ByteArray.readIntLE(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private data class GlbChunk(
        val type: Int,
        val payload: ByteArray,
    )

    private companion object {
        private const val GLB_MAGIC = 0x46546C67
        private const val GLB_VERSION = 2
        private const val GLB_JSON_CHUNK_TYPE = 0x4E4F534A
        private const val GLB_MAGIC_OFFSET = 0
        private const val GLB_LENGTH_OFFSET = 8
        private const val GLB_HEADER_SIZE = 12
        private const val GLB_CHUNK_HEADER_SIZE = 8
        private const val GLB_CHUNK_TYPE_OFFSET = 4
        private const val GLB_MIN_SIZE = GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE
        private const val GLB_ALIGNMENT = 4
        private const val GLB_JSON_PADDING_BYTE = 0x20.toByte()
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
    cause: Throwable? = null,
) : IllegalStateException(null, cause)
