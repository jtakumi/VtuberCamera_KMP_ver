package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView

class IOSLiquidGlassBackdropHostTest {

    @Test
    fun isPlatformLiquidGlassAvailable_followsTheRegisteredProvider() {
        IOSLiquidGlassBackdropHost.registerViewProvider(
            FakeLiquidGlassBackdropViewProvider(isGlassEffectSupported = true),
        )

        assertTrue(isPlatformLiquidGlassAvailable())

        IOSLiquidGlassBackdropHost.registerViewProvider(
            FakeLiquidGlassBackdropViewProvider(isGlassEffectSupported = false),
        )

        assertFalse(isPlatformLiquidGlassAvailable())
    }

    @Test
    fun makeBackdropView_handsTheCornerRequestToTheProvider() {
        val viewProvider = FakeLiquidGlassBackdropViewProvider(isGlassEffectSupported = true)
        IOSLiquidGlassBackdropHost.registerViewProvider(viewProvider)

        IOSLiquidGlassBackdropHost.makeBackdropView(
            tintArgb = liquidGlassPlatformTintArgb(LiquidGlassTone.Dark),
            cornerRadiusPoints = LiquidGlassCornerStyle.Rounded(CAPTURE_BAR_CORNER_RADIUS).cornerRadiusPoints(),
            isCapsule = false,
        )

        assertEquals(
            liquidGlassPlatformTintArgb(LiquidGlassTone.Dark),
            viewProvider.lastTintArgb,
        )
        assertEquals(CAPTURE_BAR_CORNER_RADIUS.value.toDouble(), viewProvider.lastCornerRadiusPoints)
        assertEquals(false, viewProvider.lastIsCapsule)
    }

    @Test
    fun updateBackdropView_passesTheSharedToneTransitionDuration() {
        val viewProvider = FakeLiquidGlassBackdropViewProvider(isGlassEffectSupported = true)
        IOSLiquidGlassBackdropHost.registerViewProvider(viewProvider)
        val backdropView = makeTestView()

        IOSLiquidGlassBackdropHost.updateBackdropView(
            backdropView = backdropView,
            tintArgb = liquidGlassPlatformTintArgb(LiquidGlassTone.Light),
            durationSeconds = LIQUID_GLASS_TONE_TRANSITION_SECONDS,
        )

        assertEquals(liquidGlassPlatformTintArgb(LiquidGlassTone.Light), viewProvider.lastTintArgb)
        assertEquals(LIQUID_GLASS_TONE_TRANSITION_SECONDS, viewProvider.lastDurationSeconds)
    }

    /**
     * Capsule は view の高さから半径を決めるため、半径を持たない。0 以外を渡すと UIKit 側が
     * 高さではなくその値で丸めてしまう。
     */
    @Test
    fun cornerRadiusPoints_leavesTheRadiusToTheViewHeightForCapsule() {
        assertEquals(0.0, LiquidGlassCornerStyle.Capsule.cornerRadiusPoints())
    }

    /** Compose Multiplatform の iOS では 1 dp が 1 pt に対応するため、値はそのまま渡る。 */
    @Test
    fun cornerRadiusPoints_keepsTheRoundedRadiusInPoints() {
        assertEquals(
            CAPTURE_BAR_CORNER_RADIUS.value.toDouble(),
            LiquidGlassCornerStyle.Rounded(CAPTURE_BAR_CORNER_RADIUS).cornerRadiusPoints(),
        )
    }
}

/** 呼び出された引数だけを記録する、ガラス面を持たない provider。 */
private class FakeLiquidGlassBackdropViewProvider(
    override val isGlassEffectSupported: Boolean,
) : LiquidGlassBackdropViewProvider {
    var lastTintArgb: Long? = null
        private set
    var lastCornerRadiusPoints: Double? = null
        private set
    var lastIsCapsule: Boolean? = null
        private set
    var lastDurationSeconds: Double? = null
        private set

    override fun makeBackdropView(
        tintArgb: Long,
        cornerRadiusPoints: Double,
        isCapsule: Boolean,
    ): UIView {
        lastTintArgb = tintArgb
        lastCornerRadiusPoints = cornerRadiusPoints
        lastIsCapsule = isCapsule

        return makeTestView()
    }

    override fun updateBackdropView(backdropView: UIView, tintArgb: Long, durationSeconds: Double) {
        lastTintArgb = tintArgb
        lastDurationSeconds = durationSeconds
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun makeTestView(): UIView = UIView(frame = CGRectZero.readValue())

/** 操作バーと同じ角丸。dp から pt への変換が値を変えていないかを見る。 */
private val CAPTURE_BAR_CORNER_RADIUS = 28.dp
