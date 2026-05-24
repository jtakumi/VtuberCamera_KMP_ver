package com.example.vtubercamera_kmp_ver.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test
    fun systemModeUsesSystemDarkState() {
        assertTrue(ThemeMode.System.useDarkTheme(systemIsDark = true))
        assertFalse(ThemeMode.System.useDarkTheme(systemIsDark = false))
    }

    @Test
    fun storageValueFallsBackToSystemForUnknownValues() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorageValue(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStorageValue("unknown"))
        assertEquals(ThemeMode.Light, ThemeMode.fromStorageValue("light"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStorageValue("dark"))
    }

    @Test
    fun nextCyclesThroughSystemLightAndDark() {
        assertEquals(ThemeMode.Light, ThemeMode.System.next())
        assertEquals(ThemeMode.Dark, ThemeMode.Light.next())
        assertEquals(ThemeMode.System, ThemeMode.Dark.next())
    }
}
