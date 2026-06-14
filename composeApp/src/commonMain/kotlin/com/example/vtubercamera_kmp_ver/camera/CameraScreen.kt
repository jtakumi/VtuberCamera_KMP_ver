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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.theme.ThemeMode
import com.example.vtubercamera_kmp_ver.theme.spacing
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_error_dialog_confirm
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_error_dialog_title
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_capture_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_granted_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_request_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_required_message
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_retry_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_switch_button
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_label_blink_left
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_label_blink_right
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_label_jaw
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_label_pitch
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_label_roll
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_label_smile
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_label_yaw
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_details_hide
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_details_show
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_status_searching
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_status_tracking
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_title
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_button
import vtubercamera_kmp_ver.composeapp.generated.resources.theme_mode_dark
import vtubercamera_kmp_ver.composeapp.generated.resources.theme_mode_light
import vtubercamera_kmp_ver.composeapp.generated.resources.theme_mode_system
import vtubercamera_kmp_ver.composeapp.generated.resources.theme_toggle_content_description

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
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeToggle: () -> Unit = {},
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
        uiState = uiState,
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
        onCapturePhoto = cameraViewModel::onCapturePhoto,
        themeMode = themeMode,
        onThemeModeToggle = onThemeModeToggle,
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
    uiState: CameraUiState,
    onRequestPermission: () -> Unit,
    onRetryPreview: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onDismissFilePickerError: () -> Unit,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
    onCameraZoomChanged: (Float) -> Unit,
    onCapturePhoto: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    rendererHost: CameraRendererHost = defaultCameraRendererHost,
) {
    val previewError = uiState.session.previewState as? PreviewState.Error

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        when {
            uiState.isPermissionChecking -> LoadingState()
            uiState.permission.permissionState == PermissionState.Denied -> PermissionDeniedState(
                onRequestPermission = onRequestPermission,
            )

            previewError != null -> CameraErrorState(
                error = previewError.error,
                onRetryPreview = onRetryPreview,
            )

            uiState.isPermissionGranted -> CameraPreviewState(
                cameraRepository = cameraRepository,
                uiState = uiState,
                rendererHost = rendererHost,
                onOpenFilePicker = onOpenFilePicker,
                onAvatarRenderLoadFailed = onAvatarRenderLoadFailed,
                onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
                onLensFacingChanged = onLensFacingChanged,
                onLensFacingToggle = onLensFacingToggle,
                onCameraZoomChanged = onCameraZoomChanged,
                onCapturePhoto = onCapturePhoto,
                themeMode = themeMode,
                onThemeModeToggle = onThemeModeToggle,
            )

            else -> LoadingState()
        }

        (uiState.photoCapture.toCameraMessage() ?: uiState.session.message)?.let { message ->
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

        uiState.avatarSelection.filePickerErrorMessageRes?.let { messageRes ->
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

@Composable
private fun CameraPreviewState(
    cameraRepository: CameraRepository,
    uiState: CameraUiState,
    rendererHost: CameraRendererHost,
    onOpenFilePicker: () -> Unit,
    onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
    onCameraZoomChanged: (Float) -> Unit,
    onCapturePhoto: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeToggle: () -> Unit,
) {
    val avatarSelection = uiState.avatarSelection.avatarSelection
    val avatarPreview = uiState.avatarPreview

    Box(modifier = Modifier.fillMaxSize()) {
        CameraBackgroundLayer(
            cameraRepository = cameraRepository,
            lensFacing = uiState.session.lensFacing,
            zoomScale = DEFAULT_CAMERA_ZOOM_SCALE,
            onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
            onLensFacingChanged = onLensFacingChanged,
        )
        CameraRendererLayer(
            avatarSelection = avatarSelection,
            avatarPreview = avatarPreview,
            avatarRenderState = uiState.avatarRender,
            onAvatarRenderLoadFailed = onAvatarRenderLoadFailed,
            rendererHost = rendererHost,
        )
        // ピンチジェスチャーを検出する透明オーバーレイ。ボタンより下に配置して操作を妨げない。
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        onCameraZoomChanged(zoomChange)
                    }
                },
        )
        CameraUiLayer(
            avatarPreview = avatarPreview,
            faceTracking = uiState.faceTracking,
            zoomScale = uiState.zoom.currentCameraZoomRatio,
            onOpenFilePicker = onOpenFilePicker,
            onLensFacingToggle = onLensFacingToggle,
            onCapturePhoto = onCapturePhoto,
            isCapturingPhoto = uiState.photoCapture == PhotoCaptureState.Capturing,
            themeMode = themeMode,
            onThemeModeToggle = onThemeModeToggle,
        )
    }
}

