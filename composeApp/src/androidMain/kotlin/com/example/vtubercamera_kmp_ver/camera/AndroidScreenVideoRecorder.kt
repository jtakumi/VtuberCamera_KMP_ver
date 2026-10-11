package com.example.vtubercamera_kmp_ver.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import android.view.Window
import java.io.FileDescriptor
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "ScreenVideoRecorder"

/**
 * アプリのウィンドウ（カメラ映像・アバター・背景を合成した画面）を動画としてギャラリーへ録画する。
 *
 * ウィンドウを [PixelCopy] で一定間隔に複製し、[MediaRecorder] の入力 Surface へ描き込む。エンコード・
 * 音声・mux は [MediaRecorder] に任せるため、映像と音声の同期を自前で扱わない。出力は MediaStore へ
 * 直接書き出すため、Android 10 以降では保存用の権限を必要としない。
 *
 * [isMicrophonePermitted] が true を返す録画開始時だけマイク音声を録る。権限がなければ映像のみで録画する。
 */
internal class AndroidScreenVideoRecorder(
    private val context: Context,
    private val windowProvider: () -> Window?,
    private val isMicrophonePermitted: () -> Boolean,
) : AndroidVideoRecorder {
    override fun startRecording(
        onFinalized: (uri: String?, error: Throwable?) -> Unit,
    ): AndroidVideoRecording {
        val window = checkNotNull(windowProvider()) { "Window is not available for screen recording" }
        val recordingSize = window.decorView.toRecordingSize()
        val galleryOutput = GalleryVideoOutput.create(context.contentResolver)
        val recorder = try {
            createPreparedRecorder(galleryOutput, recordingSize, withAudio = isMicrophonePermitted())
        } catch (error: IOException) {
            galleryOutput.discard()
            throw error
        } catch (error: RuntimeException) {
            galleryOutput.discard()
            throw error
        }
        val session = ScreenRecordingSession(
            window = window,
            recorder = recorder,
            size = recordingSize,
            galleryOutput = galleryOutput,
            onFinalized = onFinalized,
        )
        session.begin()
        return session
    }

    // 画面サイズと音声の有無に合わせて MediaRecorder を構成し、録画を始められる状態まで準備する。
    private fun createPreparedRecorder(
        galleryOutput: GalleryVideoOutput,
        size: RecordingSize,
        withAudio: Boolean,
    ): MediaRecorder {
        val recorder = createMediaRecorder()
        try {
            if (withAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(size.width, size.height)
            recorder.setVideoFrameRate(VIDEO_FRAME_RATE)
            recorder.setVideoEncodingBitRate(size.videoBitRate())
            if (withAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(AUDIO_BIT_RATE)
                recorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            }
            recorder.setOutputFile(galleryOutput.fileDescriptor)
            recorder.prepare()
        } catch (error: IOException) {
            recorder.release()
            throw error
        } catch (error: RuntimeException) {
            recorder.release()
            throw error
        }
        return recorder
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }
}

/** 録画する動画の解像度。H.264 の制約に合わせて幅・高さとも偶数にする。 */
private data class RecordingSize(val width: Int, val height: Int) {
    // 解像度に比例させたビットレート。極端に小さい / 大きい画面でも品質と容量が破綻しないよう範囲を絞る。
    fun videoBitRate(): Int = (width * height * BITS_PER_PIXEL).coerceIn(MIN_VIDEO_BIT_RATE, MAX_VIDEO_BIT_RATE)
}

// ウィンドウの描画サイズから録画解像度を決める。長辺を上限へ収めて縮小し、幅・高さを偶数へ丸める。
private fun View.toRecordingSize(): RecordingSize {
    check(width > 0 && height > 0) { "Window has not been laid out yet" }
    val scale = min(1f, MAX_RECORDING_LONG_SIDE.toFloat() / max(width, height))
    return RecordingSize(
        width = (width * scale).roundToInt().toEvenDimension(),
        height = (height * scale).roundToInt().toEvenDimension(),
    )
}

private fun Int.toEvenDimension(): Int = max(MIN_RECORDING_DIMENSION, this / 2 * 2)

/**
 * MediaStore へ書き出し中の動画 1 件。保存が確定するまでは IS_PENDING でギャラリーから隠しておく。
 */
private class GalleryVideoOutput private constructor(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val descriptor: ParcelFileDescriptor,
) {
    val fileDescriptor: FileDescriptor get() = descriptor.fileDescriptor

    // 録画の成否に合わせて出力を確定または破棄し、成功時は保存先の URI を返す。
    // 確定中の例外は握りつぶさず、失敗として呼び出し側へ返す。
    fun finish(isRecordingSucceeded: Boolean): Result<String> {
        val closeResult = runCatching { descriptor.close() }
        if (!isRecordingSucceeded || closeResult.isFailure) {
            discard()
            return Result.failure(
                closeResult.exceptionOrNull() ?: IllegalStateException("Recording did not succeed"),
            )
        }
        return runCatching {
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            check(resolver.update(uri, values, null, null) > 0) { "Failed to publish recorded video" }
            uri.toString()
        }.onFailure { discard() }
    }

    // 書き出し途中の出力をギャラリーから取り除く。ファイル記述子が未クローズなら先に閉じる。
    fun discard() {
        runCatching { descriptor.close() }
            .onFailure { Log.w(TAG, "Failed to close descriptor of discarded recording", it) }
        runCatching { resolver.delete(uri, null, null) }
            .onFailure { Log.w(TAG, "Failed to delete discarded recording", it) }
    }

    companion object {
        // Movies/VtuberCamera 配下に保留状態の動画を作り、書き込み用の記述子を開く。失敗時は例外を投げる。
        fun create(resolver: ContentResolver): GalleryVideoOutput {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "vtuber-camera-${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VtuberCamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)) {
                "Failed to create a gallery entry for the recording"
            }
            val descriptor = try {
                checkNotNull(resolver.openFileDescriptor(uri, "w")) { "Failed to open the gallery entry" }
            } catch (error: IOException) {
                resolver.delete(uri, null, null)
                throw error
            } catch (error: IllegalStateException) {
                resolver.delete(uri, null, null)
                throw error
            }
            return GalleryVideoOutput(resolver, uri, descriptor)
        }
    }
}

