package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassAppearance
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassCornerStyle
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassSurface
import com.example.vtubercamera_kmp_ver.theme.spacing
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_video_pause_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_video_paused_label
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_video_resume_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_video_stop_action
import vtubercamera_kmp_ver.composeapp.generated.resources.ic_video_pause
import vtubercamera_kmp_ver.composeapp.generated.resources.ic_video_resume
import vtubercamera_kmp_ver.composeapp.generated.resources.ic_video_stop

/**
 * 録画中に画面最下部へ出す操作バー。一時停止 / 再開と停止のボタンを並べる。
 *
 * バーの高さは撮影ボタンと上下 [RECORDING_CONTROL_BAR_VERTICAL_PADDING] の余白だけで決まる
 * （[RECORDING_CONTROL_BAR_HEIGHT]）。画面録画はこのバーの領域（と OS の操作領域）を録画範囲から除くので、
 * バーは映像へ映り込まない。そのため、録画中でもガラス表現を含めて通常の操作 UI と同じ見た目にできる。
 */
@Composable
internal fun RecordingControlBar(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    glass: LiquidGlassAppearance,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        appearance = glass,
        cornerStyle = LiquidGlassCornerStyle.Rounded(RECORDING_CONTROL_BAR_CORNER_RADIUS),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = RECORDING_CONTROL_BAR_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(
                space = RECORDING_CONTROL_BAR_BUTTON_SPACING,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CaptureBarIconButton(
                iconRes = if (isPaused) Res.drawable.ic_video_resume else Res.drawable.ic_video_pause,
                contentDescriptionRes = if (isPaused) {
                    Res.string.camera_video_resume_button
                } else {
                    Res.string.camera_video_pause_button
                },
                onClick = onTogglePause,
                glassStyle = glass.style,
                buttonSize = CAPTURE_SHUTTER_BUTTON_SIZE,
                iconSize = CAPTURE_SHUTTER_ICON_SIZE,
                isEmphasized = true,
            )
            CaptureBarIconButton(
                iconRes = Res.drawable.ic_video_stop,
                contentDescriptionRes = Res.string.camera_video_stop_action,
                onClick = onStop,
                glassStyle = glass.style,
                buttonSize = CAPTURE_SHUTTER_BUTTON_SIZE,
                iconSize = CAPTURE_SHUTTER_ICON_SIZE,
                isEmphasized = true,
                iconTint = RECORD_ICON_COLOR,
            )
        }
    }
}

/**
 * 一時停止中であることを示す表示。一時停止中の映像は録画に含まれないので、画面上部へ出しても映り込まない。
 * 再開すると呼び出し側が外す。
 */
@Composable
internal fun PausedIndicator(
    glass: LiquidGlassAppearance,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        appearance = glass,
        cornerStyle = LiquidGlassCornerStyle.Capsule,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.camera_video_paused_label),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.lg,
                vertical = MaterialTheme.spacing.sm,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = glass.style.contentColor,
        )
    }
}

/** 録画中の操作バーの上下の余白。バーの高さは撮影ボタンにこの余白を上下へ足したもの。 */
internal val RECORDING_CONTROL_BAR_VERTICAL_PADDING: Dp = 4.dp

/**
 * 録画中の操作バーの高さ。画面録画はこの高さと OS の操作領域を録画範囲から除く。
 */
internal val RECORDING_CONTROL_BAR_HEIGHT: Dp =
    CAPTURE_SHUTTER_BUTTON_SIZE + RECORDING_CONTROL_BAR_VERTICAL_PADDING * 2

private val RECORDING_CONTROL_BAR_CORNER_RADIUS = 28.dp
private val RECORDING_CONTROL_BAR_BUTTON_SPACING = 32.dp
