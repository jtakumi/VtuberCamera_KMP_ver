package com.example.vtubercamera_kmp_ver.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val THEME_PREFERENCES_NAME = "vtuber_camera_theme"
private const val THEME_MODE_KEY = "theme_mode"

private class AndroidThemeModeStore(context: Context) : ThemeModeStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        THEME_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun readThemeMode(): ThemeMode =
        ThemeMode.fromStorageValue(preferences.getString(THEME_MODE_KEY, null))

    override fun writeThemeMode(themeMode: ThemeMode) {
        preferences.edit().putString(THEME_MODE_KEY, themeMode.storageValue).apply()
    }
}

@Composable
actual fun rememberThemeModeStore(): ThemeModeStore {
    val context = LocalContext.current
    return remember(context) { AndroidThemeModeStore(context) }
}
