@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.theme.spacing
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.CValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.get
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.ARKit.ARBlendShapeLocationEyeBlinkLeft
import platform.ARKit.ARBlendShapeLocationEyeBlinkRight
import platform.ARKit.ARBlendShapeLocationJawOpen
import platform.ARKit.ARBlendShapeLocationMouthSmileLeft
import platform.ARKit.ARBlendShapeLocationMouthSmileRight
import platform.ARKit.ARFaceAnchor
import platform.ARKit.ARFaceTrackingConfiguration
import platform.ARKit.ARSCNView
import platform.ARKit.ARSession
import platform.ARKit.ARSessionDelegateProtocol
import platform.ARKit.ARTrackingState
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
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.getBytes
import platform.QuartzCore.CACurrentMediaTime
import platform.SceneKit.SCNScene
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.simd_float4x4
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_read_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_read_failed

private const val IOS_LIMITED_TRACKING_CONFIDENCE = 0.65f
private const val IOS_HEAD_YAW_SMOOTHING_ALPHA = 0.45f
private const val IOS_HEAD_PITCH_SMOOTHING_ALPHA = 0.45f
private const val IOS_HEAD_ROLL_SMOOTHING_ALPHA = 0.4f
private const val IOS_SMILE_SMOOTHING_ALPHA = 0.35f
private const val IOS_BLINK_HIGH_THRESHOLD = 0.68f
private const val IOS_BLINK_LOW_THRESHOLD = 0.32f
private const val IOS_BLINK_CLOSING_ALPHA = 0.55f
private const val IOS_BLINK_OPENING_ALPHA = 0.28f
private const val IOS_JAW_OPENING_ALPHA = 0.58f
private const val IOS_JAW_CLOSING_ALPHA = 0.24f
private const val IOS_FACE_FRAME_DISPATCH_INTERVAL_SECONDS = 1.0 / 30.0

@Composable
// カメラ権限の現在状態を監視し、権限要求処理を組み立てたコントローラーを保持する。
actual fun rememberCameraPermissionController(): CameraPermissionController {
    val controller = remember {
        CameraPermissionController(
            isGranted = false,
            isChecking = true,
            requestPermissionAction = {},
        )
    }

    LaunchedEffect(Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        controller.update(
            isGranted = status == AVAuthorizationStatusAuthorized,
            isChecking = false,
        )
    }

    controller.updateRequestPermissionAction {
        val current = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        if (current == AVAuthorizationStatusAuthorized) {
            controller.update(
                isGranted = true,
                isChecking = false,
            )
        } else if (current != AVAuthorizationStatusNotDetermined) {
            controller.update(
                isGranted = false,
                isChecking = false,
            )
        } else {
            controller.update(isChecking = true)
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    controller.update(
                        isGranted = granted,
                        isChecking = false,
                    )
                }
            }
        }
    }

    return controller
}

@Composable
// iOS のドキュメントピッカーを起動するランチャーを生成する。
actual fun rememberFilePickerLauncher(onFilePicked: (FilePickerResult) -> Unit): FilePickerLauncher {
    val onFilePickedState = rememberUpdatedState(onFilePicked)
    val pickerDelegate = remember {
        IOSDocumentPickerDelegate { result ->
            onFilePickedState.value(result)
        }
    }

    return remember(pickerDelegate) {
        FilePickerLauncher(
            launch = {
                currentPresentedViewController()?.let { viewController ->
                    val pickerViewController = UIDocumentPickerViewController(
                        documentTypes = listOf("public.item"),
                        inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                    )
                    pickerViewController.delegate = pickerDelegate
                    pickerViewController.allowsMultipleSelection = false
                    pickerViewController.shouldShowFileExtensions = true
                    viewController.presentViewController(
                        viewControllerToPresent = pickerViewController,
                        animated = true,
                        completion = null,
                    )
                } ?: onFilePickedState.value(FilePickerResult.Error(Res.string.file_picker_open_failed))
            },
        )
    }
}

