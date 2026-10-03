package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb

/**
 * OS が提供する Liquid Glass（iOS 26 の UIGlassEffect など）を使えるかを返す。
 *
 * false のときは [LiquidGlassSurface] が Compose だけでガラスを近似する。OS 版は背後の映像を
 * ぼかして屈折させられるが、Compose 版は platform view として Compose の外で描かれるカメラ映像を
 * ぼかせないため、見た目は完全には一致しない。
 */
expect fun isPlatformLiquidGlassAvailable(): Boolean

/**
 * OS 製のガラス面を、呼び出し側が [modifier] で与えた領域いっぱいに描く。
 *
 * [tone] は背後の明るさに対応する tint の選択に、[cornerStyle] は面の角丸に使う。
 * [isPlatformLiquidGlassAvailable] が false のプラットフォームでは何も描かない。
 */
@Composable
expect fun PlatformLiquidGlassBackdrop(
    tone: LiquidGlassTone,
    cornerStyle: LiquidGlassCornerStyle,
    modifier: Modifier = Modifier,
)

/**
 * OS 製のガラス面へ渡す tint を、ARGB を 1 つの整数へ並べた形で返す。
 *
 * OS 側の API は Compose の [androidx.compose.ui.graphics.Color] を解釈できないため、色は
 * 整数へ畳んで渡す。アルファを最上位バイトへ置いた 32bit 値を符号なしで扱えるよう `Long` で返す。
 */
fun liquidGlassPlatformTintArgb(tone: LiquidGlassTone): Long {
    val tint = when (tone) {
        LiquidGlassTone.Dark -> AppColors.LiquidGlassDarkPlatformTint
        LiquidGlassTone.Light -> AppColors.LiquidGlassLightPlatformTint
    }

    return tint.toArgb().toLong() and ARGB_MASK
}

/** 符号拡張を落として ARGB の 32bit だけを残すマスク。 */
private const val ARGB_MASK = 0xFFFFFFFFL
