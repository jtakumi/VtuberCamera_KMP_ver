package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView

/**
 * iOS 26 以降で UIGlassEffect のガラスを描けるかを返す。
 *
 * 実際に描けるかは iosApp が登録した provider だけが判断できるため、OS のバージョン判定は
 * iosApp 側へ委ねる。
 */
actual fun isPlatformLiquidGlassAvailable(): Boolean =
    IOSLiquidGlassBackdropHost.isGlassEffectSupported()

/**
 * UIGlassEffect のガラス面を interop view として重ねる。
 *
 * カメラ映像とアバターは platform view として Compose の外で描かれるため、Compose の塗りでは
 * ぼかせない。UIKit の view として重ねることで、OS が背後の映像をぼかして屈折させられる。
 */
@Composable
actual fun PlatformLiquidGlassBackdrop(
    tone: LiquidGlassTone,
    cornerStyle: LiquidGlassCornerStyle,
    modifier: Modifier,
) {
    val tintArgb = liquidGlassPlatformTintArgb(tone)
    // 角丸は面ごとに固定で、tone だけが切り替わる。view の作り直しは角丸が変わったときに限る。
    val backdropView = remember(cornerStyle) {
        IOSLiquidGlassBackdropHost.makeBackdropView(
            tintArgb = tintArgb,
            cornerRadiusPoints = cornerStyle.cornerRadiusPoints(),
            isCapsule = cornerStyle == LiquidGlassCornerStyle.Capsule,
        )
    } ?: return

    UIKitView(
        modifier = modifier,
        factory = { backdropView },
        update = { view ->
            IOSLiquidGlassBackdropHost.updateBackdropView(
                backdropView = view,
                tintArgb = tintArgb,
                durationSeconds = LIQUID_GLASS_TONE_TRANSITION_SECONDS,
            )
        },
    )
}

/**
 * 角の丸め方を UIKit の座標系 (pt) の半径へ変換する。
 *
 * Compose Multiplatform の iOS では 1 dp が 1 pt に対応するため、値をそのまま渡す。
 * [LiquidGlassCornerStyle.Capsule] は高さから半径を決めるため、ここでは半径を持たない。
 */
internal fun LiquidGlassCornerStyle.cornerRadiusPoints(): Double = when (this) {
    LiquidGlassCornerStyle.Capsule -> 0.0
    is LiquidGlassCornerStyle.Rounded -> radius.value.toDouble()
}
