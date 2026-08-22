package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundMode
import com.example.vtubercamera_kmp_ver.camera.gesture.PinchGestureTarget
import com.example.vtubercamera_kmp_ver.camera.permission.CameraPermissionUiState
import com.example.vtubercamera_kmp_ver.camera.session.CameraSessionUiState
import com.example.vtubercamera_kmp_ver.theme.spacing
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_error_dialog_confirm
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_error_dialog_title
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_black
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_blue
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_camera
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_green
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_mode_white
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_background_toggle_content_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_capture_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_confirm_message
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_confirm_negative
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_confirm_positive
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_delete_confirm_title
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_granted_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_request_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_required_message
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_retry_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_switch_button
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_button
import vtubercamera_kmp_ver.composeapp.generated.resources.pinch_target_avatar_scale
import vtubercamera_kmp_ver.composeapp.generated.resources.pinch_target_camera_zoom
import vtubercamera_kmp_ver.composeapp.generated.resources.pinch_target_toggle_content_description

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
        )
    }
    val filePickerLauncher = rememberFilePickerLauncher(cameraViewModel::onFilePicked)
    val uiState by cameraViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        cameraViewModel.initialize()
    }

    LaunchedEffect(permissionController.isGranted, permissionController.isChecking) {
        cameraViewModel.onPermissionStateChanged(
            isGranted = permissionController.isGranted,
            isChecking = permissionController.isChecking,
        )
    }

    CameraScreen(
        modifier = modifier,
        cameraRepository = repositories.cameraRepository,
        session = uiState.session,
        permission = uiState.permission,
        zoom = uiState.zoom,
        photoCapture = uiState.photoCapture,
        photoDeletion = uiState.photoDeletion,
        capturedPhotoUri = uiState.capturedPhotoUri,
        avatarRender = uiState.avatarRender,
        avatarSelection = uiState.avatarSelection.avatarSelection,
        filePickerErrorMessageRes = uiState.avatarSelection.filePickerErrorMessageRes,
        avatarScale = uiState.avatarScale.currentAvatarScale,
        backgroundMode = uiState.background.mode,
        pinchTarget = uiState.effectivePinchTarget,
        rendererHost = rendererHost,
        onRequestPermission = cameraViewModel::onRequestPermission,
        onRetryPreview = cameraViewModel::onRetryPreview,
        onOpenFilePicker = filePickerLauncher.launch,
        onDismissFilePickerError = cameraViewModel::onDismissFilePickerError,
        onAvatarRenderLoadFailed = cameraViewModel::onAvatarRenderLoadFailed,
        onFaceTrackingFrameChanged = cameraViewModel::onFaceTrackingFrameChanged,
        onLensFacingChanged = cameraViewModel::onLensFacingChanged,
        onLensFacingToggle = cameraViewModel::onToggleLensFacing,
        onCameraZoomChanged = cameraViewModel::onCameraZoomChanged,
        onAvatarScaleChanged = cameraViewModel::onAvatarScaleChanged,
        onTogglePinchTarget = cameraViewModel::onTogglePinchTarget,
        onToggleBackgroundMode = cameraViewModel::onToggleBackgroundMode,
        onCapturePhoto = cameraViewModel::onCapturePhoto,
        onDeletePhoto = cameraViewModel::onDeletePhoto,
    )
}

