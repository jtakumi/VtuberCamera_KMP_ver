package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.runtime.Composable

interface ThemeModeStore {
    fun readThemeMode(): ThemeMode
    fun writeThemeMode(themeMode: ThemeMode)
}

@Composable
expect fun rememberThemeModeStore(): ThemeModeStore
