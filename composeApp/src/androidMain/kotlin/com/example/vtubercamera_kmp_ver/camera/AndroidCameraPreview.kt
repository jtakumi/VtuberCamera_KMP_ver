package com.example.vtubercamera_kmp_ver.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vtubercamera_kmp_ver.theme.spacing
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_preview_author_label
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_renderer_host_hint
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_renderer_host_ready
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_renderer_host_title
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_preview_version_label
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_preview_vrm_badge
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_read_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_read_failed
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
actual fun rememberCameraPermissionController(): CameraPermissionController {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf<Boolean?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }
    val requestPermissionAction = rememberUpdatedState(
        newValue = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
    )
    val controller = remember {
        CameraPermissionController(
            isGranted = false,
            isChecking = true,
            requestPermissionAction = {
                requestPermissionAction.value()
            },
        )
    }

    LaunchedEffect(context) {
        permissionGranted = context.hasCameraPermission()
    }

    LaunchedEffect(permissionGranted) {
        controller.update(
            isGranted = permissionGranted == true,
            isChecking = permissionGranted == null,
        )
    }

    return controller
}

@Composable
actual fun rememberFilePickerLauncher(onFilePicked: (FilePickerResult) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            onFilePicked(FilePickerResult.Cancelled)
            return@rememberLauncherForActivityResult
        }

        val fileName = context.resolveDisplayName(uri) ?: uri.lastPathSegment ?: "selected.glb"
        val result = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: throw FilePickerException(Res.string.file_picker_read_failed)
        }.fold(
            onSuccess = { bytes ->
                VrmAvatarParser.parse(fileName = fileName, bytes = bytes).fold(
                    onSuccess = { FilePickerResult.Success(it) },
                    onFailure = { throwable -> throwable.toFilePickerError() },
                )
            },
            onFailure = { throwable -> throwable.toFilePickerError(defaultMessageRes = Res.string.file_picker_open_failed) },
        )
        onFilePicked(result)
    }

    return FilePickerLauncher(
        launch = {
            pickerLauncher.launch(arrayOf("*/*"))
        },
    )
}

@Composable
actual fun CameraPreviewHost(
    modifier: Modifier,
    cameraRepository: CameraRepository,
    lensFacing: CameraLensFacing,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val onFaceTrackingFrameChangedState = rememberUpdatedState(onFaceTrackingFrameChanged)
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
            var attemptedLensFacing = lensFacing
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val resolvedLensFacing = cameraProvider.resolveLensFacing(lensFacing)
                attemptedLensFacing = resolvedLensFacing
                if (resolvedLensFacing != lensFacing) {
                    onLensFacingChanged(resolvedLensFacing)
                }

                val selector = resolvedLensFacing.toCameraSelector()
                if (!cameraProvider.hasCameraSafely(selector)) {
                    cameraProvider.unbindAll()
                    cameraRepository.onPlatformPreviewError(
                        lensFacing = resolvedLensFacing,
                        error = CameraError.CameraUnavailable,
                    )
                    return@Runnable
                }

                val faceTrackingAnalyzer = AndroidFaceTrackingAnalyzer(
                    lensFacing = resolvedLensFacing,
                    onFaceFrame = { frame ->
                        executor.execute {
                            onFaceTrackingFrameChangedState.value(frame)
                        }
                    },
                )

                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, faceTrackingAnalyzer) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    analysis,
                )

                (previewView.tag as? AndroidFaceTrackingAnalyzer)?.close()
                previewView.tag = faceTrackingAnalyzer
                cameraRepository.onPlatformPreviewStarted(resolvedLensFacing)
            }.onFailure {
                (previewView.tag as? AndroidFaceTrackingAnalyzer)?.close()
                previewView.tag = null
                onFaceTrackingFrameChangedState.value(null)
                cameraRepository.onPlatformPreviewError(
                    lensFacing = attemptedLensFacing,
                    error = CameraError.PreviewInitializationFailed,
                )
            }
        }

        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            (previewView.tag as? AndroidFaceTrackingAnalyzer)?.close()
            previewView.tag = null
            onFaceTrackingFrameChangedState.value(null)
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    DisposableEffect(analysisExecutor) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }
}

