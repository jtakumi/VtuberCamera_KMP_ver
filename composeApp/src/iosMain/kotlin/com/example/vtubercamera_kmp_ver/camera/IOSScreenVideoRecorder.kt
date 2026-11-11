@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.camera.video.VideoRecordingAudioOptions
import com.example.vtubercamera_kmp_ver.camera.settings.MAX_MICROPHONE_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.MIN_MICROPHONE_VOLUME
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlin.math.roundToInt
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetExportPresetHighestQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableCompositionTrack
import platform.AVFoundation.AVMutableAudioMix
import platform.AVFoundation.AVMutableAudioMixInputParameters
import platform.AVFoundation.AVAssetTrack
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.setAudioMix
import platform.AVFoundation.tracksWithMediaType
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Photos.PHAssetChangeRequest
import platform.ReplayKit.RPScreenRecorder
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * ReplayKit で画面とアプリ音声を録画し、マイクを別録りしてから音量を調整して合成する。
 *
 * ReplayKit の完成動画ではマイク音声だけを加工できないため、AVAudioEngine でマイク PCM を別ファイルへ記録する。
 * PCM サンプルへ [VideoRecordingAudioOptions.microphoneGain] を適用してから映像・アプリ音声と合成し、Photos へ保存する。
 */
internal class IOSScreenVideoRecorder : IOSVideoRecorder {
    override fun startRecording(
        audioOptions: VideoRecordingAudioOptions,
        onFinalized: (uri: String?, error: Throwable?) -> Unit,
    ): IOSVideoRecording {
        val replayRecorder = RPScreenRecorder.sharedRecorder()
        check(replayRecorder.available) { "Screen recording is unavailable on this device" }
        check(!replayRecorder.recording) { "Screen recording is already in progress" }

        if (audioOptions.isNoiseSuppressionEnabled) {
            configureAudioSession(isNoiseSuppressionEnabled = true)
        }
        val session = IOSScreenRecordingSession(
            replayRecorder = replayRecorder,
            microphoneGain = audioOptions.microphoneGain.coerceIn(
                MIN_MICROPHONE_VOLUME,
                MAX_MICROPHONE_VOLUME,
            ),
            restoreAudioSession = audioOptions.isNoiseSuppressionEnabled,
            onFinalized = onceOnMain(onFinalized),
        )
        session.begin()
        return IOSVideoRecording(session::stop)
    }
}