@Composable
// Compose 上に iOS カメラプレビューを表示し、レンズ切り替えと開始停止を管理する。
actual fun CameraPreviewHost(
    modifier: Modifier,
    cameraRepository: CameraRepository,
    lensFacing: CameraLensFacing,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
) {
    val sessionManager = remember { IOSCameraSessionManager() }
    val faceTrackingSessionManager = remember { IOSFaceTrackingSessionManager() }
    val previewView = remember { CameraPreviewContainerView() }
    val usesFaceTracking = remember(lensFacing) { lensFacing.shouldUseIosFaceTracking() }
    val currentOnFaceTrackingFrameChanged = rememberUpdatedState(onFaceTrackingFrameChanged)

    UIKitView(
        modifier = modifier.fillMaxSize(),
        factory = {
            previewView.backgroundColor = UIColor.blackColor
            if (usesFaceTracking) {
                faceTrackingSessionManager.bindPreview(to = previewView)
            } else {
                sessionManager.bindPreview(to = previewView)
            }
            previewView
        },
        update = {
            if (usesFaceTracking) {
                faceTrackingSessionManager.bindPreview(to = it)
            } else {
                sessionManager.bindPreview(to = it)
            }
        },
    )

    DisposableEffect(lensFacing, usesFaceTracking) {
        var isDisposed = false
        val onPreviewConfigured: (CameraLensFacing, Throwable?) -> Unit = { resolvedLens, error ->
            if (!isDisposed) {
                if (error == null) {
                    if (resolvedLens != lensFacing) {
                        onLensFacingChanged(resolvedLens)
                    }
                    cameraRepository.onPlatformPreviewStarted(resolvedLens)
                } else {
                    cameraRepository.onPlatformPreviewError(
                        lensFacing = resolvedLens,
                        error = CameraError.PreviewInitializationFailed,
                    )
                }
            }
        }

        if (usesFaceTracking) {
            faceTrackingSessionManager.stopPreview()
            sessionManager.stopPreview {
                if (!isDisposed) {
                    faceTrackingSessionManager.startPreview(
                        onFaceTrackingFrameChanged = { frame ->
                            currentOnFaceTrackingFrameChanged.value(frame)
                        },
                        onComplete = onPreviewConfigured,
                    )
                }
            }
        } else {
            faceTrackingSessionManager.stopPreview()
            currentOnFaceTrackingFrameChanged.value(null)
            sessionManager.startPreview(
                requestedLensFacing = lensFacing,
                onComplete = onPreviewConfigured,
            )
        }

        onDispose {
            isDisposed = true
            faceTrackingSessionManager.stopPreview()
            sessionManager.stopPreview()
            currentOnFaceTrackingFrameChanged.value(null)
        }
    }
}

@Composable
// 選択中アバターの簡易プレビューを重ねて表示する。
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
// カメラ画面の下部にアバター本体用のオーバーレイを表示する。
@Suppress("UNUSED_PARAMETER")
actual fun AvatarBodyOverlay(
    avatarSelection: AvatarSelectionData,
    avatarRenderState: AvatarRenderState,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, org.jetbrains.compose.resources.StringResource) -> Unit,
    modifier: Modifier,
) {
    val avatarPreview = avatarSelection.preview

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

// AVFoundation preview layer と ARKit preview view を切り替えて保持する UIKit コンテナ。
private class CameraPreviewContainerView : UIView(frame = CGRectZero.readValue()) {
    private var hostedPreviewLayer: AVCaptureVideoPreviewLayer? = null
    private var hostedView: UIView? = null

    // AVFoundation のプレビュー層を UIKit ビューへ紐付ける。
    fun bindPreviewLayer(previewLayer: AVCaptureVideoPreviewLayer) {
        var didChangeHostedContent = false
        if (hostedView != null) {
            hostedView?.removeFromSuperview()
            hostedView = null
            didChangeHostedContent = true
        }
        if (hostedPreviewLayer !== previewLayer || previewLayer.superlayer != layer) {
            previewLayer.removeFromSuperlayer()
            layer.addSublayer(previewLayer)
            hostedPreviewLayer = previewLayer
            didChangeHostedContent = true
        }
        if (didChangeHostedContent) {
            setNeedsLayout()
        }
    }

    // ARKit preview 用の UIView をコンテナへ差し込む。
    fun bindHostedView(view: UIView) {
        var didChangeHostedContent = false
        if (hostedPreviewLayer != null) {
            hostedPreviewLayer?.removeFromSuperlayer()
            hostedPreviewLayer = null
            didChangeHostedContent = true
        }
        if (hostedView !== view || view.superview != this) {
            hostedView?.removeFromSuperview()
            view.removeFromSuperview()
            addSubview(view)
            hostedView = view
            didChangeHostedContent = true
        }
        if (didChangeHostedContent) {
            setNeedsLayout()
        }
    }

    // ビューのサイズ変更に合わせてプレビュー層の描画範囲を更新する。
    override fun layoutSubviews() {
        super.layoutSubviews()
        hostedPreviewLayer?.frame = bounds
        hostedView?.setFrame(bounds)
    }
}

// Adapts UIDocumentPicker callbacks into shared file-picker results.
private class IOSDocumentPickerDelegate(
    private val onFilePicked: (FilePickerResult) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    // ユーザーが選択したファイル URL を共有形式へ変換して返す。
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val selectedUrl = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (selectedUrl == null) {
            onFilePicked(FilePickerResult.Error(Res.string.file_picker_read_failed))
            return
        }

        onFilePicked(selectedUrl.toFilePickerResult())
    }

    // ドキュメントピッカーのキャンセル結果を呼び出し元へ通知する。
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onFilePicked(FilePickerResult.Cancelled)
    }
}

