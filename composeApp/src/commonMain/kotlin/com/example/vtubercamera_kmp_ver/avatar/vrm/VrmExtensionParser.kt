package com.example.vtubercamera_kmp_ver.avatar.vrm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

object VrmExtensionParser {
    private const val glbHeaderMagic = 0x46546C67
    private const val jsonChunkType = 0x4E4F534A
    private const val binaryChunkType = 0x004E4942

    private val json = Json { ignoreUnknownKeys = true }

    fun parseRuntimeAssetDescriptor(bytes: ByteArray): Result<VrmRuntimeAssetDescriptor> =
        parseDocument(bytes).mapCatching { document ->
            parseRuntimeAssetDescriptor(document).getOrThrow()
        }

    internal fun parseDocument(bytes: ByteArray): Result<VrmGlbDocument> = runCatching {
        if (bytes.size < 20) {
            throw VrmAssetParseException(VrmAssetParseFailureKind.ReadFailed)
        }

        val magic = bytes.readIntLE(0)
        if (magic != glbHeaderMagic) {
            throw VrmAssetParseException(VrmAssetParseFailureKind.InvalidFileType)
        }

        val declaredLength = bytes.readIntLE(8)
        if (declaredLength > bytes.size || declaredLength < 20) {
            throw VrmAssetParseException(VrmAssetParseFailureKind.InvalidFormat)
        }

        val declaredLengthLong = declaredLength.toLong()
        var offset = 12L
        var jsonChunk: String? = null
        var binaryChunk: ByteArray? = null
        while (offset <= declaredLengthLong - 8L) {
            val chunkHeaderOffset = offset.toInt()
            val chunkLength = bytes.readIntLE(chunkHeaderOffset)
            val chunkType = bytes.readIntLE(chunkHeaderOffset + 4)
            if (chunkLength < 0) {
                throw VrmAssetParseException(VrmAssetParseFailureKind.InvalidFormat)
            }

            val chunkStart = offset + 8L
            val chunkLengthLong = chunkLength.toLong()
            if (chunkLengthLong > declaredLengthLong - chunkStart) {
                throw VrmAssetParseException(VrmAssetParseFailureKind.InvalidFormat)
            }
            val chunkEnd = chunkStart + chunkLengthLong
            val chunkStartInt = chunkStart.toInt()
            val chunkEndInt = chunkEnd.toInt()

            when (chunkType) {
                jsonChunkType -> jsonChunk = bytes.decodeToString(chunkStartInt, chunkEndInt)
                binaryChunkType -> binaryChunk = bytes.copyOfRange(chunkStartInt, chunkEndInt)
            }
            offset = chunkEnd
        }

        val root = jsonChunk
            ?.let { chunk -> json.parseToJsonElement(chunk) as? JsonObject }
            ?: throw VrmAssetParseException(VrmAssetParseFailureKind.MetadataFailed)

        VrmGlbDocument(
            root = root,
            binaryChunk = binaryChunk,
        )
    }

    internal fun parseRuntimeAssetDescriptor(document: VrmGlbDocument): Result<VrmRuntimeAssetDescriptor> = runCatching {
        val parsedExtension = document.root.parseVrmExtension()
            ?: throw VrmAssetParseException(VrmAssetParseFailureKind.MetadataFailed)

        VrmSpecNormalizer.normalize(
            extension = parsedExtension,
            assetVersion = document.assetVersion,
        )
    }

    private fun JsonObject.parseVrmExtension(): ParsedVrmExtension? {
        val extensions = childObject("extensions") ?: return null
        extensions.childObject("VRMC_vrm")?.let { vrm1Extension ->
            return ParsedVrm1Extension(
                rawSpecVersion = vrm1Extension.string("specVersion"),
                meta = vrm1Extension.childObject("meta").toParsedVrm1Meta(),
                thumbnailImageIndex = vrm1Extension.childObject("meta")?.int("thumbnailImage"),
                humanoidBones = vrm1Extension.parseVrm1HumanoidBones(),
                expressions = vrm1Extension.parseVrm1Expressions(),
                lookAt = vrm1Extension.childObject("lookAt")?.toParsedLookAt(),
                firstPerson = vrm1Extension.childObject("firstPerson")?.toParsedFirstPerson(),
            )
        }

        extensions.childObject("VRM")?.let { vrm0Extension ->
            return ParsedVrm0Extension(
                rawSpecVersion = null,
                meta = vrm0Extension.childObject("meta").toParsedVrm0Meta(),
                thumbnailImageIndex = resolveVrm0ThumbnailImageIndex(vrm0Extension),
                humanoidBones = vrm0Extension.parseVrm0HumanoidBones(),
                expressions = vrm0Extension.parseVrm0Expressions(),
                lookAt = vrm0Extension.childObject("firstPerson")?.toParsedVrm0LookAt(),
                firstPerson = vrm0Extension.childObject("firstPerson")?.toParsedFirstPerson(),
            )
        }

        return null
    }

