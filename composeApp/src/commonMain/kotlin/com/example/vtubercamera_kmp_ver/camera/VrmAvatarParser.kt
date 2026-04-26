package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmAssetParseException
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmAssetParseFailureKind
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmExtensionParser
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_invalid_format
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_metadata_parse_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_read_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_select_file

object VrmAvatarParser {
    private val supportedExtensions = setOf("vrm", "glb")

    fun parse(fileName: String, bytes: ByteArray): Result<AvatarSelectionData> {
        if (!fileName.hasSupportedExtension()) {
            return Result.failure(FilePickerException(Res.string.vrm_error_select_file))
        }

        val document = VrmExtensionParser.parseDocument(bytes).getOrElse { throwable ->
            return Result.failure(throwable.toFilePickerException())
        }
        val previewDescriptor = VrmExtensionParser.parsePreviewAssetDescriptor(document).getOrElse { throwable ->
            return Result.failure(throwable.toFilePickerException())
        }
        val runtimeDescriptor = VrmExtensionParser.parseRuntimeAssetDescriptor(document).getOrElse { throwable ->
            return Result.failure(throwable.toFilePickerException())
        }

        val avatarName = previewDescriptor.meta.avatarName ?: fileName.substringBeforeLast('.')
        val authorName = previewDescriptor.meta.authors.firstOrNull { it.isNotBlank() }
        val vrmVersion = previewDescriptor.meta.version ?: previewDescriptor.rawSpecVersion ?: previewDescriptor.assetVersion
        val thumbnailBytes = previewDescriptor.thumbnailImageIndex?.let { imageIndex ->
            document.extractImageBytes(imageIndex)
        }

        return Result.success(
            AvatarSelectionData(
                preview = AvatarPreviewData(
                    fileName = fileName,
                    avatarName = avatarName,
                    authorName = authorName,
                    vrmVersion = vrmVersion,
                    thumbnailBytes = thumbnailBytes,
                ),
                assetHandle = AvatarAssetStore.store(bytes),
                runtimeDescriptor = runtimeDescriptor,
            ),
        )
    }

    private fun String.hasSupportedExtension(): Boolean {
        val extension = substringAfterLast('.', missingDelimiterValue = "")
        return extension.lowercase() in supportedExtensions
    }

    private fun Throwable.toFilePickerException(): FilePickerException {
        val messageRes = when (this) {
            is VrmAssetParseException -> when (kind) {
                VrmAssetParseFailureKind.InvalidFileType -> Res.string.vrm_error_select_file
                VrmAssetParseFailureKind.ReadFailed -> Res.string.vrm_error_read_failed
                VrmAssetParseFailureKind.InvalidFormat -> Res.string.vrm_error_invalid_format
                VrmAssetParseFailureKind.MetadataFailed -> Res.string.vrm_error_metadata_parse_failed
            }

            else -> Res.string.vrm_error_read_failed
        }
        return FilePickerException(messageRes)
    }
}
