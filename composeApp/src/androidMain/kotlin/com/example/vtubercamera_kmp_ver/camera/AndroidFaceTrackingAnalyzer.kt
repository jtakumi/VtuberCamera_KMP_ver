package com.example.vtubercamera_kmp_ver.camera

import android.graphics.PointF
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** tracking id が継続している顔の信頼度。 */
private const val TRACKED_FACE_CONFIDENCE = 1f

/** 検出はできたが同一の顔として追跡できていないフレームの信頼度。共有側のしきい値は上回る。 */
private const val UNSTABLE_FACE_CONFIDENCE = 0.75f

@ExperimentalGetImage
internal class AndroidFaceTrackingAnalyzer(
    private val lensFacing: CameraLensFacing,
    private val onFaceFrame: (NormalizedFaceFrame?) -> Unit,
    private val detectorClient: AndroidFaceDetectorClient = MlKitAndroidFaceDetectorClient(),
    private val hasMediaImage: (ImageProxy) -> Boolean = { imageProxy -> imageProxy.image != null },
    private val buildInputImage: (ImageProxy) -> InputImage = { imageProxy ->
        val mediaImage = requireNotNull(imageProxy.image)
        InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    },
) : ImageAnalysis.Analyzer {
    private val isProcessing = AtomicBoolean(false)
    private var previousFrame: NormalizedFaceFrame? = null
    private val translationEstimator = FaceTranslationEstimator()

    // Android lint recognizes @ExperimentalGetImage for ImageProxy.image access, while Kotlin reports @OptIn does not satisfy this CameraX check.
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (!hasMediaImage(imageProxy)) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val image = buildInputImage(imageProxy)
        detectorClient.process(
            image = image,
            onSuccess = { faces ->
                val face = faces.firstOrNull()
                val nextFrame = face?.toNormalizedFrame(
                    timestampMillis = TimeUnit.NANOSECONDS.toMillis(imageProxy.imageInfo.timestamp),
                    lensFacing = lensFacing,
                    previousFrame = previousFrame,
                    headTranslation = translationEstimator.estimate(
                        face = face,
                        frameWidthPixels = imageProxy.uprightWidthPixels(),
                        lensFacing = lensFacing,
                    ),
                )
                if (face == null) translationEstimator.reset()
                previousFrame = nextFrame
                onFaceFrame(nextFrame)
            },
            onFailure = {
                previousFrame = null
                translationEstimator.reset()
                onFaceFrame(null)
            },
            onComplete = {
                isProcessing.set(false)
                imageProxy.close()
            },
        )
    }

    fun close() {
        detectorClient.close()
        translationEstimator.reset()
    }
}

internal interface AndroidFaceDetectorClient {
    fun process(
        image: InputImage,
        onSuccess: (List<AndroidDetectedFace>) -> Unit,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit,
    )

    fun close()
}

internal data class AndroidDetectedFace(
    val boundingBoxHeight: Float,
    val boundingBoxCenterX: Float = 0f,
    val headEulerAngleX: Float,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
    val trackingId: Int?,
    val upperLipBottomY: Float?,
    val lowerLipTopY: Float?,
)

private class MlKitAndroidFaceDetectorClient : AndroidFaceDetectorClient {
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build(),
    )

    override fun process(
        image: InputImage,
        onSuccess: (List<AndroidDetectedFace>) -> Unit,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit,
    ) {
        detector.process(image)
            .addOnSuccessListener { faces ->
                // アバターは最も目立つ 1 人だけを追跡する。全顔の輪郭点を Kotlin オブジェクトへ
                // 展開せず、必要な顔だけ変換する。
                onSuccess(
                    faces.firstOrNull()?.let(Face::toDetectedFace)?.let(::listOf).orEmpty(),
                )
            }
            .addOnFailureListener { throwable ->
                onFailure(throwable)
            }
            .addOnCompleteListener {
                onComplete()
            }
    }

    override fun close() {
        detector.close()
    }
}

