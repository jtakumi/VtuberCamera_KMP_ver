package com.example.vtubercamera_kmp_ver.camera.session

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.CameraLensFacing
import com.example.vtubercamera_kmp_ver.camera.CameraMessage
import com.example.vtubercamera_kmp_ver.camera.CameraMessageType
import com.example.vtubercamera_kmp_ver.camera.CameraRepository
import com.example.vtubercamera_kmp_ver.camera.CameraRepositoryException
import com.example.vtubercamera_kmp_ver.camera.PreviewState
import com.example.vtubercamera_kmp_ver.camera.toCameraMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_retrying_preview
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_error_switching_lens

// カメラセッションのライフサイクル (preview start / retry / lens 切替 / repository state 同期) を担う。
class CameraSessionController(
    private val cameraRepository: CameraRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CameraSessionUiState())
    val state: StateFlow<CameraSessionUiState> = _state.asStateFlow()

    init {
        scope.launch { observePreviewState() }
    }

    // 初期レンズを解決したうえでカメラプレビュー開始を依頼する。
    suspend fun start() {
        val initialLensResult = cameraRepository.resolveInitialLens(_state.value.lensFacing)
        if (initialLensResult.isFailure) {
            setError(initialLensResult.exceptionOrNull().toCameraError(CameraError.CameraUnavailable))
            return
        }
        val initialLens = initialLensResult.getOrDefault(_state.value.lensFacing)
        _state.update { it.copy(lensFacing = initialLens) }
        cameraRepository.startPreview(initialLens)
            .onSuccess { resolvedLens ->
                _state.update { it.copy(lensFacing = resolvedLens) }
            }
            .onFailure {
                setError(it.toCameraError(CameraError.PreviewInitializationFailed))
            }
    }

    // プレビュー再試行時の案内メッセージを出しつつ開始処理をやり直す。
    fun onRetryPreview() {
        scope.launch {
            _state.update {
                it.copy(
                    previewState = PreviewState.Preparing,
                    errorState = null,
                    message = CameraMessage(
                        type = CameraMessageType.Guide,
                        messageRes = Res.string.camera_error_retrying_preview,
                    ),
                )
            }
            cameraRepository.startPreview(_state.value.lensFacing)
                .onSuccess { resolvedLens ->
                    _state.update { it.copy(lensFacing = resolvedLens, message = null) }
                }
                .onFailure {
                    setError(it.toCameraError(CameraError.PreviewInitializationFailed))
                }
        }
    }

    // 前後カメラの切り替え要求を発行し、切り替え中の状態を表示する。
    fun onToggleLensFacing() {
        scope.launch {
            val currentLens = _state.value.lensFacing
            _state.update { current ->
                current.copy(
                    previewState = PreviewState.Preparing,
                    message = CameraMessage(
                        type = CameraMessageType.Guide,
                        messageRes = Res.string.camera_error_switching_lens,
                    ),
                )
            }

            cameraRepository.switchLens(currentLens)
                .onSuccess { switchedLens ->
                    _state.update { current ->
                        current.copy(
                            lensFacing = switchedLens,
                            errorState = null,
                            message = null,
                        )
                    }
                }
                .onFailure {
                    setError(CameraError.LensSwitchFailed)
                }
        }
    }

    // 実際に選択されたレンズ向きを UI 状態へ反映する。
    fun onLensFacingChanged(lensFacing: CameraLensFacing) {
        _state.update { it.copy(lensFacing = lensFacing) }
    }

    // 権限の再要求などプレビューを継続したまま、付帯するエラー表示だけ消したい場合の同期エントリ。
    fun clearError() {
        _state.update { it.copy(errorState = null, message = null) }
    }

    // 権限が Denied→Granted へ復帰した直後など、エラーを消してプレビュー準備中扱いに戻すための同期エントリ。
    fun clearErrorAndPrepare() {
        _state.update {
            it.copy(
                previewState = PreviewState.Preparing,
                errorState = null,
                message = null,
            )
        }
    }

    // 権限が Unknown へ戻った際、エラー表示中であればプレビュー準備中扱いへリセットする同期エントリ。
    fun resetPreviewIfErrored() {
        _state.update { current ->
            if (current.previewState is PreviewState.Error) {
                current.copy(
                    previewState = PreviewState.Preparing,
                    errorState = null,
                    message = null,
                )
            } else {
                current
            }
        }
    }

    // カメラ関連エラーをセッション状態へ集約して反映する。
    fun setError(error: CameraError) {
        _state.update {
            it.copy(
                previewState = PreviewState.Error(error),
                errorState = error,
                message = error.toCameraMessage(),
            )
        }
    }

    // リポジトリのプレビュー状態を監視してセッション状態へ同期する。
    private suspend fun observePreviewState() {
        cameraRepository.observePreviewState().collect { previewState ->
            _state.update { current ->
                val error = (previewState as? PreviewState.Error)?.error
                current.copy(
                    previewState = previewState,
                    errorState = error,
                    message = error?.toCameraMessage(),
                )
            }
        }
    }
}

// リポジトリ由来の例外から共通のカメラエラー種別を取り出す。
private fun Throwable?.toCameraError(fallback: CameraError): CameraError =
    (this as? CameraRepositoryException)?.error ?: fallback
