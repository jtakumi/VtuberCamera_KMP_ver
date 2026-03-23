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
)

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
