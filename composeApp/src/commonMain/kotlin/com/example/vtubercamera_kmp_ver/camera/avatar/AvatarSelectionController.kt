package com.example.vtubercamera_kmp_ver.camera.avatar

import com.example.vtubercamera_kmp_ver.camera.AvatarAssetHandle
import com.example.vtubercamera_kmp_ver.camera.AvatarAssetStore
import com.example.vtubercamera_kmp_ver.camera.FilePickerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource

// アバター選択ファイルの状態と AvatarAssetStore 上のリソース寿命を一元管理する。
class AvatarSelectionController {
    private val _state = MutableStateFlow(AvatarSelectionUiState())
    val state: StateFlow<AvatarSelectionUiState> = _state.asStateFlow()

    private var currentAvatarAssetHandle: AvatarAssetHandle? = null

    // ファイルピッカーの結果に応じてアバタープレビューやエラーを更新する。
    fun onFilePicked(result: FilePickerResult) {
        when (result) {
            is FilePickerResult.Success -> {
                currentAvatarAssetHandle?.let(AvatarAssetStore::remove)
                currentAvatarAssetHandle = result.avatarSelection.assetHandle
                _state.update {
                    it.copy(
                        avatarSelection = result.avatarSelection,
                        filePickerErrorMessageRes = null,
                    )
                }
            }
            is FilePickerResult.Error -> _state.update {
                it.copy(filePickerErrorMessageRes = result.messageRes)
            }
            FilePickerResult.Cancelled -> Unit
        }
    }

    // renderer 側の avatar 読み込み失敗を UI エラーへ変換し、現在の選択を解除する。
    fun onAvatarRenderLoadFailed(
        failedAssetHandle: AvatarAssetHandle,
        messageRes: StringResource,
    ) {
        if (currentAvatarAssetHandle != failedAssetHandle) {
            return
        }
        currentAvatarAssetHandle?.let(AvatarAssetStore::remove)
        currentAvatarAssetHandle = null
        _state.update {
            it.copy(
                avatarSelection = null,
                filePickerErrorMessageRes = messageRes,
            )
        }
    }

    // ファイル選択エラー表示を閉じる。
    fun onDismissFilePickerError() {
        _state.update { it.copy(filePickerErrorMessageRes = null) }
    }

    // 現在保持しているアバターアセットを AvatarAssetStore から解放する。
    fun release() {
        currentAvatarAssetHandle?.let(AvatarAssetStore::remove)
        currentAvatarAssetHandle = null
    }
}
