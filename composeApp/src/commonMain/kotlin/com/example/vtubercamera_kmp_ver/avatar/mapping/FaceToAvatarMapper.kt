package com.example.vtubercamera_kmp_ver.avatar.mapping

import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarTrackingStatus
import com.example.vtubercamera_kmp_ver.camera.NormalizedFaceFrame

/**
 * 生のトラッカー値を制限し、不正なアバター出力にならないようにする設定。
 */
data class AvatarMappingClampConfig(
    val yawRangeDegrees: ClosedFloatingPointRange<Float> = -40f..40f,
    val pitchRangeDegrees: ClosedFloatingPointRange<Float> = -25f..25f,
    val rollRangeDegrees: ClosedFloatingPointRange<Float> = -30f..30f,
    val expressionRange: ClosedFloatingPointRange<Float> = 0f..1f,
)

data class FaceToAvatarMapperConfig(
    val trackingConfidenceThreshold: Float = 0.5f,
    val clamp: AvatarMappingClampConfig = AvatarMappingClampConfig(),
    val smoothing: AvatarMotionSmoothingConfig = AvatarMotionSmoothingConfig(),
    val bodySwayGain: Float = 18f,
    val bodyLeanGain: Float = 12f,
    // Position estimates are derived from the face rectangle on Android. Head pose remains
    // part of the result so the torso continues to follow a turn when the face stays centered.
    val bodySwayFromYawGain: Float = 0.5f,
    val bodyLeanFromPitchGain: Float = 0.4f,
)

/**
 * 顔トラッキングのフレーム情報を [AvatarRenderState] に変換する副作用のない共有マッパー。
 */
class FaceToAvatarMapper(
    private val config: FaceToAvatarMapperConfig = FaceToAvatarMapperConfig(),
) {
    private val motionSmoother = AvatarMotionSmoother(config.smoothing)

    fun map(
        frame: NormalizedFaceFrame?,
        previousState: AvatarRenderState = AvatarRenderState.Neutral,
    ): AvatarRenderState {
        val target = when {
            frame == null -> buildDecayState(previousState, AvatarTrackingStatus.NotTracked)
            frame.trackingConfidence >= config.trackingConfidenceThreshold -> buildTrackingState(frame)
            else -> buildDecayState(
                previousState = previousState,
                status = AvatarTrackingStatus.Lost,
                frame = frame,
            )
        }

        return motionSmoother.smooth(
            previous = previousState,
            target = target,
        )
    }

    private fun buildTrackingState(frame: NormalizedFaceFrame): AvatarRenderState = AvatarRenderState(
        rig = AvatarRigState(
            headYawDegrees = frame.headYawDegrees.clamp(
                minValue = config.clamp.yawRangeDegrees.start,
                maxValue = config.clamp.yawRangeDegrees.endInclusive,
            ),
            headPitchDegrees = frame.headPitchDegrees.clamp(
                minValue = config.clamp.pitchRangeDegrees.start,
                maxValue = config.clamp.pitchRangeDegrees.endInclusive,
            ),
            headRollDegrees = frame.headRollDegrees.clamp(
                minValue = config.clamp.rollRangeDegrees.start,
                maxValue = config.clamp.rollRangeDegrees.endInclusive,
            ),
            bodySwayDegrees = bodySwayDegrees(frame),
            bodyLeanDegrees = bodyLeanDegrees(frame),
        ),
        expressions = AvatarExpressionWeights(
            leftEyeBlink = frame.leftEyeBlink.clamp(
                minValue = config.clamp.expressionRange.start,
                maxValue = config.clamp.expressionRange.endInclusive,
            ),
            rightEyeBlink = frame.rightEyeBlink.clamp(
                minValue = config.clamp.expressionRange.start,
                maxValue = config.clamp.expressionRange.endInclusive,
            ),
            jawOpen = frame.jawOpen.clamp(
                minValue = config.clamp.expressionRange.start,
                maxValue = config.clamp.expressionRange.endInclusive,
            ),
            mouthSmile = frame.mouthSmile.clamp(
                minValue = config.clamp.expressionRange.start,
                maxValue = config.clamp.expressionRange.endInclusive,
            ),
        ),
        trackingStatus = AvatarTrackingStatus.Tracking,
        trackingConfidence = frame.trackingConfidence.clamp(0f, 1f),
        sourceTimestampMillis = frame.timestampMillis,
    )

    private fun bodySwayDegrees(frame: NormalizedFaceFrame): Float {
        val translationSway = frame.headTranslationX * config.bodySwayGain
        val poseContribution = frame.headYawDegrees * config.bodySwayFromYawGain
        return (translationSway + poseContribution).clamp(-18f, 18f)
    }

    private fun bodyLeanDegrees(frame: NormalizedFaceFrame): Float {
        val translationLean = -frame.headTranslationZ * config.bodyLeanGain
        val poseContribution = frame.headPitchDegrees * config.bodyLeanFromPitchGain
        return (translationLean + poseContribution).clamp(-12f, 12f)
    }

    private fun buildDecayState(
        previousState: AvatarRenderState,
        status: AvatarTrackingStatus,
        frame: NormalizedFaceFrame? = null,
    ): AvatarRenderState = AvatarRenderState(
        rig = AvatarRigState(),
        expressions = AvatarExpressionWeights(),
        trackingStatus = status,
        trackingConfidence = frame?.trackingConfidence?.clamp(0f, 1f) ?: 0f,
        sourceTimestampMillis = frame?.timestampMillis ?: previousState.sourceTimestampMillis,
    )
}
