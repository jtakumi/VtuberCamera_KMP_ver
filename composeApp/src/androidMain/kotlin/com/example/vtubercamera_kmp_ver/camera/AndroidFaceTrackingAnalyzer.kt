package com.example.vtubercamera_kmp_ver.camera

import android.graphics.PointF
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

internal class AndroidFaceTrackingAnalyzer(
    private val lensFacing: CameraLensFacing,
    private val onFaceFrame: (NormalizedFaceFrame?) -> Unit,
) : ImageAnalysis.Analyzer {
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build(),
    )
    private val isProcessing = AtomicBoolean(false)
    private var previousFrame: NormalizedFaceFrame? = null

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val nextFrame = faces.firstOrNull()?.toNormalizedFrame(
                    timestampMillis = TimeUnit.NANOSECONDS.toMillis(imageProxy.imageInfo.timestamp),
                    lensFacing = lensFacing,
                    previousFrame = previousFrame,
                )
                previousFrame = nextFrame
                onFaceFrame(nextFrame)
            }
            .addOnFailureListener {
                previousFrame = null
                onFaceFrame(null)
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    fun close() {
        detector.close()
    }
}

private fun Face.toNormalizedFrame(
    timestampMillis: Long,
    lensFacing: CameraLensFacing,
    previousFrame: NormalizedFaceFrame?,
): NormalizedFaceFrame {
    val faceHeight = boundingBox.height().toFloat().coerceAtLeast(1f)
    val rawYaw = if (lensFacing == CameraLensFacing.Front) -headEulerAngleY else headEulerAngleY
    val rawRoll = if (lensFacing == CameraLensFacing.Front) -headEulerAngleZ else headEulerAngleZ
    val rawPitch = headEulerAngleX
    val rawLeftBlink = 1f - (leftEyeOpenProbability ?: 1f)
    val rawRightBlink = 1f - (rightEyeOpenProbability ?: 1f)
    val rawJawOpen = estimateJawOpen(faceHeight)
    val rawSmile = (smilingProbability ?: 0f).coerceIn(0f, 1f)
    val trackingConfidence = buildList {
        leftEyeOpenProbability?.let { add(it.coerceIn(0f, 1f)) }
        rightEyeOpenProbability?.let { add(it.coerceIn(0f, 1f)) }
        smilingProbability?.let { add(it.coerceIn(0f, 1f)) }
        if (trackingId != null) add(1f)
    }.average().toFloat().takeIf { it.isFinite() } ?: 0.8f

    val currentFrame = NormalizedFaceFrame(
        timestampMillis = timestampMillis,
        trackingConfidence = trackingConfidence,
        headYawDegrees = rawYaw,
        headPitchDegrees = rawPitch,
        headRollDegrees = rawRoll,
        leftEyeBlink = rawLeftBlink.coerceIn(0f, 1f),
        rightEyeBlink = rawRightBlink.coerceIn(0f, 1f),
        jawOpen = rawJawOpen,
        mouthSmile = rawSmile,
    )

    return smoothFrame(previousFrame = previousFrame, currentFrame = currentFrame)
}

private fun Face.estimateJawOpen(faceHeight: Float): Float {
    val upperLip = contourCenterY(FaceContour.UPPER_LIP_BOTTOM)
    val lowerLip = contourCenterY(FaceContour.LOWER_LIP_TOP)
    if (upperLip == null || lowerLip == null) {
        return 0f
    }

    val mouthGapRatio = abs(lowerLip - upperLip) / faceHeight
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