/**
 * 共有 camera screen を描画し、必要に応じて renderer layer へ custom renderer host を注入する。
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
    capturedPhotoUri: String?,
    avatarRender: AvatarRenderState,
    avatarSelection: AvatarSelectionData?,
    filePickerErrorMessageRes: StringResource?,
    avatarScale: Float,
    backgroundMode: CameraBackgroundMode,
    pinchTarget: PinchGestureTarget,
    onRequestPermission: () -> Unit,
    onRetryPreview: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onDismissFilePickerError: () -> Unit,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
    onCameraZoomChanged: (Float) -> Unit,
    onAvatarScaleChanged: (Float) -> Unit,
    onTogglePinchTarget: () -> Unit,
    onToggleBackgroundMode: () -> Unit,
    onCapturePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    rendererHost: CameraRendererHost = defaultCameraRendererHost,
) {
    val previewError = session.previewState as? PreviewState.Error

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        when {
            permission.permissionState == PermissionState.Unknown -> LoadingState()
            permission.permissionState == PermissionState.Denied -> PermissionDeniedState(
                onRequestPermission = onRequestPermission,
            )

            previewError != null -> CameraErrorState(
                error = previewError.error,
                onRetryPreview = onRetryPreview,
            )

            permission.permissionState == PermissionState.Granted -> CameraPreviewState(
                cameraRepository = cameraRepository,
                lensFacing = session.lensFacing,
                zoomScale = zoom.currentCameraZoomRatio,
                backgroundMode = backgroundMode,
                avatarSelection = avatarSelection,
                avatarRenderState = avatarRender,
                avatarScale = avatarScale,
                pinchTarget = pinchTarget,
                photoCapture = photoCapture,
                photoDeletion = photoDeletion,
                capturedPhotoUri = capturedPhotoUri,
                rendererHost = rendererHost,
                onOpenFilePicker = onOpenFilePicker,
                onAvatarRenderLoadFailed = onAvatarRenderLoadFailed,
                onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
                onLensFacingChanged = onLensFacingChanged,
                onLensFacingToggle = onLensFacingToggle,
                onCameraZoomChanged = onCameraZoomChanged,
                onAvatarScaleChanged = onAvatarScaleChanged,
                onTogglePinchTarget = onTogglePinchTarget,
                onToggleBackgroundMode = onToggleBackgroundMode,
                onCapturePhoto = onCapturePhoto,
                onDeletePhoto = onDeletePhoto,
            )

            else -> LoadingState()
        }

        (
            photoDeletion.toCameraMessage()
                ?: photoCapture.toCameraMessage()
                ?: session.message
        )?.let { message ->
            CameraMessageBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = MaterialTheme.spacing.lg,
                        end = MaterialTheme.spacing.lg,
                        bottom = MaterialTheme.spacing.xl * 4,
                    ),
            )
        }

        filePickerErrorMessageRes?.let { messageRes ->
            AlertDialog(
                onDismissRequest = onDismissFilePickerError,
                title = { Text(stringResource(Res.string.avatar_error_dialog_title)) },
                text = { Text(stringResource(messageRes)) },
                confirmButton = {
                    Button(onClick = onDismissFilePickerError) {
                        Text(stringResource(Res.string.avatar_error_dialog_confirm))
                    }
                },
            )
        }
    }
}

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
    avatarSelection: AvatarSelectionData?,
    avatarRenderState: AvatarRenderState,
    avatarScale: Float,
    pinchTarget: PinchGestureTarget,
    photoCapture: PhotoCaptureState,
    photoDeletion: PhotoDeletionState,
    capturedPhotoUri: String?,
    rendererHost: CameraRendererHost,
    onOpenFilePicker: () -> Unit,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
    onCameraZoomChanged: (Float) -> Unit,
    onAvatarScaleChanged: (Float) -> Unit,
    onTogglePinchTarget: () -> Unit,
    onToggleBackgroundMode: () -> Unit,
    onCapturePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
) {
    val avatarPreview = avatarSelection?.preview

    // レイヤーは背景 → アバター → ジェスチャー → 操作 UI の順で重ねる。アバターを拡大しても
    // ボタン類が隠れないよう、z 順を zIndex で明示して操作 UI を常に最前面に置く。
    Box(modifier = Modifier.fillMaxSize()) {
        CameraBackgroundLayer(
            cameraRepository = cameraRepository,
            lensFacing = lensFacing,
            zoomScale = DEFAULT_CAMERA_ZOOM_SCALE,
            backgroundMode = backgroundMode,
            onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
            onLensFacingChanged = onLensFacingChanged,
            modifier = Modifier.zIndex(CAMERA_BACKGROUND_LAYER_Z_INDEX),
        )
        CameraRendererLayer(
            avatarSelection = avatarSelection,
            avatarPreview = avatarPreview,
            avatarRenderState = avatarRenderState,
            avatarScale = avatarScale,
            onAvatarRenderLoadFailed = onAvatarRenderLoadFailed,
            rendererHost = rendererHost,
        )
        // ピンチジェスチャーを検出する透明オーバーレイ。ボタンより下に配置して操作を妨げない。
        // 切り替えボタンで選んだ対象へジェスチャーを振り分けるため、対象が変わったら検出を貼り直す。
        Box(
            modifier = Modifier
                .matchParentSize()
                .zIndex(PINCH_GESTURE_LAYER_Z_INDEX)
                .pointerInput(pinchTarget) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        when (pinchTarget) {
                            PinchGestureTarget.CameraZoom -> onCameraZoomChanged(zoomChange)
                            PinchGestureTarget.AvatarScale -> onAvatarScaleChanged(zoomChange)
                        }
                    }
                },
        )
        CameraUiLayer(
            avatarPreview = avatarPreview,
            zoomScale = zoomScale,
            avatarScale = avatarScale,
            pinchTarget = pinchTarget,
            canTogglePinchTarget = avatarSelection != null,
            onTogglePinchTarget = onTogglePinchTarget,
            backgroundMode = backgroundMode,
            onToggleBackgroundMode = onToggleBackgroundMode,
            onOpenFilePicker = onOpenFilePicker,
            onLensFacingToggle = onLensFacingToggle,
            onCapturePhoto = onCapturePhoto,
            onDeletePhoto = onDeletePhoto,
            isCapturingPhoto = photoCapture == PhotoCaptureState.Capturing,
            canDeletePhoto = capturedPhotoUri != null && photoDeletion != PhotoDeletionState.Deleting,
            isDeletingPhoto = photoDeletion == PhotoDeletionState.Deleting,
        )
    }
}

/**
 * カメラ映像の背景レイヤーを全画面で表示し、face tracking 更新を preview host へ渡す。
 *
 * [backgroundMode] が単色プリセットのときは、preview host の上へ不透明な単色を重ねて
 * 実際の顔が映らないようにする。preview host 自体は常に構成したままにして face tracking を
 * 止めないため、背景を隠してもアバターは顔の動きに追従し続ける。
 */
