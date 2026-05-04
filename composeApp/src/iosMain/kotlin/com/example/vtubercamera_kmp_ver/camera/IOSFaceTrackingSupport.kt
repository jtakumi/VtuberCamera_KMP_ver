@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.example.vtubercamera_kmp_ver.camera

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlinx.cinterop.CValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.darwin.simd_float4x4

internal const val IOS_LIMITED_TRACKING_CONFIDENCE: Float = 0.65f
private const val IOS_HEAD_YAW_SMOOTHING_ALPHA = 0.45f
private const val IOS_HEAD_PITCH_SMOOTHING_ALPHA = 0.45f
private const val IOS_HEAD_ROLL_SMOOTHING_ALPHA = 0.4f
private const val IOS_SMILE_SMOOTHING_ALPHA = 0.35f
private const val IOS_BLINK_HIGH_THRESHOLD = 0.68f
private const val IOS_BLINK_LOW_THRESHOLD = 0.32f
private const val IOS_BLINK_CLOSING_ALPHA = 0.55f
private const val IOS_BLINK_OPENING_ALPHA = 0.28f
private const val IOS_JAW_OPENING_ALPHA = 0.58f
private const val IOS_JAW_CLOSING_ALPHA = 0.24f
internal const val IOS_FACE_FRAME_DISPATCH_INTERVAL_SECONDS: Double = 1.0 / 30.0

internal enum class IOSFaceTrackingState {
    Normal,
    Limited,
    Unavailable,
}

internal data class IOSHeadPoseDegrees(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
)

internal data class IOSFaceTrackingBlendShapeValues(
    val leftEyeBlink: Float,
    val rightEyeBlink: Float,
    val jawOpen: Float,
    val smileLeft: Float,
    val smileRight: Float,
)

internal class IOSFaceTrackingDelegateCore<FaceAnchor>(
    private val firstFaceAnchor: (List<*>) -> FaceAnchor?,
    private val convertFaceAnchor: (
        anchor: FaceAnchor,
        trackingConfidence: Float,
        previousFrame: NormalizedFaceFrame?,
    ) -> NormalizedFaceFrame?,
    private val currentTimeSeconds: () -> Double,
    private val onFaceTrackingFrameChanged: (NormalizedFaceFrame?) -> Unit,
) {
    private var previousFrame: NormalizedFaceFrame? = null
    private var lastDispatchedFaceFrameSeconds: Double? = null
    private var trackingConfidence: Float = 1f

    // 追加された face anchor を shared frame として通知する。
    fun didAddAnchors(anchors: List<*>) {
        publishFaceFrame(anchors)
    }

    // 更新された face anchor を shared frame として通知する。
    fun didUpdateAnchors(anchors: List<*>) {
        publishFaceFrame(anchors)
    }

    // face anchor が消えたときに tracking state を初期化する。
    fun didRemoveAnchors(anchors: List<*>) {
        if (firstFaceAnchor(anchors) != null) {
            clearTrackedFace()
            dispatchFaceFrame(null)
        }
    }

    // ARKit tracking 状態から shared 側の tracking confidence を更新する。
    fun didChangeTrackingState(state: IOSFaceTrackingState) {
        trackingConfidence = when (state) {
            IOSFaceTrackingState.Normal -> 1f
            IOSFaceTrackingState.Limited -> IOS_LIMITED_TRACKING_CONFIDENCE
            IOSFaceTrackingState.Unavailable -> 0f
        }
        if (trackingConfidence == 0f) {
            clearTrackedFace(resetTrackingConfidence = false)
            dispatchFaceFrame(null)
        }
    }

    // セッション失敗または中断に伴って tracking state を破棄する。
    fun didFailOrInterrupt() {
        clearTrackedFace()
        dispatchFaceFrame(null)
    }

    // 保持中の前回フレームと throttle 状態を破棄する。
    fun clearTrackedFace(resetTrackingConfidence: Boolean = true) {
        previousFrame = null
        lastDispatchedFaceFrameSeconds = null
        if (resetTrackingConfidence) {
            trackingConfidence = 1f
        }
    }

    // face anchor 群から先頭の顔を抜き出して shared frame へ変換する。
    private fun publishFaceFrame(anchors: List<*>) {
        val faceAnchor = firstFaceAnchor(anchors)
        val nextFrame = faceAnchor?.let { anchor ->
            convertFaceAnchor(
                anchor,
                trackingConfidence,
                previousFrame,
            )
        }
        previousFrame = nextFrame
        dispatchFaceFrame(nextFrame)
    }

    // 30fps を超える更新を抑えつつ frame を通知する。
    private fun dispatchFaceFrame(frame: NormalizedFaceFrame?) {
        if (frame == null) {
            lastDispatchedFaceFrameSeconds = null
        } else {
            val nowSeconds = currentTimeSeconds()
            val lastDispatchedSeconds = lastDispatchedFaceFrameSeconds
            if (
                lastDispatchedSeconds != null &&
                nowSeconds - lastDispatchedSeconds < IOS_FACE_FRAME_DISPATCH_INTERVAL_SECONDS
            ) {
                return
            }
            lastDispatchedFaceFrameSeconds = nowSeconds
        }
        onFaceTrackingFrameChanged(frame)
    }
}

// TrueDepth 対応時だけ ARKit face tracking の開始処理を実行する。
internal fun startIosFaceTrackingPreview(
    isSupported: Boolean,
    prepareTracking: () -> Unit,
    runSession: () -> Throwable?,
    onComplete: (resolvedLens: CameraLensFacing, error: Throwable?) -> Unit,
) {
    if (!isSupported) {
        onComplete(CameraLensFacing.Front, IllegalStateException("ARKit face tracking is unsupported"))
        return
    }

    prepareTracking()
    onComplete(CameraLensFacing.Front, runSession())
}