    private fun JsonObject.resolveVrm0ThumbnailImageIndex(vrm0Extension: JsonObject): Int? {
        val textureIndex = vrm0Extension.childObject("meta")?.int("texture") ?: return null
        return childArray("textures")
            ?.getOrNull(textureIndex)
            ?.jsonObjectOrNull()
            ?.int("source")
    }

    private fun JsonObject?.toParsedVrm0Meta(): ParsedVrmMeta {
        val meta = this
        return ParsedVrmMeta(
            avatarName = meta?.string("title"),
            authors = listOfNotNull(meta?.string("author")),
            version = meta?.string("version"),
        )
    }

    private fun JsonObject?.toParsedVrm1Meta(): ParsedVrmMeta {
        val meta = this
        return ParsedVrmMeta(
            avatarName = meta?.string("name"),
            authors = meta?.stringArray("authors").orEmpty(),
            version = meta?.string("version"),
        )
    }

    private fun JsonObject.parseVrm0HumanoidBones(): List<ParsedHumanoidBone> {
        return childObject("humanoid")
            ?.childArray("humanBones")
            ?.mapNotNull { element ->
                element.jsonObjectOrNull()?.let { boneObject ->
                    val name = boneObject.string("bone") ?: return@let null
                    val nodeIndex = boneObject.int("node") ?: return@let null
                    ParsedHumanoidBone(name = name, nodeIndex = nodeIndex)
                }
            }
            .orEmpty()
    }

    private fun JsonObject.parseVrm1HumanoidBones(): List<ParsedHumanoidBone> {
        val humanBonesObject = childObject("humanoid")?.childObject("humanBones")
        if (humanBonesObject != null) {
            return humanBonesObject.mapNotNull { (name, value) ->
                val nodeIndex = value.jsonObjectOrNull()?.int("node") ?: return@mapNotNull null
                ParsedHumanoidBone(name = name, nodeIndex = nodeIndex)
            }
        }

        return childObject("humanoid")
            ?.childArray("humanBones")
            ?.mapNotNull { element ->
                element.jsonObjectOrNull()?.let { boneObject ->
                    val name = boneObject.string("bone") ?: return@let null
                    val nodeIndex = boneObject.int("node") ?: return@let null
                    ParsedHumanoidBone(name = name, nodeIndex = nodeIndex)
                }
            }
            .orEmpty()
    }

    private fun JsonObject.parseVrm0Expressions(): List<ParsedVrmExpression> {
        return childObject("blendShapeMaster")
            ?.childArray("blendShapeGroups")
            ?.mapNotNull { element ->
                val expressionObject = element.jsonObjectOrNull() ?: return@mapNotNull null
                val presetName = expressionObject.string("presetName")?.normalizeExpressionName()
                val displayName = expressionObject.string("name") ?: presetName ?: return@mapNotNull null
                ParsedVrmExpression(
                    runtimeName = presetName ?: displayName,
                    displayName = displayName,
                    presetName = presetName,
                    isBinary = expressionObject.boolean("isBinary") ?: false,
                    morphTargetBinds = expressionObject.parseMorphTargetBinds(
                        bindsKey = "binds",
                        nodeKey = "mesh",
                        weightMultiplier = 0.01f,
                    ),
                    overrideBlink = null,
                    overrideLookAt = null,
                    overrideMouth = null,
                )
            }
            .orEmpty()
    }

    private fun JsonObject.parseVrm1Expressions(): List<ParsedVrmExpression> {
        val expressionsElement = get("expressions") ?: return emptyList()
        expressionsElement.jsonObjectOrNull()?.let { expressionsObject ->
            val presetExpressions = expressionsObject.childObject("preset")
                ?.entries
                ?.mapNotNull { (name, value) ->
                    value.jsonObjectOrNull()?.toParsedVrm1Expression(
                        expressionKey = name,
                        presetName = name.normalizeExpressionName(),
                    )
                }
                .orEmpty()
            val customExpressions = expressionsObject.childObject("custom")
                ?.entries
                ?.mapNotNull { (name, value) ->
                    value.jsonObjectOrNull()?.toParsedVrm1Expression(
                        expressionKey = name,
                        presetName = null,
                    )
                }
                .orEmpty()
            if (presetExpressions.isNotEmpty() || customExpressions.isNotEmpty()) {
                return presetExpressions + customExpressions
            }
        }

        return expressionsElement.jsonArrayOrNull()
            ?.mapNotNull { element ->
                val expressionObject = element.jsonObjectOrNull() ?: return@mapNotNull null
                val expressionKey = expressionObject.string("name") ?: return@mapNotNull null
                expressionObject.toParsedVrm1Expression(
                    expressionKey = expressionKey,
                    presetName = expressionObject.string("preset")?.normalizeExpressionName(),
                )
            }
            .orEmpty()
    }

