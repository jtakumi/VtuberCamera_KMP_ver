package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundMode
import com.example.vtubercamera_kmp_ver.camera.capturemode.CameraCaptureMode
import com.example.vtubercamera_kmp_ver.camera.gesture.PinchGestureTarget
import com.example.vtubercamera_kmp_ver.camera.permission.CameraPermissionUiState
import com.example.vtubercamera_kmp_ver.camera.session.CameraSessionUiState
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageSettings
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsActions
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsUiState
import com.example.vtubercamera_kmp_ver.camera.settings.LocalStoredMediaReader
import com.example.vtubercamera_kmp_ver.camera.settings.PickedMediaKind
import com.example.vtubercamera_kmp_ver.camera.ui.CAMERA_CAPTURE_BAR_HEIGHT
import com.example.vtubercamera_kmp_ver.camera.ui.CameraBackgroundImageLayer
import com.example.vtubercamera_kmp_ver.camera.ui.CameraCaptureBar
import com.example.vtubercamera_kmp_ver.camera.ui.CameraSettingsDialog
import com.example.vtubercamera_kmp_ver.camera.ui.CameraTopBar
import com.example.vtubercamera_kmp_ver.camera.ui.CameraUiHideButton
import com.example.vtubercamera_kmp_ver.camera.ui.overlayGlassTone
import com.example.vtubercamera_kmp_ver.theme.rememberLiquidGlassAppearance
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassAppearance
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassCornerStyle
import com.example.vtubercamera_kmp_ver.theme.LiquidGlassSurface
import com.example.vtubercamera_kmp_ver.theme.spacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_error_dialog_confirm
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_error_dialog_title
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_granted_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_request_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_required_message
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_retry_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_ui_show_action
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_video_stop_action

/**
 * 共有 camera route を構成し、必要に応じて renderer layer へ custom renderer host を注入する。
 *
 * @param rendererHost custom renderer slot の実装。既定値は [defaultCameraRendererHost] で、
 * 現在の overlay ベースの avatar body 表示を維持する。
 */
