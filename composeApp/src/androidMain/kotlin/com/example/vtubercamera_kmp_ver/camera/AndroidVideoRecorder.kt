package com.example.vtubercamera_kmp_ver.camera

import android.content.Context
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File

// CameraX の VideoCapture を repository から切り離すための録画開始口。repository のテストでは fake へ差し替える。
internal fun interface AndroidVideoRecorder {
    // [outputFile] へ録画を開始する。[onFinalized] は書き出しの完了時に 1 度だけ呼ばれ、
    // 成功なら null、失敗なら原因を受け取る。録画を止めるための [AndroidVideoRecording] を返す。
    fun startRecording(
        outputFile: File,
        onFinalized: (error: Throwable?) -> Unit,
    ): AndroidVideoRecording
}

// 開始済みの録画への停止要求。停止後の書き出し完了は [AndroidVideoRecorder.startRecording] の callback で届く。
internal fun interface AndroidVideoRecording {
    fun stop()
}

// 録画の書き出しが CameraX のエラーコードつきで終了したことを表す。
internal class VideoRecordingException(
    val errorCode: Int,
    cause: Throwable?,
) : Exception("Video recording finalized with error code $errorCode", cause)

// CameraX の [VideoCapture] を使って、音声なしの動画を [File] へ録画する。
// 音声を録るには RECORD_AUDIO 権限と withAudioEnabled() が必要で、現時点では扱わない。
internal class CameraXVideoRecorder(
    private val context: Context,
    private val videoCapture: VideoCapture<Recorder>,
) : AndroidVideoRecorder {
    override fun startRecording(
        outputFile: File,
        onFinalized: (error: Throwable?) -> Unit,
    ): AndroidVideoRecording {
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        val recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    onFinalized(
                        if (event.hasError()) {
                            VideoRecordingException(errorCode = event.error, cause = event.cause)
                        } else {
                            null
                        },
                    )
                }
            }
        return AndroidVideoRecording { recording.stop() }
    }
}
