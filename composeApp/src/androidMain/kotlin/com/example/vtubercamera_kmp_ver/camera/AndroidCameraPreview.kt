package com.example.vtubercamera_kmp_ver.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun rememberCameraPermissionController(): CameraPermissionController {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf<Boolean?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(context) {
        permissionGranted = context.hasCameraPermission()
    }

    return remember(permissionGranted, permissionLauncher) {
        CameraPermissionController(
            isGranted = permissionGranted == true,
            isChecking = permissionGranted == null,
            requestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

@Composable
actual fun rememberFilePickerLauncher(): FilePickerLauncher {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { }

    return remember(pickerLauncher) {
        FilePickerLauncher(
            launch = {
                pickerLauncher.launch(arrayOf("*/*"))
            },
        )
    }
}

@Composable
actual fun CameraPreviewHost(
    modifier: Modifier,
    lensFacing: CameraLensFacing,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )

    DisposableEffect(lifecycleOwner, previewView, cameraProviderFuture, lensFacing) {
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val resolvedLensFacing = cameraProvider.resolveLensFacing(lensFacing)
            if (resolvedLensFacing != lensFacing) {
                onLensFacingChanged(resolvedLensFacing)
            }

            val selector = resolvedLensFacing.toCameraSelector()
            if (!cameraProvider.hasCameraSafely(selector)) {
                cameraProvider.unbindAll()
                return@Runnable
            }

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
            )
        }

        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun CameraLensFacing.toCameraSelector(): CameraSelector {
    return when (this) {
        CameraLensFacing.Back -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraLensFacing.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
    }
}

private fun ProcessCameraProvider.resolveLensFacing(requested: CameraLensFacing): CameraLensFacing {
    if (hasCameraSafely(requested.toCameraSelector())) {
        return requested
    }

    val fallback = requested.toggled()
    return if (hasCameraSafely(fallback.toCameraSelector())) {
        fallback
    } else {
        requested
    }
}

private fun ProcessCameraProvider.hasCameraSafely(selector: CameraSelector): Boolean {
    return try {
        hasCamera(selector)
    } catch (_: CameraInfoUnavailableException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}
