package com.example.vtubercamera_kmp_ver.avatar.state

import androidx.compose.runtime.Immutable
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarExpressionWeights
import com.example.vtubercamera_kmp_ver.avatar.model.AvatarRigState

// `Lost` は「顔は検出できているが信頼度が低い」状態で、頭の向きは実測値を保ちつつ表情だけを減衰させる。
// `NotTracked` は顔が取れていない状態で、姿勢・表情ともにニュートラルへ戻していく。
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
