package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlatformLiquidGlassTest {

    @Test
    fun liquidGlassPlatformTintArgb_packsTheDarkTokenWithAlphaInTheHighByte() {
        assertEquals(
            AppColors.LiquidGlassDarkPlatformTint.toArgb().toLong() and ARGB_MASK,
            liquidGlassPlatformTintArgb(LiquidGlassTone.Dark),
        )
    }

    @Test
    fun liquidGlassPlatformTintArgb_packsTheLightTokenWithAlphaInTheHighByte() {
        assertEquals(
            AppColors.LiquidGlassLightPlatformTint.toArgb().toLong() and ARGB_MASK,
            liquidGlassPlatformTintArgb(LiquidGlassTone.Light),
        )
    }

    /**
     * 明暗で tint が変わらないと、OS 製のガラスだけ背景プリセットに追従しなくなる。
     * 値そのものではなく「2 つの tone が別の色になる」ことを確かめる。
     */
    @Test
    fun liquidGlassPlatformTintArgb_differsBetweenTones() {
        assertNotEquals(
            liquidGlassPlatformTintArgb(LiquidGlassTone.Dark),
            liquidGlassPlatformTintArgb(LiquidGlassTone.Light),
        )
    }

    /**
     * ARGB は符号付き 32bit へ収めると最上位ビットが符号になるため、そのまま Long へ広げると
     * 不透明度の高い色が負値になる。0 以上へ畳めていることを確かめる。
     */
    @Test
    fun liquidGlassPlatformTintArgb_staysUnsigned() {
        for (tone in LiquidGlassTone.entries) {
            val tintArgb = liquidGlassPlatformTintArgb(tone)

            assertEquals(tintArgb and ARGB_MASK, tintArgb, "tint for $tone must fit in 32 unsigned bits")
        }
    }
}

private const val ARGB_MASK = 0xFFFFFFFFL
