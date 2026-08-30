package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundMode
import com.example.vtubercamera_kmp_ver.camera.gesture.PinchGestureTarget
import com.example.vtubercamera_kmp_ver.theme.AppColors
import com.example.vtubercamera_kmp_ver.theme.spacing
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_scale_ratio_label
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_black
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_blue
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_camera
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_green
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_white
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_toggle_content_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_zoom_ratio_label
import vtubercamera_kmp_ver.composeapp.generated.resources.pinch_target_avatar_scale
import vtubercamera_kmp_ver.composeapp.generated.resources.pinch_target_camera_zoom
import vtubercamera_kmp_ver.composeapp.generated.resources.pinch_target_toggle_content_description

/**
 * カメラ画面上部の状態バー。左に現在のピンチ対象の倍率、中央にピンチ対象の切り替え、
 * 右に背景プリセットの切り替えを 1 行で並べる。
 *
 * [canTogglePinchTarget] が false のときは中央の切り替えを出さないが、左右は
 * [Arrangement.SpaceBetween] で画面端に固定されるため表示位置は変わらない。
 */
@Composable
internal fun CameraTopBar(
    zoomScale: Float,
    avatarScale: Float,
    pinchTarget: PinchGestureTarget,
    canTogglePinchTarget: Boolean,
    onTogglePinchTarget: () -> Unit,
    backgroundMode: CameraBackgroundMode,
    onToggleBackgroundMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScaleRatioIndicator(
            pinchTarget = pinchTarget,
            zoomScale = zoomScale,
            avatarScale = avatarScale,
        )
        // アバター選択済みのときだけ、ピンチ操作の対象を切り替えられるようにする。
        if (canTogglePinchTarget) {
            PinchTargetToggleChip(
                pinchTarget = pinchTarget,
                onClick = onTogglePinchTarget,
            )
        }
        CameraBackgroundToggleChip(
            backgroundMode = backgroundMode,
            onClick = onToggleBackgroundMode,
        )
    }
}

/**
 * ピンチ操作の対象に対応する倍率を表示するインジケーター。
 *
 * 対象がカメラズームなら [zoomScale]、アバター拡縮なら [avatarScale] を、
 * どちらを見ているか分かるラベル付きで表示する。
 */
