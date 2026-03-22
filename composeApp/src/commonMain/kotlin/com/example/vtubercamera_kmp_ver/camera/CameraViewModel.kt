package com.example.vtubercamera_kmp_ver.camera

import CameraUiState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


class CameraViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun onLensFacingChanged(lensFacing: CameraLensFacing) {
        _uiState.update { currentState ->
            currentState.copy(lensFacing = lensFacing)
        }
    }

    fun onPermissionStateChanged(
        isGranted: Boolean,
        isChecking: Boolean,
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                isPermissionGranted = isGranted,
                isPermissionChecking = isChecking,
            )
        }
    }

    fun onToggleLensFacing() {
        _uiState.update { currentState ->
            currentState.copy(lensFacing = currentState.lensFacing.toggled())
        }
    }
}