    private fun JsonObject.toParsedVrm1Expression(
        expressionKey: String,
        presetName: String?,
    ): ParsedVrmExpression {
        val normalizedKey = expressionKey.normalizeExpressionName()
        val displayName = string("name") ?: normalizedKey
        return ParsedVrmExpression(
            runtimeName = presetName ?: normalizedKey,
            displayName = displayName,
            presetName = presetName,
            isBinary = boolean("isBinary") ?: false,
            morphTargetBinds = parseMorphTargetBinds(
                bindsKey = "morphTargetBinds",
                nodeKey = "node",
                weightMultiplier = 1f,
            ),
            overrideBlink = string("overrideBlink"),
            overrideLookAt = string("overrideLookAt"),
            overrideMouth = string("overrideMouth"),
        )
    }

    private fun JsonObject.parseMorphTargetBinds(
        bindsKey: String,
        nodeKey: String,
        weightMultiplier: Float,
    ): List<ParsedMorphTargetBind> {
        return childArray(bindsKey)
            ?.mapNotNull { element ->
                val bindObject = element.jsonObjectOrNull() ?: return@mapNotNull null
                val nodeIndex = bindObject.int(nodeKey) ?: return@mapNotNull null
                val morphTargetIndex = bindObject.int("index") ?: return@mapNotNull null
                val rawWeight = bindObject.float("weight") ?: return@mapNotNull null
                ParsedMorphTargetBind(
                    nodeIndex = nodeIndex,
                    morphTargetIndex = morphTargetIndex,
                    weight = rawWeight * weightMultiplier,
                )
            }
            .orEmpty()
    }

    private fun JsonObject.toParsedVrm0LookAt(): ParsedLookAt? {
        val lookAtType = string("lookAtTypeName")
        val offsetFromHeadBone = childObject("firstPersonBoneOffset")?.toParsedFloat3()
        if (lookAtType == null && offsetFromHeadBone == null) return null
        return ParsedLookAt(
            type = lookAtType,
            offsetFromHeadBone = offsetFromHeadBone,
        )
    }

    private fun JsonObject.toParsedLookAt(): ParsedLookAt? {
        val type = string("type")
        val offsetFromHeadBone = childObject("offsetFromHeadBone")?.toParsedFloat3()
            ?: get("offsetFromHeadBone")?.jsonArrayOrNull()?.toParsedFloat3()
        if (type == null && offsetFromHeadBone == null) return null
        return ParsedLookAt(
            type = type,
            offsetFromHeadBone = offsetFromHeadBone,
        )
    }

    private fun JsonObject.toParsedFirstPerson(): ParsedFirstPerson? {
        val firstPersonBone = int("firstPersonBone")
        val meshAnnotationIndices = childArray("meshAnnotations")
            ?.mapNotNull { element ->
                val annotationObject = element.jsonObjectOrNull() ?: return@mapNotNull null
                annotationObject.int("node") ?: annotationObject.int("mesh")
            }
            .orEmpty()
        if (firstPersonBone == null && meshAnnotationIndices.isEmpty()) return null
        return ParsedFirstPerson(
            firstPersonBone = firstPersonBone,
            meshAnnotationIndices = meshAnnotationIndices,
        )
    }

    private fun JsonObject.toParsedFloat3(): ParsedFloat3? {
        val x = float("x") ?: return null
        val y = float("y") ?: return null
        val z = float("z") ?: return null
        return ParsedFloat3(x = x, y = y, z = z)
    }

    private fun JsonArray.toParsedFloat3(): ParsedFloat3? {
        if (size < 3) return null
        val x = getOrNull(0)?.jsonPrimitiveOrNull()?.floatOrNull ?: return null
        val y = getOrNull(1)?.jsonPrimitiveOrNull()?.floatOrNull ?: return null
        val z = getOrNull(2)?.jsonPrimitiveOrNull()?.floatOrNull ?: return null
        return ParsedFloat3(x = x, y = y, z = z)
    }

    private fun String.normalizeExpressionName(): String {
        val trimmedName = trim()
        if (trimmedName.isEmpty()) return trimmedName
        return expressionAliases[trimmedName.lowercase()] ?: trimmedName
    }

    private val expressionAliases: Map<String, String> = mapOf(
        "blinkleft" to "blinkLeft",
        "blinkright" to "blinkRight",
        "blink_l" to "blink_l",
        "blink_r" to "blink_r",
        "happy" to "happy",
        "joy" to "joy",
        "smile" to "smile",
        "a" to "a",
        "aa" to "aa",
        "jawopen" to "jawOpen",
    )
}

