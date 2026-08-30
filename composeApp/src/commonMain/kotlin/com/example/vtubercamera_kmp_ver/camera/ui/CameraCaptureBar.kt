package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.vtubercamera_kmp_ver.theme.AppColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_capture_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_confirm_message
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_confirm_negative
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_confirm_positive
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_confirm_title
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_switch_button
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_button
import vtubercamera_kmp_ver.composeapp.generated.resources.ic_camera_capture
import vtubercamera_kmp_ver.composeapp.generated.resources.ic_camera_switch
import vtubercamera_kmp_ver.composeapp.generated.resources.ic_photo_delete
import vtubercamera_kmp_ver.composeapp.generated.resources.ic_photo_picker

/**
 * カメラ画面下部の操作バー。ファイル選択・撮影・レンズ切り替えをアイコンで並べ、
 * 撮影済みの写真があるときだけ削除アイコンを追加する。
 *
 * 削除は取り消せないため、押下しても即座には実行せず確認ダイアログを挟む。
 */
@Composable
internal fun CameraCaptureBar(
    onOpenFilePicker: () -> Unit,
    onLensFacingToggle: () -> Unit,
    onCapturePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
    isCapturingPhoto: Boolean,
    canDeletePhoto: Boolean,
    isDeletingPhoto: Boolean,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CAPTURE_BAR_CORNER_RADIUS),
        color = AppColors.CameraOverlayScrim,
    ) {
        Row(
            modifier = Modifier.padding(CAPTURE_BAR_PADDING),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CaptureBarIconButton(
                iconRes = Res.drawable.ic_photo_picker,
                contentDescriptionRes = Res.string.file_picker_open_button,
                onClick = onOpenFilePicker,
            )
            CaptureBarIconButton(
                iconRes = Res.drawable.ic_camera_capture,
                contentDescriptionRes = Res.string.camera_capture_button,
                onClick = onCapturePhoto,
                buttonSize = CAPTURE_SHUTTER_BUTTON_SIZE,
                iconSize = CAPTURE_SHUTTER_ICON_SIZE,
                isEmphasized = true,
                isBusy = isCapturingPhoto,
            )
            CaptureBarIconButton(
                iconRes = Res.drawable.ic_camera_switch,
                contentDescriptionRes = Res.string.camera_switch_button,
                onClick = onLensFacingToggle,
            )
            // 撮影済み画像があるときだけ削除導線を表示する。削除中もボタンを残して進行を示す。
            if (canDeletePhoto || isDeletingPhoto) {
                CaptureBarIconButton(
                    iconRes = Res.drawable.ic_photo_delete,
                    contentDescriptionRes = Res.string.camera_delete_button,
                    onClick = { showDeleteConfirm = true },
                    isEnabled = canDeletePhoto,
                    isBusy = isDeletingPhoto,
                )
            }
        }
    }

    // 削除対象が無くなった場合はダイアログ表示条件からも外し、開いたままにならないようにする。
    if (showDeleteConfirm && canDeletePhoto) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.camera_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.camera_delete_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePhoto()
                    },
                ) {
                    Text(stringResource(Res.string.camera_delete_confirm_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.camera_delete_confirm_negative))
                }
            },
        )
    }
}

/**
 * 操作バーに並べるアイコンボタン。
 *
 * ラベル文字を持たないため、[contentDescriptionRes] を読み上げ用の名前として必ず設定する。
 * [isBusy] のときは進行中インジケーターへ差し替え、二重押下しないよう押下も止める。
 */
@Composable
private fun CaptureBarIconButton(
    iconRes: DrawableResource,
    contentDescriptionRes: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = CAPTURE_BAR_BUTTON_SIZE,
    iconSize: Dp = CAPTURE_BAR_ICON_SIZE,
    isEmphasized: Boolean = false,
    isEnabled: Boolean = true,
    isBusy: Boolean = false,
) {
    val buttonContentDescription = stringResource(contentDescriptionRes)
    val contentColor = if (isEnabled) {
        AppColors.CameraOverlayOnScrim
    } else {
        AppColors.CameraOverlayOnScrimVariant
    }

    Surface(
        modifier = modifier
            .size(buttonSize)
            .clickable(
                enabled = isEnabled && !isBusy,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = buttonContentDescription },
        shape = RoundedCornerShape(CAPTURE_BAR_BUTTON_CORNER_RADIUS),
        color = AppColors.CameraOverlayButton,
        border = if (isEmphasized) {
            BorderStroke(CAPTURE_SHUTTER_BORDER_WIDTH, AppColors.CameraOverlayBorder)
        } else {
            null
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = contentColor,
                )
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    // 読み上げ名は押下領域の Surface 側に付けているため、ここでは重複させない。
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = contentColor,
                )
            }
        }
    }
}

/** 操作バーの高さ。バーへ重ならない位置を決めたい呼び出し側が参照する。 */
internal val CAMERA_CAPTURE_BAR_HEIGHT: Dp get() = CAPTURE_SHUTTER_BUTTON_SIZE + CAPTURE_BAR_PADDING * 2

private val CAPTURE_BAR_CORNER_RADIUS = 28.dp
private val CAPTURE_BAR_PADDING = 12.dp
private val CAPTURE_BAR_BUTTON_SIZE = 64.dp
private val CAPTURE_BAR_BUTTON_CORNER_RADIUS = 20.dp
private val CAPTURE_BAR_ICON_SIZE = 28.dp
private val CAPTURE_SHUTTER_BUTTON_SIZE = 76.dp
private val CAPTURE_SHUTTER_ICON_SIZE = 34.dp
private val CAPTURE_SHUTTER_BORDER_WIDTH = 2.dp