// 現在画面上に表示中の UIViewController をたどって取得する。
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

// 選択されたファイル URL を読み込み、アバタープレビュー結果へ変換する。
private fun NSURL.toFilePickerResult(): FilePickerResult {
    val fileName = lastPathComponent ?: "selected.glb"
    val didAccessResource = startAccessingSecurityScopedResource()

    return try {
        val data = NSData.create(contentsOfURL = this)
            ?: throw FilePickerException(Res.string.file_picker_read_failed)
        VrmAvatarParser.parse(fileName = fileName, bytes = data.toByteArray()).fold(
            onSuccess = { avatarSelection -> FilePickerResult.Success(avatarSelection) },
            onFailure = { throwable -> throwable.toFilePickerError() },
        )
    } catch (throwable: Throwable) {
        throwable.toFilePickerError()
    } finally {
        if (didAccessResource) {
            stopAccessingSecurityScopedResource()
        }
    }
}

// NSData を Kotlin の ByteArray へコピー変換する。
private fun NSData.toByteArray(): ByteArray {
    val byteCount = length().toInt()
    if (byteCount == 0) {
        return ByteArray(0)
    }

    return ByteArray(byteCount).apply {
        usePinned { pinned ->
            getBytes(pinned.addressOf(0), length())
        }
    }
}

// ファイル読み込み時の例外を UI 表示用のエラー型へ変換する。
private fun Throwable.toFilePickerError(): FilePickerResult.Error {
    val messageRes = when (this) {
        is FilePickerException -> messageRes
        else -> Res.string.vrm_error_read_failed
    }
    return FilePickerResult.Error(messageRes)
}

// Owns the iOS camera session lifecycle and resolves usable lenses for preview startup.
private class IOSCameraSessionManager {
    private val session = AVCaptureSession()
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session)
    // Serialize capture-session work off the main thread to avoid UI stalls.
    private val sessionQueue = dispatch_queue_create("com.example.vtubercamera.camera.session", null)
    private var currentInput: AVCaptureDeviceInput? = null

    init {
        previewLayer.videoGravity = "AVLayerVideoGravityResizeAspectFill"
    }

    // プレビュー層を表示用ビューへ接続する。
    fun bindPreview(to: CameraPreviewContainerView) {
        to.bindPreviewLayer(previewLayer)
    }

    // 指定レンズでカメラ入力を構成し、プレビュー開始結果をコールバックする。
    fun startPreview(
        requestedLensFacing: CameraLensFacing,
        onComplete: (resolvedLens: CameraLensFacing, error: Throwable?) -> Unit,
    ) {
        val resolvedLens = resolveAvailableLens(requestedLensFacing)
            ?: return onComplete(requestedLensFacing, IllegalStateException("No available camera lens"))
        val device = cameraDevice(resolvedLens.toDevicePosition())
            ?: return onComplete(resolvedLens, IllegalStateException("Resolved camera device is unavailable"))
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
            ?: return onComplete(resolvedLens, IllegalStateException("Failed to create camera input"))

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
                dispatch_async(dispatch_get_main_queue()) {
                    onComplete(resolvedLens, IllegalStateException("Failed to add camera input"))
                }
                return@dispatch_async
            }
            session.addInput(input)
            currentInput = input
            session.commitConfiguration()

            val started = runCatching {
                if (!session.running) {
                    session.startRunning()
                }
            }

            dispatch_async(dispatch_get_main_queue()) {
                started.fold(
                    onSuccess = { onComplete(resolvedLens, null) },
                    onFailure = { onComplete(resolvedLens, it) },
                )
            }
        }
    }

    // 実行中のカメラプレビューを停止する。
    fun stopPreview(onStopped: () -> Unit = {}) {
        dispatch_async(sessionQueue) {
            if (session.running) {
                session.stopRunning()
            }
            dispatch_async(dispatch_get_main_queue()) {
                onStopped()
            }
        }
    }

    // 要求レンズが使えない場合は利用可能な別レンズへフォールバックする。
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

