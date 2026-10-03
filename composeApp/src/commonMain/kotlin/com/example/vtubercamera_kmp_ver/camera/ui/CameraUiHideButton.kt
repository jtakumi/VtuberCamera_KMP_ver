package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassAppearance
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassCornerStyle
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassSurface
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_ui_hide_button
import vtubercamera_kmp_ver.composeapp.generated.resources.ic_ui_hide

/**
 * 操作 UI を隠す UI 非表示モードを ON にする丸型ボタン。
 *
 * 押下すると [onClick] を通じて上部バーと操作バーが消える。消えた後の再表示は、画面のタップで行う。
 * ラベル文字を持たないため、読み上げ用の名前をボタンへ必ず設定する。
 * 面は上部バーと同じ [glass] の Liquid Glass で描き、背景プリセットが変わっても判別できるようにする。
 */
@Composable
internal fun CameraUiHideButton(
    onClick: () -> Unit,
    glass: LiquidGlassAppearance,
    modifier: Modifier = Modifier,
) {
    val buttonContentDescription = stringResource(Res.string.camera_ui_hide_button)

    LiquidGlassSurface(
        appearance = glass,
        cornerStyle = LiquidGlassCornerStyle.Capsule,
        modifier = modifier.size(UI_HIDE_BUTTON_SIZE),
    ) {
        Box(
            modifier = Modifier
                // ガラスの落ち影を切り取らないよう、ripple の切り抜きは面の内側の層だけに掛ける。
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { contentDescription = buttonContentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_ui_hide),
                // 読み上げ名は押下領域の Box 側に付けているため、ここでは重複させない。
                contentDescription = null,
                modifier = Modifier.size(UI_HIDE_ICON_SIZE),
                tint = glass.style.contentColor,
            )
        }
    }
}

// 上部バーのチップと同じ、押下しやすい最小サイズ。
private val UI_HIDE_BUTTON_SIZE = 48.dp
private val UI_HIDE_ICON_SIZE = 24.dp
