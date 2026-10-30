package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageSettings
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettings
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsActions
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsUiState
import com.example.vtubercamera_kmp_ver.camera.settings.MAX_BGM_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.MAX_MICROPHONE_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.MIN_BGM_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.MIN_MICROPHONE_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia
import com.example.vtubercamera_kmp_ver.theme.LIQUID_GLASS_RIM_WIDTH
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassAppearance
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassCornerStyle
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassStyle
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassSurface
import com.example.vtubercamera_kmp_ver.theme.spacing
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_settings_close
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_settings_title
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_background_image_change
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_background_image_clear
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_background_image_load_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_background_image_none
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_background_image_pick
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_background_image_range_hint
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_change
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_clear
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_none
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_pick
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_play
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_start_with_recording
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_start_with_recording_description
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_stop
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_volume_content_description
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_bgm_volume_label
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_microphone_volume_content_description
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_microphone_volume_label
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_noise_suppression
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_noise_suppression_description
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_section_audio
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_section_background_image
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_section_bgm

/**
 * BGM・録音・背景画像の設定をまとめて操作する設定ダイアログ。
 *
 * 面は [glass] の Liquid Glass で、内容が画面に収まらないときは縦にスクロールする。OS 製のガラスは
 * スクロールする大きな面の背後に敷くと扱いにくいため使わず、Compose で描く。
 * [viewportAspectRatio] は背景画像の範囲を調整する枠の縦横比で、実際に背景を映す画面の縦横比を渡す。
 */
@Composable
internal fun CameraSettingsDialog(
    state: CameraSettingsUiState,
    actions: CameraSettingsActions,
    viewportAspectRatio: Float,
    glass: LiquidGlassAppearance,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LiquidGlassSurface(
            appearance = glass,
            cornerStyle = LiquidGlassCornerStyle.Rounded(SETTINGS_DIALOG_CORNER_RADIUS),
            modifier = Modifier
                .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.xl)
                .fillMaxWidth()
                .widthIn(max = SETTINGS_DIALOG_MAX_WIDTH),
            allowPlatformGlass = false,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(MaterialTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            ) {
                SettingsHeader(glass = glass, onClose = onDismissRequest)
                state.messageRes?.let { messageRes ->
                    SettingsMessage(
                        text = stringResource(messageRes),
                        onDismiss = actions.onDismissMessage,
                    )
                }
                BgmSection(
                    settings = state.settings,
                    isBgmPlaying = state.isBgmPlaying,
                    actions = actions,
                    glass = glass,
                )
                AudioSection(settings = state.settings, actions = actions, glass = glass)
                BackgroundImageSection(
                    backgroundImage = state.settings.backgroundImage,
                    viewportAspectRatio = viewportAspectRatio,
                    actions = actions,
                    glass = glass,
                )
            }
        }
    }
}

// タイトルと閉じるボタンを 1 行に並べる。
@Composable
private fun SettingsHeader(
    glass: LiquidGlassAppearance,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.camera_settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = glass.style.contentColor,
        )
        SettingsButton(
            label = stringResource(Res.string.camera_settings_close),
            onClick = onClose,
            glassStyle = glass.style,
        )
    }
}