@Composable
fun CameraRoute(
    modifier: Modifier = Modifier,
    rendererHost: CameraRendererHost = defaultCameraRendererHost,
) {
    val permissionController = rememberCameraPermissionController()
    val repositories = rememberCameraRepositories(permissionController)
    val cameraViewModel: CameraViewModel = viewModel {
        CameraViewModel(
            cameraRepository = repositories.cameraRepository,
            permissionRepository = repositories.permissionRepository,
            settingsPlatform = repositories.settingsPlatform,
        )
    }
    val filePickerLauncher = rememberFilePickerLauncher(cameraViewModel::onFilePicked)
    val bgmPickerLauncher = rememberMediaPickerLauncher(
        kind = PickedMediaKind.Bgm,
        onPicked = cameraViewModel::onBgmPickResult,
    )
    val backgroundImagePickerLauncher = rememberMediaPickerLauncher(
        kind = PickedMediaKind.BackgroundImage,
        onPicked = cameraViewModel::onBackgroundImagePickResult,
    )
    val settingsActions = remember(cameraViewModel, bgmPickerLauncher, backgroundImagePickerLauncher) {
        CameraSettingsActions(
            onPickBgm = bgmPickerLauncher.launch,
            onClearBgm = cameraViewModel::onClearBgm,
            onToggleBgmPlayback = cameraViewModel::onToggleBgmPlayback,
            onBgmVolumeChanged = cameraViewModel::onBgmVolumeChanged,
            onBgmVolumeChangeFinished = cameraViewModel::onBgmVolumeChangeFinished,
            onToggleBgmStartsWithRecording = cameraViewModel::onToggleBgmStartsWithRecording,
            onToggleNoiseSuppression = cameraViewModel::onToggleNoiseSuppression,
            onPickBackgroundImage = backgroundImagePickerLauncher.launch,
            onClearBackgroundImage = cameraViewModel::onClearBackgroundImage,
            onBackgroundImageCropChanged = cameraViewModel::onBackgroundImageCropChanged,
            onBackgroundImageCropChangeFinished = cameraViewModel::onBackgroundImageCropChangeFinished,
            onDismissMessage = cameraViewModel::onDismissSettingsMessage,
        )
    }
    val uiState by cameraViewModel.uiState.collectAsStateWithLifecycle()

    // 設定画面で流している BGM が、アプリを離れた後も鳴り続けないようにする。
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        cameraViewModel.onAppBackgrounded()
    }

    LaunchedEffect(Unit) {
        cameraViewModel.initialize()
    }

    LaunchedEffect(permissionController.isGranted, permissionController.isChecking) {
        cameraViewModel.onPermissionStateChanged(
            isGranted = permissionController.isGranted,
            isChecking = permissionController.isChecking,
        )
    }

    CompositionLocalProvider(
        LocalStoredMediaReader provides repositories.settingsPlatform.settingsRepository,
    ) {
        CameraScreen(
            cameraRepository = repositories.cameraRepository,
            session = uiState.session,
            permission = uiState.permission,
            zoom = uiState.zoom,
            photoCapture = uiState.photoCapture,
            photoDeletion = uiState.photoDeletion,
            captureMode = uiState.captureMode.mode,
            videoRecording = uiState.videoRecording,
            avatarRender = uiState.avatarRender,
            avatarSelection = uiState.avatarSelection.avatarSelection,
            filePickerErrorMessageRes = uiState.avatarSelection.filePickerErrorMessageRes,
            avatarScale = uiState.avatarScale.currentAvatarScale,
            backgroundMode = uiState.background.mode,
            settings = uiState.settings,
            settingsActions = settingsActions,
            pinchTarget = uiState.effectivePinchTarget,
            isUiHidden = uiState.uiVisibility.isHidden,
            onRequestPermission = cameraViewModel::onRequestPermission,
            onRetryPreview = cameraViewModel::onRetryPreview,
            onOpenFilePicker = filePickerLauncher.launch,
            onDismissFilePickerError = cameraViewModel::onDismissFilePickerError,
            onAvatarRenderLoadFailed = cameraViewModel::onAvatarRenderLoadFailed,
            onFaceTrackingFrameChanged = cameraViewModel::onFaceTrackingFrameChanged,
            onLensFacingChanged = cameraViewModel::onLensFacingChanged,
            onCameraZoomChanged = cameraViewModel::onCameraZoomChanged,
            onAvatarScaleChanged = cameraViewModel::onAvatarScaleChanged,
            onToggleBackgroundMode = cameraViewModel::onToggleBackgroundMode,
            onHideUi = cameraViewModel::onHideUi,
            onShowUi = cameraViewModel::onShowUi,
            onCapturePhoto = cameraViewModel::onCapturePhoto,
            onToggleCaptureMode = cameraViewModel::onToggleCaptureMode,
            onToggleVideoRecording = cameraViewModel::onToggleVideoRecording,
            modifier = modifier,
            rendererHost = rendererHost,
        )
    }
}

/**
 * 共有 camera screen を描画し、必要に応じて renderer layer へ custom renderer host を注入する。
 *
 * [isUiHidden] が true のあいだは UI 非表示モードとして操作 UI を描かない。このとき画面のどこを
 * タップしても [onShowUi] が呼ばれて操作 UI へ戻れる。[onHideUi] は操作 UI 上の非表示ボタンから呼ばれる。
 *
 * 動画の録画は画面そのものを撮るため、[videoRecording] が進行中のあいだは [isUiHidden] によらず操作 UI を
 * 描かず、映像へ映り込まないようにする。このとき画面タップは録画の停止（[onToggleVideoRecording]）になる。
 *
 * 設定画面は操作 UI の設定ボタンから開く。[settings] は設定画面と背景画像の描画に使う設定の状態で、
 * 設定画面の操作は [settingsActions] を通じて伝える。
 *
 * @param rendererHost custom renderer slot の実装。既定値は [defaultCameraRendererHost] で、
 * 現在の overlay ベースの avatar body 表示を維持する。
 */
