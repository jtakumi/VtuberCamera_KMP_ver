package com.example.vtubercamera_kmp_ver.camera.avatar

import com.example.vtubercamera_kmp_ver.camera.AvatarSelectionData
import org.jetbrains.compose.resources.StringResource

// 現在選択中のアバター情報とファイル選択エラーの表示メッセージを保持する。
data class AvatarSelectionUiState(
    val avatarSelection: AvatarSelectionData? = null,
    val filePickerErrorMessageRes: StringResource? = null,
)
