package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val DarkBackground = Color(0xFF000000)
    val DarkOverlaySurface = Color(0xE61A1A1A)
    val DarkOverlayTextSecondary = Color(0xFFB9B9BF)
    val DarkSurfaceVariant = Color(0xFF2C2C2E)
    val DarkOnSurface = Color(0xFFEFEFEF)
    val DarkPrimary = Color(0xFF75C7FF)
    val DarkOnPrimary = Color(0xFF00344F)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)
    val DarkSecondaryContainer = Color(0xFF123A56)
    val DarkOnSecondaryContainer = Color(0xFFC7E7FF)

    val LightBackground = Color(0xFFF7F7FA)
    val LightOverlaySurface = Color(0xE6FFFFFF)
    val LightOverlayTextSecondary = Color(0xFF5F6470)
    val LightSurfaceVariant = Color(0xFFE1E3EA)
    val LightOnSurface = Color(0xFF171A20)
    val LightPrimary = Color(0xFF148FEA)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)
    val LightSecondaryContainer = Color(0xFFD8EEFF)
    val LightOnSecondaryContainer = Color(0xFF003654)
    val LightScrim = Color(0xFF16181D)

    // カメラ画面のオーバーレイ UI 用の Liquid Glass トークン。背景がカメラ映像や単色プリセットに
    // 変わるため、システムのライト / ダークではなく「背後が暗いか明るいか」で 2 系統を切り替える。
    // Dark は映像や暗色プリセットの上に置く暗いガラス、Light は白系プリセットの上に置く明るいガラス。
    val LiquidGlassDarkTintTop = Color(0x662A2E35)
    val LiquidGlassDarkTintBottom = Color(0x8C0A0C0F)
    val LiquidGlassDarkSpecular = Color(0x40FFFFFF)
    val LiquidGlassDarkRimTop = Color(0x8CFFFFFF)
    val LiquidGlassDarkRimBottom = Color(0x1AFFFFFF)
    val LiquidGlassDarkShadow = Color(0xB3000000)
    val LiquidGlassDarkContent = Color(0xFFFFFFFF)
    val LiquidGlassDarkContentVariant = Color(0xB3FFFFFF)
    val LiquidGlassDarkInnerFill = Color(0x33FFFFFF)
    val LiquidGlassDarkInnerSelectedFill = Color(0x59FFFFFF)
    val LiquidGlassDarkInnerRim = Color(0x33FFFFFF)

    val LiquidGlassLightTintTop = Color(0x8CFFFFFF)
    val LiquidGlassLightTintBottom = Color(0xA6EDEFF4)
    val LiquidGlassLightSpecular = Color(0x99FFFFFF)
    val LiquidGlassLightRimTop = Color(0xB3FFFFFF)
    val LiquidGlassLightRimBottom = Color(0x26000000)
    val LiquidGlassLightShadow = Color(0x59000000)
    val LiquidGlassLightContent = Color(0xFF171A20)
    val LiquidGlassLightContentVariant = Color(0xA6171A20)
    val LiquidGlassLightInnerFill = Color(0x1F000000)
    val LiquidGlassLightInnerSelectedFill = Color(0x3D000000)
    val LiquidGlassLightInnerRim = Color(0x1F000000)
}