@Composable
fun CameraScreen(
    cameraRepository: CameraRepository,
    session: CameraSessionUiState,
    permission: CameraPermissionUiState,
    zoom: CameraZoomUiState,
    photoCapture: PhotoCaptureState,
    photoDeletion: PhotoDeletionState,
    captureMode: CameraCaptureMode,
    videoRecording: VideoRecordingState,
    avatarRender: AvatarRenderState,
    avatarSelection: AvatarSelectionData?,
    filePickerErrorMessageRes: StringResource?,
    avatarScale: Float,
    backgroundMode: CameraBackgroundMode,
    settings: CameraSettingsUiState,
    settingsActions: CameraSettingsActions,
    pinchTarget: PinchGestureTarget,
    isUiHidden: Boolean,
    onRequestPermission: () -> Unit,
    onRetryPreview: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onDismissFilePickerError: () -> Unit,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onCameraZoomChanged: (Float) -> Unit,
    onAvatarScaleChanged: (Float) -> Unit,
    onToggleBackgroundMode: () -> Unit,
    onHideUi: () -> Unit,
    onShowUi: () -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleCaptureMode: () -> Unit,
    onToggleVideoRecording: () -> Unit,
    modifier: Modifier = Modifier,
    rendererHost: CameraRendererHost = defaultCameraRendererHost,
) {
    val glass = rememberLiquidGlassAppearance(backgroundMode.overlayGlassTone)
    val previewError = session.previewState as? PreviewState.Error
    val cameraMessage = photoDeletion.toCameraMessage()
        ?: photoCapture.toCameraMessage()
        ?: videoRecording.toCameraMessage()
        ?: session.message
    var visibleCameraMessage by remember { mutableStateOf<CameraMessage?>(null) }

    LaunchedEffect(cameraMessage) {
        visibleCameraMessage = cameraMessage
        if (cameraMessage != null) {
            delay(CAMERA_MESSAGE_DURATION_MILLIS)
            visibleCameraMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        when {
            permission.permissionState == PermissionState.Unknown -> LoadingState(glass)
            permission.permissionState == PermissionState.Denied -> PermissionDeniedState(
                onRequestPermission = onRequestPermission,
                glass = glass,
            )

            previewError != null -> CameraErrorState(
                error = previewError.error,
                onRetryPreview = onRetryPreview,
                glass = glass,
            )

            permission.permissionState == PermissionState.Granted -> CameraPreviewState(
                cameraRepository = cameraRepository,
                lensFacing = session.lensFacing,
                zoomScale = zoom.currentCameraZoomRatio,
                backgroundMode = backgroundMode,
                settings = settings,
                settingsActions = settingsActions,
                avatarSelection = avatarSelection,
                avatarRenderState = avatarRender,
                avatarScale = avatarScale,
                pinchTarget = pinchTarget,
                glass = glass,
                photoCapture = photoCapture,
                captureMode = captureMode,
                videoRecording = videoRecording,
                isUiHidden = isUiHidden,
                rendererHost = rendererHost,
                onOpenFilePicker = onOpenFilePicker,
                onAvatarRenderLoadFailed = onAvatarRenderLoadFailed,
                onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
                onLensFacingChanged = onLensFacingChanged,
                onCameraZoomChanged = onCameraZoomChanged,
                onAvatarScaleChanged = onAvatarScaleChanged,
                onToggleBackgroundMode = onToggleBackgroundMode,
                onHideUi = onHideUi,
                onShowUi = onShowUi,
                onCapturePhoto = onCapturePhoto,
                onToggleCaptureMode = onToggleCaptureMode,
                onToggleVideoRecording = onToggleVideoRecording,
            )

            else -> LoadingState(glass)
        }

        visibleCameraMessage?.let { message ->
            CameraMessageBanner(
                message = message,
                glass = glass,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    // 操作バーの真上に出す。バーの高さが変わっても重ならないよう実寸から余白を決める。
                    .padding(
                        start = MaterialTheme.spacing.lg,
                        end = MaterialTheme.spacing.lg,
                        bottom = CAMERA_CAPTURE_BAR_HEIGHT + MaterialTheme.spacing.md,
                    ),
            )
        }

        filePickerErrorMessageRes?.let { messageRes ->
            GlassErrorDialog(
                onDismissRequest = onDismissFilePickerError,
                title = stringResource(Res.string.avatar_error_dialog_title),
                message = stringResource(messageRes),
                confirmLabel = stringResource(Res.string.avatar_error_dialog_confirm),
                glass = glass,
            )
        }
    }
}

private const val CAMERA_MESSAGE_DURATION_MILLIS = 3_000L

// 画面の大きさが決まる前に使う縦横比。縦向きの一般的なスマートフォンに合わせる。
private const val DEFAULT_SCREEN_ASPECT_RATIO = 9f / 19.5f

// カメラ画面のレイヤー重ね順。数値が大きいほど手前に描画される。アバターは画面全体を使えるため、
// 操作 UI が常にアバターより手前になるようにここで順序を固定する。
private const val CAMERA_BACKGROUND_LAYER_Z_INDEX = 0f
private const val AVATAR_RENDERER_LAYER_Z_INDEX = 1f
private const val PINCH_GESTURE_LAYER_Z_INDEX = 2f
private const val CAMERA_CONTROLS_LAYER_Z_INDEX = 3f

@Composable
private fun CameraPreviewState(
    cameraRepository: CameraRepository,
    lensFacing: CameraLensFacing,
    zoomScale: Float,
    backgroundMode: CameraBackgroundMode,
    settings: CameraSettingsUiState,
    settingsActions: CameraSettingsActions,
    avatarSelection: AvatarSelectionData?,
    avatarRenderState: AvatarRenderState,
    avatarScale: Float,
    pinchTarget: PinchGestureTarget,
    glass: LiquidGlassAppearance,
    photoCapture: PhotoCaptureState,
    captureMode: CameraCaptureMode,
    videoRecording: VideoRecordingState,
    isUiHidden: Boolean,
    rendererHost: CameraRendererHost,
    onOpenFilePicker: () -> Unit,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onCameraZoomChanged: (Float) -> Unit,
    onAvatarScaleChanged: (Float) -> Unit,
    onToggleBackgroundMode: () -> Unit,
    onHideUi: () -> Unit,
    onShowUi: () -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleCaptureMode: () -> Unit,
    onToggleVideoRecording: () -> Unit,
) {
    val avatarPreview = avatarSelection?.preview
    val showUiActionLabel = stringResource(Res.string.camera_ui_show_action)
    val stopRecordingActionLabel = stringResource(Res.string.camera_video_stop_action)
    val isRecordingInProgress = videoRecording.isInProgress
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    // 背景画像の範囲は画面の縦横比で決まる。設定画面の調整枠にも同じ比率を渡し、見た範囲がそのまま映るようにする。
    var screenAspectRatio by remember { mutableFloatStateOf(DEFAULT_SCREEN_ASPECT_RATIO) }

    // レイヤーは背景 → アバター → ジェスチャー → 操作 UI の順で重ねる。アバターを拡大しても
    // ボタン類が隠れないよう、z 順を zIndex で明示して操作 UI を常に最前面に置く。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    screenAspectRatio = size.width.toFloat() / size.height
                }
            },
    ) {
        CameraBackgroundLayer(
            cameraRepository = cameraRepository,
            lensFacing = lensFacing,
            zoomScale = DEFAULT_CAMERA_ZOOM_SCALE,
            backgroundMode = backgroundMode,
            backgroundImage = settings.settings.backgroundImage,
            captureMode = captureMode,
            onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
            onLensFacingChanged = onLensFacingChanged,
            modifier = Modifier.zIndex(CAMERA_BACKGROUND_LAYER_Z_INDEX),
        )
        CameraRendererLayer(
            avatarSelection = avatarSelection,
            avatarPreview = avatarPreview,
            avatarRenderState = avatarRenderState,
            avatarScale = avatarScale,
            backgroundMode = backgroundMode,
            isBackgroundImageActive = settings.settings.backgroundImage.image != null,
            onAvatarRenderLoadFailed = onAvatarRenderLoadFailed,
            rendererHost = rendererHost,
        )
        // ピンチジェスチャーを検出する透明オーバーレイ。ボタンより下に配置して操作を妨げない。
        // 切り替えボタンで選んだ対象へジェスチャーを振り分けるため、対象が変わったら検出を貼り直す。
        // UI 非表示モード中はボタンが無いので、タップで操作 UI へ戻れるようにする。操作 UI を隠してもピンチは
        // 効かせ続けたいため、タップ検出は別レイヤーにせずこの層へ足す。
        Box(
            modifier = Modifier
                .matchParentSize()
                .zIndex(PINCH_GESTURE_LAYER_Z_INDEX)
                .then(
                    when {
                        // 録画中は画面タップが停止操作になる。UI の再表示は録画の終了後に任せる。
                        isRecordingInProgress -> Modifier.stopRecordingOnTap(
                            label = stopRecordingActionLabel,
                            onStopRecording = onToggleVideoRecording,
                        )
                        isUiHidden -> Modifier.revealUiOnTap(
                            label = showUiActionLabel,
                            onShowUi = onShowUi,
                        )
                        else -> Modifier
                    },
                )
                .pointerInput(pinchTarget) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        when (pinchTarget) {
                            PinchGestureTarget.CameraZoom -> onCameraZoomChanged(zoomChange)
                            PinchGestureTarget.AvatarScale -> onAvatarScaleChanged(zoomChange)
                        }
                    }
                },
        )
        // UI 非表示モード中は操作 UI を composition から外す。透明にするだけだと、見えないボタンが
        // 押せてしまううえ、iOS では OS 製ガラスの interop view が画面に残るおそれがある。
        if (!isUiHidden && !isRecordingInProgress) {
            CameraUiLayer(
                zoomScale = zoomScale,
                avatarScale = avatarScale,
                pinchTarget = pinchTarget,
                canTogglePinchTarget = avatarSelection != null,
                backgroundMode = backgroundMode,
                onToggleBackgroundMode = onToggleBackgroundMode,
                onHideUi = onHideUi,
                onOpenFilePicker = onOpenFilePicker,
                onOpenSettings = { isSettingsOpen = true },
                onCapturePhoto = onCapturePhoto,
                isCapturingPhoto = photoCapture == PhotoCaptureState.Capturing,
                captureMode = captureMode,
                onToggleCaptureMode = onToggleCaptureMode,
                onToggleVideoRecording = onToggleVideoRecording,
                glass = glass,
            )
            if (isSettingsOpen) {
                CameraSettingsDialog(
                    state = settings,
                    actions = settingsActions,
                    viewportAspectRatio = screenAspectRatio,
                    glass = glass,
                    onDismissRequest = { isSettingsOpen = false },
                )
            }
        }
    }
}

