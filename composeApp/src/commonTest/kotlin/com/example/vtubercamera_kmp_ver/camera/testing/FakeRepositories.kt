package com.example.vtubercamera_kmp_ver.camera.testing

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.CameraLensFacing
import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.CameraZoomUiState
import com.example.vtubercamera_kmp_ver.camera.PermissionRepository
import com.example.vtubercamera_kmp_ver.camera.PermissionState
import com.example.vtubercamera_kmp_ver.camera.PhotoCaptureState
import com.example.vtubercamera_kmp_ver.camera.PhotoDeletionState
import com.example.vtubercamera_kmp_ver.camera.PreviewState
import com.example.vtubercamera_kmp_ver.camera.VideoRecordingState
import com.example.vtubercamera_kmp_ver.camera.settings.BgmPlayer
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettings
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsPlatform
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsRepository
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia
import com.example.vtubercamera_kmp_ver.camera.video.VideoRecordingAudioOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// commonTest 全体で共有するカメラリポジトリのフェイク実装。call 記録 / 結果差し替え / preview state 注入を提供する。
internal class FakeCameraRepository(
    private val resolveInitialLensResult: Result<CameraLensFacing> = Result.success(
        CameraLensFacing.Front,
    ),
    private val startPreviewResult: Result<CameraLensFacing>? = null,
    private val deletePhotoResult: Result<Unit> = Result.success(Unit),
    private val startVideoRecordingResult: Result<Unit> = Result.success(Unit),
    private val stopVideoRecordingResult: Result<String?> = Result.success("fake://video.mp4"),
) : CameraRepository {
    private val previewState = MutableStateFlow<PreviewState>(PreviewState.Preparing)
    private val photoCaptureState = MutableStateFlow<PhotoCaptureState>(PhotoCaptureState.Idle)
    private val photoDeletionState = MutableStateFlow<PhotoDeletionState>(PhotoDeletionState.Idle)
    private val videoRecordingState = MutableStateFlow<VideoRecordingState>(VideoRecordingState.Idle)
    private val zoomUiState = MutableStateFlow(
        CameraZoomUiState(
            currentCameraZoomRatio = 1f,
            minCameraZoomRatio = 1f,
            maxCameraZoomRatio = 5f,
        ),
    )

    var startPreviewCallCount: Int = 0
        private set

    var resolveInitialLensCallCount: Int = 0
        private set

    var startVideoRecordingCallCount: Int = 0
        private set

    var stopVideoRecordingCallCount: Int = 0
        private set

    val startPreviewRequests = mutableListOf<CameraLensFacing>()
    val resolveInitialLensRequests = mutableListOf<CameraLensFacing>()
    val startVideoRecordingRequests = mutableListOf<VideoRecordingAudioOptions>()
    val setZoomRatioRequests = mutableListOf<Float>()
    val deletePhotoRequests = mutableListOf<String>()

    override suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing> {
        startPreviewCallCount += 1
        startPreviewRequests += lensFacing
        val result = startPreviewResult ?: Result.success(lensFacing)
        if (result.isSuccess) {
            previewState.value = PreviewState.Showing
        }
        return result
    }

    override suspend fun stopPreview() {
        previewState.value = PreviewState.Preparing
    }

    override suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing> {
        resolveInitialLensCallCount += 1
        resolveInitialLensRequests += preferred
        return resolveInitialLensResult
    }

    override fun observePreviewState(): Flow<PreviewState> {
        return previewState.asStateFlow()
    }

    override fun observePhotoCaptureState(): Flow<PhotoCaptureState> {
        return photoCaptureState.asStateFlow()
    }

    override suspend fun capturePhoto(): Result<String?> {
        photoCaptureState.value = PhotoCaptureState.Capturing
        photoCaptureState.value = PhotoCaptureState.Succeeded("fake://photo.jpg")
        return Result.success("fake://photo.jpg")
    }

    override fun observeVideoRecordingState(): Flow<VideoRecordingState> {
        return videoRecordingState.asStateFlow()
    }

    override suspend fun startVideoRecording(audioOptions: VideoRecordingAudioOptions): Result<Unit> {
        startVideoRecordingCallCount += 1
        startVideoRecordingRequests += audioOptions
        return startVideoRecordingResult.also { result ->
            videoRecordingState.value = if (result.isSuccess) {
                VideoRecordingState.Recording
            } else {
                VideoRecordingState.Failed(CameraError.VideoRecordFailed)
            }
        }
    }

    override suspend fun stopVideoRecording(): Result<String?> {
        stopVideoRecordingCallCount += 1
        videoRecordingState.value = VideoRecordingState.Finalizing
        return stopVideoRecordingResult.also { result ->
            videoRecordingState.value = result.fold(
                onSuccess = { uri -> VideoRecordingState.Succeeded(uri) },
                onFailure = { VideoRecordingState.Failed(CameraError.VideoRecordFailed) },
            )
        }
    }

    override fun observePhotoDeletionState(): Flow<PhotoDeletionState> {
        return photoDeletionState.asStateFlow()
    }

    override suspend fun deletePhoto(uri: String): Result<Unit> {
        deletePhotoRequests += uri
        photoDeletionState.value = PhotoDeletionState.Deleting
        return deletePhotoResult.fold(
            onSuccess = {
                photoDeletionState.value = PhotoDeletionState.Succeeded
                Result.success(Unit)
            },
            onFailure = { throwable ->
                photoDeletionState.value = PhotoDeletionState.Failed(CameraError.PhotoDeleteFailed)
                Result.failure(throwable)
            },
        )
    }

    override fun onPlatformPreviewStarted(lensFacing: CameraLensFacing) {
        previewState.value = PreviewState.Showing
    }

    override fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError) {
        previewState.value = PreviewState.Error(error)
    }

    override fun observeZoomState(): Flow<CameraZoomUiState> {
        return zoomUiState.asStateFlow()
    }

    override fun onPlatformZoomStateChanged(zoomUiState: CameraZoomUiState) {
        this.zoomUiState.value = CameraZoomUiState(
            currentCameraZoomRatio = zoomUiState.currentCameraZoomRatio,
            minCameraZoomRatio = zoomUiState.minCameraZoomRatio,
            maxCameraZoomRatio = zoomUiState.maxCameraZoomRatio,
        )
    }

    override fun setZoomRatio(updatedZoomRatio: Float) {
        setZoomRatioRequests += updatedZoomRatio
        zoomUiState.value = zoomUiState.value.copy(currentCameraZoomRatio = updatedZoomRatio)
    }

    fun emitPreviewState(state: PreviewState) {
        previewState.value = state
    }
}