/**
 * カメラ映像の背景レイヤーを全画面で表示し、face tracking 更新を preview host へ渡す。
 */
@Composable
private fun CameraBackgroundLayer(
    cameraRepository: CameraRepository,
    lensFacing: CameraLensFacing,
    zoomScale: Float,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
) {
    Box(
        modifier = Modifier
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
            onLensFacingChanged = onLensFacingChanged,
            onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
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
                onAvatarRenderLoadFailed = onAvatarRenderLoadFailed,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = MaterialTheme.spacing.lg,
                        end = MaterialTheme.spacing.lg,
                        top = MaterialTheme.spacing.xl * 2,
                        bottom = MaterialTheme.spacing.xl,
                    ),
            ),
        )
    }
}

/**
 * renderer host が camera preview layer に avatar content を描画するための共有文脈を保持する。
 *
 * [avatarSelection] は renderer が使う選択済み asset handle / runtime 情報、[avatarPreview] は
 * 表示用のメタ情報、[avatarRenderState] は renderer host が参照する共有の tracking / render state、
 * [onAvatarRenderLoadFailed] は renderer 側の読み込み失敗を UI へ戻す callback、[modifier] は
 * CameraScreen 側で決めた renderer layer の配置情報を表す。
 */
data class RendererHostSlotState(
    /** renderer host が参照する選択済み avatar の asset handle / runtime 情報。 */
    val avatarSelection: AvatarSelectionData,
    /** renderer host が参照する選択済み avatar のメタ情報。 */
    val avatarPreview: AvatarPreviewData,
    /** renderer host が参照する共有の avatar tracking / render state。 */
    val avatarRenderState: AvatarRenderState,
    /** renderer 側の読み込み失敗を UI へ戻す callback。 */
    val onAvatarRenderLoadFailed: (AvatarAssetHandle, StringResource) -> Unit,
    /** CameraScreen 側で決めた renderer layer の配置と padding。 */
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
        onAvatarRenderLoadFailed = state.onAvatarRenderLoadFailed,
        modifier = state.modifier,
    )
}

/**
 * カメラ操作ボタン、avatar preview overlay、face tracking 情報を前景 UI として重ねる。
 */
@Composable
private fun BoxScope.CameraUiLayer(
    avatarPreview: AvatarPreviewData?,
    faceTracking: FaceTrackingUiState,
    zoomScale: Float,
    onOpenFilePicker: () -> Unit,
    onLensFacingToggle: () -> Unit,
    onCapturePhoto: () -> Unit,
    isCapturingPhoto: Boolean,
    themeMode: ThemeMode,
    onThemeModeToggle: () -> Unit,
) {
    val themeToggleContentDescription = stringResource(
        Res.string.theme_toggle_content_description,
    )
    var isFaceTrackingExpanded by remember { mutableStateOf(false) }

    TopStatusOverlay(
        faceTracking = faceTracking,
        zoomScale = zoomScale,
        themeMode = themeMode,
        themeToggleContentDescription = themeToggleContentDescription,
        isFaceTrackingExpanded = isFaceTrackingExpanded,
        onFaceTrackingClick = { isFaceTrackingExpanded = !isFaceTrackingExpanded },
        onThemeModeToggle = onThemeModeToggle,
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(MaterialTheme.spacing.lg),
    )
    BottomCaptureControls(
        avatarPreview = avatarPreview,
        onOpenFilePicker = onOpenFilePicker,
        onLensFacingToggle = onLensFacingToggle,
        onCapturePhoto = onCapturePhoto,
        isCapturingPhoto = isCapturingPhoto,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(MaterialTheme.spacing.lg),
    )
}

private val ThemeMode.symbol: String
    @Composable
    get() = stringResource(
        when (this) {
            ThemeMode.System -> Res.string.theme_mode_system
            ThemeMode.Light -> Res.string.theme_mode_light
            ThemeMode.Dark -> Res.string.theme_mode_dark
        },
    )

