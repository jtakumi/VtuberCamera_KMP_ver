package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AppColorScheme = darkColorScheme(
    background = AppColors.Background,
    surface = AppColors.OverlaySurface,
    surfaceVariant = AppColors.SurfaceVariant,
    onSurface = AppColors.OnSurface,
    onSurfaceVariant = AppColors.OverlayTextSecondary,
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    errorContainer = AppColors.ErrorContainer,
    onErrorContainer = AppColors.OnErrorContainer,
    secondaryContainer = AppColors.SecondaryContainer,
    onSecondaryContainer = AppColors.OnSecondaryContainer,
    scrim = AppColors.Background,
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
fun VtuberCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
    ) {
        CompositionLocalProvider(
            LocalAppSpacing provides AppSpacing(),
            content = content,
        )
    }
}
