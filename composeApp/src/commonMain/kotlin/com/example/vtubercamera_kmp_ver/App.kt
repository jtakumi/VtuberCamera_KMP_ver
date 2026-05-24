package com.example.vtubercamera_kmp_ver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.vtubercamera_kmp_ver.camera.CameraRoute
import com.example.vtubercamera_kmp_ver.theme.VtuberCameraTheme
import com.example.vtubercamera_kmp_ver.theme.rememberThemeModeStore

@Composable
@Preview
fun App() {
    val themeModeStore = rememberThemeModeStore()
    var themeMode by remember { mutableStateOf(themeModeStore.readThemeMode()) }

    VtuberCameraTheme(themeMode = themeMode) {
        CameraRoute(
            themeMode = themeMode,
            onThemeModeToggle = {
                val nextThemeMode = themeMode.next()
                themeMode = nextThemeMode
                themeModeStore.writeThemeMode(nextThemeMode)
            },
        )
    }
}
