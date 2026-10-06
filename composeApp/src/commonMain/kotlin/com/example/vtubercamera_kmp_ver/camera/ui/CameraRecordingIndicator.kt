package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassAppearance
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassCornerStyle
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassSurface
import com.example.vtubercamera_kmp_ver.theme.spacing
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_recording_indicator_label

/**
 * 録画中であることと経過時間を示すインジケーター。
 *
 * 録画中のあいだだけ composition に置く前提で、composition へ入った時点を録画開始として経過時間を数える。
 * 経過時間は短命な UI 表示なので、状態は ViewModel ではなくこの composable の中に持つ。
 */
@Composable
internal fun CameraRecordingIndicator(
    glass: LiquidGlassAppearance,
    modifier: Modifier = Modifier,
) {
    val startMark = remember { TimeSource.Monotonic.markNow() }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    // 秒の切り替わりを取りこぼさないよう、1 秒より細かい間隔で経過時間を測り直す。
    LaunchedEffect(startMark) {
        while (true) {
            elapsedSeconds = startMark.elapsedNow().inWholeSeconds
            delay(RECORDING_TIMER_TICK_MILLIS)
        }
    }

    LiquidGlassSurface(
        appearance = glass,
        cornerStyle = LiquidGlassCornerStyle.Capsule,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 赤い点は装飾で、録画中という情報は隣のテキストが読み上げる。
            Box(
                modifier = Modifier
                    .size(RECORDING_DOT_SIZE)
                    .background(color = CAMERA_RECORDING_ACCENT_COLOR, shape = CircleShape),
            )
            Text(
                text = stringResource(
                    Res.string.camera_recording_indicator_label,
                    elapsedSeconds.toElapsedTimeLabel(),
                ),
                color = glass.style.contentColor,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 経過秒数を "mm:ss" へ変換する。例: 65 は "01:05"、負数は 0 秒として扱う。 */
internal fun Long.toElapsedTimeLabel(): String {
    val totalSeconds = coerceAtLeast(0L)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "${minutes.toString().padStart(TIME_LABEL_DIGITS, '0')}:" +
        seconds.toString().padStart(TIME_LABEL_DIGITS, '0')
}

/** 録画中を示す赤。インジケーターの点と、録画 / 停止ボタンのアイコンで揃える。 */
internal val CAMERA_RECORDING_ACCENT_COLOR = Color(0xFFE53935)

private const val RECORDING_TIMER_TICK_MILLIS = 250L
private const val SECONDS_PER_MINUTE = 60L
private const val TIME_LABEL_DIGITS = 2

private val RECORDING_DOT_SIZE = 10.dp