internal data class VrmGlbDocument(
    val root: JsonObject,
    val binaryChunk: ByteArray?,
) {
    val assetVersion: String?
        get() = root.childObject("asset")?.string("version")

    fun extractImageBytes(imageIndex: Int): ByteArray? {
        val image = root.childArray("images")
            ?.getOrNull(imageIndex)
            ?.jsonObjectOrNull()
            ?: return null
        val bufferViewIndex = image.int("bufferView") ?: return null
        val mimeType = image.string("mimeType") ?: return null
        if (!mimeType.startsWith("image/")) return null

        val bufferView = root.childArray("bufferViews")
            ?.getOrNull(bufferViewIndex)
            ?.jsonObjectOrNull()
            ?: return null
        val byteOffset = bufferView.int("byteOffset") ?: 0
        val byteLength = bufferView.int("byteLength") ?: return null
        val bytes = binaryChunk ?: return null
        if (byteOffset < 0 || byteLength < 0) return null

        val startLong = byteOffset.toLong()
        val lengthLong = byteLength.toLong()
        val endLong = startLong + lengthLong
        if (endLong > bytes.size.toLong()) return null

        val start = startLong.toInt()
        val end = endLong.toInt()
        return bytes.copyOfRange(start, end)
    }
}

internal enum class VrmAssetParseFailureKind {
    InvalidFileType,
    ReadFailed,
    InvalidFormat,
    MetadataFailed,
}

internal class VrmAssetParseException(
    val kind: VrmAssetParseFailureKind,
) : IllegalArgumentException()

internal sealed interface ParsedVrmExtension {
    val rawSpecVersion: String?
    val meta: ParsedVrmMeta
    val thumbnailImageIndex: Int?
    val humanoidBones: List<ParsedHumanoidBone>
    val expressions: List<ParsedVrmExpression>
    val lookAt: ParsedLookAt?
    val firstPerson: ParsedFirstPerson?
}

internal data class ParsedVrm0Extension(
    override val rawSpecVersion: String?,
    override val meta: ParsedVrmMeta,
    override val thumbnailImageIndex: Int?,
    override val humanoidBones: List<ParsedHumanoidBone>,
    override val expressions: List<ParsedVrmExpression>,
    override val lookAt: ParsedLookAt?,
    override val firstPerson: ParsedFirstPerson?,
) : ParsedVrmExtension

internal data class ParsedVrm1Extension(
    override val rawSpecVersion: String?,
    override val meta: ParsedVrmMeta,
    override val thumbnailImageIndex: Int?,
    override val humanoidBones: List<ParsedHumanoidBone>,
    override val expressions: List<ParsedVrmExpression>,
    override val lookAt: ParsedLookAt?,
    override val firstPerson: ParsedFirstPerson?,
) : ParsedVrmExtension

internal data class ParsedVrmMeta(
    val avatarName: String?,
    val authors: List<String>,
    val version: String?,
)

internal data class ParsedHumanoidBone(
    val name: String,
    val nodeIndex: Int,
)

internal data class ParsedVrmExpression(
    val runtimeName: String,
    val displayName: String,
    val presetName: String?,
    val isBinary: Boolean,
    val morphTargetBinds: List<ParsedMorphTargetBind>,
    val overrideBlink: String?,
    val overrideLookAt: String?,
    val overrideMouth: String?,
)

internal data class ParsedMorphTargetBind(
    val nodeIndex: Int,
    val morphTargetIndex: Int,
    val weight: Float,
)

internal data class ParsedLookAt(
    val type: String?,
    val offsetFromHeadBone: ParsedFloat3?,
)

internal data class ParsedFirstPerson(
    val firstPersonBone: Int?,
    val meshAnnotationIndices: List<Int>,
)

internal data class ParsedFloat3(
    val x: Float,
    val y: Float,
    val z: Float,
)

internal fun JsonObject.childObject(key: String): JsonObject? = get(key)?.jsonObjectOrNull()

internal fun JsonObject.childArray(key: String): JsonArray? = get(key)?.jsonArrayOrNull()

internal fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitiveOrNull()?.contentOrNull?.takeIf { value -> value.isNotBlank() }

internal fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitiveOrNull()?.intOrNull

internal fun JsonObject.float(key: String): Float? = get(key)?.jsonPrimitiveOrNull()?.floatOrNull

internal fun JsonObject.boolean(key: String): Boolean? = get(key)?.jsonPrimitiveOrNull()?.booleanOrNull

internal fun JsonObject.stringArray(key: String): List<String>? {
    return childArray(key)
        ?.mapNotNull { element ->
            element.jsonPrimitiveOrNull()?.contentOrNull?.takeIf { value -> value.isNotBlank() }
        }
        ?.takeIf { values -> values.isNotEmpty() }
}

internal fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

private fun ByteArray.readIntLE(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}