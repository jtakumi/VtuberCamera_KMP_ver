package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageCrop
import com.example.vtubercamera_kmp_ver.camera.settings.transformedBy
import com.example.vtubercamera_kmp_ver.theme.LIQUID_GLASS_RIM_WIDTH
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassStyle
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_background_image_range_content_description

/**
 * 背景画像のどの範囲を画面へ映すかを、ドラッグとピンチで決める枠。
 *
 * 枠は画面と同じ縦横比 [viewportAspectRatio] で、実際の背景と同じ描き方で [image] を映す。そのため、
 * 枠に見えている範囲がそのまま画面に映る範囲になる。操作中は [onCropChanged] で範囲を通知し、指を離した
 * 時点で [onCropChangeFinished] を呼ぶ。保存のような重い処理は後者でまとめて行う。
 *
 * 枠は縦にスクロールする設定画面の中に置かれるため、枠の上でのドラッグは範囲の移動として消費する。
 */
@Composable
internal fun BackgroundImageCropEditor(
    image: ImageBitmap,
    crop: BackgroundImageCrop,
    viewportAspectRatio: Float,
    onCropChanged: (BackgroundImageCrop) -> Unit,
    onCropChangeFinished: () -> Unit,
    glassStyle: LiquidGlassStyle,
    modifier: Modifier = Modifier,
) {
    val rangeContentDescription = stringResource(
        Res.string.settings_background_image_range_content_description,
    )
    val currentCrop by rememberUpdatedState(crop)
    val currentOnCropChanged by rememberUpdatedState(onCropChanged)
    val currentOnCropChangeFinished by rememberUpdatedState(onCropChangeFinished)
    val imageAspectRatio = image.width.toFloat() / image.height
    val frameShape = RoundedCornerShape(EDITOR_CORNER_RADIUS)

    Box(
        modifier = modifier
            .aspectRatio(viewportAspectRatio)
            .clip(frameShape)
            .border(LIQUID_GLASS_RIM_WIDTH, glassStyle.innerRimColor, frameShape)
            .semantics { contentDescription = rangeContentDescription }
            .pointerInput(imageAspectRatio, viewportAspectRatio) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (size.width > 0 && size.height > 0 && (zoomChange != 1f || pan != Offset.Zero)) {
                            currentOnCropChanged(
                                currentCrop.transformedBy(
                                    panFractionX = pan.x / size.width,
                                    panFractionY = pan.y / size.height,
                                    zoomChange = zoomChange,
                                    imageAspectRatio = imageAspectRatio,
                                    viewportAspectRatio = viewportAspectRatio,
                                ),
                            )
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { change -> change.pressed })
                    currentOnCropChangeFinished()
                }
            },
    ) {
        BackgroundImageCanvas(
            image = image,
            crop = crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

private val EDITOR_CORNER_RADIUS = 12.dp
