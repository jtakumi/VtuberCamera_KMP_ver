package com.example.vtubercamera_kmp_ver.camera

import CameraUiState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_granted_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_request_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_required_message
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_switch_button

@Composable
fun CameraRoute(
    modifier: Modifier = Modifier,
    cameraViewModel: CameraViewModel = viewModel { CameraViewModel() },
) {
    val permissionController = rememberCameraPermissionController()
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
        onLensFacingChanged = cameraViewModel::onLensFacingChanged,
        onLensFacingToggle = cameraViewModel::onToggleLensFacing,
    )
}

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    onRequestPermission: () -> Unit,
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
                onLensFacingChanged = onLensFacingChanged,
                onLensFacingToggle = onLensFacingToggle,
            )
            else -> PermissionDeniedState(
                onRequestPermission = onRequestPermission,
            )
        }
    }
}

@Composable
private fun CameraPreviewState(
    uiState: CameraUiState,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
    onLensFacingToggle: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewHost(
            modifier = Modifier.fillMaxSize(),
            lensFacing = uiState.lensFacing,
            onLensFacingChanged = onLensFacingChanged,
        )
        Button(
            onClick = onLensFacingToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp),
        ) {
            Text(stringResource(Res.string.camera_switch_button))
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
            .padding(horizontal = 24.dp),
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
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequestPermission) {
            Text(stringResource(Res.string.camera_permission_request_button))
        }
    }
}