internal class FakePermissionRepository(
    private val checkCameraPermissionResult: PermissionState,
) : PermissionRepository {
    override suspend fun checkCameraPermission(): PermissionState {
        return checkCameraPermissionResult
    }

    override suspend fun requestCameraPermission(): PermissionState {
        return checkCameraPermissionResult
    }
}

// 設定の保存先を持たないテスト用のリポジトリ。保存した設定と削除したファイルを記録する。
internal class FakeCameraSettingsRepository(
    initialSettings: CameraSettings = CameraSettings(),
    private val loadResult: Result<CameraSettings> = Result.success(initialSettings),
    private val saveResult: Result<Unit> = Result.success(Unit),
) : CameraSettingsRepository {
    val savedSettings = mutableListOf<CameraSettings>()
    val deletedMedia = mutableListOf<StoredMedia>()

    override fun load(): Result<CameraSettings> = loadResult

    override fun save(settings: CameraSettings): Result<Unit> {
        savedSettings += settings
        return saveResult
    }

    override fun deleteMedia(media: StoredMedia): Result<Unit> {
        deletedMedia += media
        return Result.success(Unit)
    }

    override fun read(media: StoredMedia): ByteArray? = null
}

// 音を出さないテスト用の BGM プレーヤー。再生の要求と音量を記録する。
internal class FakeBgmPlayer(
    private val playResult: Result<Unit> = Result.success(Unit),
) : BgmPlayer {
    override var onPlaybackFailed: (() -> Unit)? = null
    override var isPlaying: Boolean = false
        private set
    override var positionMs: Long = 0L

    val playRequests = mutableListOf<PlayRequest>()
    val volumeRequests = mutableListOf<Float>()
    var stopCallCount: Int = 0
        private set
    var isReleased: Boolean = false
        private set

    override fun play(track: StoredMedia, volume: Float, startPositionMs: Long): Result<Unit> {
        playRequests += PlayRequest(track, volume, startPositionMs)
        isPlaying = playResult.isSuccess
        return playResult
    }

    override fun stop() {
        stopCallCount += 1
        isPlaying = false
    }

    override fun setVolume(volume: Float) {
        volumeRequests += volume
    }

    override fun release() {
        isReleased = true
    }

    data class PlayRequest(val track: StoredMedia, val volume: Float, val startPositionMs: Long)
}

// ViewModel のテストへ渡す設定まわりの一式。中身を検証したいテストは repository / player を直接参照する。
internal class FakeCameraSettingsPlatform(
    val repository: FakeCameraSettingsRepository = FakeCameraSettingsRepository(),
    val bgmPlayer: FakeBgmPlayer = FakeBgmPlayer(),
) {
    val platform = CameraSettingsPlatform(settingsRepository = repository, bgmPlayer = bgmPlayer)
}
