package com.example.vtubercamera_kmp_ver.theme

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidGlassTest {

    @Test
    fun liquidGlassStyle_darkToneUsesDarkTokens() {
        val style = liquidGlassStyle(LiquidGlassTone.Dark)

        assertEquals(AppColors.LiquidGlassDarkTintTop, style.tintTop)
        assertEquals(AppColors.LiquidGlassDarkContent, style.contentColor)
        assertEquals(AppColors.LiquidGlassDarkInnerSelectedFill, style.innerSelectedFillColor)
    }

    @Test
    fun liquidGlassStyle_lightToneUsesLightTokens() {
        val style = liquidGlassStyle(LiquidGlassTone.Light)

        assertEquals(AppColors.LiquidGlassLightTintTop, style.tintTop)
        assertEquals(AppColors.LiquidGlassLightContent, style.contentColor)
        assertEquals(AppColors.LiquidGlassLightInnerSelectedFill, style.innerSelectedFillColor)
    }

    @Test
    fun lerp_keepsEndpointsAtBoundaryFractions() {
        val dark = liquidGlassStyle(LiquidGlassTone.Dark)
        val light = liquidGlassStyle(LiquidGlassTone.Light)

        assertAlphaEquals(dark.tintTop.alpha, lerp(dark, light, 0f).tintTop.alpha)
        assertAlphaEquals(light.tintTop.alpha, lerp(dark, light, 1f).tintTop.alpha)
    }

    @Test
    fun lerp_movesTowardTheOtherToneAtMidpoint() {
        val dark = liquidGlassStyle(LiquidGlassTone.Dark)
        val light = liquidGlassStyle(LiquidGlassTone.Light)

        val midpointAlpha = lerp(dark, light, 0.5f).tintTop.alpha

        assertTrue(
            midpointAlpha > dark.tintTop.alpha && midpointAlpha < light.tintTop.alpha,
            "midpoint alpha $midpointAlpha should sit between the two tones",
        )
    }

    @Test
    fun lerp_clampsFractionOutsideTheUnitRange() {
        val dark = liquidGlassStyle(LiquidGlassTone.Dark)
        val light = liquidGlassStyle(LiquidGlassTone.Light)

        assertEquals(lerp(dark, light, 0f), lerp(dark, light, -1f))
        assertEquals(lerp(dark, light, 1f), lerp(dark, light, 2f))
    }

    /**
     * 透明度を許容誤差付きで比較する。
     *
     * 色の補間は Oklab を経由して sRGB へ戻すため、端点でも浮動小数の丸めが乗る。
     */
    private fun assertAlphaEquals(expected: Float, actual: Float) {
        assertTrue(
            abs(expected - actual) < ALPHA_TOLERANCE,
            "expected alpha $expected but was $actual",
        )
    }
}

private const val ALPHA_TOLERANCE = 0.001f
