package com.example.vtubercamera_kmp_ver.camera

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_granted_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_request_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_required_message
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_switch_button

@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val permissionController = rememberCameraPermissionController()
    var lensFacing by remember { mutableStateOf(CameraLensFacing.Back) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        when {
            permissionController.isChecking -> LoadingState()
            permissionController.isGranted -> CameraPreviewState(
                lensFacing = lensFacing,
                onLensFacingChanged = { lensFacing = it },
            )
            else -> PermissionDeniedState(
                onRequestPermission = permissionController.requestPermission,
            )
        }
    }
}

@Composable
private fun CameraPreviewState(
    lensFacing: CameraLensFacing,
    onLensFacingChanged: (CameraLensFacing) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewHost(
            modifier = Modifier.fillMaxSize(),
            lensFacing = lensFacing,
            onLensFacingChanged = onLensFacingChanged,
        )
        Button(
            onClick = { onLensFacingChanged(lensFacing.toggled()) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
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
