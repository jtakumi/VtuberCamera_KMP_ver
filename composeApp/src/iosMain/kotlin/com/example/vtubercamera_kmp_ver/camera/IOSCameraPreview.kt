@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.example.vtubercamera_kmp_ver.theme.spacing
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.position
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_failed

@Composable
actual fun rememberCameraPermissionController(): CameraPermissionController {
    var isGranted by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        isGranted = status == AVAuthorizationStatusAuthorized
        isChecking = false
    }

    return remember(isGranted, isChecking) {
        CameraPermissionController(
            isGranted = isGranted,
            isChecking = isChecking,
            requestPermission = {
                val current = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
                if (current == AVAuthorizationStatusAuthorized) {
                    isGranted = true
                    isChecking = false
                } else if (current != AVAuthorizationStatusNotDetermined) {
                    isGranted = false
                    isChecking = false
                } else {
                    isChecking = true
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        dispatch_async(dispatch_get_main_queue()) {
                            isGranted = granted
                            isChecking = false
                        }
                    }
                }
            },
        )
    }
}

@Composable
actual fun rememberFilePickerLauncher(onFilePicked: (FilePickerResult) -> Unit): FilePickerLauncher {
    return remember {
        FilePickerLauncher(
            launch = {
                currentPresentedViewController()?.let { viewController ->
                    val pickerViewController = UIDocumentPickerViewController(
                        documentTypes = listOf("public.item"),
                        inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                    )
                    viewController.presentViewController(
                        viewControllerToPresent = pickerViewController,
                        animated = true,
                        completion = null,
                    )
                } ?: onFilePicked(FilePickerResult.Error(Res.string.file_picker_open_failed))
            },
        )
    }
}

@Composable
actual fun CameraPreviewHost(
    modifier: Modifier,
    lensFacing: CameraLensFacing,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
) {
    val sessionManager = remember { IOSCameraSessionManager() }
    val previewView = remember { CameraPreviewView() }

    UIKitView(
        modifier = modifier.fillMaxSize(),
        factory = {
            previewView.backgroundColor = UIColor.blackColor
            sessionManager.bindPreview(to = previewView)
            previewView
        },
        update = {
            sessionManager.bindPreview(to = it)
        },
    )

    DisposableEffect(lensFacing) {
        val resolved = sessionManager.startPreview(lensFacing).getOrNull()
        if (resolved != null && resolved != lensFacing) {
            onLensFacingChanged(resolved)
        }

        onDispose {
            sessionManager.stopPreview()
            onFaceTrackingFrameChanged(null)
        }
    }
}

@Composable
actual fun AvatarPreviewOverlay(
    avatarPreview: AvatarPreviewData,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(avatarPreview.avatarName)
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
                .fillMaxWidth(0.56f)
                .fillMaxHeight(0.48f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            tonalElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = avatarPreview.avatarName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(MaterialTheme.spacing.lg),
                )
            }
        }
    }
}

private class CameraPreviewView : UIView(frame = CGRectZero.readValue()) {
    private var hostedPreviewLayer: AVCaptureVideoPreviewLayer? = null

    fun bindPreviewLayer(previewLayer: AVCaptureVideoPreviewLayer) {
        if (hostedPreviewLayer !== previewLayer || previewLayer.superlayer != layer) {
            previewLayer.removeFromSuperlayer()
            layer.addSublayer(previewLayer)
            hostedPreviewLayer = previewLayer
        }
        setNeedsLayout()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        hostedPreviewLayer?.frame = bounds
    }
}

private fun currentPresentedViewController(): UIViewController? {
    var currentViewController = UIApplication.sharedApplication.windows
        .filterIsInstance<UIWindow>()
        .firstOrNull { window -> window.isKeyWindow() }
        ?.rootViewController

    while (currentViewController?.presentedViewController != null) {
        currentViewController = currentViewController.presentedViewController
    }

    return currentViewController
}

