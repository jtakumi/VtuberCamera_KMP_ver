package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable
import com.example.vtubercamera_kmp_ver.camera.settings.MediaPickResult
import com.example.vtubercamera_kmp_ver.camera.settings.PickedMediaKind

/**
 * [kind] のメディアを選び、アプリ専用領域へ取り込むピッカーの起動処理を生成する。
 *
 * 選択と取り込みの結果は [onPicked] で受け取る。取り込みに失敗したときは、理由を [MediaPickResult.Error] で返す。
 */
@Composable
expect fun rememberMediaPickerLauncher(
    kind: PickedMediaKind,
    onPicked: (MediaPickResult) -> Unit,
): FilePickerLauncher
