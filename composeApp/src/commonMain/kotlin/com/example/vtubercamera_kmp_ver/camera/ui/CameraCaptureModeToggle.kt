package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.vtubercamera_kmp_ver.camera.capturemode.CameraCaptureMode
import com.example.vtubercamera_kmp_ver.theme.LIQUID_GLASS_RIM_WIDTH
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassAppearance
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassCornerStyle
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassStyle
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassSurface
import com.example.vtubercamera_kmp_ver.theme.spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_capture_mode_photo
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_capture_mode_toggle_content_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_capture_mode_video

/**
 * 写真と動画の撮影モードを切り替えるトグル。
 *
 * 両モードを並べて現在の選択を強調表示し、押下で [onClick] を通じてもう一方へ切り替える。
 */
@Composable
internal fun CameraCaptureModeToggle(
    captureMode: CameraCaptureMode,
    onClick: () -> Unit,
    glass: LiquidGlassAppearance,
    modifier: Modifier = Modifier,
) {
    val toggleContentDescription = stringResource(
        Res.string.camera_capture_mode_toggle_content_description,
    )
    val selectedLabel = stringResource(captureMode.labelRes)

    LiquidGlassSurface(
        appearance = glass,
        cornerStyle = LiquidGlassCornerStyle.Capsule,
        modifier = modifier
            .height(CAPTURE_MODE_TOGGLE_HEIGHT)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            // 選択中のモードは stateDescription で読み上げ、セグメントの見た目と情報量を揃える。
            .semantics(mergeDescendants = true) {
                contentDescription = toggleContentDescription
                stateDescription = selectedLabel
            },
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(CAPTURE_MODE_SEGMENT_GAP),
            horizontalArrangement = Arrangement.spacedBy(CAPTURE_MODE_SEGMENT_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraCaptureMode.entries.forEach { mode ->
                CaptureModeSegment(
                    mode = mode,
                    isSelected = mode == captureMode,
                    glassStyle = glass.style,
                )
            }
        }
    }
}

/** トグル内の 1 セグメント。選択中はガラス内側の塗りと縁を出して現在地を示す。 */
@Composable
private fun CaptureModeSegment(
    mode: CameraCaptureMode,
    isSelected: Boolean,
    glassStyle: LiquidGlassStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) glassStyle.innerSelectedFillColor else Color.Transparent,
                shape = CircleShape,
            )
            .border(
                width = LIQUID_GLASS_RIM_WIDTH,
                color = if (isSelected) glassStyle.innerRimColor else Color.Transparent,
                shape = CircleShape,
            ),
    ) {
        Text(
            text = stringResource(mode.labelRes),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs,
            ),
            color = if (isSelected) {
                glassStyle.contentColor
            } else {
                glassStyle.contentVariantColor
            },
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** セグメントに表示する撮影モード名。 */
private val CameraCaptureMode.labelRes: StringResource
    get() = when (this) {
        CameraCaptureMode.Photo -> Res.string.camera_capture_mode_photo
        CameraCaptureMode.Video -> Res.string.camera_capture_mode_video
    }

/** トグルの高さ。操作バー全体の高さを決めたい呼び出し側が参照する。 */
internal val CAPTURE_MODE_TOGGLE_HEIGHT: Dp = 48.dp

private val CAPTURE_MODE_SEGMENT_GAP = 4.dp