private class IOSCameraSessionManager {
    private val session = AVCaptureSession()
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session)
    // Serialize capture-session work off the main thread to avoid UI stalls.
    private val sessionQueue = dispatch_queue_create("com.example.vtubercamera.camera.session", null)
    private var currentInput: AVCaptureDeviceInput? = null

    init {
        previewLayer.videoGravity = "AVLayerVideoGravityResizeAspectFill"
    }

    fun bindPreview(to: CameraPreviewView) {
        to.bindPreviewLayer(previewLayer)
    }

    fun startPreview(requestedLensFacing: CameraLensFacing): Result<CameraLensFacing> {
        val resolvedLens = resolveAvailableLens(requestedLensFacing)
            ?: return Result.failure(IllegalStateException("No available camera lens"))
        val device = cameraDevice(resolvedLens.toDevicePosition())
            ?: return Result.failure(IllegalStateException("Resolved camera device is unavailable"))
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) as? AVCaptureDeviceInput
            ?: return Result.failure(IllegalStateException("Failed to create camera input"))

        dispatch_async(sessionQueue) {
            session.beginConfiguration()
            val previousInput = currentInput
            previousInput?.let { session.removeInput(it) }
            if (!session.canAddInput(input)) {
                previousInput?.takeIf { session.canAddInput(it) }?.let { restoredInput ->
                    session.addInput(restoredInput)
                    currentInput = restoredInput
                }
                session.commitConfiguration()
                return@dispatch_async
            }
            session.addInput(input)
            currentInput = input
            session.commitConfiguration()

            if (!session.running) {
                session.startRunning()
            }
        }
        return Result.success(resolvedLens)
    }

    fun stopPreview() {
        dispatch_async(sessionQueue) {
            if (session.running) {
                session.stopRunning()
            }
        }
    }

    private fun resolveAvailableLens(requested: CameraLensFacing): CameraLensFacing? {
        if (cameraDevice(requested.toDevicePosition()) != null) {
            return requested
        }

        val fallback = requested.toggled()
        return if (cameraDevice(fallback.toDevicePosition()) != null) {
            fallback
        } else {
            null
        }
    }
}

@Composable
actual fun rememberCameraRepositories(
    permissionController: CameraPermissionController,
): CameraRepositories {
    return remember(permissionController) {
        val previewState = kotlinx.coroutines.flow.MutableStateFlow<PreviewState>(PreviewState.Preparing)
        CameraRepositories(
            cameraRepository = object : CameraRepository {
                override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
                    val resolvedLens = resolveAvailableLens(lensFacing)
                        ?: return Result.failure(IllegalStateException("No available camera lens"))
                    previewState.value = PreviewState.Showing
                    return Result.success(resolvedLens)
                }

                override suspend fun stopPreview() {
                    previewState.value = PreviewState.Preparing
                }

                override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
                    previewState.value = PreviewState.Preparing
                    val targetLens = current.toggled()
                    if (cameraDevice(targetLens.toDevicePosition()) == null) {
                        return Result.failure(IllegalStateException("Requested lens is unavailable"))
                    }
                    previewState.value = PreviewState.Showing
                    return Result.success(targetLens)
                }

                override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
                    val resolvedLens = resolveAvailableLens(preferred)
                        ?: return Result.failure(IllegalStateException("No available camera lens"))
                    return Result.success(resolvedLens)
                }

                override fun observePreviewState(): kotlinx.coroutines.flow.Flow<PreviewState> = previewState
            },
            permissionRepository = object : PermissionRepository {
                private fun currentPermissionState(): PermissionState {
                    return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
                        AVAuthorizationStatusAuthorized -> PermissionState.Granted
                        AVAuthorizationStatusNotDetermined -> PermissionState.Unknown
                        else -> PermissionState.Denied
                    }
                }

                override suspend fun checkCameraPermission(): PermissionState {
                    return currentPermissionState()
                }

                override suspend fun requestCameraPermission(): PermissionState {
                    permissionController.requestPermission()
                    return currentPermissionState()
                }
            },
        )
    }
}

private fun resolveAvailableLens(requested: CameraLensFacing): CameraLensFacing? {
    if (cameraDevice(requested.toDevicePosition()) != null) {
        return requested
    }

    val fallback = requested.toggled()
    return if (cameraDevice(fallback.toDevicePosition()) != null) {
        fallback
    } else {
        null
    }
}

private fun CameraLensFacing.toDevicePosition() = when (this) {
    CameraLensFacing.Front -> AVCaptureDevicePositionFront
    CameraLensFacing.Back -> AVCaptureDevicePositionBack
}

private fun cameraDevice(position: platform.AVFoundation.AVCaptureDevicePosition): AVCaptureDevice? {
    return AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
        .filterIsInstance<AVCaptureDevice>()
        .firstOrNull { it.position == position }
}
