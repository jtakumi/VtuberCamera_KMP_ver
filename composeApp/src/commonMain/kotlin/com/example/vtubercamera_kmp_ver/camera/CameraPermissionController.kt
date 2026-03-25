package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource

@Immutable
data class CameraPermissionController(
    val isGranted: Boolean,
    val isChecking: Boolean,
    val requestPermission: () -> Unit,
)

@Immutable
data class FilePickerLauncher(
    val launch: () -> Unit,
)

@Immutable
data class AvatarPreviewData(
    val fileName: String,
    val avatarName: String,
    val authorName: String?,
    val vrmVersion: String?,
    val thumbnailBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AvatarPreviewData

        if (fileName != other.fileName) return false
        if (avatarName != other.avatarName) return false
        if (authorName != other.authorName) return false
        if (vrmVersion != other.vrmVersion) return false
        if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        val standardPoint = 31
        val fallbackPoint = 0
        var result = fileName.hashCode()
        result = standardPoint * result + avatarName.hashCode()
        result = standardPoint * result + (authorName?.hashCode() ?: fallbackPoint)
        result = standardPoint * result + (vrmVersion?.hashCode() ?: fallbackPoint)
        result = standardPoint * result + (thumbnailBytes?.contentHashCode() ?: fallbackPoint)
        return result
    }
}

sealed interface FilePickerResult {
    data class Success(val avatarPreview: AvatarPreviewData) : FilePickerResult
    data class Error(val messageRes: StringResource) : FilePickerResult
    data object Cancelled : FilePickerResult
}

class FilePickerException(
    val messageRes: StringResource,
) : IllegalArgumentException()

@Composable
expect fun rememberCameraPermissionController(): CameraPermissionController

@Composable
expect fun rememberFilePickerLauncher(onFilePicked: (FilePickerResult) -> Unit): FilePickerLauncher

@Composable
expect fun CameraPreviewHost(
    modifier: Modifier = Modifier,
    lensFacing: CameraLensFacing,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
)

@Composable
expect fun AvatarPreviewOverlay(
    avatarPreview: AvatarPreviewData,
    modifier: Modifier = Modifier,
)

@Composable
expect fun AvatarBodyOverlay(
    avatarPreview: AvatarPreviewData,
    modifier: Modifier = Modifier,
)
