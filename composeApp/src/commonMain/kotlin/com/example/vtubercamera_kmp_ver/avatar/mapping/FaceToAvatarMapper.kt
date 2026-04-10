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
)

/**
 * 顔トラッキングのフレーム情報を [AvatarRenderState] に変換する副作用のない共有マッパー。
 */
class FaceToAvatarMapper(
    private val config: FaceToAvatarMapperConfig = FaceToAvatarMapperConfig(),
) {
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

        return AvatarMotionSmoother.smooth(
            previous = previousState,
            target = target,
            config = config.smoothing,
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
