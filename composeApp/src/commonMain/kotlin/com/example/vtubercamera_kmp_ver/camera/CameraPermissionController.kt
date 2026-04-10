package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource

// Holds the latest camera permission state and delegates permission requests for the current screen.
@Stable
class CameraPermissionController(
    isGranted: Boolean,
    isChecking: Boolean,
    requestPermissionAction: () -> Unit,
) {
    var isGranted by mutableStateOf(isGranted)
        internal set

    var isChecking by mutableStateOf(isChecking)
        internal set

    private var requestPermissionAction: () -> Unit = requestPermissionAction

    fun requestPermission() {
        requestPermissionAction()
    }

    internal fun updateRequestPermissionAction(requestPermissionAction: () -> Unit) {
        this.requestPermissionAction = requestPermissionAction
    }

    internal fun update(
        isGranted: Boolean = this.isGranted,
        isChecking: Boolean = this.isChecking,
    ) {
        this.isGranted = isGranted
        this.isChecking = isChecking
    }
}

// Exposes a platform file picker trigger to shared UI code.
@Stable
data class FilePickerLauncher(
    val launch: () -> Unit,
)

// Describes the avatar metadata shown after a file has been selected and parsed successfully.
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

// Carries a localized validation or IO error from platform file picking back to shared state.
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
    cameraRepository: CameraRepository,
    lensFacing: CameraLensFacing,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
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
