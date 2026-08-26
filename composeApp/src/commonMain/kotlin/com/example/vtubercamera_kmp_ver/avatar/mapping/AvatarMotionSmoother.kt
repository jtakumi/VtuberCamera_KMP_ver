package com.example.vtubercamera_kmp_ver.avatar.mapping

import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AvatarMotionSmoothingConfig(
    val minCutoff: Float = 1.2f,
    val beta: Float = 0.035f,
    val derivativeCutoff: Float = 1f,
    val lostAlpha: Float = 0.15f,
    /** Compatibility escape hatch for deterministic tests and user tuning. */
    val trackingAlpha: Float? = null,
)

/**
 * One Euro filtering for pose channels: stable around rest, responsive during fast movement.
 * Expression channels retain direct EMA behavior so blinks and mouth motion are not delayed.
 */
class AvatarMotionSmoother(
    private val config: AvatarMotionSmoothingConfig,
) {
    private var lastTimestampMillis: Long? = null
    private val poseFilters = Array(5) { OneEuroFilter(config) }

    fun smooth(previous: AvatarRenderState, target: AvatarRenderState): AvatarRenderState {
        // 顔が全く取れていないときだけ、姿勢も表情もニュートラルへ向けて減衰させる。
        if (target.trackingStatus == AvatarTrackingStatus.NotTracked) {
            lastTimestampMillis = null
            poseFilters.forEach(OneEuroFilter::reset)
            return target.copy(
                rig = lerpRig(previous.rig, target.rig, config.lostAlpha.coerceIn(0f, 1f)),
                expressions = lerpExpressions(previous.expressions, target.expressions, config.lostAlpha),
            )
        }

        // Lost は「顔は見えているが信頼度が低い」状態。頭の向きは実測値を追い続け、
        // 信頼度低下の影響を受けやすい表情だけを lostAlpha でニュートラルへ寄せる。
        val isLowConfidence = target.trackingStatus == AvatarTrackingStatus.Lost
        val timestamp = target.sourceTimestampMillis
        val dt = lastTimestampMillis?.let { last ->
            timestamp?.let { ((it - last).coerceAtLeast(1L) / 1000f).coerceAtMost(0.1f) }
        } ?: (1f / 30f)
        lastTimestampMillis = timestamp
        val fixedAlpha = config.trackingAlpha?.coerceIn(0f, 1f)

        fun pose(index: Int, previousValue: Float, value: Float): Float =
            fixedAlpha?.let { lerp(previousValue, value, it) } ?: poseFilters[index].filter(value, dt)

        val expressionAlpha = if (isLowConfidence) {
            config.lostAlpha.coerceIn(0f, 1f)
        } else {
            fixedAlpha ?: DEFAULT_EXPRESSION_ALPHA
        }
        return target.copy(
            rig = AvatarRigState(
                headYawDegrees = pose(0, previous.rig.headYawDegrees, target.rig.headYawDegrees),
                headPitchDegrees = pose(1, previous.rig.headPitchDegrees, target.rig.headPitchDegrees),
                headRollDegrees = pose(2, previous.rig.headRollDegrees, target.rig.headRollDegrees),
                bodySwayDegrees = pose(3, previous.rig.bodySwayDegrees, target.rig.bodySwayDegrees),
                bodyLeanDegrees = pose(4, previous.rig.bodyLeanDegrees, target.rig.bodyLeanDegrees),
            ),
            expressions = lerpExpressions(previous.expressions, target.expressions, expressionAlpha),
        )
    }

    private fun lerpRig(from: AvatarRigState, to: AvatarRigState, alpha: Float) = AvatarRigState(
        headYawDegrees = lerp(from.headYawDegrees, to.headYawDegrees, alpha),
        headPitchDegrees = lerp(from.headPitchDegrees, to.headPitchDegrees, alpha),
        headRollDegrees = lerp(from.headRollDegrees, to.headRollDegrees, alpha),
        bodySwayDegrees = lerp(from.bodySwayDegrees, to.bodySwayDegrees, alpha),
        bodyLeanDegrees = lerp(from.bodyLeanDegrees, to.bodyLeanDegrees, alpha),
    )

    private fun lerpExpressions(
        from: AvatarExpressionWeights,
        to: AvatarExpressionWeights,
        alpha: Float,
    ) = AvatarExpressionWeights(
        leftEyeBlink = lerp(from.leftEyeBlink, to.leftEyeBlink, alpha),
        rightEyeBlink = lerp(from.rightEyeBlink, to.rightEyeBlink, alpha),
        jawOpen = lerp(from.jawOpen, to.jawOpen, alpha),
        mouthSmile = lerp(from.mouthSmile, to.mouthSmile, alpha),
    )

    private class OneEuroFilter(private val config: AvatarMotionSmoothingConfig) {
        private var value: Float? = null
        private var derivative = 0f

        fun reset() {
            value = null
            derivative = 0f
        }

        fun filter(input: Float, dt: Float): Float {
            val previous = value ?: return input.also { value = it }
            val derivativeAlpha = alpha(config.derivativeCutoff, dt)
            derivative = lerp(derivative, (input - previous) / dt, derivativeAlpha)
            val cutoff = config.minCutoff + config.beta * abs(derivative)
            return lerp(previous, input, alpha(cutoff, dt)).also { value = it }
        }

        private fun alpha(cutoff: Float, dt: Float): Float {
            val tau = 1f / (2f * PI.toFloat() * cutoff.coerceAtLeast(0.001f))
            return 1f / (1f + tau / dt)
        }
    }

    private companion object {
        const val DEFAULT_EXPRESSION_ALPHA = 0.55f
    }
}

private fun lerp(from: Float, to: Float, alpha: Float): Float =
    from + (to - from) * alpha.coerceIn(0f, 1f)

internal fun Float.clamp(minValue: Float, maxValue: Float): Float = max(minValue, min(this, maxValue))
