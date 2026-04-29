package com.example.vtubercamera_kmp_ver.camera

import android.graphics.Rect
import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidFaceTrackingAnalyzerTest {
    @Test
    fun analyze_closesFrameWhenMediaImageIsMissing() {
        val detector = FakeAndroidFaceDetectorClient()
        val frames = mutableListOf<NormalizedFaceFrame?>()
        val analyzer = createAnalyzer(detector = detector, frames = frames)
        val imageProxy = createImageProxy(mediaImage = null)

        analyzer.analyze(imageProxy.proxy)

        assertEquals(1, imageProxy.closeCount)
        assertEquals(0, detector.processCount)
        assertTrue(frames.isEmpty())
    }

    @Test
    fun analyze_dropsFrameWhilePreviousFrameIsStillProcessing() {
        val detector = FakeAndroidFaceDetectorClient().apply {
            enqueueSuccess(
                faces = listOf(face(headEulerAngleY = 8f)),
                completeImmediately = false,
            )
        }
        val analyzer = createAnalyzer(detector = detector)
        val firstImage = createImageProxy()
        val secondImage = createImageProxy()

        analyzer.analyze(firstImage.proxy)
        analyzer.analyze(secondImage.proxy)

        assertEquals(1, detector.processCount)
        assertEquals(0, firstImage.closeCount)
        assertEquals(1, secondImage.closeCount)

        detector.completePending()
        assertEquals(1, firstImage.closeCount)
    }

    @Test
    fun analyze_resetsPreviousFrameWhenNoFaceIsDetected() {
        val frames = mutableListOf<NormalizedFaceFrame?>()
        val detector = FakeAndroidFaceDetectorClient().apply {
            enqueueSuccess(faces = listOf(face(headEulerAngleY = 10f)))
            enqueueSuccess(faces = emptyList())
            enqueueSuccess(faces = listOf(face(headEulerAngleY = 20f)))
        }
        val analyzer = createAnalyzer(
            detector = detector,
            lensFacing = CameraLensFacing.Back,
            frames = frames,
        )

        analyzer.analyze(createImageProxy().proxy)
        analyzer.analyze(createImageProxy().proxy)
        analyzer.analyze(createImageProxy().proxy)

        assertEquals(3, frames.size)
        assertEquals(10f, frames[0]?.headYawDegrees)
        assertNull(frames[1])
        assertEquals(20f, frames[2]?.headYawDegrees)
    }

    @Test
    fun analyze_resetsPreviousFrameWhenDetectionFails() {
        val frames = mutableListOf<NormalizedFaceFrame?>()
        val detector = FakeAndroidFaceDetectorClient().apply {
            enqueueSuccess(faces = listOf(face(headEulerAngleY = 10f)))
            enqueueFailure()
            enqueueSuccess(faces = listOf(face(headEulerAngleY = 20f)))
        }
        val analyzer = createAnalyzer(
            detector = detector,
            lensFacing = CameraLensFacing.Back,
            frames = frames,
        )

        analyzer.analyze(createImageProxy().proxy)
        analyzer.analyze(createImageProxy().proxy)
        analyzer.analyze(createImageProxy().proxy)

        assertEquals(3, frames.size)
        assertEquals(10f, frames[0]?.headYawDegrees)
        assertNull(frames[1])
        assertEquals(20f, frames[2]?.headYawDegrees)
    }

    @Test
    fun toNormalizedFrame_appliesLensSignAndNullFallbacks() {
        val detectedFace = face(
            headEulerAngleY = 12f,
            headEulerAngleZ = 8f,
            leftEyeOpenProbability = null,
            rightEyeOpenProbability = 0.1f,
            smilingProbability = null,
            trackingId = null,
            upperLipBottomY = null,
            lowerLipTopY = null,
        )

        val frontFrame = detectedFace.toNormalizedFrame(
            timestampMillis = 42L,
            lensFacing = CameraLensFacing.Front,
            previousFrame = null,
        )
        val backFrame = detectedFace.toNormalizedFrame(
            timestampMillis = 42L,
            lensFacing = CameraLensFacing.Back,
            previousFrame = null,
        )

        assertEquals(expected = -12f, actual = frontFrame.headYawDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 12f, actual = backFrame.headYawDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = -8f, actual = frontFrame.headRollDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 8f, actual = backFrame.headRollDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0f, actual = frontFrame.leftEyeBlink, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.9f, actual = frontFrame.rightEyeBlink, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0f, actual = frontFrame.jawOpen, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0f, actual = frontFrame.mouthSmile, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.1f, actual = frontFrame.trackingConfidence, absoluteTolerance = 0.0001f)
    }

    @Test
    fun toNormalizedFrame_appliesBlinkThresholdJawSmileAndSmoothing() {
        val previousFrame = NormalizedFaceFrame(
            timestampMillis = 10L,
            trackingConfidence = 0.9f,
            headYawDegrees = 0f,
            headPitchDegrees = 0f,
            headRollDegrees = 0f,
            leftEyeBlink = 0.2f,
            rightEyeBlink = 0.8f,
            jawOpen = 0.3f,
            mouthSmile = 0.1f,
        )
        val smoothedFrame = face(
            headEulerAngleY = 10f,
            headEulerAngleX = 4f,
            headEulerAngleZ = 6f,
            leftEyeOpenProbability = 0.1f,
            rightEyeOpenProbability = 0.85f,
            smilingProbability = 0.8f,
            trackingId = 7,
            upperLipBottomY = 30f,
            lowerLipTopY = 38f,
        ).toNormalizedFrame(
            timestampMillis = 42L,
            lensFacing = CameraLensFacing.Back,
            previousFrame = previousFrame,
        )

        assertEquals(expected = 4.5f, actual = smoothedFrame.headYawDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 1.8f, actual = smoothedFrame.headPitchDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 2.4f, actual = smoothedFrame.headRollDegrees, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.64f, actual = smoothedFrame.leftEyeBlink, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.576f, actual = smoothedFrame.rightEyeBlink, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.5448889f, actual = smoothedFrame.jawOpen, absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.345f, actual = smoothedFrame.mouthSmile, absoluteTolerance = 0.0001f)
        assertTrue(smoothedFrame.trackingConfidence > 0.9f)
    }

    @Test
    fun close_closesDetectorClient() {
        val detector = FakeAndroidFaceDetectorClient()
        val analyzer = createAnalyzer(detector = detector)

        analyzer.close()

        assertTrue(detector.isClosed)
    }

    private fun createAnalyzer(
        detector: FakeAndroidFaceDetectorClient,
        lensFacing: CameraLensFacing = CameraLensFacing.Front,
        frames: MutableList<NormalizedFaceFrame?> = mutableListOf(),
    ): AndroidFaceTrackingAnalyzer {
        return AndroidFaceTrackingAnalyzer(
            lensFacing = lensFacing,
            onFaceFrame = { frames += it },
            detectorClient = detector,
            buildInputImage = { _, _ -> stubInputImage() },
        )
    }

    private fun createImageProxy(
        mediaImage: Image? = FakeMediaImage(),
        rotationDegrees: Int = 90,
        timestampNanos: Long = 42_000_000L,
    ): TestImageProxy {
        val imageInfo = Proxy.newProxyInstance(
            ImageInfo::class.java.classLoader,
            arrayOf(ImageInfo::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getRotationDegrees" -> rotationDegrees
                "getTimestamp" -> timestampNanos
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeImageInfo"
                "equals" -> proxy === args?.firstOrNull()
                else -> error("Unexpected ImageInfo method: ${method.name}")
            }
        } as ImageInfo

        val testImageProxy = TestImageProxy()
        testImageProxy.proxy = Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "close" -> {
                    testImageProxy.closeCount += 1
                    null
                }

                "getImage" -> mediaImage
                "getImageInfo" -> imageInfo
                "getCropRect" -> Rect(0, 0, 2, 2)
                "getWidth" -> 2
                "getHeight" -> 2
                "getFormat" -> ImageFormat.YUV_420_888
                "getPlanes" -> emptyArray<ImageProxy.PlaneProxy>()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeImageProxy"
                "equals" -> proxy === args?.firstOrNull()
                else -> error("Unexpected ImageProxy method: ${method.name}")
            }
        } as ImageProxy
        return testImageProxy
    }

    private fun face(
        boundingBoxHeight: Float = 100f,
        headEulerAngleX: Float = 0f,
        headEulerAngleY: Float = 0f,
        headEulerAngleZ: Float = 0f,
        leftEyeOpenProbability: Float? = 0.8f,
        rightEyeOpenProbability: Float? = 0.7f,
        smilingProbability: Float? = 0.6f,
        trackingId: Int? = 1,
        upperLipBottomY: Float? = 30f,
        lowerLipTopY: Float? = 34f,
    ): AndroidDetectedFace {
        return AndroidDetectedFace(
            boundingBoxHeight = boundingBoxHeight,
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            leftEyeOpenProbability = leftEyeOpenProbability,
            rightEyeOpenProbability = rightEyeOpenProbability,
            smilingProbability = smilingProbability,
            trackingId = trackingId,
            upperLipBottomY = upperLipBottomY,
            lowerLipTopY = lowerLipTopY,
        )
    }

    private fun stubInputImage(): InputImage {
        return InputImage.fromByteArray(
            ByteArray(6),
            2,
            2,
            0,
            InputImage.IMAGE_FORMAT_NV21,
        )
    }

    private class TestImageProxy {
        var closeCount: Int = 0
        lateinit var proxy: ImageProxy
    }

    private class FakeAndroidFaceDetectorClient : AndroidFaceDetectorClient {
        private val responses = ArrayDeque<Response>()
        private var pendingComplete: (() -> Unit)? = null

        var isClosed: Boolean = false
            private set

        var processCount: Int = 0
            private set

        override fun process(
            image: InputImage,
            onSuccess: (List<AndroidDetectedFace>) -> Unit,
            onFailure: (Throwable) -> Unit,
            onComplete: () -> Unit,
        ) {
            processCount += 1
            val response = responses.removeFirst()
            when (response) {
                is Response.Failure -> {
                    onFailure(response.error)
                    if (response.completeImmediately) {
                        onComplete()
                    } else {
                        pendingComplete = onComplete
                    }
                }

                is Response.Success -> {
                    onSuccess(response.faces)
                    if (response.completeImmediately) {
                        onComplete()
                    } else {
                        pendingComplete = onComplete
                    }
                }
            }
        }

        override fun close() {
            isClosed = true
        }

        fun enqueueFailure(
            error: Throwable = IllegalStateException("boom"),
            completeImmediately: Boolean = true,
        ) {
            responses += Response.Failure(error = error, completeImmediately = completeImmediately)
        }

        fun enqueueSuccess(
            faces: List<AndroidDetectedFace>,
            completeImmediately: Boolean = true,
        ) {
            responses += Response.Success(faces = faces, completeImmediately = completeImmediately)
        }

        fun completePending() {
            val onComplete = pendingComplete
            pendingComplete = null
            onComplete?.invoke()
        }
    }

    private sealed interface Response {
        data class Failure(
            val error: Throwable,
            val completeImmediately: Boolean,
        ) : Response

        data class Success(
            val faces: List<AndroidDetectedFace>,
            val completeImmediately: Boolean,
        ) : Response
    }

    private class FakeMediaImage : Image() {
        override fun close() = Unit

        override fun getFormat(): Int = ImageFormat.YUV_420_888

        override fun getHeight(): Int = 2

        override fun getPlanes(): Array<Plane> = emptyArray()

        override fun getTimestamp(): Long = 42L

        override fun getWidth(): Int = 2
    }
}
