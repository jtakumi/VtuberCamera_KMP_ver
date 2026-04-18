package com.example.vtubercamera_kmp_ver.avatar.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import kotlinx.coroutines.isActive

@Composable
fun AndroidFilamentAvatarHost(
    avatarRenderState: AvatarRenderState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember(context) {
        AndroidFilamentAvatarRenderer(context)
    }
    val latestRenderState = rememberUpdatedState(avatarRenderState)

    AndroidView(
        modifier = modifier,
        factory = { renderer.hostView },
        update = {
            renderer.updateRenderState(latestRenderState.value)
        },
    )

    LaunchedEffect(renderer) {
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                renderer.render(frameTimeNanos)
            }
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.destroy()
        }
    }
}