// ARKit の front-camera preview と face tracking をまとめて管理する。
private class IOSFaceTrackingSessionManager {
    private val previewView = ARSCNView(frame = CGRectZero.readValue()).apply {
        backgroundColor = UIColor.blackColor
        scene = SCNScene()
    }
    private val sessionDelegate = IOSFaceTrackingSessionDelegate()

    init {
        previewView.session.delegate = sessionDelegate
    }

    // ARKit preview view を表示用コンテナへ接続する。
    fun bindPreview(to: CameraPreviewContainerView) {
        to.bindHostedView(previewView)
    }

    // TrueDepth 対応時に ARKit face tracking を開始する。
    fun startPreview(
        onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
        onComplete: (resolvedLens: CameraLensFacing, error: Throwable?) -> Unit,
    ) {
        if (!ARFaceTrackingConfiguration.isSupported) {
            onComplete(CameraLensFacing.Front, IllegalStateException("ARKit face tracking is unsupported"))
            return
        }

        sessionDelegate.onFaceTrackingFrameChanged = onFaceTrackingFrameChanged
        sessionDelegate.clearTrackedFace()
        val configuration = ARFaceTrackingConfiguration().apply {
            setLightEstimationEnabled(true)
        }
        val started = runCatching {
            previewView.session.runWithConfiguration(configuration)
        }
        onComplete(CameraLensFacing.Front, started.exceptionOrNull())
    }

    // ARKit face tracking を停止して最新フレームを破棄する。
    fun stopPreview() {
        sessionDelegate.onFaceTrackingFrameChanged = {}
        sessionDelegate.clearTrackedFace()
        previewView.session.pause()
    }
}

@Composable
// iOS 実装のカメラ関連リポジトリ群を生成して Compose に保持する。
actual fun rememberCameraRepositories(
    permissionController: CameraPermissionController,
): CameraRepositories {
    return remember(permissionController) {
        val previewState = kotlinx.coroutines.flow.MutableStateFlow<PreviewState>(PreviewState.Preparing)
        CameraRepositories(
            cameraRepository = object : CameraRepository {
                private var pendingLensFacing: CameraLensFacing? = null

                // プレビュー開始前の状態を整え、利用可能なレンズを解決する。
                override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
                    val resolvedLens = resolveAvailableLens(lensFacing)
                        ?: return Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
                    pendingLensFacing = resolvedLens
                    previewState.value = PreviewState.Preparing
                    return Result.success(resolvedLens)
                }

                // プレビュー停止に合わせて内部状態を初期化する。
                override suspend fun stopPreview() {
                    pendingLensFacing = null
                    previewState.value = PreviewState.Preparing
                }

                // 現在と反対側のレンズへ切り替え可能か確認して反映する。
                override suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing> {
                    previewState.value = PreviewState.Preparing
                    val targetLens = current.toggled()
                    if (cameraDevice(targetLens.toDevicePosition()) == null) {
                        previewState.value = PreviewState.Error(CameraError.LensSwitchFailed)
                        return Result.failure(CameraRepositoryException(CameraError.LensSwitchFailed))
                    }
                    pendingLensFacing = targetLens
                    return Result.success(targetLens)
                }

                // 初回表示時に利用できるレンズを決定する。
                override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
                    val resolvedLens = resolveAvailableLens(preferred)
                        ?: return Result.failure(CameraRepositoryException(CameraError.CameraUnavailable))
                    return Result.success(resolvedLens)
                }

                // プレビュー状態の変更を監視する Flow を返す。
                override fun observePreviewState(): kotlinx.coroutines.flow.Flow<PreviewState> = previewState

                // ネイティブ側でプレビュー開始が完了したことを状態へ反映する。
                override fun onPlatformPreviewStarted(lensFacing: CameraLensFacing) {
                    if (pendingLensFacing == null || pendingLensFacing == lensFacing) {
                        pendingLensFacing = lensFacing
                        previewState.value = PreviewState.Showing
                    }
                }

                // ネイティブ側のプレビュー開始失敗を状態へ反映する。
                override fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError) {
                    if (pendingLensFacing == lensFacing) {
                        pendingLensFacing = null
                        previewState.value = PreviewState.Error(error)
                    }
                }
            },
            permissionRepository = object : PermissionRepository {
                // 現在の iOS カメラ権限状態を共通の PermissionState へ変換する。
                private fun currentPermissionState(): PermissionState {
                    return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
                        AVAuthorizationStatusAuthorized -> PermissionState.Granted
                        AVAuthorizationStatusNotDetermined -> PermissionState.Unknown
                        else -> PermissionState.Denied
                    }
                }

                // 現在のカメラ権限状態を確認する。
                override suspend fun checkCameraPermission(): PermissionState {
                    return currentPermissionState()
                }

                // カメラ権限要求を実行し、その後の状態を返す。
                override suspend fun requestCameraPermission(): PermissionState {
                    permissionController.requestPermission()
                    return currentPermissionState()
                }
            },
        )
    }
}

