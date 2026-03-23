package com.example.vtubercamera_kmp_ver.camera

import CameraUiState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_switch_button
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_button

@Composable
fun CameraRoute(
    modifier: Modifier = Modifier,
    cameraViewModel: CameraViewModel = viewModel { CameraViewModel() },
) {
    val permissionController = rememberCameraPermissionController()
    val filePickerLauncher = rememberFilePickerLauncher(cameraViewModel::onFilePicked)
    val uiState by cameraViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(permissionController.isGranted, permissionController.isChecking) {
        cameraViewModel.onPermissionStateChanged(
            isGranted = permissionController.isGranted,
            isChecking = permissionController.isChecking,
        )
    }

    CameraScreen(
        modifier = modifier,
        uiState = uiState,
        onRequestPermission = permissionController.requestPermission,
        onOpenFilePicker = filePickerLauncher.launch,
        onDismissFilePickerError = cameraViewModel::onDismissFilePickerError,
        onLensFacingChanged = cameraViewModel::onLensFacingChanged,
        onLensFacingToggle = cameraViewModel::onToggleLensFacing,
    )
}

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    onRequestPermission: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onDismissFilePickerError: () -> Unit,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        when {
            uiState.isPermissionChecking -> LoadingState()
            uiState.isPermissionGranted -> CameraPreviewState(
                uiState = uiState,
                onOpenFilePicker = onOpenFilePicker,
                onLensFacingChanged = onLensFacingChanged,
                onLensFacingToggle = onLensFacingToggle,
            )
            else -> PermissionDeniedState(
                onRequestPermission = onRequestPermission,
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
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewHost(
            modifier = Modifier.fillMaxSize(),
            lensFacing = uiState.lensFacing,
            onLensFacingChanged = onLensFacingChanged,
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