@Composable
private fun ScaleRatioIndicator(
    pinchTarget: PinchGestureTarget,
    zoomScale: Float,
    avatarScale: Float,
    modifier: Modifier = Modifier,
) {
    val ratio = when (pinchTarget) {
        PinchGestureTarget.CameraZoom -> zoomScale
        PinchGestureTarget.AvatarScale -> avatarScale
    }

    OverlayChip(modifier = modifier) {
        Text(
            text = stringResource(pinchTarget.ratioLabelRes, ratio.toRatioLabel()),
            color = AppColors.CameraOverlayOnScrim,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * ピンチ操作の対象をカメラズームとアバター拡縮で切り替えるチップ。
 *
 * 両方の対象を並べて現在の選択を強調表示し、押下で [onClick] を通じてもう一方へ切り替える。
 */
@Composable
private fun PinchTargetToggleChip(
    pinchTarget: PinchGestureTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleContentDescription = stringResource(
        Res.string.pinch_target_toggle_content_description,
    )
    val selectedLabel = stringResource(pinchTarget.segmentLabelRes)

    Surface(
        modifier = modifier
            .heightIn(min = OVERLAY_CHIP_MINIMUM_TOUCH_TARGET)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            // 選択中の対象は stateDescription で読み上げ、セグメントの見た目と情報量を揃える。
            .semantics(mergeDescendants = true) {
                contentDescription = toggleContentDescription
                stateDescription = selectedLabel
            },
        shape = CircleShape,
        color = AppColors.CameraOverlayScrim,
    ) {
        Row(
            modifier = Modifier.padding(PINCH_TARGET_SEGMENT_GAP),
            horizontalArrangement = Arrangement.spacedBy(PINCH_TARGET_SEGMENT_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PinchGestureTarget.entries.forEach { target ->
                PinchTargetSegment(
                    target = target,
                    isSelected = target == pinchTarget,
                )
            }
        }
    }
}

/** ピンチ対象チップ内の 1 セグメント。選択中は塗りと文字色を強めて現在地を示す。 */
@Composable
private fun PinchTargetSegment(
    target: PinchGestureTarget,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (isSelected) AppColors.CameraOverlaySelected else Color.Transparent,
    ) {
        Text(
            text = stringResource(target.segmentLabelRes),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.sm,
                vertical = MaterialTheme.spacing.xs,
            ),
            color = if (isSelected) {
                AppColors.CameraOverlayOnScrim
            } else {
                AppColors.CameraOverlayOnScrimVariant
            },
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * カメラ映像を覆う背景プリセットを順番に切り替えるチップ。
 *
 * 表示ラベルは現在の背景モードを示し、押下で [onClick] を通じて次のプリセットへ進む。
 */
@Composable
private fun CameraBackgroundToggleChip(
    backgroundMode: CameraBackgroundMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleContentDescription = stringResource(
        Res.string.camera_background_toggle_content_description,
    )
    val modeLabel = stringResource(backgroundMode.labelRes)

    OverlayChip(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .sizeIn(minWidth = OVERLAY_CHIP_MINIMUM_TOUCH_TARGET)
            .semantics(mergeDescendants = true) {
                contentDescription = toggleContentDescription
                stateDescription = modeLabel
            },
    ) {
        Text(
            text = modeLabel,
            color = AppColors.CameraOverlayOnScrim,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * 上部バーで共通に使う丸型チップの器。
 *
 * カメラ映像や単色背景の上でも読めるよう、テーマ色ではなく固定の暗色 + 白文字で描画する。
 */
@Composable
private fun OverlayChip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.heightIn(min = OVERLAY_CHIP_MINIMUM_TOUCH_TARGET),
        shape = CircleShape,
        color = AppColors.CameraOverlayScrim,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 倍率インジケーターで使う、対象名を含んだ書式ラベル。 */
private val PinchGestureTarget.ratioLabelRes: StringResource
    get() = when (this) {
        PinchGestureTarget.CameraZoom -> Res.string.camera_zoom_ratio_label
        PinchGestureTarget.AvatarScale -> Res.string.avatar_scale_ratio_label
    }

/** ピンチ対象チップのセグメントに表示する対象名。 */
private val PinchGestureTarget.segmentLabelRes: StringResource
    get() = when (this) {
        PinchGestureTarget.CameraZoom -> Res.string.pinch_target_camera_zoom
        PinchGestureTarget.AvatarScale -> Res.string.pinch_target_avatar_scale
    }

/** 背景モードに対応するチップ表示用のラベル。 */
private val CameraBackgroundMode.labelRes: StringResource
    get() = when (this) {
        CameraBackgroundMode.Camera -> Res.string.camera_background_mode_camera
        CameraBackgroundMode.Black -> Res.string.camera_background_mode_black
        CameraBackgroundMode.White -> Res.string.camera_background_mode_white
        CameraBackgroundMode.Green -> Res.string.camera_background_mode_green
        CameraBackgroundMode.Blue -> Res.string.camera_background_mode_blue
    }

/** 倍率を小数第 1 位までの表示文字列へ変換する。例: 1.25f は "1.3x" になる。 */
internal fun Float.toRatioLabel(): String {
    val roundedTenths = (this * RATIO_LABEL_SCALE).roundToInt()
    val whole = roundedTenths / RATIO_LABEL_SCALE
    val decimal = roundedTenths % RATIO_LABEL_SCALE

    return "${whole}.${decimal}x"
}

private const val RATIO_LABEL_SCALE = 10

private val OVERLAY_CHIP_MINIMUM_TOUCH_TARGET = 48.dp
private val PINCH_TARGET_SEGMENT_GAP = 4.dp