/**
 * UI 非表示モード中に、画面のどこをタップしても [onShowUi] を呼べるようにする。
 *
 * ピンチ検出より外側（先に付けた側）へ置くこと。pointer event は内側の modifier から先に届くため、
 * ピンチやドラッグで消費された操作はタップとして扱われず、ピンチ操作の途中で操作 UI が再表示されない。
 * 画面全体が 1 つのボタンとして読み上げられるよう、[label] を semantics の名前と click の操作名に設定する。
 * これによりスクリーンリーダーの click 操作でも再表示へ戻れる。
 */
private fun Modifier.revealUiOnTap(
    label: String,
    onShowUi: () -> Unit,
): Modifier = this
    .pointerInput(onShowUi) {
        detectTapGestures(onTap = { onShowUi() })
    }
    .semantics {
        role = Role.Button
        contentDescription = label
        onClick(label = label) {
            onShowUi()
            true
        }
    }

/**
 * 録画中に、画面のどこをタップしても [onStopRecording] を呼べるようにする。
 *
 * 録画中は操作 UI を隠しているため、停止の入口はこのタップだけになる。[revealUiOnTap] と同じく、
 * ピンチ検出より外側へ置いてピンチ操作がタップとして扱われないようにし、画面全体を 1 つのボタンとして
 * [label] で読み上げる。開始準備中や保存中のタップは、受け取った側（録画 controller）が無視する。
 */
