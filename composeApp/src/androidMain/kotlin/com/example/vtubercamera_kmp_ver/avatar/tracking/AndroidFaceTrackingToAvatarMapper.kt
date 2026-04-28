package com.example.vtubercamera_kmp_ver.avatar.tracking

import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState

/**
 * Android renderer 向けに追跡値を軽く補正し、カメラオフセットや表情反映を見えやすくする。
 */
internal class AndroidFaceTrackingToAvatarMapper {
    fun map(renderState: AvatarRenderState): AvatarRenderState {
        if (!renderState.isTracking) {
            return renderState
        }

        val correctedRig = AvatarRigState(
            headYawDegrees = (renderState.rig.headYawDegrees * YAW_GAIN).coerceIn(-MAX_YAW_DEGREES, MAX_YAW_DEGREES),
            headPitchDegrees = (renderState.rig.headPitchDegrees * PITCH_GAIN).coerceIn(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES),
            headRollDegrees = (renderState.rig.headRollDegrees * ROLL_GAIN).coerceIn(-MAX_ROLL_DEGREES, MAX_ROLL_DEGREES),
        )

        val correctedExpressions = AvatarExpressionWeights(
            leftEyeBlink = emphasizeBlink(renderState.expressions.leftEyeBlink),
            rightEyeBlink = emphasizeBlink(renderState.expressions.rightEyeBlink),
            jawOpen = emphasizeJaw(renderState.expressions.jawOpen),
            mouthSmile = emphasizeSmile(renderState.expressions.mouthSmile),
        )

        return renderState.copy(
            rig = correctedRig,
            expressions = correctedExpressions,
        )
    }

    private fun emphasizeBlink(value: Float): Float {
        return ((value - 0.1f) / 0.82f).coerceIn(0f, 1f)
    }

    private fun emphasizeJaw(value: Float): Float {
        return (value * 1.2f).coerceIn(0f, 1f)
    }

    private fun emphasizeSmile(value: Float): Float {
        return ((value - 0.08f) / 0.7f).coerceIn(0f, 1f)
    }

    private companion object {
        private const val YAW_GAIN = 1.15f
        private const val PITCH_GAIN = 1.08f
        private const val ROLL_GAIN = 1.05f
        private const val MAX_YAW_DEGREES = 45f
        private const val MAX_PITCH_DEGREES = 30f
        private const val MAX_ROLL_DEGREES = 35f
    }
}
