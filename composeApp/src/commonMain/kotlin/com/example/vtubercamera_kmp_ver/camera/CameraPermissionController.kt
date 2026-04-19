package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.vrm.VrmRuntimeAssetDescriptor
import org.jetbrains.compose.resources.StringResource

// 最新のカメラ権限状態を保持し、この画面での権限リクエストを仲介する。
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

// 共有 UI から呼び出せるように、プラットフォーム側のファイルピッカー起動処理を公開する。
@Stable
data class FilePickerLauncher(
    val launch: () -> Unit,
)

// ファイル選択と解析が成功したあとに表示するアバターのメタデータ。
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
        val hashMultiplier = 31
        val defaultHashValue = 0
        var result = fileName.hashCode()
        result = hashMultiplier * result + avatarName.hashCode()
        result = hashMultiplier * result + (authorName?.hashCode() ?: defaultHashValue)
        result = hashMultiplier * result + (vrmVersion?.hashCode() ?: defaultHashValue)
        result = hashMultiplier * result + (thumbnailBytes?.contentHashCode() ?: defaultHashValue)
        return result
    }
}

// renderer で利用する選択済みアバターの asset handle と VRM ランタイム情報を保持する。
@Immutable
data class AvatarSelectionData(
    val preview: AvatarPreviewData,
    val assetHandle: AvatarAssetHandle,
    val runtimeDescriptor: VrmRuntimeAssetDescriptor,
)

sealed interface FilePickerResult {
    data class Success(val avatarSelection: AvatarSelectionData) : FilePickerResult
    data class Error(val messageRes: StringResource) : FilePickerResult
    data object Cancelled : FilePickerResult
}

// プラットフォーム側のファイル選択で発生した検証エラーや IO エラーを、共有状態へローカライズ済みで返す。
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
    avatarSelection: AvatarSelectionData,
    avatarRenderState: AvatarRenderState,
    onAvatarRenderLoadFailed: (StringResource) -> Unit,
    modifier: Modifier = Modifier,
)
