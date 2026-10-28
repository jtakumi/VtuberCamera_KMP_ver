package com.example.vtubercamera_kmp_ver.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vtubercamera_kmp_ver.camera.avatar.AvatarScaleController
import com.example.vtubercamera_kmp_ver.camera.avatar.AvatarSelectionController
import com.example.vtubercamera_kmp_ver.camera.background.CameraBackgroundController
import com.example.vtubercamera_kmp_ver.camera.capturemode.CameraCaptureModeController
import com.example.vtubercamera_kmp_ver.camera.facetracking.FaceTrackingPresenter
import com.example.vtubercamera_kmp_ver.camera.gesture.PinchGestureController
import com.example.vtubercamera_kmp_ver.camera.permission.CameraPermissionCoordinator
import com.example.vtubercamera_kmp_ver.camera.permission.PermissionChange
import com.example.vtubercamera_kmp_ver.camera.photo.PhotoCaptureController
import com.example.vtubercamera_kmp_ver.camera.photo.PhotoDeletionController
import com.example.vtubercamera_kmp_ver.camera.session.CameraSessionController
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageCrop
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsController
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsPlatform
import com.example.vtubercamera_kmp_ver.camera.settings.MediaPickResult
import com.example.vtubercamera_kmp_ver.camera.uivisibility.CameraUiVisibilityController
import com.example.vtubercamera_kmp_ver.camera.video.VideoRecordingController
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
    settingsPlatform: CameraSettingsPlatform,
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
    private val photoCaptureController = PhotoCaptureController(
        cameraRepository = cameraRepository,
        scope = viewModelScope,
    )
    private val photoDeletionController = PhotoDeletionController(
        cameraRepository = cameraRepository,
        scope = viewModelScope,
    )
    private val settingsController = CameraSettingsController(
        repository = settingsPlatform.settingsRepository,
        bgmPlayer = settingsPlatform.bgmPlayer,
    )
    private val videoRecordingController = VideoRecordingController(
        cameraRepository = cameraRepository,
        scope = viewModelScope,
        audioOptionsProvider = settingsController::recordingAudioOptions,
    )
    private val captureModeController = CameraCaptureModeController()
    private val faceTrackingPresenter = FaceTrackingPresenter()
    private val avatarSelectionController = AvatarSelectionController()
    private val avatarScaleController = AvatarScaleController()
    private val backgroundController = CameraBackgroundController()
    private val pinchGestureController = PinchGestureController()
    private val uiVisibilityController = CameraUiVisibilityController()

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // controller の同期 update 直後に uiState.value が見えるよう、Unconfined で per-controller collect する。
    // viewModelScope の Job を共有しているため、ViewModel cleared 時にまとめて cancel される。
    private val mirrorScope = CoroutineScope(
        viewModelScope.coroutineContext + Dispatchers.Unconfined,
    )

    // 撮影に成功した画像 URI を保持し、削除対象として参照する。削除成功でクリアする。
    private var lastCapturedPhotoUri: String? = null

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
            photoCaptureController.state.collect { photoCapture ->
                if (photoCapture is PhotoCaptureState.Succeeded) {
                    lastCapturedPhotoUri = photoCapture.uri
                }
                _uiState.update {
                    // 新しい撮影サイクルが始まったら、前回の削除結果バナーを消す。
                    val photoDeletion = if (photoCapture == PhotoCaptureState.Capturing) {
                        PhotoDeletionState.Idle
                    } else {
                        it.photoDeletion
                    }
                    it.copy(
                        photoCapture = photoCapture,
                        photoDeletion = photoDeletion,
                        capturedPhotoUri = lastCapturedPhotoUri,
                    )
                }
            }
        }
        mirrorScope.launch {
            photoDeletionController.state.collect { photoDeletion ->
                if (photoDeletion == PhotoDeletionState.Succeeded) {
                    lastCapturedPhotoUri = null
                }
                _uiState.update {
                    it.copy(
                        photoDeletion = photoDeletion,
                        capturedPhotoUri = lastCapturedPhotoUri,
                    )
                }
            }
        }
        mirrorScope.launch {
            videoRecordingController.state.collect { videoRecording ->
                _uiState.update { it.copy(videoRecording = videoRecording) }
                settingsController.onRecordingStateChanged(videoRecording)
            }
        }
        mirrorScope.launch {
            settingsController.state.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        mirrorScope.launch {
            captureModeController.state.collect { captureMode ->
                _uiState.update { it.copy(captureMode = captureMode) }
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
        mirrorScope.launch {
            backgroundController.state.collect { background ->
                _uiState.update { it.copy(background = background) }
            }
        }
        mirrorScope.launch {
            pinchGestureController.state.collect { pinchTarget ->
                _uiState.update { it.copy(pinchTarget = pinchTarget) }
            }
        }
        mirrorScope.launch {
            uiVisibilityController.state.collect { uiVisibility ->
                _uiState.update { it.copy(uiVisibility = uiVisibility) }
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

    // ピンチ操作の相対倍率をアバター表示倍率へ反映する。
    fun onAvatarScaleChanged(scaleChange: Float) {
        avatarScaleController.onAvatarScaleChanged(scaleChange)
        _uiState.update { it.copy(avatarScale = avatarScaleController.state) }
    }


    // カメラ映像を覆う背景モードを次のプリセットへ切り替える。
    fun onToggleBackgroundMode() {
        backgroundController.onToggleBackgroundMode()
    }

    // BGM のピッカーの結果を受け取る。選択済みなら差し替え、失敗なら案内を出し、キャンセルなら何もしない。
    fun onBgmPickResult(result: MediaPickResult) {
        when (result) {
            is MediaPickResult.Success -> settingsController.onBgmPicked(result.media)
            is MediaPickResult.Error -> settingsController.onMediaPickFailed(result.messageRes)
            MediaPickResult.Cancelled -> Unit
        }
    }

    // BGM の設定を外す。
    fun onClearBgm() {
        settingsController.onClearBgm()
    }

    // 設定画面から BGM を再生 / 停止する。
    fun onToggleBgmPlayback() {
        settingsController.onToggleBgmPlayback()
    }

    fun onBgmVolumeChanged(volume: Float) {
        settingsController.onBgmVolumeChanged(volume)
    }

    fun onBgmVolumeChangeFinished() {
        settingsController.onBgmVolumeChangeFinished()
    }

    // 録画の開始と同時に BGM を流す設定を反転する。
    fun onToggleBgmStartsWithRecording() {
        settingsController.onToggleBgmStartsWithRecording()
    }

    // 雑音抑制の設定を反転する。
    fun onToggleNoiseSuppression() {
        settingsController.onToggleNoiseSuppression()
    }

    // 背景画像のピッカーの結果を受け取る。選択済みなら背景に設定し、失敗なら案内を出し、キャンセルなら何もしない。
    fun onBackgroundImagePickResult(result: MediaPickResult) {
        when (result) {
            is MediaPickResult.Success -> settingsController.onBackgroundImagePicked(result.media)
            is MediaPickResult.Error -> settingsController.onMediaPickFailed(result.messageRes)
            MediaPickResult.Cancelled -> Unit
        }
    }

    // 背景画像の設定を外して単色の背景プリセットへ戻す。
    fun onClearBackgroundImage() {
        settingsController.onClearBackgroundImage()
    }

    fun onBackgroundImageCropChanged(crop: BackgroundImageCrop) {
        settingsController.onBackgroundImageCropChanged(crop)
    }

    fun onBackgroundImageCropChangeFinished() {
        settingsController.onBackgroundImageCropChangeFinished()
    }

    // 設定の保存や BGM 再生失敗の案内を閉じる。
    fun onDismissSettingsMessage() {
        settingsController.onDismissMessage()
    }

    // アプリが背面へ回ったとき、設定画面で流している BGM を止める。
    fun onAppBackgrounded() {
        settingsController.onAppBackgrounded()
    }

    // 操作 UI を隠す UI 非表示モードを ON にする。
    fun onHideUi() {
        uiVisibilityController.onHideUi()
    }

    // UI 非表示モードを OFF にして、隠していた操作 UI を再表示する。
    fun onShowUi() {
        uiVisibilityController.onShowUi()
    }

    // 写真 / 動画の撮影モードを切り替える。モード切り替えはカメラの再構成を伴うため、録画中は無視する。
    fun onToggleCaptureMode() {
        if (_uiState.value.isVideoRecordingActive) {
            return
        }
        acknowledgeCaptureOutcomes()
        captureModeController.onToggleCaptureMode()
    }

    // 動画モードのシャッターで、録画の開始と停止を切り替える。
    fun onToggleVideoRecording() {
        photoCaptureController.onOutcomeAcknowledged()
        videoRecordingController.onToggleRecording()
    }

    fun onCapturePhoto() {
        videoRecordingController.onOutcomeAcknowledged()
        photoCaptureController.capturePhoto()
    }

    fun onDeletePhoto() {
        val uri = lastCapturedPhotoUri ?: return
        photoDeletionController.deletePhoto(uri)
    }

    fun onDismissFilePickerError() {
        avatarSelectionController.onDismissFilePickerError()
    }

    internal fun releaseCurrentAvatarAsset() {
        avatarSelectionController.release()
    }

    // 写真と動画の完了 / 失敗の結果を通知済みにする。残したままだと、次の操作の最中に古い結果バナーが
    // 再表示されたり、別の操作の通知を隠したりする。
    private fun acknowledgeCaptureOutcomes() {
        photoCaptureController.onOutcomeAcknowledged()
        videoRecordingController.onOutcomeAcknowledged()
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
        settingsController.release()
        releaseCurrentAvatarAsset()
        super.onCleared()
    }
}
