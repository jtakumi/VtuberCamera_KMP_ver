package com.example.vtubercamera_kmp_ver.avatar.state

import androidx.compose.runtime.Immutable
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState

// `Lost` は、マッパーがニュートラルへ戻していく間も直前のアバター状態を保持する。
enum class AvatarTrackingStatus {
    NotTracked,
    Tracking,
    Lost,
}

@Immutable
data class AvatarRenderState(
    val rig: AvatarRigState = AvatarRigState(),
    val expressions: AvatarExpressionWeights = AvatarExpressionWeights(),
    val trackingStatus: AvatarTrackingStatus = AvatarTrackingStatus.NotTracked,
    val trackingConfidence: Float = 0f,
    val sourceTimestampMillis: Long? = null,
) {
    val isTracking: Boolean
        get() = trackingStatus == AvatarTrackingStatus.Tracking

    companion object {
        val Neutral = AvatarRenderState()
    }
}
