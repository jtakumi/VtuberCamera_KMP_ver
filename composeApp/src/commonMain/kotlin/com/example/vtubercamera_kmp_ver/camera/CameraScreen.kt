package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vtubercamera_kmp_ver.theme.spacing
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_error_dialog_confirm
import vtubercamera_kmp_ver.composeapp.generated.resources.avatar_error_dialog_title
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
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_status_searching
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_status_tracking
import vtubercamera_kmp_ver.composeapp.generated.resources.face_tracking_title
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_button

@Composable
fun CameraRoute(
    modifier: Modifier = Modifier,
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
        uiState = uiState,
        onRequestPermission = cameraViewModel::onRequestPermission,
        onRetryPreview = cameraViewModel::onRetryPreview,
        onOpenFilePicker = filePickerLauncher.launch,
        onDismissFilePickerError = cameraViewModel::onDismissFilePickerError,
        onFaceTrackingFrameChanged = cameraViewModel::onFaceTrackingFrameChanged,
        onLensFacingChanged = cameraViewModel::onLensFacingChanged,
        onLensFacingToggle = cameraViewModel::onToggleLensFacing,
    )
}

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    onRequestPermission: () -> Unit,
    onRetryPreview: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onDismissFilePickerError: () -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewError = uiState.previewState as? PreviewState.Error

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        when {
            uiState.isPermissionChecking -> LoadingState()
            uiState.permissionState == PermissionState.Denied -> PermissionDeniedState(
                onRequestPermission = onRequestPermission,
            )
            previewError != null -> CameraErrorState(
                error = previewError.error,
                onRetryPreview = onRetryPreview,
            )
            uiState.isPermissionGranted -> CameraPreviewState(
                uiState = uiState,
                onOpenFilePicker = onOpenFilePicker,
                onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
                onLensFacingChanged = onLensFacingChanged,
                onLensFacingToggle = onLensFacingToggle,
            )
            else -> LoadingState()
        }

        uiState.message?.let { message ->
            CameraMessageBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(MaterialTheme.spacing.lg),
            )
        }

        uiState.filePickerErrorMessageRes?.let { messageRes ->
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
    uiState: CameraUiState,
    onOpenFilePicker: () -> Unit,
    onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewHost(
            modifier = Modifier.fillMaxSize(),
            lensFacing = uiState.lensFacing,
            onLensFacingChanged = onLensFacingChanged,
            onFaceTrackingFrameChanged = onFaceTrackingFrameChanged,
        )
        uiState.avatarPreview?.let { avatarPreview ->
            AvatarBodyOverlay(
                avatarPreview = avatarPreview,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = MaterialTheme.spacing.lg,
                        end = MaterialTheme.spacing.lg,
                        top = MaterialTheme.spacing.xl * 2,
                        bottom = MaterialTheme.spacing.xl,
                    ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(MaterialTheme.spacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onOpenFilePicker) {
                Text(stringResource(Res.string.file_picker_open_button))
            }
            Button(onClick = onLensFacingToggle) {
                Text(stringResource(Res.string.camera_switch_button))
            }
        }
        uiState.avatarPreview?.let { avatarPreview ->
            AvatarPreviewOverlay(
                avatarPreview = avatarPreview,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(MaterialTheme.spacing.xl),
            )
        }
        FaceTrackingOverlay(
            faceTracking = uiState.faceTracking,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = MaterialTheme.spacing.xl,
                    top = MaterialTheme.spacing.xl * 5,
                ),
        )
    }
}

@Composable
private fun FaceTrackingOverlay(
    faceTracking: FaceTrackingUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.spacing.lg),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = MaterialTheme.spacing.xs,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Text(
                text = stringResource(Res.string.face_tracking_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (faceTracking.isTracking) {
                        Res.string.face_tracking_status_tracking
                    } else {
                        Res.string.face_tracking_status_searching
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            }
        }
    }
}

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
