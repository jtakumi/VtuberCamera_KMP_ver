package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    fun lerp_matchesTheStartToneAtFractionZero() {
        val dark = liquidGlassStyle(LiquidGlassTone.Dark)
        val light = liquidGlassStyle(LiquidGlassTone.Light)

        assertStyleNearlyEquals(dark, lerp(dark, light, 0f))
    }

    @Test
    fun lerp_matchesTheStopToneAtFractionOne() {
        val dark = liquidGlassStyle(LiquidGlassTone.Dark)
        val light = liquidGlassStyle(LiquidGlassTone.Light)

        assertStyleNearlyEquals(light, lerp(dark, light, 1f))
    }

    @Test
    fun lerp_leavesBothTonesAtTheMidpoint() {
        val dark = liquidGlassStyle(LiquidGlassTone.Dark)
        val light = liquidGlassStyle(LiquidGlassTone.Light)

        val midpoint = lerp(dark, light, 0.5f)

        assertNotEquals(lerp(dark, light, 0f), midpoint)
        assertNotEquals(lerp(dark, light, 1f), midpoint)
    }

    @Test
    fun lerp_clampsFractionOutsideTheUnitRange() {
        val dark = liquidGlassStyle(LiquidGlassTone.Dark)
        val light = liquidGlassStyle(LiquidGlassTone.Light)

        assertEquals(lerp(dark, light, 0f), lerp(dark, light, -1f))
        assertEquals(lerp(dark, light, 1f), lerp(dark, light, 2f))
    }

    /**
     * 2 つの色構成を全項目・全チャンネルで比較する。
     *
     * 端点の一致だけを見るのではなく項目名を添えて全項目を比較することで、補間の対象から漏れた
     * 項目や、別の項目の色を取り違えている実装を検出する。
     */
    private fun assertStyleNearlyEquals(expected: LiquidGlassStyle, actual: LiquidGlassStyle) {
        for ((expectedEntry, actualEntry) in expected.namedColors().zip(actual.namedColors())) {
            val (name, expectedColor) = expectedEntry

            assertChannelsNearlyEqual(name, expectedColor, actualEntry.second)
        }
    }

    /** 色を RGBA の各チャンネルで比較する。 */
    private fun assertChannelsNearlyEqual(name: String, expected: Color, actual: Color) {
        val channels = listOf(
            "red" to (expected.red to actual.red),
            "green" to (expected.green to actual.green),
            "blue" to (expected.blue to actual.blue),
            "alpha" to (expected.alpha to actual.alpha),
        )

        for ((channel, values) in channels) {
            val (expectedValue, actualValue) = values

            assertTrue(
                abs(expectedValue - actualValue) < CHANNEL_TOLERANCE,
                "$name.$channel expected $expectedValue but was $actualValue",
            )
        }
    }

    /** 色構成の全項目を、項目名付きで宣言順に並べる。 */
    private fun LiquidGlassStyle.namedColors(): List<Pair<String, Color>> = listOf(
        "tintTop" to tintTop,
        "tintBottom" to tintBottom,
        "specularColor" to specularColor,
        "rimTopColor" to rimTopColor,
        "rimBottomColor" to rimBottomColor,
        "shadowColor" to shadowColor,
        "contentColor" to contentColor,
        "contentVariantColor" to contentVariantColor,
        "innerFillColor" to innerFillColor,
        "innerSelectedFillColor" to innerSelectedFillColor,
        "innerRimColor" to innerRimColor,
    )
}

/**
 * 色の比較に使うチャンネルあたりの許容誤差。
 *
 * 色の補間は Oklab を経由して sRGB へ戻すため、端点でも浮動小数の丸めと 8bit 量子化
 * （1 段 = 約 0.0039）が乗る。その数倍は許容しつつ、取り違えを見逃さない値にする。dark と
 * light は全項目でどこか 1 チャンネルが 0.34 以上離れているため、この値なら取り違えは必ず落ちる。
 */
private const val CHANNEL_TOLERANCE = 0.02f
