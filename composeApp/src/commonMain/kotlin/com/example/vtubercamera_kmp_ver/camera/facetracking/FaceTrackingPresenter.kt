package com.example.vtubercamera_kmp_ver.camera.facetracking

import com.example.vtubercamera_kmp_ver.avatar.mapping.FaceToAvatarMapper
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.camera.FaceTrackingDisplayState
import com.example.vtubercamera_kmp_ver.camera.FaceTrackingUiState
import com.example.vtubercamera_kmp_ver.camera.NormalizedFaceFrame
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 顔トラッキングの生フレームを画面表示用ラベルと avatar render state へ変換する。
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
                    display = frame?.toDisplayState(),
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

// 顔トラッキングの生データを画面表示用ラベル付き状態へ変換する。
private fun NormalizedFaceFrame.toDisplayState(): FaceTrackingDisplayState =
    FaceTrackingDisplayState(
        headYawLabel = "${headYawDegrees.roundToInt()} deg",
        headPitchLabel = "${headPitchDegrees.roundToInt()} deg",
        headRollLabel = "${headRollDegrees.roundToInt()} deg",
        leftEyeBlinkLabel = leftEyeBlink.asPercentLabel(),
        rightEyeBlinkLabel = rightEyeBlink.asPercentLabel(),
        jawOpenLabel = jawOpen.asPercentLabel(),
        mouthSmileLabel = mouthSmile.asPercentLabel(),
    )

// 0 から 1 の値をパーセント表記へ整形する。
private fun Float.asPercentLabel(): String = "${(coerceIn(0f, 1f) * 100).roundToInt()}%"