@Composable
private fun CameraBackgroundLayer(
    cameraRepository: CameraRepository,
    lensFacing: CameraLensFacing,
    zoomScale: Float,
    backgroundMode: CameraBackgroundMode,
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
            onLensFacingChanged = onLensFacingChanged,
            onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
        )
        backgroundMode.overlayColor?.let { overlayColor ->
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayColor),
            )
        }
    }
}

/**
 * 背景モードに対応する不透明な単色を返す。カメラ映像をそのまま見せるモードでは null を返す。
 */
private val CameraBackgroundMode.overlayColor: Color?
    get() = when (this) {
        CameraBackgroundMode.Camera -> null
        CameraBackgroundMode.Black -> BACKGROUND_BLACK
        CameraBackgroundMode.White -> BACKGROUND_WHITE
        CameraBackgroundMode.Green -> BACKGROUND_CHROMA_GREEN
        CameraBackgroundMode.Blue -> BACKGROUND_CHROMA_BLUE
    }

/** 背景モードに対応するチップ表示用のラベル。 */
private val CameraBackgroundMode.labelRes: StringResource
    get() = when (this) {
        CameraBackgroundMode.Camera -> Res.string.camera_background_mode_camera
        CameraBackgroundMode.Black -> Res.string.camera_background_mode_black
        CameraBackgroundMode.White -> Res.string.camera_background_mode_white
        CameraBackgroundMode.Green -> Res.string.camera_background_mode_green
        CameraBackgroundMode.Blue -> Res.string.camera_background_mode_blue
    }

private val BACKGROUND_BLACK = Color(0xFF000000)
private val BACKGROUND_WHITE = Color(0xFFFFFFFF)

// 合成用途で背景を差し替えやすいよう、緑・青はクロマキー標準色に合わせる。
private val BACKGROUND_CHROMA_GREEN = Color(0xFF00B140)
private val BACKGROUND_CHROMA_BLUE = Color(0xFF0047BB)

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
        onAvatarRenderLoadFailed = state.onAvatarRenderLoadFailed,
        modifier = state.modifier,
    )
}

/**
 * カメラ操作ボタンと avatar preview overlay を前景 UI として重ねる。
 *
 * アバターが画面全体まで拡大してもボタン類が隠れないよう、この layer は他のどの layer よりも
 * 大きい [CAMERA_CONTROLS_LAYER_Z_INDEX] を持つ。
 */