// 指定レンズが使えない場合に代替レンズを含めて利用可否を解決する。
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

// iOS で front camera と ARKit が両方使えるときだけ face tracking を有効化する。
private fun CameraLensFacing.shouldUseIosFaceTracking(): Boolean {
    return this == CameraLensFacing.Front && ARFaceTrackingConfiguration.isSupported
}

// 共通のレンズ向きを AVFoundation のデバイス向きへ変換する。
private fun CameraLensFacing.toDevicePosition() = when (this) {
    CameraLensFacing.Front -> AVCaptureDevicePositionFront
    CameraLensFacing.Back -> AVCaptureDevicePositionBack
}

// 指定位置に対応する iOS カメラデバイスを取得する。
private fun cameraDevice(position: platform.AVFoundation.AVCaptureDevicePosition): AVCaptureDevice? {
    return AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
        .filterIsInstance<AVCaptureDevice>()
        .firstOrNull { it.position == position }
}

// ARKit delegate から shared face-tracking frame へ変換して Compose 側へ流す。
private class IOSFaceTrackingSessionDelegate : NSObject(), ARSessionDelegateProtocol {
    var onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit = {}
    private var previousFrame: NormalizedFaceFrame? = null
    private var lastDispatchedFaceFrameSeconds: Double? = null
    private var trackingConfidence: Float = 1f

    // 追加・更新された face anchor を shared frame として通知する。
    @ObjCSignatureOverride
    override fun session(session: ARSession, didAddAnchors: List<*>) {
        publishFaceFrame(anchors = didAddAnchors)
    }

    // 継続中の face anchor 更新を shared frame として通知する。
    @ObjCSignatureOverride
    override fun session(session: ARSession, didUpdateAnchors: List<*>) {
        publishFaceFrame(anchors = didUpdateAnchors)
    }

    // face anchor が消えたときに tracking state を初期化する。
    @ObjCSignatureOverride
    override fun session(session: ARSession, didRemoveAnchors: List<*>) {
        if (didRemoveAnchors.any { it is ARFaceAnchor }) {
            clearTrackedFace()
            dispatchFaceFrame(null)
        }
    }

    // ARKit tracking が不安定になったときは shared face state を破棄する。
    override fun session(session: ARSession, cameraDidChangeTrackingState: platform.ARKit.ARCamera) {
        trackingConfidence = when (cameraDidChangeTrackingState.trackingState) {
            ARTrackingState.ARTrackingStateNormal -> 1f
            ARTrackingState.ARTrackingStateLimited -> IOS_LIMITED_TRACKING_CONFIDENCE
            else -> 0f
        }
        if (trackingConfidence == 0f) {
            clearTrackedFace(resetTrackingConfidence = false)
            dispatchFaceFrame(null)
        }
    }