private fun Face.toDetectedFace(): AndroidDetectedFace {
    return AndroidDetectedFace(
        boundingBoxHeight = boundingBox.height().toFloat().coerceAtLeast(1f),
        boundingBoxCenterX = boundingBox.exactCenterX(),
        headEulerAngleX = headEulerAngleX,
        headEulerAngleY = headEulerAngleY,
        headEulerAngleZ = headEulerAngleZ,
        leftEyeOpenProbability = leftEyeOpenProbability,
        rightEyeOpenProbability = rightEyeOpenProbability,
        smilingProbability = smilingProbability,
        trackingId = trackingId,
        upperLipBottomY = contourCenterY(FaceContour.UPPER_LIP_BOTTOM),
        lowerLipTopY = contourCenterY(FaceContour.LOWER_LIP_TOP),
    )
}

internal fun AndroidDetectedFace.toNormalizedFrame(
    timestampMillis: Long,
    lensFacing: CameraLensFacing,
    previousFrame: NormalizedFaceFrame?,
    headTranslation: HeadTranslation = HeadTranslation(),
): NormalizedFaceFrame {
    val rawYaw = if (lensFacing == CameraLensFacing.Front) -headEulerAngleY else headEulerAngleY
    val rawRoll = if (lensFacing == CameraLensFacing.Front) -headEulerAngleZ else headEulerAngleZ
    val rawPitch = headEulerAngleX
    val rawLeftBlink = 1f - (leftEyeOpenProbability ?: 1f)
    val rawRightBlink = 1f - (rightEyeOpenProbability ?: 1f)
    val rawJawOpen = estimateJawOpen()
    val rawSmile = (smilingProbability ?: 0f).coerceIn(0f, 1f)

    val currentFrame = NormalizedFaceFrame(
        timestampMillis = timestampMillis,
        trackingConfidence = detectionConfidence(),
        headYawDegrees = rawYaw,
        headPitchDegrees = rawPitch,
        headRollDegrees = rawRoll,
        headTranslationX = headTranslation.x,
        headTranslationZ = headTranslation.z,
        leftEyeBlink = rawLeftBlink.coerceIn(0f, 1f),
        rightEyeBlink = rawRightBlink.coerceIn(0f, 1f),
        jawOpen = rawJawOpen,
        mouthSmile = rawSmile,
    )

    return smoothFrame(previousFrame = previousFrame, currentFrame = currentFrame)
}

/**
 * 顔検出そのものの確からしさを返す。
 *
 * ML Kit は検出スコアを公開しないため、tracking id が振られているか（=前フレームと同じ顔として
 * 追跡できているか）だけを根拠にする。目の開き具合や笑顔の確率は「表情の値」であって検出の
 * 確からしさではなく、横を向く・まばたきするだけで下がる。これを信頼度として扱うと共有マッパーが
 * `Lost` へ落ち、顔が正面でないときほどアバターが正面へ戻ってしまう。
 */
private fun AndroidDetectedFace.detectionConfidence(): Float = when (trackingId) {
    null -> UNSTABLE_FACE_CONFIDENCE
    else -> TRACKED_FACE_CONFIDENCE
}

/** A relative position estimate based on the detected face rectangle. */
internal data class HeadTranslation(
    val x: Float = 0f,
    val z: Float = 0f,
)

/**
 * ML Kit face detection has no 3D translation output. Lateral movement and apparent face-size
 * changes provide a stable body-motion approximation instead.
 *
 * 横位置はフレーム中央を基準にする。初回検出位置を基準にすると、画面の端に寄って映り始めた
 * ときにその位置が「中央」として扱われ、アバターだけが正面に立ったまま以降の動きだけを
 * 追うことになるため。奥行きは絶対的な基準になる顔サイズが無いので、追跡中の顔ごとに
 * 初回の顔サイズを基準として保持する。
 */
internal class FaceTranslationEstimator {
    private var trackingId: Int? = null
    private var referenceHeight: Float? = null