@Composable
actual fun AvatarPreviewOverlay(
    avatarPreview: AvatarPreviewData,
    modifier: Modifier,
) {
    val thumbnailBitmap = rememberAvatarBitmap(avatarPreview)

    Surface(
        modifier = modifier.fillMaxWidth(0.72f),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = avatarPreview.avatarName,
                    modifier = Modifier.size(96.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.avatar_preview_vrm_badge),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(
                modifier = Modifier.heightIn(min = 96.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = avatarPreview.avatarName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = avatarPreview.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                avatarPreview.authorName?.let { author ->
                    Text(
                        text = stringResource(Res.string.avatar_preview_author_label, author),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                avatarPreview.vrmVersion?.let { version ->
                    Text(
                        text = stringResource(Res.string.avatar_preview_version_label, version),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
actual fun AvatarBodyOverlay(
    avatarPreview: AvatarPreviewData,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
            tonalElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.96f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f),
                            ),
                        ),
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(28.dp),
                    ),
            ) {
                AvatarRendererHostView(
                    modifier = Modifier.fillMaxSize(),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(MaterialTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                ) {
                    Text(
                        text = stringResource(Res.string.avatar_renderer_host_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.avatar_renderer_host_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(MaterialTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                ) {
                    Text(
                        text = avatarPreview.avatarName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = avatarPreview.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(Res.string.avatar_renderer_host_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarRendererHostView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(
                    TextureView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        isOpaque = false
                        alpha = 0f
                    },
                )
            }
        },
    )
}

private fun Context.hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.resolveDisplayName(uri: android.net.Uri): String? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
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

private fun Throwable.toFilePickerError(defaultMessageRes: org.jetbrains.compose.resources.StringResource = Res.string.vrm_error_read_failed): FilePickerResult.Error {
    return when (this) {
        is FilePickerException -> FilePickerResult.Error(messageRes)
        else -> FilePickerResult.Error(defaultMessageRes)
    }
}

@Composable
private fun rememberAvatarBitmap(avatarPreview: AvatarPreviewData) = remember(avatarPreview.thumbnailBytes) {
    avatarPreview.thumbnailBytes?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
}

@Composable
actual fun rememberCameraRepositories(
    permissionController: CameraPermissionController,
): CameraRepositories {
    val context = LocalContext.current
    return remember(permissionController, context) {
        val previewState = MutableStateFlow<PreviewState>(PreviewState.Preparing)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        CameraRepositories(
            cameraRepository = object : CameraRepository {
                private var pendingLensFacing: CameraLensFacing? = null

                override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
                    val cameraProvider = cameraProviderFuture.await()
                    val resolvedLens = cameraProvider.resolveLensFacing(lensFacing)
                    if (!cameraProvider.hasCameraSafely(resolvedLens.toCameraSelector())) {
                        previewState.value = PreviewState.Error(CameraError.CameraUnavailable)
                        return Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
                    }
                    pendingLensFacing = resolvedLens
                    previewState.value = PreviewState.Preparing
                    return Result.success(resolvedLens)
                }

                override suspend fun stopPreview() {
                    pendingLensFacing = null
                    previewState.value = PreviewState.Preparing
                }

                override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
                    val cameraProvider = cameraProviderFuture.await()
                    previewState.value = PreviewState.Preparing
                    val targetLens = current.toggled()
                    if (!cameraProvider.hasCameraSafely(targetLens.toCameraSelector())) {
                        previewState.value = PreviewState.Error(CameraError.LensSwitchFailed)
                        return Result.failure(CameraRepositoryException(CameraError.LensSwitchFailed))
                    }
                    pendingLensFacing = targetLens
                    return Result.success(targetLens)
                }

                override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
                    val cameraProvider = cameraProviderFuture.await()
                    val resolvedLens = cameraProvider.resolveLensFacing(preferred)
                    return if (cameraProvider.hasCameraSafely(resolvedLens.toCameraSelector())) {
                        Result.success(resolvedLens)
                    } else {
                        Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
                    }
                }

                override fun observePreviewState(): Flow<PreviewState> = previewState

                override fun onPlatformPreviewStarted(lensFacing: CameraLensFacing) {
                    if (pendingLensFacing == null || pendingLensFacing == lensFacing) {
                        pendingLensFacing = lensFacing
                        previewState.value = PreviewState.Showing
                    }
                }

                override fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError) {
                    if (pendingLensFacing == lensFacing) {
                        pendingLensFacing = null
                        previewState.value = PreviewState.Error(error)
                    }
                }
            },
            permissionRepository = object : PermissionRepository {
                override suspend fun checkCameraPermission(): PermissionState {
                    return when {
                        permissionController.isChecking -> PermissionState.Unknown
                        permissionController.isGranted -> PermissionState.Granted
                        else -> PermissionState.Denied
                    }
                }

                override suspend fun requestCameraPermission(): PermissionState {
                    permissionController.requestPermission()
                    return checkCameraPermission()
                }
            },
        )
    }
}

private suspend fun ListenableFuture<ProcessCameraProvider>.await(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                runCatching { get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            Executor { runnable -> runnable.run() },
        )
    }