private fun Modifier.stopRecordingOnTap(
    label: String,
    onStopRecording: () -> Unit,
): Modifier = this
    .pointerInput(onStopRecording) {
        detectTapGestures(onTap = { onStopRecording() })
    }
    .semantics {
        role = Role.Button
        contentDescription = label
        onClick(label = label) {
            onStopRecording()
            true
        }
    }

/**
 * カメラ映像の背景レイヤーを全画面で表示し、face tracking 更新を preview host へ渡す。
 *
 * preview host の上へ常に不透明な単色を重ね、設定した背景画像があればさらにその上へ重ねて、実際の顔が映らないようにする。
 * iOS では preview host の native view が Compose の描画より前面に出るため、背景画像は [backgroundImage] として
 * preview host 側へも渡し、native view の内側で描く。
 * preview host 自体は常に構成したままにして face tracking を
 * 止めないため、背景を隠してもアバターは顔の動きに追従し続ける。
 */
@Composable
private fun CameraBackgroundLayer(
    cameraRepository: CameraRepository,
    lensFacing: CameraLensFacing,
    zoomScale: Float,
    backgroundMode: CameraBackgroundMode,
    backgroundImage: BackgroundImageSettings,
    captureMode: CameraCaptureMode,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        CameraPreviewHost(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                },
            cameraRepository = cameraRepository,
            lensFacing = lensFacing,
            backgroundMode = backgroundMode,
            backgroundImage = backgroundImage,
            captureMode = captureMode,
            onLensFacingChanged = onLensFacingChanged,
            onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundMode.overlayColor),
        )
        // 背景画像は単色の上に重ねる。画像を読み込む間や読み込めないときは、単色がそのまま見える。
        CameraBackgroundImageLayer(
            backgroundImage = backgroundImage,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * platform renderer host を差し込む中間レイヤーを構成する。
 *
 * 現在は avatar 選択済みのときだけ既定の static overlay host を表示する。
 *
 * @param rendererHost platform-specific または custom renderer を差し込む slot。
 * [RendererHostSlotState] を受け取り、CameraScreen が決めた renderer layer 上へ描画する。
 */
@Composable
private fun BoxScope.CameraRendererLayer(
    avatarSelection: AvatarSelectionData?,
    avatarPreview: AvatarPreviewData?,
    avatarRenderState: AvatarRenderState,
    avatarScale: Float,
    backgroundMode: CameraBackgroundMode,
    isBackgroundImageActive: Boolean,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    rendererHost: CameraRendererHost = defaultCameraRendererHost,
) {
    // renderer host は avatar 選択済みのときだけ差し込む。
    if (avatarSelection != null && avatarPreview != null) {
        rendererHost(
            RendererHostSlotState(
                avatarSelection = avatarSelection,
                avatarPreview = avatarPreview,
                avatarRenderState = avatarRenderState,
                avatarScale = avatarScale,
                backgroundMode = backgroundMode,
                isBackgroundImageActive = isBackgroundImageActive,
                onAvatarRenderLoadFailed = onAvatarRenderLoadFailed,
                // アバターの表示可能領域は画面全体。拡大しても操作 UI に隠されるだけで
                // 画面外へ切り取られないよう、余白を持たせずに全面へ広げる。
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(AVATAR_RENDERER_LAYER_Z_INDEX),
            ),
        )
    }
}

/**
 * renderer host が camera preview layer に avatar content を描画するための共有文脈を保持する。
 *
 * [avatarSelection] は renderer が使う選択済み asset handle / runtime 情報、[avatarPreview] は
 * 表示用のメタ情報、[avatarRenderState] は renderer host が参照する共有の tracking / render state、
 * [avatarScale] はピンチ操作で決まったアバター表示倍率、[onAvatarRenderLoadFailed] は renderer 側の
 * 読み込み失敗を UI へ戻す callback、[modifier] は CameraScreen 側で決めた renderer layer の
 * 配置情報（画面全体を占める layer）を表す。
 */
data class RendererHostSlotState(
    /** renderer host が参照する選択済み avatar の asset handle / runtime 情報。 */
    val avatarSelection: AvatarSelectionData,
    /** renderer host が参照する選択済み avatar のメタ情報。 */
    val avatarPreview: AvatarPreviewData,
    /** renderer host が参照する共有の avatar tracking / render state。 */
    val avatarRenderState: AvatarRenderState,
    /** ピンチ操作で決まったアバター表示倍率。1.0 が既定サイズを表す。 */
    val avatarScale: Float,
    /** renderer 側の読み込み失敗を UI へ戻す callback。 */
    val onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    /** CameraScreen 側で決めた renderer layer の配置。画面全体を占める layer を表す。 */
    val modifier: Modifier,
    /** renderer layer の背面に描画する背景。iOS の native renderer へも渡す。 */
    val backgroundMode: CameraBackgroundMode,
    /** 背景画像を表示中か。true のとき、native renderer は単色で塗りつぶさず背面の画像を見せる。 */
    val isBackgroundImageActive: Boolean,
)

/**
 * platform-specific または custom avatar renderer を CameraScreen へ差し込む拡張ポイント。
 *
 * [BoxScope] receiver は CameraScreen 内の renderer layer に対応する。
 * 実装側は [RendererHostSlotState] から avatar のメタ情報、共有 render state、配置 modifier を
 * 受け取り、その layer 内に avatar content を描画する。
 */
typealias CameraRendererHost = @Composable BoxScope.(RendererHostSlotState) -> Unit

/** 既定の avatar renderer host 実装を [CameraRendererHost] の型で保持する。 */
private val defaultCameraRendererHost: CameraRendererHost = { state ->
    DefaultAvatarRendererHost(state)
}

/**
 * 現在の既定 renderer host 実装。
 *
 * [RendererHostSlotState.avatarPreview] を使って現在の static avatar overlay を表示しつつ、
 * 将来の dynamic renderer 実装へ向けて [RendererHostSlotState.avatarRenderState] も
 * slot 契約のまま保持する。
 */
@Composable
private fun DefaultAvatarRendererHost(
    state: RendererHostSlotState,
) {
    AvatarBodyOverlay(
        avatarSelection = state.avatarSelection,
        avatarRenderState = state.avatarRenderState,
        avatarScale = state.avatarScale,
        backgroundMode = state.backgroundMode,
        isBackgroundImageActive = state.isBackgroundImageActive,
        onAvatarRenderLoadFailed = state.onAvatarRenderLoadFailed,
        modifier = state.modifier,
    )
}

/**
 * カメラ操作 UI を前景レイヤーとして重ねる。
 *
 * アバターが画面全体まで拡大してもボタン類が隠れないよう、この layer は他のどの layer よりも
 * 大きい [CAMERA_CONTROLS_LAYER_Z_INDEX] を持つ。
 *
 * 操作 UI は Liquid Glass の面として描く。背後に来るのはカメラ映像か背景プリセットなので、
 * ガラスの明暗は [backgroundMode] から決めて上部バーと操作バーで同じ 1 枚として揃える。
 *
 * UI 非表示モードに入るボタン [onHideUi] は、チップ 3 つで幅が埋まる上部バーの下、右端に置く。
 */
@Composable
private fun BoxScope.CameraUiLayer(
    zoomScale: Float,
    avatarScale: Float,
    pinchTarget: PinchGestureTarget,
    canTogglePinchTarget: Boolean,
    backgroundMode: CameraBackgroundMode,
    onToggleBackgroundMode: () -> Unit,
    onHideUi: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onCapturePhoto: () -> Unit,
    isCapturingPhoto: Boolean,
    captureMode: CameraCaptureMode,
    onToggleCaptureMode: () -> Unit,
    onToggleVideoRecording: () -> Unit,
    glass: LiquidGlassAppearance,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .zIndex(CAMERA_CONTROLS_LAYER_Z_INDEX)
            .statusBarsPadding()
            // チップ 3 つを 1 行に収めるため、上部バーだけ画面端の余白を狭める。
            .padding(MaterialTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        CameraTopBar(
            zoomScale = zoomScale,
            avatarScale = avatarScale,
            pinchTarget = pinchTarget,
            canTogglePinchTarget = canTogglePinchTarget,
            backgroundMode = backgroundMode,
            onToggleBackgroundMode = onToggleBackgroundMode,
            glass = glass,
            modifier = Modifier.fillMaxWidth(),
        )
        CameraUiHideButton(
            onClick = onHideUi,
            glass = glass,
            modifier = Modifier.align(Alignment.End),
        )
    }
    CameraCaptureBar(
        onOpenFilePicker = onOpenFilePicker,
        onOpenSettings = onOpenSettings,
        onCapturePhoto = onCapturePhoto,
        isCapturingPhoto = isCapturingPhoto,
        captureMode = captureMode,
        onToggleCaptureMode = onToggleCaptureMode,
        onToggleVideoRecording = onToggleVideoRecording,
        glass = glass,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .zIndex(CAMERA_CONTROLS_LAYER_Z_INDEX)
            .navigationBarsPadding()
            // navigationBarsPadding() で OS の操作領域を避けるため、ここでは下余白を加えない。
            .padding(
                start = MaterialTheme.spacing.lg,
                top = MaterialTheme.spacing.lg,
                end = MaterialTheme.spacing.lg,
            ),
    )
}

@Composable
private fun CameraMessageBanner(
    message: CameraMessage,
    glass: LiquidGlassAppearance,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        appearance = glass,
        cornerStyle = LiquidGlassCornerStyle.Rounded(MaterialTheme.spacing.md),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                message.messageRes,
                *message.formatArgs.toTypedArray(),
            ),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.sm,
            ),
            color = if (message.type == CameraMessageType.Error) {
                MaterialTheme.colorScheme.error
            } else {
                glass.style.contentColor
            },
        )
    }
}

