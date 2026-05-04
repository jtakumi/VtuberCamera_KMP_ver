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
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.get
import kotlinx.cinterop.readValue
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
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_read_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.vrm_error_read_failed

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
actual fun AvatarBodyOverlay(
    avatarSelection: AvatarSelectionData,
    avatarRenderState: AvatarRenderState,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, org.jetbrains.compose.resources.StringResource) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(avatarSelection.assetHandle) {
        val didPublish = runCatching {
            IOSAvatarRenderInterop.publishSelectedAvatar(avatarSelection)
        }.getOrElse {
            false
        }
        if (!didPublish) {
            onAvatarRenderLoadFailed(
                avatarSelection.assetHandle,
                Res.string.vrm_error_read_failed,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            IOSAvatarRenderInterop.publishClearedAvatar()
        }
    }

    LaunchedEffect(avatarRenderState) {
        IOSAvatarRenderInterop.publishRenderState(avatarRenderState)
    }

    Box(modifier = modifier.fillMaxSize())
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
        val resolvedLens = resolveAvailableLens(requested = requestedLensFacing) { lensFacing ->
            cameraDevice(lensFacing.toDevicePosition()) != null
        }
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
        startIosFaceTrackingPreview(
            isSupported = ARFaceTrackingConfiguration.isSupported,
            prepareTracking = {
                sessionDelegate.onFaceTrackingFrameChanged = onFaceTrackingFrameChanged
                sessionDelegate.clearTrackedFace()
            },
            runSession = {
                val configuration = ARFaceTrackingConfiguration().apply {
                    setLightEstimationEnabled(true)
                }
                runCatching {
                    previewView.session.runWithConfiguration(configuration)
                }.exceptionOrNull()
            },
            onComplete = onComplete,
        )
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
            cameraRepository = IOSCameraRepository(
                hasLens = { lensFacing -> cameraDevice(lensFacing.toDevicePosition()) != null },
                previewState = previewState,
            ),
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
    private val delegateCore = IOSFaceTrackingDelegateCore<ARFaceAnchor>(
        firstFaceAnchor = { anchors -> anchors.firstNotNullOfOrNull { it as? ARFaceAnchor } },
        convertFaceAnchor = { anchor, trackingConfidence, previousFrame ->
            anchor.toNormalizedFaceFrame(
                trackingConfidence = trackingConfidence,
                previousFrame = previousFrame,
            )
        },
        currentTimeSeconds = { platform.QuartzCore.CACurrentMediaTime() },
        onFaceTrackingFrameChanged = { frame ->
            dispatch_async(dispatch_get_main_queue()) {
                onFaceTrackingFrameChanged(frame)
            }
        },
    )

    // 追加・更新された face anchor を shared frame として通知する。
    @ObjCSignatureOverride
    override fun session(session: ARSession, didAddAnchors: List<*>) {
        delegateCore.didAddAnchors(didAddAnchors)
    }

    // 継続中の face anchor 更新を shared frame として通知する。
    @ObjCSignatureOverride
    override fun session(session: ARSession, didUpdateAnchors: List<*>) {
        delegateCore.didUpdateAnchors(didUpdateAnchors)
    }

    // face anchor が消えたときに tracking state を初期化する。
    @ObjCSignatureOverride
    override fun session(session: ARSession, didRemoveAnchors: List<*>) {
        delegateCore.didRemoveAnchors(didRemoveAnchors)
    }

    // ARKit tracking が不安定になったときは shared face state を破棄する。
    override fun session(session: ARSession, cameraDidChangeTrackingState: platform.ARKit.ARCamera) {
        val trackingState = when (cameraDidChangeTrackingState.trackingState) {
            ARTrackingState.ARTrackingStateNormal -> IOSFaceTrackingState.Normal
            ARTrackingState.ARTrackingStateLimited -> IOSFaceTrackingState.Limited
            else -> IOSFaceTrackingState.Unavailable
        }
        delegateCore.didChangeTrackingState(trackingState)
    }

    // セッション失敗時に tracking state を破棄する。
    override fun session(session: ARSession, didFailWithError: NSError) {
        delegateCore.didFailOrInterrupt()
    }

    // セッション中断時に tracking state を破棄する。
    override fun sessionWasInterrupted(session: ARSession) {
        delegateCore.didFailOrInterrupt()
    }

    // 中断復帰後に古い tracking state を持ち越さないよう初期化する。
    override fun sessionInterruptionEnded(session: ARSession) {
        delegateCore.didFailOrInterrupt()
    }

    // 保持中の前回フレームを破棄する。
    fun clearTrackedFace(resetTrackingConfidence: Boolean = true) {
        delegateCore.clearTrackedFace(resetTrackingConfidence = resetTrackingConfidence)
    }
}

// ARKit face anchor を shared face-tracking frame へ変換する。
private fun ARFaceAnchor.toNormalizedFaceFrame(
    trackingConfidence: Float,
    previousFrame: NormalizedFaceFrame?,
): NormalizedFaceFrame {
    val blendShapes = blendShapes
    val pose = transform.toHeadPoseDegrees()
    return pose.toNormalizedFaceFrame(
        timestampMillis = currentMonotonicTimestampMillis(),
        trackingConfidence = trackingConfidence,
        blendShapes = IOSFaceTrackingBlendShapeValues(
            leftEyeBlink = blendShapes.floatValue(ARBlendShapeLocationEyeBlinkLeft),
            rightEyeBlink = blendShapes.floatValue(ARBlendShapeLocationEyeBlinkRight),
            jawOpen = blendShapes.floatValue(ARBlendShapeLocationJawOpen),
            smileLeft = blendShapes.floatValue(ARBlendShapeLocationMouthSmileLeft),
            smileRight = blendShapes.floatValue(ARBlendShapeLocationMouthSmileRight),
        ),
        previousFrame = previousFrame,
    )
}

// ARKit の blendShapes map から 0..1 の値を安全に読み出す。
private fun Map<Any?, *>.floatValue(key: Any?): Float {
    val number = this[key] as? NSNumber ?: return 0f
    return number.floatValue.coerceIn(0f, 1f)
}

// ARSession.currentFrame を参照すると ARFrame を保持しやすいため、delegate では単調時刻だけを使う。
private fun currentMonotonicTimestampMillis(): Long {
    return (platform.QuartzCore.CACurrentMediaTime() * 1000.0).toLong()
}