@Composable
private fun BoxScope.CameraUiLayer(
    avatarPreview: AvatarPreviewData?,
    zoomScale: Float,
    avatarScale: Float,
    pinchTarget: PinchGestureTarget,
    canTogglePinchTarget: Boolean,
    onTogglePinchTarget: () -> Unit,
    backgroundMode: CameraBackgroundMode,
    onToggleBackgroundMode: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onLensFacingToggle: () -> Unit,
    onCapturePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
    isCapturingPhoto: Boolean,
    canDeletePhoto: Boolean,
    isDeletingPhoto: Boolean,
) {
    TopStatusOverlay(
        zoomScale = zoomScale,
        avatarScale = avatarScale,
        pinchTarget = pinchTarget,
        canTogglePinchTarget = canTogglePinchTarget,
        onTogglePinchTarget = onTogglePinchTarget,
        backgroundMode = backgroundMode,
        onToggleBackgroundMode = onToggleBackgroundMode,
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .zIndex(CAMERA_CONTROLS_LAYER_Z_INDEX)
            .statusBarsPadding()
            .padding(MaterialTheme.spacing.lg),
    )
    BottomCaptureControls(
        avatarPreview = avatarPreview,
        onOpenFilePicker = onOpenFilePicker,
        onLensFacingToggle = onLensFacingToggle,
        onCapturePhoto = onCapturePhoto,
        onDeletePhoto = onDeletePhoto,
        isCapturingPhoto = isCapturingPhoto,
        canDeletePhoto = canDeletePhoto,
        isDeletingPhoto = isDeletingPhoto,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .zIndex(CAMERA_CONTROLS_LAYER_Z_INDEX)
            .navigationBarsPadding()
            .padding(MaterialTheme.spacing.lg),
    )
}

/**
 * ピンチ操作の対象に対応する倍率を表示するインジケーター。
 *
 * [ratio] にはカメラズーム倍率とアバター表示倍率のどちらかが渡る。
 */
@Composable
private fun ScaleRatioIndicator(
    ratio: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.spacing.md),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = MaterialTheme.spacing.xs,
    ) {
        Text(
            text = ratio.toRatioLabel(),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * ピンチ操作の対象をカメラズームとアバター拡縮で切り替えるチップ。
 *
 * 表示ラベルは現在の対象を示し、押下で [onClick] を通じてもう一方へ切り替える。
 */
@Composable
private fun PinchTargetToggleChip(
    pinchTarget: PinchGestureTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleContentDescription = stringResource(
        Res.string.pinch_target_toggle_content_description,
    )

    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = toggleContentDescription },
        shape = RoundedCornerShape(MaterialTheme.spacing.md),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = MaterialTheme.spacing.xs,
    ) {
        Text(
            text = stringResource(pinchTarget.labelRes),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs,
            ),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val PinchGestureTarget.labelRes: StringResource
    get() = when (this) {
        PinchGestureTarget.CameraZoom -> Res.string.pinch_target_camera_zoom
        PinchGestureTarget.AvatarScale -> Res.string.pinch_target_avatar_scale
    }

private fun Float.toRatioLabel(): String{
    val roundedTenths = (this * RATIO_LABEL_SCALE).roundToInt()
    val whole = roundedTenths /RATIO_LABEL_SCALE
    val decimal = roundedTenths % RATIO_LABEL_SCALE

    return "${whole}.${decimal}x"
}

private const val RATIO_LABEL_SCALE = 10

@Composable
private fun TopStatusOverlay(
    zoomScale: Float,
    avatarScale: Float,
    pinchTarget: PinchGestureTarget,
    canTogglePinchTarget: Boolean,
    onTogglePinchTarget: () -> Unit,
    backgroundMode: CameraBackgroundMode,
    onToggleBackgroundMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.spacing.sm,
                alignment = Alignment.End,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // アバター選択済みのときだけ、ピンチ操作の対象を切り替えられるようにする。
            if (canTogglePinchTarget) {
                PinchTargetToggleChip(
                    pinchTarget = pinchTarget,
                    onClick = onTogglePinchTarget,
                )
            }
            ScaleRatioIndicator(
                ratio = when (pinchTarget) {
                    PinchGestureTarget.CameraZoom -> zoomScale
                    PinchGestureTarget.AvatarScale -> avatarScale
                },
            )
        }
        // 背景切り替えは次の行の右端へ置く。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            CameraBackgroundToggleChip(
                backgroundMode = backgroundMode,
                onClick = onToggleBackgroundMode,
            )
        }
    }
}