// 設定の保存や BGM 再生の失敗を知らせる。タップで閉じる。
@Composable
private fun SettingsMessage(
    text: String,
    onDismiss: () -> Unit,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(vertical = MaterialTheme.spacing.sm),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

// BGM の選択・再生・音量・録画との連動をまとめる。
@Composable
private fun BgmSection(
    settings: CameraSettings,
    isBgmPlaying: Boolean,
    actions: CameraSettingsActions,
    glass: LiquidGlassAppearance,
) {
    val track = settings.bgmTrack

    SettingsSection(title = stringResource(Res.string.settings_section_bgm), glass = glass) {
        MediaSummary(
            media = track,
            emptyText = stringResource(Res.string.settings_bgm_none),
            glass = glass,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            SettingsButton(
                label = stringResource(
                    if (track == null) Res.string.settings_bgm_pick else Res.string.settings_bgm_change,
                ),
                onClick = actions.onPickBgm,
                glassStyle = glass.style,
            )
            if (track != null) {
                SettingsButton(
                    label = stringResource(Res.string.settings_bgm_clear),
                    onClick = actions.onClearBgm,
                    glassStyle = glass.style,
                )
            }
        }
        if (track != null) {
            SettingsButton(
                label = stringResource(
                    if (isBgmPlaying) Res.string.settings_bgm_stop else Res.string.settings_bgm_play,
                ),
                onClick = actions.onToggleBgmPlayback,
                glassStyle = glass.style,
            )
            BgmVolumeSlider(
                volume = settings.bgmVolume,
                onVolumeChanged = actions.onBgmVolumeChanged,
                onVolumeChangeFinished = actions.onBgmVolumeChangeFinished,
                glassStyle = glass.style,
            )
        }
        SettingsToggleRow(
            label = stringResource(Res.string.settings_bgm_start_with_recording),
            description = stringResource(Res.string.settings_bgm_start_with_recording_description),
            checked = settings.isBgmStartedWithRecording,
            onCheckedChange = actions.onToggleBgmStartsWithRecording,
            glassStyle = glass.style,
        )
    }
}

// 録音まわり（雑音抑制）の設定。
@Composable
private fun AudioSection(
    settings: CameraSettings,
    actions: CameraSettingsActions,
    glass: LiquidGlassAppearance,
) {
    SettingsSection(title = stringResource(Res.string.settings_section_audio), glass = glass) {
        if (actions.supportsMicrophoneGain) {
            MicrophoneVolumeSlider(
                volume = settings.microphoneVolume,
                onVolumeChanged = actions.onMicrophoneVolumeChanged,
                onVolumeChangeFinished = actions.onMicrophoneVolumeChangeFinished,
                glassStyle = glass.style,
            )
        }
        SettingsToggleRow(
            label = stringResource(Res.string.settings_noise_suppression),
            description = stringResource(Res.string.settings_noise_suppression_description),
            checked = settings.isNoiseSuppressionEnabled,
            onCheckedChange = actions.onToggleNoiseSuppression,
            glassStyle = glass.style,
        )
    }
}

// 録画に入れるマイク音量。標準を100%として、最大200%まで調整できる。
@Composable
private fun MicrophoneVolumeSlider(
    volume: Float,
    onVolumeChanged: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    glassStyle: LiquidGlassStyle,
) {
    val microphoneVolumeDescription = stringResource(Res.string.settings_microphone_volume_content_description)
    val percent = (volume * PERCENT_SCALE).roundToInt()

    Column {
        Text(
            text = stringResource(Res.string.settings_microphone_volume_label, "$percent%"),
            style = MaterialTheme.typography.bodyMedium,
            color = glassStyle.contentColor,
        )
        Slider(
            value = volume,
            onValueChange = onVolumeChanged,
            onValueChangeFinished = onVolumeChangeFinished,
            valueRange = MIN_MICROPHONE_VOLUME..MAX_MICROPHONE_VOLUME,
            modifier = Modifier.semantics { contentDescription = microphoneVolumeDescription },
            colors = SliderDefaults.colors(
                thumbColor = glassStyle.contentColor,
                activeTrackColor = glassStyle.contentColor,
                inactiveTrackColor = glassStyle.contentVariantColor,
            ),
        )
    }
}

// 背景画像の選択と、映す範囲の調整。
@Composable
private fun BackgroundImageSection(
    backgroundImage: BackgroundImageSettings,
    viewportAspectRatio: Float,
    actions: CameraSettingsActions,
    glass: LiquidGlassAppearance,
) {
    val media = backgroundImage.image

    SettingsSection(title = stringResource(Res.string.settings_section_background_image), glass = glass) {
        MediaSummary(
            media = media,
            emptyText = stringResource(Res.string.settings_background_image_none),
            glass = glass,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            SettingsButton(
                label = stringResource(
                    if (media == null) {
                        Res.string.settings_background_image_pick
                    } else {
                        Res.string.settings_background_image_change
                    },
                ),
                onClick = actions.onPickBackgroundImage,
                glassStyle = glass.style,
            )
            if (media != null) {
                SettingsButton(
                    label = stringResource(Res.string.settings_background_image_clear),
                    onClick = actions.onClearBackgroundImage,
                    glassStyle = glass.style,
                )
            }
        }
        if (media != null) {
            BackgroundImageRangeEditor(
                media = media,
                backgroundImage = backgroundImage,
                viewportAspectRatio = viewportAspectRatio,
                actions = actions,
                glass = glass,
            )
        }
    }
}

// 画像を読み込めたら範囲の調整枠を出し、読み込み中と失敗は枠の代わりに状態を示す。
@Composable
private fun BackgroundImageRangeEditor(
    media: StoredMedia,
    backgroundImage: BackgroundImageSettings,
    viewportAspectRatio: Float,
    actions: CameraSettingsActions,
    glass: LiquidGlassAppearance,
) {
    when (val imageState = rememberStoredImage(media)) {
        StoredImageState.Loading -> CircularProgressIndicator(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            color = glass.style.contentColor,
        )

        StoredImageState.Failed -> Text(
            text = stringResource(Res.string.settings_background_image_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        is StoredImageState.Loaded -> {
            Text(
                text = stringResource(Res.string.settings_background_image_range_hint),
                style = MaterialTheme.typography.bodySmall,
                color = glass.style.contentVariantColor,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                BackgroundImageCropEditor(
                    image = imageState.image,
                    crop = backgroundImage.crop,
                    viewportAspectRatio = viewportAspectRatio,
                    onCropChanged = actions.onBackgroundImageCropChanged,
                    onCropChangeFinished = actions.onBackgroundImageCropChangeFinished,
                    glassStyle = glass.style,
                    modifier = Modifier.widthIn(max = RANGE_EDITOR_MAX_WIDTH),
                )
            }
        }
    }
}

// 見出しと、その下に並べる設定項目の組。
@Composable
private fun SettingsSection(
    title: String,
    glass: LiquidGlassAppearance,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = glass.style.contentColor,
        )
        content()
    }
}

// 取り込み済みメディアの元のファイル名を出す。未設定のときは [emptyText] を出す。
@Composable
private fun MediaSummary(
    media: StoredMedia?,
    emptyText: String,
    glass: LiquidGlassAppearance,
) {
    Text(
        text = media?.displayName ?: emptyText,
        style = MaterialTheme.typography.bodyMedium,
        color = glass.style.contentVariantColor,
        maxLines = 2,
    )
}

// 音量の表示とスライダー。表示は 0〜100 の百分率。
@Composable
private fun BgmVolumeSlider(
    volume: Float,
    onVolumeChanged: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    glassStyle: LiquidGlassStyle,
) {
    val volumeContentDescription = stringResource(Res.string.settings_bgm_volume_content_description)
    val percent = (volume * PERCENT_SCALE).roundToInt()

    Column {
        Text(
            text = stringResource(Res.string.settings_bgm_volume_label, "$percent%"),
            style = MaterialTheme.typography.bodyMedium,
            color = glassStyle.contentColor,
        )
        Slider(
            value = volume,
            onValueChange = onVolumeChanged,
            onValueChangeFinished = onVolumeChangeFinished,
            valueRange = MIN_BGM_VOLUME..MAX_BGM_VOLUME,
            modifier = Modifier.semantics { contentDescription = volumeContentDescription },
            colors = SliderDefaults.colors(
                thumbColor = glassStyle.contentColor,
                activeTrackColor = glassStyle.contentColor,
                inactiveTrackColor = glassStyle.contentVariantColor,
            ),
        )
    }
}

// ラベルと補足を左に、スイッチを右に置く行。行全体をスイッチとして読み上げ、どこを押しても切り替わる。
@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    glassStyle: LiquidGlassStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { onCheckedChange() },
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = glassStyle.contentColor,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = glassStyle.contentVariantColor,
            )
        }
        // 操作は行全体の toggleable が受けるため、スイッチ自身は表示だけにする。
        Switch(checked = checked, onCheckedChange = null)
    }
}

// 設定画面で使う丸型のテキストボタン。ガラス面の内側の層として描く。
@Composable
private fun SettingsButton(
    label: String,
    onClick: () -> Unit,
    glassStyle: LiquidGlassStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = SETTINGS_BUTTON_MIN_HEIGHT)
            .clip(CircleShape)
            .background(color = glassStyle.innerFillColor, shape = CircleShape)
            .border(width = LIQUID_GLASS_RIM_WIDTH, color = glassStyle.innerRimColor, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = glassStyle.contentColor,
            maxLines = 1,
        )
    }
}

private val SETTINGS_DIALOG_CORNER_RADIUS = 28.dp
private val SETTINGS_DIALOG_MAX_WIDTH = 560.dp
private val SETTINGS_BUTTON_MIN_HEIGHT = 44.dp
private val RANGE_EDITOR_MAX_WIDTH = 220.dp
private const val PERCENT_SCALE = 100
