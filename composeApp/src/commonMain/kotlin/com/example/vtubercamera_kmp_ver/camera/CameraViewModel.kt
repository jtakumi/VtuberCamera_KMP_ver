package com.example.vtubercamera_kmp_ver.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vtubercamera_kmp_ver.camera.avatar.AvatarSelectionController
import com.example.vtubercamera_kmp_ver.camera.facetracking.FaceTrackingPresenter
import com.example.vtubercamera_kmp_ver.camera.permission.CameraPermissionCoordinator
import com.example.vtubercamera_kmp_ver.camera.permission.PermissionChange
import com.example.vtubercamera_kmp_ver.camera.session.CameraSessionController
import com.example.vtubercamera_kmp_ver.camera.zoom.CameraZoomController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

// ドメインごとの controller / presenter を束ねるカメラ画面の薄い coordinator。
class CameraViewModel(
    cameraRepository: CameraRepository,
    permissionRepository: PermissionRepository,
) : ViewModel() {
    private val sessionController = CameraSessionController(
        cameraRepository = cameraRepository,
        scope = viewModelScope,
    )
    private val permissionCoordinator = CameraPermissionCoordinator(
        permissionRepository = permissionRepository,
        scope = viewModelScope,
    ).apply {
        onPermissionChanged = ::applyPermissionChange
        onPermissionRequested = sessionController::clearError
    }
    private val zoomController = CameraZoomController(
        cameraRepository = cameraRepository,
        scope = viewModelScope,
    )
    private val faceTrackingPresenter = FaceTrackingPresenter()
    private val avatarSelectionController = AvatarSelectionController()

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // controller の同期 update 直後に uiState.value が見えるよう、Unconfined で per-controller collect する。
    // viewModelScope の Job を共有しているため、ViewModel cleared 時にまとめて cancel される。
    private val mirrorScope = CoroutineScope(
        viewModelScope.coroutineContext + Dispatchers.Unconfined,
    )

    init {
        mirrorScope.launch {
            sessionController.state.collect { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
        mirrorScope.launch {
            permissionCoordinator.state.collect { permission ->
                _uiState.update { it.copy(permission = permission) }
            }
        }
        mirrorScope.launch {
            zoomController.state.collect { zoom ->
                _uiState.update { it.copy(zoom = zoom) }
            }
        }
        mirrorScope.launch {
            faceTrackingPresenter.state.collect { presenter ->
                _uiState.update {
                    it.copy(
                        faceTracking = presenter.faceTracking,
                        avatarRender = presenter.avatarRender,
                    )
                }
            }
        }
        mirrorScope.launch {
            avatarSelectionController.state.collect { avatarSelection ->
                _uiState.update { it.copy(avatarSelection = avatarSelection) }
            }
        }
    }

    fun initialize() {
        permissionCoordinator.initialize()
    }

    fun onRequestPermission() {
        permissionCoordinator.onRequestPermission()
    }

    fun onRetryPreview() {
        sessionController.onRetryPreview()
    }

    fun onLensFacingChanged(lensFacing: CameraLensFacing) {
        sessionController.onLensFacingChanged(lensFacing)
    }

    fun onPermissionStateChanged(isGranted: Boolean, isChecking: Boolean) {
        permissionCoordinator.onPermissionStateChanged(isGranted, isChecking)
    }

    fun onToggleLensFacing() {
        sessionController.onToggleLensFacing()
    }

    fun onFaceTrackingFrameChanged(frame: NormalizedFaceFrame?) {
        faceTrackingPresenter.onFaceTrackingFrameChanged(frame)
    }

    fun onFilePicked(result: FilePickerResult) {
        avatarSelectionController.onFilePicked(result)
    }

    fun onAvatarRenderLoadFailed(
        failedAssetHandle: AvatarAssetHandle,
        messageRes: StringResource,
    ) {
        avatarSelectionController.onAvatarRenderLoadFailed(failedAssetHandle, messageRes)
    }

    fun onCameraZoomChanged(scaleChange: Float) {
        zoomController.onCameraZoomChanged(scaleChange)
    }

    fun onDismissFilePickerError() {
        avatarSelectionController.onDismissFilePickerError()
    }

    internal fun releaseCurrentAvatarAsset() {
        avatarSelectionController.release()
    }

    private fun applyPermissionChange(change: PermissionChange) {
        when (change) {
            PermissionChange.GrantedEntered -> {
                sessionController.clearErrorAndPrepare()
                viewModelScope.launch { sessionController.start() }
            }
            PermissionChange.GrantedRefreshed -> sessionController.clearErrorAndPrepare()
            PermissionChange.DeniedReceived -> sessionController.setError(CameraError.PermissionDenied)
            PermissionChange.UnknownReceived -> sessionController.resetPreviewIfErrored()
        }
    }

    override fun onCleared() {
        releaseCurrentAvatarAsset()
        super.onCleared()
    }
}