/**
 * カメラ映像を覆う背景プリセットを順番に切り替えるチップ。
 *
 * 表示ラベルは現在の背景モードを示し、押下で [onClick] を通じて次のプリセットへ進む。
 */
@Composable
private fun CameraBackgroundToggleChip(
    backgroundMode: CameraBackgroundMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleContentDescription = stringResource(
        Res.string.camera_background_toggle_content_description,
    )

    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = toggleContentDescription },
        shape = RoundedCornerShape(MaterialTheme.spacing.md),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = MaterialTheme.spacing.xs,
    ) {
        Text(
            text = stringResource(backgroundMode.labelRes),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs,
            ),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BottomCaptureControls(
    avatarPreview: AvatarPreviewData?,
    onOpenFilePicker: () -> Unit,
    onLensFacingToggle: () -> Unit,
    onCapturePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
    isCapturingPhoto: Boolean,
    canDeletePhoto: Boolean,
    isDeletingPhoto: Boolean,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        avatarPreview?.let {
            CompactAvatarChip(avatarPreview = it)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaterialTheme.spacing.lg),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            tonalElevation = MaterialTheme.spacing.xs,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onOpenFilePicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(CAPTURE_CONTROL_BUTTON_HEIGHT),
                    ) {
                        Text(stringResource(Res.string.file_picker_open_button))
                    }
                    Button(
                        onClick = onCapturePhoto,
                        enabled = !isCapturingPhoto,
                        modifier = Modifier
                            .weight(1f)
                            .height(CAPTURE_CONTROL_BUTTON_HEIGHT),
                    ) {
                        if (isCapturingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(CAPTURE_PROGRESS_SIZE),
                            )
                        } else {
                            Text(stringResource(Res.string.camera_capture_button))
                        }
                    }
                    Button(
                        onClick = onLensFacingToggle,
                        modifier = Modifier
                            .weight(1f)
                            .height(CAPTURE_CONTROL_BUTTON_HEIGHT),
                    ) {
                        Text(stringResource(Res.string.camera_switch_button))
                    }
                }
                // 撮影済み画像があるときだけ削除導線を表示する。
                if (canDeletePhoto || isDeletingPhoto) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = canDeletePhoto,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isDeletingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(CAPTURE_PROGRESS_SIZE),
                            )
                        } else {
                            Text(stringResource(Res.string.camera_delete_button))
                        }
                    }
                }
            }
        }
    }

    // 削除対象が無くなった場合はダイアログ表示条件からも外し、開いたままにならないようにする。
    if (showDeleteConfirm && canDeletePhoto) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.camera_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.camera_delete_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePhoto()
                    },
                ) {
                    Text(stringResource(Res.string.camera_delete_confirm_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.camera_delete_confirm_negative))
                }
            },
        )
    }
}

@Composable
private fun CompactAvatarChip(
    avatarPreview: AvatarPreviewData,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.spacing.md),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = MaterialTheme.spacing.xs,
    ) {
        Text(
            text = avatarPreview.avatarName,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val CAPTURE_CONTROL_BUTTON_HEIGHT = 64.dp
private val CAPTURE_PROGRESS_SIZE = 20.dp

@Composable
private fun CameraMessageBanner(
    message: CameraMessage,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = if (message.type == CameraMessageType.Error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = RoundedCornerShape(MaterialTheme.spacing.md),
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
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PermissionDeniedState(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.camera_permission_required_message),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.camera_permission_granted_description),
            modifier = Modifier.padding(
                top = MaterialTheme.spacing.md,
                bottom = MaterialTheme.spacing.lg,
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequestPermission) {
            Text(stringResource(Res.string.camera_permission_request_button))
        }
    }
}

@Composable
private fun CameraErrorState(
    error: CameraError,
    onRetryPreview: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(error.toCameraMessage().messageRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetryPreview,
            modifier = Modifier.padding(top = MaterialTheme.spacing.lg),
        ) {
            Text(stringResource(Res.string.camera_retry_button))
        }
    }
}
