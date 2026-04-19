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
import com.example.vtubercamera_kmp_ver.camera.AvatarSelectionData
import kotlinx.coroutines.isActive

@Composable
internal fun AndroidFilamentAvatarHost(
    avatarSelection: AvatarSelectionData,
    avatarRenderState: AvatarRenderState,
    onAvatarLoadFailure: (AvatarAssetLoadException) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestLoadFailureHandler = rememberUpdatedState(onAvatarLoadFailure)
    val renderer = remember(context) {
        AndroidFilamentAvatarRenderer(context)
    }
    val latestRenderState = rememberUpdatedState(avatarRenderState)

    AndroidView(
        modifier = modifier,
        factory = { renderer.hostView },
        update = {
            renderer.updateRendererState(
                avatarSelection = avatarSelection,
                nextRenderState = latestRenderState.value,
                onAvatarLoadFailure = latestLoadFailureHandler.value,
            )
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