    /**
     * 顔矩形から体の動きに使う相対位置を推定する。
     *
     * 追跡対象が変わった、またはまだ顔サイズの基準が無いフレームでは、そのフレームの顔サイズを
     * 基準として記録する。横位置はフレーム中央基準なので、基準を記録した初回フレームでも
     * 実際の立ち位置を返す。
     */
    fun estimate(
        face: AndroidDetectedFace,
        frameWidthPixels: Int,
        lensFacing: CameraLensFacing = CameraLensFacing.Back,
    ): HeadTranslation {
        val frameWidth = frameWidthPixels.coerceAtLeast(1).toFloat()
        val centerX = (face.boundingBoxCenterX / frameWidth).coerceIn(0f, 1f)
        // 除数として使うため、極端に小さな顔矩形でも下限を割らないところまで丸める。
        val height = (face.boundingBoxHeight.coerceAtLeast(1f) / frameWidth).coerceAtLeast(MIN_FACE_HEIGHT_RATIO)
        if (referenceHeight == null || trackingId != face.trackingId) {
            trackingId = face.trackingId
            referenceHeight = height
        }

        val depthReferenceHeight = referenceHeight ?: height
        return HeadTranslation(
            x = (((centerX - FRAME_CENTER_X) / depthReferenceHeight) * LATERAL_SCALE * lensFacing.translationDirection())
                .coerceIn(-MAX_LATERAL_TRANSLATION, MAX_LATERAL_TRANSLATION),
            z = ((height / depthReferenceHeight - 1f) * DEPTH_SCALE)
                .coerceIn(-MAX_DEPTH_TRANSLATION, MAX_DEPTH_TRANSLATION),
        )
    }

    fun reset() {
        trackingId = null
        referenceHeight = null
    }

    private companion object {
        const val FRAME_CENTER_X = 0.5f
        const val MIN_FACE_HEIGHT_RATIO = 0.08f
        const val LATERAL_SCALE = 0.8f
        const val DEPTH_SCALE = 0.8f
        const val MAX_LATERAL_TRANSLATION = 1f
        const val MAX_DEPTH_TRANSLATION = 0.7f
    }
}

private fun CameraLensFacing.translationDirection(): Float = when (this) {
    CameraLensFacing.Front -> -1f
    CameraLensFacing.Back -> 1f
}

private fun ImageProxy.uprightWidthPixels(): Int = when (imageInfo.rotationDegrees) {
    90, 270 -> height
    else -> width
}

private fun AndroidDetectedFace.estimateJawOpen(): Float {
    val upperLip = upperLipBottomY ?: return 0f
    val lowerLip = lowerLipTopY ?: return 0f
    val mouthGapRatio = abs(lowerLip - upperLip) / boundingBoxHeight.coerceAtLeast(1f)
    return ((mouthGapRatio - 0.015f) / 0.09f).coerceIn(0f, 1f)
}

private fun Face.contourCenterY(contourType: Int): Float? {
    val points = getContour(contourType)?.points.orEmpty()
    if (points.isEmpty()) return null

    return points.map(PointF::y).average().toFloat()
}

private fun smoothFrame(
    previousFrame: NormalizedFaceFrame?,
    currentFrame: NormalizedFaceFrame,
): NormalizedFaceFrame {
    if (previousFrame == null) {
        return currentFrame
    }

    return currentFrame.copy(
        headYawDegrees = lerp(previousFrame.headYawDegrees, currentFrame.headYawDegrees, 0.45f),
        headPitchDegrees = lerp(previousFrame.headPitchDegrees, currentFrame.headPitchDegrees, 0.45f),
        headRollDegrees = lerp(previousFrame.headRollDegrees, currentFrame.headRollDegrees, 0.4f),
        leftEyeBlink = smoothBlink(previousFrame.leftEyeBlink, currentFrame.leftEyeBlink),
        rightEyeBlink = smoothBlink(previousFrame.rightEyeBlink, currentFrame.rightEyeBlink),
        jawOpen = smoothJaw(previousFrame.jawOpen, currentFrame.jawOpen),
        mouthSmile = lerp(previousFrame.mouthSmile, currentFrame.mouthSmile, 0.35f),
    )
}

private fun smoothBlink(previous: Float, current: Float): Float {
    val snapped = when {
        current >= 0.68f -> 1f
        current <= 0.32f -> 0f
        else -> current
    }
    val alpha = if (snapped > previous) 0.55f else 0.28f
    return lerp(previous, snapped, alpha).coerceIn(0f, 1f)
}

private fun smoothJaw(previous: Float, current: Float): Float {
    val alpha = if (current > previous) 0.58f else 0.24f
    return lerp(previous, current, alpha).coerceIn(0f, 1f)
}

private fun lerp(start: Float, end: Float, alpha: Float): Float {
    return start + (end - start) * alpha
}