@Composable
private fun ZoomIndicator(
    zoomScale: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.spacing.md),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = MaterialTheme.spacing.xs,
    ) {
        Text(
            text = zoomScale.toZoomRatio(),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Float.toZoomRatio(): String{
    val roundedTenths = (this * ZOOM_RATIO_LABEL_RATIO).roundToInt()
    val whole = roundedTenths /ZOOM_RATIO_LABEL_RATIO
    val decimal = roundedTenths % ZOOM_RATIO_LABEL_RATIO

    return "${whole}.${decimal}x"
}

private const val ZOOM_RATIO_LABEL_RATIO = 10

@Composable
private fun TopStatusOverlay(
    faceTracking: FaceTrackingUiState,
    zoomScale: Float,
    themeMode: ThemeMode,
    themeToggleContentDescription: String,
    isFaceTrackingExpanded: Boolean,
    onFaceTrackingClick: () -> Unit,
    onThemeModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            FaceTrackingStatusChip(
                faceTracking = faceTracking,
                isExpanded = isFaceTrackingExpanded,
                onClick = onFaceTrackingClick,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZoomIndicator(zoomScale = zoomScale)
                Surface(
                    shape = RoundedCornerShape(MaterialTheme.spacing.md),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    tonalElevation = MaterialTheme.spacing.xs,
                )
                {
                    IconButton(
                        onClick = onThemeModeToggle,
                        modifier = Modifier
                            .size(CONTROL_CHIP_SIZE)
                            .semantics {
                                contentDescription = themeToggleContentDescription
                            },
                    ) {
                        Text(
                            text = themeMode.symbol,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        if (isFaceTrackingExpanded) {
            FaceTrackingDetailsPanel(faceTracking = faceTracking)
        }
    }
}

@Composable
private fun FaceTrackingStatusChip(
    faceTracking: FaceTrackingUiState,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(MaterialTheme.spacing.md),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = MaterialTheme.spacing.xs,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.sm,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (faceTracking.isTracking) {
                        Res.string.face_tracking_status_tracking
                    } else {
                        Res.string.face_tracking_status_searching
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    if (isExpanded) {
                        Res.string.face_tracking_details_hide
                    } else {
                        Res.string.face_tracking_details_show
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FaceTrackingDetailsPanel(
    faceTracking: FaceTrackingUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaterialTheme.spacing.md),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = MaterialTheme.spacing.xs,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Text(
                text = stringResource(Res.string.face_tracking_title),
                style = MaterialTheme.typography.titleSmall,
            )
            faceTracking.display?.let { display ->
                FaceTrackingMetricRow(
                    label = stringResource(Res.string.face_tracking_label_yaw),
                    value = display.headYawLabel,
                )
                FaceTrackingMetricRow(
                    label = stringResource(Res.string.face_tracking_label_pitch),
                    value = display.headPitchLabel,
                )
                FaceTrackingMetricRow(
                    label = stringResource(Res.string.face_tracking_label_roll),
                    value = display.headRollLabel,
                )
                FaceTrackingMetricRow(
                    label = stringResource(Res.string.face_tracking_label_blink_left),
                    value = display.leftEyeBlinkLabel,
                )
                FaceTrackingMetricRow(
                    label = stringResource(Res.string.face_tracking_label_blink_right),
                    value = display.rightEyeBlinkLabel,
                )
                FaceTrackingMetricRow(
                    label = stringResource(Res.string.face_tracking_label_jaw),
                    value = display.jawOpenLabel,
                )
                FaceTrackingMetricRow(
                    label = stringResource(Res.string.face_tracking_label_smile),
                    value = display.mouthSmileLabel,
                )
            } ?: Text(
                text = stringResource(Res.string.face_tracking_status_searching),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BottomCaptureControls(
    avatarPreview: AvatarPreviewData?,
    onOpenFilePicker: () -> Unit,
    onLensFacingToggle: () -> Unit,
    onCapturePhoto: () -> Unit,
    isCapturingPhoto: Boolean,
    modifier: Modifier = Modifier,
) {
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
            Row(
                modifier = Modifier.padding(MaterialTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onOpenFilePicker,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.file_picker_open_button))
                }
                Button(
                    onClick = onCapturePhoto,
                    enabled = !isCapturingPhoto,
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.camera_switch_button))
                }
            }
        }
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

private val CONTROL_CHIP_SIZE = 44.dp
private val CAPTURE_PROGRESS_SIZE = 20.dp

@Composable
private fun FaceTrackingMetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}


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