// ARKit face-tracking の生データを shared face-tracking frame へ変換する。
internal fun IOSHeadPoseDegrees.toNormalizedFaceFrame(
    timestampMillis: Long,
    trackingConfidence: Float,
    blendShapes: IOSFaceTrackingBlendShapeValues,
    previousFrame: NormalizedFaceFrame?,
): NormalizedFaceFrame {
    val clampedBlendShapes = blendShapes.clamped()
    val currentFrame = NormalizedFaceFrame(
        timestampMillis = timestampMillis,
        trackingConfidence = trackingConfidence,
        headYawDegrees = -yawDegrees,
        headPitchDegrees = pitchDegrees,
        headRollDegrees = -rollDegrees,
        leftEyeBlink = clampedBlendShapes.leftEyeBlink,
        rightEyeBlink = clampedBlendShapes.rightEyeBlink,
        jawOpen = clampedBlendShapes.jawOpen,
        mouthSmile = (clampedBlendShapes.smileLeft + clampedBlendShapes.smileRight) / 2f,
    )
    return smoothFaceTrackingFrame(previousFrame = previousFrame, currentFrame = currentFrame)
}

// ARKit の transform 行列要素から head pose を度数へ変換する。
internal fun iosHeadPoseDegreesFromRotationMatrix(
    matrix02: Float,
    matrix12: Float,
    matrix22: Float,
    matrix01: Float,
    matrix00: Float,
): IOSHeadPoseDegrees {
    val pitchRadians = asin((-matrix02).coerceIn(-1f, 1f))
    val rollRadians = atan2(matrix12, matrix22)
    val yawRadians = atan2(matrix01, matrix00)
    return IOSHeadPoseDegrees(
        yawDegrees = yawRadians.toDegrees(),
        pitchDegrees = pitchRadians.toDegrees(),
        rollDegrees = rollRadians.toDegrees(),
    )
}

// ARKit の transform 行列から head pose を度数へ変換する。
internal fun CValue<simd_float4x4>.toHeadPoseDegrees(): IOSHeadPoseDegrees {
    return useContents {
        val values = columns.reinterpret<FloatVar>()
        iosHeadPoseDegreesFromRotationMatrix(
            matrix02 = values[8],
            matrix12 = values[9],
            matrix22 = values[10],
            matrix01 = values[4],
            matrix00 = values[0],
        )
    }
}

private fun IOSFaceTrackingBlendShapeValues.clamped(): IOSFaceTrackingBlendShapeValues {
    return copy(
        leftEyeBlink = leftEyeBlink.coerceIn(0f, 1f),
        rightEyeBlink = rightEyeBlink.coerceIn(0f, 1f),
        jawOpen = jawOpen.coerceIn(0f, 1f),
        smileLeft = smileLeft.coerceIn(0f, 1f),
        smileRight = smileRight.coerceIn(0f, 1f),
    )
}

// ARKit の生データを Android 側と近い応答にそろえるため平滑化する。
private fun smoothFaceTrackingFrame(
    previousFrame: NormalizedFaceFrame?,
    currentFrame: NormalizedFaceFrame,
): NormalizedFaceFrame {
    if (previousFrame == null) {
        return currentFrame
    }

    return currentFrame.copy(
        headYawDegrees = lerp(
            previousFrame.headYawDegrees,
            currentFrame.headYawDegrees,
            IOS_HEAD_YAW_SMOOTHING_ALPHA,
        ),
        headPitchDegrees = lerp(
            previousFrame.headPitchDegrees,
            currentFrame.headPitchDegrees,
            IOS_HEAD_PITCH_SMOOTHING_ALPHA,
        ),
        headRollDegrees = lerp(
            previousFrame.headRollDegrees,
            currentFrame.headRollDegrees,
            IOS_HEAD_ROLL_SMOOTHING_ALPHA,
        ),
        leftEyeBlink = smoothBlink(previousFrame.leftEyeBlink, currentFrame.leftEyeBlink),
        rightEyeBlink = smoothBlink(previousFrame.rightEyeBlink, currentFrame.rightEyeBlink),
        jawOpen = smoothJaw(previousFrame.jawOpen, currentFrame.jawOpen),
        mouthSmile = lerp(previousFrame.mouthSmile, currentFrame.mouthSmile, IOS_SMILE_SMOOTHING_ALPHA),
    )
}

// まばたきは閉じる方向をやや強めに追従させる。
private fun smoothBlink(previous: Float, current: Float): Float {
    val snapped = when {
        current >= IOS_BLINK_HIGH_THRESHOLD -> 1f
        current <= IOS_BLINK_LOW_THRESHOLD -> 0f
        else -> current
    }
    val alpha = if (snapped > previous) IOS_BLINK_CLOSING_ALPHA else IOS_BLINK_OPENING_ALPHA
    return lerp(previous, snapped, alpha).coerceIn(0f, 1f)
}

// 口の開きは開閉速度差を持たせて違和感を抑える。
private fun smoothJaw(previous: Float, current: Float): Float {
    val alpha = if (current > previous) IOS_JAW_OPENING_ALPHA else IOS_JAW_CLOSING_ALPHA
    return lerp(previous, current, alpha).coerceIn(0f, 1f)
}

// 共通の線形補間で角度と表情係数をならす。
private fun lerp(start: Float, end: Float, alpha: Float): Float {
    return start + (end - start) * alpha
}

// ラジアンを UI 表示向けの度数へ変換する。
private fun Float.toDegrees(): Float {
    return this * (180f / PI.toFloat())
}