    // セッション失敗時に tracking state を破棄する。
    override fun session(session: ARSession, didFailWithError: NSError) {
        clearTrackedFace()
        dispatchFaceFrame(null)
    }

    // セッション中断時に tracking state を破棄する。
    override fun sessionWasInterrupted(session: ARSession) {
        clearTrackedFace()
        dispatchFaceFrame(null)
    }

    // 中断復帰後に古い tracking state を持ち越さないよう初期化する。
    override fun sessionInterruptionEnded(session: ARSession) {
        clearTrackedFace()
        dispatchFaceFrame(null)
    }

    // 保持中の前回フレームを破棄する。
    fun clearTrackedFace(resetTrackingConfidence: Boolean = true) {
        previousFrame = null
        lastDispatchedFaceFrameSeconds = null
        if (resetTrackingConfidence) {
            trackingConfidence = 1f
        }
    }

    // face anchor 群から先頭の顔を抜き出して shared frame へ変換する。
    private fun publishFaceFrame(
        anchors: List<*>,
    ) {
        val faceAnchor = anchors.firstNotNullOfOrNull { it as? ARFaceAnchor }
        val nextFrame = faceAnchor?.toNormalizedFaceFrame(
            trackingConfidence = trackingConfidence,
            previousFrame = previousFrame,
        )
        previousFrame = nextFrame
        dispatchFaceFrame(nextFrame)
    }

    // ARKit delegate は main thread 固定ではないため、shared state 更新は常に main queue へ寄せる。
    private fun dispatchFaceFrame(frame: NormalizedFaceFrame?) {
        if (frame == null) {
            lastDispatchedFaceFrameSeconds = null
        } else {
            val nowSeconds = CACurrentMediaTime()
            val lastDispatchedSeconds = lastDispatchedFaceFrameSeconds
            if (
                lastDispatchedSeconds != null &&
                nowSeconds - lastDispatchedSeconds < IOS_FACE_FRAME_DISPATCH_INTERVAL_SECONDS
            ) {
                return
            }
            lastDispatchedFaceFrameSeconds = nowSeconds
        }
        dispatch_async(dispatch_get_main_queue()) {
            onFaceTrackingFrameChanged(frame)
        }
    }
}

// ARKit face anchor を shared face-tracking frame へ変換する。
private fun ARFaceAnchor.toNormalizedFaceFrame(
    trackingConfidence: Float,
    previousFrame: NormalizedFaceFrame?,
): NormalizedFaceFrame {
    val blendShapes = blendShapes
    val pose = transform.toHeadPoseDegrees()
    val currentFrame = NormalizedFaceFrame(
        timestampMillis = currentMonotonicTimestampMillis(),
        trackingConfidence = trackingConfidence,
        // shared `NormalizedFaceFrame` は Android の front-camera 規約に合わせているため、
        // ARKit の camera-relative な yaw / roll だけを反転し、上下方向の pitch は両者で向きが一致するのでそのまま使う。
        headYawDegrees = -pose.yawDegrees,
        headPitchDegrees = pose.pitchDegrees,
        headRollDegrees = -pose.rollDegrees,
        leftEyeBlink = blendShapes.floatValue(ARBlendShapeLocationEyeBlinkLeft),
        rightEyeBlink = blendShapes.floatValue(ARBlendShapeLocationEyeBlinkRight),
        jawOpen = blendShapes.floatValue(ARBlendShapeLocationJawOpen),
        mouthSmile = (
            blendShapes.floatValue(ARBlendShapeLocationMouthSmileLeft) +
                blendShapes.floatValue(ARBlendShapeLocationMouthSmileRight)
            ) / 2f,
    )
    return smoothFaceTrackingFrame(previousFrame = previousFrame, currentFrame = currentFrame)
}