/**
 * 録画 1 回分の実行体。専用スレッドでウィンドウの複製と Surface への描画を繰り返し、停止要求で
 * [MediaRecorder] を止めてギャラリーへの保存を確定する。複製・描画・終了処理は同じスレッドで直列に
 * 動くため、終了処理の最中に新しいフレームが描かれることはない。
 */
private class ScreenRecordingSession(
    private val window: Window,
    private val recorder: MediaRecorder,
    size: RecordingSize,
    private val galleryOutput: GalleryVideoOutput,
    private val onFinalized: (uri: String?, error: Throwable?) -> Unit,
) : AndroidVideoRecording {
    private val surface: Surface = recorder.surface
    private val frame: Bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    private val workerThread = HandlerThread("screen-video-recorder").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 以降はすべて workerHandler のスレッドからだけ触る。
    private var isFinished = false
    private var drawnFrameCount = 0
    private var consecutiveCopyFailureCount = 0

    // 録画を始めてフレームの取り込みを開始する。開始に失敗したときは資源を解放して例外を投げる。
    fun begin() {
        try {
            recorder.start()
        } catch (error: RuntimeException) {
            releaseResources()
            galleryOutput.discard()
            throw error
        }
        workerHandler.post(::copyFrame)
    }

    override fun stop() {
        workerHandler.post { finish(failure = null) }
    }

    // ウィンドウを 1 フレーム分複製する。複製できないウィンドウ（破棄済みなど）は録画の失敗として終える。
    private fun copyFrame() {
        if (isFinished) return
        val startedAtMillis = SystemClock.uptimeMillis()
        try {
            PixelCopy.request(window, frame, { result -> onFrameCopied(result, startedAtMillis) }, workerHandler)
        } catch (error: IllegalArgumentException) {
            finish(failure = error)
        }
    }

    // 複製したフレームを録画の入力 Surface へ描き、次のフレームを一定間隔で予約する。
    private fun onFrameCopied(result: Int, startedAtMillis: Long) {
        if (isFinished) return
        if (result == PixelCopy.SUCCESS) {
            consecutiveCopyFailureCount = 0
            try {
                drawFrameToRecorder()
            } catch (error: IllegalStateException) {
                finish(failure = error)
                return
            } catch (error: Surface.OutOfResourcesException) {
                finish(failure = error)
                return
            }
            drawnFrameCount++
        } else {
            consecutiveCopyFailureCount++
            if (consecutiveCopyFailureCount >= MAX_CONSECUTIVE_COPY_FAILURES) {
                // アプリが背面に回るなどして複製できない状態が続いた。それまでに撮れた分は残し、
                // 1 フレームも撮れていないときだけ失敗にする。
                finish(
                    failure = if (drawnFrameCount > 0) {
                        null
                    } else {
                        IllegalStateException("Window could not be copied (PixelCopy result $result)")
                    },
                )
                return
            }
        }
        val elapsedMillis = SystemClock.uptimeMillis() - startedAtMillis
        workerHandler.postDelayed(::copyFrame, (FRAME_INTERVAL_MILLIS - elapsedMillis).coerceAtLeast(0L))
    }

    private fun drawFrameToRecorder() {
        val canvas = surface.lockHardwareCanvas()
        try {
            canvas.drawBitmap(frame, 0f, 0f, null)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    // 録画を止め、ギャラリーへの保存を確定または破棄して、結果を main thread へ通知する。
    private fun finish(failure: Throwable?) {
        if (isFinished) return
        isFinished = true
        workerHandler.removeCallbacksAndMessages(null)
        var error = failure
        try {
            // 有効なデータが無いまま止めると RuntimeException になる。出力は壊れているため失敗として扱う。
            recorder.stop()
        } catch (stopError: RuntimeException) {
            error = error ?: stopError
        }
        releaseResources()
        val savedUri = galleryOutput.finish(isRecordingSucceeded = error == null)
        val finalError = error ?: savedUri.exceptionOrNull()
        if (finalError != null) {
            Log.w(TAG, "Screen recording failed", finalError)
        }
        workerThread.quitSafely()
        mainHandler.post { onFinalized(savedUri.getOrNull(), finalError) }
    }

    // フレーム用 Bitmap は解放しない。停止要求が複製の最中に届くと、PixelCopy がまだ書き込み中の可能性が
    // あるため、参照が切れた後の GC に任せる。
    private fun releaseResources() {
        recorder.release()
        surface.release()
    }
}

private const val VIDEO_FRAME_RATE = 30
private const val FRAME_INTERVAL_MILLIS = 1_000L / VIDEO_FRAME_RATE
private const val MAX_RECORDING_LONG_SIDE = 1_920
private const val MIN_RECORDING_DIMENSION = 2
private const val BITS_PER_PIXEL = 4
private const val MIN_VIDEO_BIT_RATE = 2_000_000
private const val MAX_VIDEO_BIT_RATE = 12_000_000
private const val AUDIO_BIT_RATE = 128_000
private const val AUDIO_SAMPLE_RATE = 44_100

// 約 5 秒分（30 fps 換算）の連続失敗でウィンドウを複製できないと判断する。
private const val MAX_CONSECUTIVE_COPY_FAILURES = 150
