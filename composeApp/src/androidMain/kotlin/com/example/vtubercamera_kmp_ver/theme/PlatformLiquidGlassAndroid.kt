package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android には OS が提供する Liquid Glass が無いため、常に false を返して Compose 実装の
 * ガラスを使わせる。
 */
actual fun isPlatformLiquidGlassAvailable(): Boolean = false

/**
 * Android では OS 製のガラスが無いので何も描かない。
 *
 * [isPlatformLiquidGlassAvailable] が false を返すため [LiquidGlassSurface] からは呼ばれないが、
 * 呼ばれても layer の重ね順を変えないよう、描画も領域確保も行わない。
 */
@Composable
actual fun PlatformLiquidGlassBackdrop(
    tone: LiquidGlassTone,
    cornerStyle: LiquidGlassCornerStyle,
    modifier: Modifier,
) {
    // 描くものが無い。領域を確保すると Compose 実装のガラスの上へ空の layer が乗るため、
    // レイアウトへも何も足さない。
}
