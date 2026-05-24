package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DarkAppColorScheme = darkColorScheme(
    background = AppColors.DarkBackground,
    surface = AppColors.DarkOverlaySurface,
    surfaceVariant = AppColors.DarkSurfaceVariant,
    onSurface = AppColors.DarkOnSurface,
    onSurfaceVariant = AppColors.DarkOverlayTextSecondary,
    primary = AppColors.DarkPrimary,
    onPrimary = AppColors.DarkOnPrimary,
    errorContainer = AppColors.DarkErrorContainer,
    onErrorContainer = AppColors.DarkOnErrorContainer,
    secondaryContainer = AppColors.DarkSecondaryContainer,
    onSecondaryContainer = AppColors.DarkOnSecondaryContainer,
    scrim = AppColors.DarkBackground,
)

private val LightAppColorScheme = lightColorScheme(
    background = AppColors.LightBackground,
    surface = AppColors.LightOverlaySurface,
    surfaceVariant = AppColors.LightSurfaceVariant,
    onSurface = AppColors.LightOnSurface,
    onSurfaceVariant = AppColors.LightOverlayTextSecondary,
    primary = AppColors.LightPrimary,
    onPrimary = AppColors.LightOnPrimary,
    errorContainer = AppColors.LightErrorContainer,
    onErrorContainer = AppColors.LightOnErrorContainer,
    secondaryContainer = AppColors.LightSecondaryContainer,
    onSecondaryContainer = AppColors.LightOnSecondaryContainer,
    scrim = AppColors.LightScrim,
)

@Immutable
data class AppSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
)

private val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }

val MaterialTheme.spacing: AppSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalAppSpacing.current

@Composable
fun VtuberCameraTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = themeMode.useDarkTheme(isSystemInDarkTheme())

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkAppColorScheme else LightAppColorScheme,
    ) {
        CompositionLocalProvider(
            LocalAppSpacing provides AppSpacing(),
            content = content,
        )
    }
}
