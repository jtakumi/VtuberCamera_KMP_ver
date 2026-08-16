package com.example.vtubercamera_kmp_ver.camera.facetracking

import com.example.vtubercamera_kmp_ver.avatar.mapping.FaceToAvatarMapper
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.camera.FaceTrackingUiState
import com.example.vtubercamera_kmp_ver.camera.NormalizedFaceFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 顔トラッキングの生フレームを UI 状態と avatar render state へ変換する。
class FaceTrackingPresenter(
    private val faceToAvatarMapper: FaceToAvatarMapper = FaceToAvatarMapper(),
) {
    private val _state = MutableStateFlow(FaceTrackingPresenterState())
    val state: StateFlow<FaceTrackingPresenterState> = _state.asStateFlow()

    fun onFaceTrackingFrameChanged(frame: NormalizedFaceFrame?) {
        _state.update { current ->
            val nextAvatarRender = faceToAvatarMapper.map(
                frame = frame,
                previousState = current.avatarRender,
            )
            FaceTrackingPresenterState(
                faceTracking = FaceTrackingUiState(
                    isTracking = frame != null,
                    frame = frame,
                ),
                avatarRender = nextAvatarRender,
            )
        }
    }
}

// 顔トラッキング表示状態と avatar render state をまとめて伝えるための内部状態。
data class FaceTrackingPresenterState(
    val faceTracking: FaceTrackingUiState = FaceTrackingUiState(),
    val avatarRender: AvatarRenderState = AvatarRenderState.Neutral,
)