// ARKit の transform 行列から head pose を度数へ変換する。
private fun CValue<simd_float4x4>.toHeadPoseDegrees(): IOSHeadPoseDegrees {
    var yawRadians = 0f
    var pitchRadians = 0f
    var rollRadians = 0f
    useContents {
        // simd_float4x4 は column-major なので、コードで使う values[8], values[9], values[10],
        // values[4], values[0] はそれぞれ matrix[0,2], matrix[1,2], matrix[2,2], matrix[0,1],
        // matrix[0,0] に対応し、ZYX の rotation matrix 分解として Euler 角へ変換する。
        val values = columns.reinterpret<FloatVar>()
        // asin は [-1, 1] の外を受け取れないため、浮動小数誤差による domain error を避ける。
        pitchRadians = asin((-values[8]).coerceIn(-1f, 1f))
        rollRadians = atan2(values[9], values[10])
        yawRadians = atan2(values[4], values[0])
    }
    return IOSHeadPoseDegrees(
        yawDegrees = yawRadians.toDegrees(),
        pitchDegrees = pitchRadians.toDegrees(),
        rollDegrees = rollRadians.toDegrees(),
    )
}

// ARKit の blendShapes map から 0..1 の値を安全に読み出す。
private fun Map<Any?, *>.floatValue(key: Any?): Float {
    val number = this[key] as? NSNumber ?: return 0f
    return number.floatValue.coerceIn(0f, 1f)
}

// ARKit の生データを Android 側と近い応答にそろえるため平滑化する。
private fun smoothFaceTrackingFrame(
    previousFrame: NormalizedFaceFrame?,
    currentFrame: NormalizedFaceFrame,
): NormalizedFaceFrame {
    if (previousFrame == null) {
        return currentFrame
    }

    return currentFrame.copy(
        headYawDegrees = lerp(
            previousFrame.headYawDegrees,
            currentFrame.headYawDegrees,
            IOS_HEAD_YAW_SMOOTHING_ALPHA,
        ),
        headPitchDegrees = lerp(
            previousFrame.headPitchDegrees,
            currentFrame.headPitchDegrees,
            IOS_HEAD_PITCH_SMOOTHING_ALPHA,
        ),
        headRollDegrees = lerp(
            previousFrame.headRollDegrees,
            currentFrame.headRollDegrees,
            IOS_HEAD_ROLL_SMOOTHING_ALPHA,
        ),
        leftEyeBlink = smoothBlink(previousFrame.leftEyeBlink, currentFrame.leftEyeBlink),
        rightEyeBlink = smoothBlink(previousFrame.rightEyeBlink, currentFrame.rightEyeBlink),
        jawOpen = smoothJaw(previousFrame.jawOpen, currentFrame.jawOpen),
        mouthSmile = lerp(previousFrame.mouthSmile, currentFrame.mouthSmile, IOS_SMILE_SMOOTHING_ALPHA),
    )
}

// まばたきは閉じる方向をやや強めに追従させる。
private fun smoothBlink(previous: Float, current: Float): Float {
    // ARKit の raw 値は 0..1 でも細かな揺れが出るため、Android 側に寄せた閾値で開閉を安定化する。
    val snapped = when {
        current >= IOS_BLINK_HIGH_THRESHOLD -> 1f
        current <= IOS_BLINK_LOW_THRESHOLD -> 0f
        else -> current
    }
    val alpha = if (snapped > previous) IOS_BLINK_CLOSING_ALPHA else IOS_BLINK_OPENING_ALPHA
    return lerp(previous, snapped, alpha).coerceIn(0f, 1f)
}

// 口の開きは開閉速度差を持たせて違和感を抑える。
private fun smoothJaw(previous: Float, current: Float): Float {
    // 口を開く方向は `IOS_JAW_OPENING_ALPHA`、閉じる方向は `IOS_JAW_CLOSING_ALPHA` を使い、
    // 口パクが詰まりすぎず追従感も落とさないようにする。
    val alpha = if (current > previous) IOS_JAW_OPENING_ALPHA else IOS_JAW_CLOSING_ALPHA
    return lerp(previous, current, alpha).coerceIn(0f, 1f)
}

// 共通の線形補間で角度と表情係数をならす。
private fun lerp(start: Float, end: Float, alpha: Float): Float {
    return start + (end - start) * alpha
}

// ラジアンを UI 表示向けの度数へ変換する。
private fun Float.toDegrees(): Float {
    return this * (180f / PI.toFloat())
}

// ARSession.currentFrame を参照すると ARFrame を保持しやすいため、delegate では単調時刻だけを使う。
private fun currentMonotonicTimestampMillis(): Long {
    return (CACurrentMediaTime() * 1000.0).toLong()
}

private data class IOSHeadPoseDegrees(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
)
