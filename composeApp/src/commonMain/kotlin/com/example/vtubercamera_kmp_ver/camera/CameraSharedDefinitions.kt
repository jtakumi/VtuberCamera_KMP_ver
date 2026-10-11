package com.example.vtubercamera_kmp_ver.camera

import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_lens_switch_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_permission_denied
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_photo_capture_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_photo_delete_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_preview_initialization_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_unavailable
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_unknown
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_video_record_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_photo_capture_succeeded
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_photo_delete_succeeded
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_video_record_succeeded

// 画面から参照する共有のカメラ権限状態。
enum class PermissionState {
    Unknown,
    Granted,
    Denied,
}

sealed interface PreviewState {
    data object Preparing : PreviewState

    data object Showing : PreviewState

    data class Error(val error: CameraError) : PreviewState
}

// UI とリポジトリで扱う共有のカメラエラー種別。
enum class CameraError {
    PermissionDenied,
    CameraUnavailable,
    PreviewInitializationFailed,
    LensSwitchFailed,
    PhotoCaptureFailed,
    PhotoDeleteFailed,
    VideoRecordFailed,
    Unknown,
}

sealed interface PhotoCaptureState {
    data object Idle : PhotoCaptureState

    data object Capturing : PhotoCaptureState

    // [uri] は保存先のギャラリー項目を指す。
    data class Succeeded(val uri: String?) : PhotoCaptureState

    data class Failed(val error: CameraError) : PhotoCaptureState
}

// 動画録画の進行に関する共有 UI 状態。[Starting]・[Recording]・[Finalizing] のあいだは録画が進行中で、
// 操作 UI は映像へ映り込まないよう隠れる。
sealed interface VideoRecordingState {
    data object Idle : VideoRecordingState

    // 録画開始を受け付けてから、操作 UI が画面から消えて録画が実際に始まるまでの状態。
    data object Starting : VideoRecordingState

    data object Recording : VideoRecordingState

    // 録画停止を要求してから、ギャラリーへの保存が完了するまでの状態。
    data object Finalizing : VideoRecordingState

    // [uri] は保存先のギャラリー項目を指す。
    data class Succeeded(val uri: String?) : VideoRecordingState

    data class Failed(val error: CameraError) : VideoRecordingState
}

// 録画が進行中か。進行中は操作 UI を隠し、モードやレンズの切り替えを受け付けない。
val VideoRecordingState.isInProgress: Boolean
    get() = when (this) {
        VideoRecordingState.Starting,
        VideoRecordingState.Recording,
        VideoRecordingState.Finalizing,
        -> true
        VideoRecordingState.Idle,
        is VideoRecordingState.Succeeded,
        is VideoRecordingState.Failed,
        -> false
    }

// 撮影画像の削除に関する共有 UI 状態。
sealed interface PhotoDeletionState {
    data object Idle : PhotoDeletionState

    data object Deleting : PhotoDeletionState

    data object Succeeded : PhotoDeletionState

    data class Failed(val error: CameraError) : PhotoDeletionState
}

// 共有のカメラ UI で、案内バナーとエラーバナーを区別する。
enum class CameraMessageType {
    Guide,
    Error,
}

// 共有のカメラ画面で使う、ローカライズ済みメッセージと表示種別をまとめる。
data class CameraMessage(
    val type: CameraMessageType,
    val messageRes: StringResource,
    val formatArgs: List<Any> = emptyList(),
)

fun CameraError.toCameraMessage(): CameraMessage {
    val messageRes = when (this) {
        CameraError.PermissionDenied -> Res.string.camera_error_permission_denied
        CameraError.CameraUnavailable -> Res.string.camera_error_unavailable
        CameraError.PreviewInitializationFailed -> {
            Res.string.camera_error_preview_initialization_failed
        }
        CameraError.LensSwitchFailed -> Res.string.camera_error_lens_switch_failed
        CameraError.PhotoCaptureFailed -> Res.string.camera_error_photo_capture_failed
        CameraError.PhotoDeleteFailed -> Res.string.camera_error_photo_delete_failed
        CameraError.VideoRecordFailed -> Res.string.camera_error_video_record_failed
        CameraError.Unknown -> Res.string.camera_error_unknown
    }
    return CameraMessage(
        type = CameraMessageType.Error,
        messageRes = messageRes,
    )
}

fun PhotoCaptureState.toCameraMessage(): CameraMessage? = when (this) {
    PhotoCaptureState.Idle,
    PhotoCaptureState.Capturing,
    -> null
    is PhotoCaptureState.Succeeded -> CameraMessage(
        type = CameraMessageType.Guide,
        messageRes = Res.string.camera_photo_capture_succeeded,
    )
    is PhotoCaptureState.Failed -> error.toCameraMessage()
}

fun VideoRecordingState.toCameraMessage(): CameraMessage? = when (this) {
    VideoRecordingState.Idle,
    VideoRecordingState.Starting,
    VideoRecordingState.Recording,
    VideoRecordingState.Finalizing,
    -> null
    is VideoRecordingState.Succeeded -> CameraMessage(
        type = CameraMessageType.Guide,
        messageRes = Res.string.camera_video_record_succeeded,
    )
    is VideoRecordingState.Failed -> error.toCameraMessage()
}

fun PhotoDeletionState.toCameraMessage(): CameraMessage? = when (this) {
    PhotoDeletionState.Idle,
    PhotoDeletionState.Deleting,
    -> null
    PhotoDeletionState.Succeeded -> CameraMessage(
        type = CameraMessageType.Guide,
        messageRes = Res.string.camera_photo_delete_succeeded,
    )
    is PhotoDeletionState.Failed -> error.toCameraMessage()
}

// リポジトリ呼び出し失敗時も、ドメイン上のカメラエラー種別を保持する。
class CameraRepositoryException(val error: CameraError) : Exception(error.name)

interface CameraRepository {
    suspend fun startPreview(lensFacing: CameraLensFacing): Result<CameraLensFacing>

    suspend fun stopPreview()

    suspend fun switchLens(current: CameraLensFacing): Result<CameraLensFacing>

    suspend fun resolveInitialLens(preferred: CameraLensFacing): Result<CameraLensFacing>

    fun observePreviewState(): Flow<PreviewState>

    fun observePhotoCaptureState(): Flow<PhotoCaptureState>

    suspend fun capturePhoto(): Result<String?>

    fun observeVideoRecordingState(): Flow<VideoRecordingState>

    // アプリ画面（カメラ映像とアバター）の録画を開始する。録画中や準備未完了のときは
    // [CameraError.VideoRecordFailed] で失敗する。
    suspend fun startVideoRecording(): Result<Unit>

    // 録画を停止し、ギャラリーへの保存完了まで待って保存先の URI を返す。録画中でなければ失敗する。
    suspend fun stopVideoRecording(): Result<String?>

    fun observePhotoDeletionState(): Flow<PhotoDeletionState>

    suspend fun deletePhoto(uri: String): Result<Unit>

    fun onPlatformPreviewStarted(lensFacing: CameraLensFacing)

    fun onPlatformPreviewError(lensFacing: CameraLensFacing, error: CameraError)

    fun observeZoomState(): Flow<CameraZoomUiState>

    fun onPlatformZoomStateChanged(zoomUiState: CameraZoomUiState)

    fun setZoomRatio(updatedZoomRatio: Float)
}

interface PermissionRepository {
    suspend fun checkCameraPermission(): PermissionState

    suspend fun requestCameraPermission(): PermissionState
}