@Composable
private fun LoadingState(glass: LiquidGlassAppearance) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            appearance = glass,
            cornerStyle = LiquidGlassCornerStyle.Capsule,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(MaterialTheme.spacing.lg),
                color = glass.style.contentColor,
            )
        }
    }
}

@Composable
private fun PermissionDeniedState(
    onRequestPermission: () -> Unit,
    glass: LiquidGlassAppearance,
) {
    StateGlassPanel(glass = glass, allowPlatformGlass = false) {
        Text(
            text = stringResource(Res.string.camera_permission_required_message),
            style = MaterialTheme.typography.headlineSmall,
            color = glass.style.contentColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.camera_permission_granted_description),
            modifier = Modifier.padding(
                top = MaterialTheme.spacing.md,
                bottom = MaterialTheme.spacing.lg,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = glass.style.contentVariantColor,
            textAlign = TextAlign.Center,
        )
        GlassActionButton(
            label = stringResource(Res.string.camera_permission_request_button),
            glass = glass,
            allowPlatformGlass = false,
            onClick = onRequestPermission,
        )
    }
}

@Composable
private fun CameraErrorState(
    error: CameraError,
    onRetryPreview: () -> Unit,
    glass: LiquidGlassAppearance,
) {
    StateGlassPanel(glass = glass) {
        Text(
            text = stringResource(error.toCameraMessage().messageRes),
            style = MaterialTheme.typography.headlineSmall,
            color = glass.style.contentColor,
            textAlign = TextAlign.Center,
        )
        GlassActionButton(
            label = stringResource(Res.string.camera_retry_button),
            glass = glass,
            onClick = onRetryPreview,
            modifier = Modifier.padding(top = MaterialTheme.spacing.lg),
        )
    }
}

@Composable
private fun StateGlassPanel(
    glass: LiquidGlassAppearance,
    allowPlatformGlass: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = MaterialTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            appearance = glass,
            cornerStyle = LiquidGlassCornerStyle.Rounded(28.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            allowPlatformGlass = allowPlatformGlass,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun GlassActionButton(
    label: String,
    glass: LiquidGlassAppearance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    allowPlatformGlass: Boolean = true,
) {
    Box(
        modifier = modifier.clip(CircleShape).clickable(
            interactionSource = null,
            indication = null,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            appearance = glass,
            cornerStyle = LiquidGlassCornerStyle.Capsule,
            allowPlatformGlass = allowPlatformGlass,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.lg,
                    vertical = MaterialTheme.spacing.md,
                ),
                color = glass.style.contentColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun GlassErrorDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String,
    glass: LiquidGlassAppearance,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        LiquidGlassSurface(
            appearance = glass,
            cornerStyle = LiquidGlassCornerStyle.Rounded(28.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = glass.style.contentColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.lg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.style.contentVariantColor,
                    textAlign = TextAlign.Center,
                )
                GlassActionButton(
                    label = confirmLabel,
                    glass = glass,
                    onClick = onDismissRequest,
                )
            }
        }
    }
}