/** 録画中の ReplayKit、マイク録音、停止後の合成と Photos 保存をまとめて管理する。 */
private class IOSScreenRecordingSession(
    private val replayRecorder: RPScreenRecorder,
    private val microphoneGain: Float,
    private val restoreAudioSession: Boolean,
    private val onFinalized: (uri: String?, error: Throwable?) -> Unit,
) {
    private val replayPath = temporaryRecordingPath("screen")
    private val microphonePath = temporaryRecordingPath("microphone", extension = "m4a")
    private val mergedPath = temporaryRecordingPath("merged")
    private var microphoneRecorder: IOSMicrophoneGainRecorder? = null
    private var isCaptureStarted = false
    private var isStopRequested = false
    private var isFinalized = false

    /** マイク権限を確認して必要な録音を開始する。拒否時も画面とアプリ音声の録画は続ける。 */
    fun begin() {
        val session = AVAudioSession.sharedInstance()
        when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> startCapture(withMicrophone = true)
            AVAudioSessionRecordPermissionDenied -> startCapture(withMicrophone = false)
            AVAudioSessionRecordPermissionUndetermined -> session.requestRecordPermission { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    startCapture(withMicrophone = granted)
                }
            }
            else -> startCapture(withMicrophone = false)
        }
    }

    /** 停止要求を記録し、非同期の権限確認や開始処理が終わった時点で停止する。 */
    fun stop() {
        dispatch_async(dispatch_get_main_queue()) {
            if (isFinalized) return@dispatch_async
            isStopRequested = true
            if (isCaptureStarted) stopCaptureAndFinalize()
        }
    }

    // マイク録音を先に始めてから ReplayKit を起動し、両者の開始時刻のずれを小さくする。
    private fun startCapture(withMicrophone: Boolean) {
        if (isFinalized) return
        try {
            if (withMicrophone) startMicrophoneRecorder()
            replayRecorder.microphoneEnabled = false
            replayRecorder.startRecordingWithHandler { error ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (error != null) {
                        microphoneRecorder?.stop()
                        microphoneRecorder = null
                        finish(null, error.toThrowable())
                    } else {
                        isCaptureStarted = true
                        if (isStopRequested) stopCaptureAndFinalize()
                    }
                }
            }
        } catch (error: Throwable) {
            microphoneRecorder?.stop()
            microphoneRecorder = null
            finish(null, error)
        }
    }

    // マイク PCM に gain を適用し、AAC の m4a ファイルへ記録する。
    private fun startMicrophoneRecorder() {
        val recorder = IOSMicrophoneGainRecorder(microphonePath, microphoneGain)
        microphoneRecorder = recorder
        recorder.start()
    }

    // ReplayKit の動画を確定し、マイク音声を別トラックとして合成する。
    private fun stopCaptureAndFinalize() {
        if (!isCaptureStarted || isFinalized) return
        isCaptureStarted = false
        val microphoneFailure = microphoneRecorder?.stop()
        microphoneRecorder = null
        val replayUrl = NSURL.fileURLWithPath(replayPath)
        replayRecorder.stopRecordingWithOutputURL(replayUrl) { error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (error != null) {
                    finish(null, error.toThrowable())
                } else if (microphoneFailure != null) {
                    finish(null, microphoneFailure)
                } else if (microphoneFileIsAvailable()) {
                    mergeRecordings(
                        replayUrl = replayUrl,
                        microphoneUrl = NSURL.fileURLWithPath(microphonePath),
                    ) { mergeError ->
                        if (mergeError != null) {
                            finish(null, mergeError)
                        } else {
                            saveToPhotos(NSURL.fileURLWithPath(mergedPath))
                        }
                    }
                } else {
                    saveToPhotos(replayUrl)
                }
            }
        }
    }

    // ReplayKit の映像とアプリ音声を維持し、マイクトラックだけに音量を適用した MP4 を作る。
    private fun mergeRecordings(
        replayUrl: NSURL,
        microphoneUrl: NSURL,
        onComplete: (Throwable?) -> Unit,
    ) {
        try {
            val screenAsset = AVAsset.assetWithURL(replayUrl)
            val microphoneAsset = AVAsset.assetWithURL(microphoneUrl)
            val composition = AVMutableComposition()
            val zeroTime = CMTimeMake(value = 0L, timescale = 1)
            val timeRange = CMTimeRangeMake(zeroTime, screenAsset.duration)

            val screenMediaTracks = listOf(
                AVMediaTypeVideo to screenAsset.tracksWithMediaType(AVMediaTypeVideo),
                AVMediaTypeAudio to screenAsset.tracksWithMediaType(AVMediaTypeAudio),
            )
            for ((mediaType, tracks) in screenMediaTracks) {
                for (sourceTrack in tracks.filterIsInstance<AVAssetTrack>()) {
                    val destinationTrack = checkNotNull(
                        composition.addMutableTrackWithMediaType(mediaType, preferredTrackID = INVALID_TRACK_ID),
                    ) { "Failed to create a composition track for $mediaType" }
                    insertTrack(sourceTrack, destinationTrack, timeRange)
                }
            }

            val sourceMicrophoneTrack = microphoneAsset.tracksWithMediaType(AVMediaTypeAudio)
                .filterIsInstance<AVAssetTrack>()
                .firstOrNull()
            if (sourceMicrophoneTrack == null) {
                saveToPhotos(replayUrl)
                return
            }
            val microphoneTrack = checkNotNull(
                composition.addMutableTrackWithMediaType(AVMediaTypeAudio, preferredTrackID = INVALID_TRACK_ID),
            ) { "Failed to create a microphone composition track" }
            val microphoneTimeRange = CMTimeRangeMake(zeroTime, microphoneAsset.duration)
            insertTrack(sourceMicrophoneTrack, microphoneTrack, microphoneTimeRange)

            val microphoneParameters = AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(microphoneTrack)
            microphoneParameters.setVolume(1f, atTime = zeroTime)
            val audioMix = AVMutableAudioMix()
            audioMix.setInputParameters(listOf(microphoneParameters))

            val exportSession = AVAssetExportSession(
                asset = composition,
                presetName = AVAssetExportPresetHighestQuality,
            )
            exportSession.outputURL = NSURL.fileURLWithPath(mergedPath)
            exportSession.outputFileType = AVFileTypeMPEG4
            exportSession.setAudioMix(audioMix)
            exportSession.shouldOptimizeForNetworkUse = true
            exportSession.exportAsynchronouslyWithCompletionHandler {
                dispatch_async(dispatch_get_main_queue()) {
                    if (exportSession.status == AVAssetExportSessionStatusCompleted) {
                        onComplete(null)
                    } else {
                        onComplete(
                            exportSession.error?.toThrowable()
                                ?: IllegalStateException("Recording export failed with status ${exportSession.status}"),
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            onComplete(error)
        }
    }

    // 指定した時間範囲の映像または音声を composition track へ挿入する。
    private fun insertTrack(
        source: platform.AVFoundation.AVAssetTrack,
        destination: AVMutableCompositionTrack,
        timeRange: kotlinx.cinterop.CValue<platform.CoreMedia.CMTimeRange>,
    ) {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            check(
                destination.insertTimeRange(
                    timeRange,
                    ofTrack = source,
                    atTime = CMTimeMake(value = 0, timescale = 1),
                    error = error.ptr,
                ),
            ) {
                "Failed to combine recording tracks: ${error.value?.localizedDescription}"
            }
        }
    }

    // マイク録音ファイルが存在するときだけ合成する。
    private fun microphoneFileIsAvailable(): Boolean =
        platform.Foundation.NSFileManager.defaultManager.fileExistsAtPath(microphonePath)

    // 録画ファイルを Photos へ追加し、作業ファイルを片付けてから完了を通知する。
    private fun saveToPhotos(outputUrl: NSURL) {
        saveToPhotoLibrary(
            createAsset = {
                PHAssetChangeRequest
                    .creationRequestForAssetFromVideoAtFileURL(outputUrl)
                    ?.placeholderForCreatedAsset
                    ?.localIdentifier
            },
            onComplete = { uri, error -> finish(uri, error) },
        )
    }

    // 一時ファイルを削除し、audio session を戻して完了 callback を 1 度だけ実行する。
    private fun finish(uri: String?, error: Throwable?) {
        if (isFinalized) return
        isFinalized = true
        microphoneRecorder?.stop()
        microphoneRecorder = null
        listOf(replayPath, microphonePath, mergedPath).forEach(::deletePhotoFile)
        if (restoreAudioSession) restoreDefaultAudioSessionMode()
        onFinalized(uri, error)
    }
}

// 完了通知を 1 度だけ main queue から呼ぶ。
private fun onceOnMain(
    onFinalized: (uri: String?, error: Throwable?) -> Unit,
): (uri: String?, error: Throwable?) -> Unit {
    var isFinalized = false
    return { uri, error ->
        dispatch_async(dispatch_get_main_queue()) {
            if (!isFinalized) {
                isFinalized = true
                onFinalized(uri, error)
            }
        }
    }
}

private fun temporaryRecordingPath(prefix: String, extension: String = "mp4"): String =
    NSTemporaryDirectory() + "vtuber-camera-$prefix-${NSUUID().UUIDString}.$extension"

private fun NSError.toThrowable(): Throwable = IllegalStateException(localizedDescription)

/** マイク PCM の各サンプルへ録画音量を掛けて AAC ファイルへ書き込む。 */
private class IOSMicrophoneGainRecorder(
    private val outputPath: String,
    private val gain: Float,
) {
    private val engine = AVAudioEngine()
    private var outputFile: AVAudioFile? = null
    private var isTapInstalled = false

    private var failure: Throwable? = null

    /** マイク入力 tap と AAC ファイル書き込みを開始する。非対応 PCM 形式は明示的に失敗させる。 */
    fun start() {
        val inputNode = engine.inputNode
        val inputFormat = inputNode.outputFormatForBus(MICROPHONE_BUS)
        check(
            inputFormat.commonFormat == AVAudioPCMFormatFloat32 ||
                inputFormat.commonFormat == AVAudioPCMFormatInt16,
        ) { "Unsupported microphone PCM format: ${inputFormat.commonFormat}" }

        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val fileSettings = mapOf<Any?, Any?>(
                AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
                AVSampleRateKey to NSNumber(double = inputFormat.sampleRate),
                AVNumberOfChannelsKey to NSNumber(unsignedInt = inputFormat.channelCount),
                AVEncoderAudioQualityKey to NSNumber(int = AVAudioQualityHigh.toInt()),
            )
            outputFile = AVAudioFile(
                forWriting = NSURL.fileURLWithPath(outputPath),
                settings = fileSettings,
                commonFormat = inputFormat.commonFormat,
                interleaved = false,
                error = error.ptr,
            )

            inputNode.installTapOnBus(
                bus = MICROPHONE_BUS,
                bufferSize = MICROPHONE_BUFFER_FRAMES,
                format = inputFormat,
            ) { buffer, _ ->
                if (buffer == null || failure != null) return@installTapOnBus
                try {
                    applyGain(buffer, inputFormat.commonFormat)
                    memScoped {
                        val writeError = alloc<ObjCObjectVar<NSError?>>()
                        check(outputFile?.writeFromBuffer(buffer, error = writeError.ptr) == true) {
                            "Failed to write microphone audio: ${writeError.value?.localizedDescription}"
                        }
                    }
                } catch (writeFailure: Throwable) {
                    failure = writeFailure
                }
            }
            isTapInstalled = true
            engine.prepare()
            check(engine.startAndReturnError(error.ptr)) {
                "Failed to start microphone capture: ${error.value?.localizedDescription}"
            }
        }
    }

    /** Microphone tap を停止してファイルを閉じ、失敗があれば返す。 */
    fun stop(): Throwable? {
        engine.stop()
        if (isTapInstalled) {
            engine.inputNode.removeTapOnBus(MICROPHONE_BUS)
            isTapInstalled = false
        }
        outputFile?.close()
        outputFile = null
        return failure
    }

    // 形式ごとに全チャンネルの PCM を増幅し、出力可能な振幅範囲へ clamp する。
    private fun applyGain(buffer: platform.AVFAudio.AVAudioPCMBuffer, commonFormat: ULong) {
        val frameCount = buffer.frameLength.toInt()
        val channelCount = buffer.format.channelCount.toInt()
        when (commonFormat) {
            AVAudioPCMFormatFloat32 -> {
                val channels = checkNotNull(buffer.floatChannelData) { "Float PCM samples are unavailable" }
                for (channelIndex in 0..<channelCount) {
                    val samples = checkNotNull(channels[channelIndex]) { "Float PCM channel is unavailable" }
                    for (frameIndex in 0..<frameCount) {
                        samples[frameIndex] = (samples[frameIndex] * gain).coerceIn(-1f, 1f)
                    }
                }
            }
            AVAudioPCMFormatInt16 -> {
                val channels = checkNotNull(buffer.int16ChannelData) { "Int16 PCM samples are unavailable" }
                for (channelIndex in 0..<channelCount) {
                    val samples = checkNotNull(channels[channelIndex]) { "Int16 PCM channel is unavailable" }
                    for (frameIndex in 0..<frameCount) {
                        samples[frameIndex] = (samples[frameIndex] * gain)
                            .roundToInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                    }
                }
            }
        }
    }
}

private const val MICROPHONE_BUS = 0uL
private const val MICROPHONE_BUFFER_FRAMES = 1_024u
private const val INVALID_TRACK_ID = 0
