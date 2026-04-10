package com.example.vtubercamera_kmp_ver.avatar.mapping

import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import kotlin.math.max
import kotlin.math.min

/**
 * マッパーで使う共有の平滑化設定。
 *
 * `trackingAlpha` は安定して追跡できている間、`lostAlpha` はニュートラルへ戻している間に使う。
 */
data class AvatarMotionSmoothingConfig(
    val trackingAlpha: Float = 0.45f,
    val lostAlpha: Float = 0.15f,
)

object AvatarMotionSmoother {
    fun smooth(
        previous: AvatarRenderState,
        target: AvatarRenderState,
        config: AvatarMotionSmoothingConfig,
    ): AvatarRenderState {
        val alpha = when (target.trackingStatus) {
            com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus.Tracking -> config.trackingAlpha
            com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus.Lost,
            com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus.NotTracked,
            -> config.lostAlpha
        }.coerceIn(0f, 1f)

        return target.copy(
            rig = smoothRig(previous.rig, target.rig, alpha),
            expressions = smoothExpressions(previous.expressions, target.expressions, alpha),
        )
    }

    private fun smoothRig(
        previous: AvatarRigState,
        target: AvatarRigState,
        alpha: Float,
    ): AvatarRigState = AvatarRigState(
        headYawDegrees = lerp(previous.headYawDegrees, target.headYawDegrees, alpha),
        headPitchDegrees = lerp(previous.headPitchDegrees, target.headPitchDegrees, alpha),
        headRollDegrees = lerp(previous.headRollDegrees, target.headRollDegrees, alpha),
    )

    private fun smoothExpressions(
        previous: AvatarExpressionWeights,
        target: AvatarExpressionWeights,
        alpha: Float,
    ): AvatarExpressionWeights = AvatarExpressionWeights(
        leftEyeBlink = lerp(previous.leftEyeBlink, target.leftEyeBlink, alpha),
        rightEyeBlink = lerp(previous.rightEyeBlink, target.rightEyeBlink, alpha),
        jawOpen = lerp(previous.jawOpen, target.jawOpen, alpha),
        mouthSmile = lerp(previous.mouthSmile, target.mouthSmile, alpha),
    )

    private fun lerp(from: Float, to: Float, alpha: Float): Float = from + (to - from) * alpha
}

internal fun Float.clamp(minValue: Float, maxValue: Float): Float = max(minValue, min(this, maxValue))
