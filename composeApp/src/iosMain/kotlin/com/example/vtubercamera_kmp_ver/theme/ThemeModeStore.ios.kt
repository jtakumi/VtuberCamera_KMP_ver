package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private const val THEME_MODE_KEY = "theme_mode"

private class IosThemeModeStore(
    private val userDefaults: NSUserDefaults,
) : ThemeModeStore {
    override fun readThemeMode(): ThemeMode =
        ThemeMode.fromStorageValue(userDefaults.stringForKey(THEME_MODE_KEY))

    override fun writeThemeMode(themeMode: ThemeMode) {
        userDefaults.setObject(themeMode.storageValue, forKey = THEME_MODE_KEY)
    }
}

@Composable
actual fun rememberThemeModeStore(): ThemeModeStore =
    remember { IosThemeModeStore(NSUserDefaults.standardUserDefaults) }
