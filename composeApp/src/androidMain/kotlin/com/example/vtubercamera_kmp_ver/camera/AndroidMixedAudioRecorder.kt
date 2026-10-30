package com.example.vtubercamera_kmp_ver.camera

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException

private const val TAG = "MixedAudioRecorder"

/**
 * 録画に入れる音声（マイクと BGM のミックス）を、録画とは別に AAC のファイルへ書き出す。
 *
 * 録画の映像を担う [android.media.MediaRecorder] はマイクの音しか扱えないため、BGM を混ぜたり雑音抑制を
 * かけたりする録画では、映像だけを録画して音声をここで作り、録画の停止後に 2 つを 1 つのファイルへ結合する。
 *
 * マイクは [AudioRecord] で録り、読み出しの周期をそのまま全体の進行として使う。マイクを使わない（権限が無い）ときは、
 * 実時間に合わせて BGM だけの無音混じりの音声を進める。音声の処理は専用スレッドで直列に行う。
 */
internal class MixedAudioRecorder private constructor(
    private val audioRecord: AudioRecord?,
    private val noiseSuppressor: NoiseSuppressor?,
    private val bgmDecoder: BgmPcmDecoder?,
    private val bgmGain: Float,
    private val micGain: Float,
    private val writer: AacFileWriter,
    private val outputFile: File,
) {
    private val thread = Thread(::runLoop, "mixed-audio-recorder")

    @Volatile
    private var isStopRequested = false

    @Volatile
    private var failure: Throwable? = null

    @Volatile
    private var hasAudio = false

    private var isBegun = false

    /** マイクの取り込みと音声の書き出しを始める。開始できないときは例外を投げ、資源は解放済みになる。 */
    fun begin() {
        try {
            audioRecord?.startRecording()
        } catch (error: IllegalStateException) {
            // 音声スレッドを始める前に失敗した。エンコーダーなどを閉じてから呼び出し側へ伝える。
            finishWriting()
            release()
            throw error
        }
        isBegun = true
        thread.start()
    }

    /**
     * 音声の書き出しを止めてファイルを確定し、できあがったファイルを返す。
     *
     * 途中でマイクの読み出しなどに失敗しても、それまでに書けた分があればそのファイルを返す。
     * 1 サンプルも書けていないときは失敗を返す。
     */
    fun stop(): Result<File> {
        if (!isBegun) {
            // 始める前に取り消された。音声スレッドが無いので、ここで資源を閉じる。
            finishWriting()
            release()
            return Result.failure(IllegalStateException("The audio recorder was never started"))
        }
        isStopRequested = true
        thread.join(STOP_JOIN_TIMEOUT_MS)
        val recordingFailure = failure
        return if (hasAudio) {
            Result.success(outputFile)
        } else {
            Result.failure(recordingFailure ?: IllegalStateException("No audio was recorded"))
        }
    }

    // 録画が終わるまで、チャンクごとにマイクと BGM を混ぜて AAC へ書き続ける。
    private fun runLoop() {
        val micBuffer = ShortArray(CHUNK_SAMPLES)
        val bgmBuffer = ShortArray(CHUNK_SAMPLES)
        val mixedBuffer = ShortArray(CHUNK_SAMPLES)
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        var producedSamples = 0L
        try {
            while (!isStopRequested) {
                val sampleCount = if (audioRecord != null) {
                    audioRecord.read(micBuffer, 0, CHUNK_SAMPLES).also { readCount ->
                        if (readCount < 0) throw IOException("AudioRecord.read failed: $readCount")
                    }
                } else {
                    CHUNK_SAMPLES
                }
                if (sampleCount == 0) continue

                val bgmSamples = bgmDecoder?.let { decoder ->
                    val readCount = decoder.read(bgmBuffer, sampleCount)
                    // デコードが追いつかなかった分は無音で埋め、音声の長さを録画とずらさない。
                    bgmBuffer.fill(0, readCount, sampleCount)
                    bgmBuffer
                }
                mixPcm16(
                    mic = if (audioRecord != null) micBuffer else null,
                    bgm = bgmSamples,
                    sampleCount = sampleCount,
                    bgmGain = bgmGain,
                    out = mixedBuffer,
                    micGain = micGain,
                )
                writer.write(mixedBuffer, sampleCount)
                hasAudio = true
                producedSamples += sampleCount

                if (audioRecord == null) {
                    sleepUntilRealtime(startedAtNanos, producedSamples)
                }
            }
        } catch (error: IOException) {
            recordFailure(error)
        } catch (error: RuntimeException) {
            recordFailure(error)
        } finally {
            finishWriting()
            release()
        }
    }

    // マイクを使わないとき、書いたサンプル数に相当する実時間が経つまで待って、音声の進行を録画に合わせる。
    private fun sleepUntilRealtime(startedAtNanos: Long, producedSamples: Long) {
        val dueAtNanos = startedAtNanos + producedSamples * NANOS_PER_SECOND / MIX_SAMPLE_RATE
        val waitMillis = (dueAtNanos - SystemClock.elapsedRealtimeNanos()) / NANOS_PER_MILLI
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis)
            } catch (_: InterruptedException) {
                isStopRequested = true
                Thread.currentThread().interrupt()
            }
        }
    }

    // 書き出しを確定する。確定に失敗したときは、そのファイルは使えないものとして音声なし扱いにする。
    private fun finishWriting() {
        try {
            hasAudio = writer.finish() && hasAudio
        } catch (error: IOException) {
            hasAudio = false
            recordFailure(error)
        } catch (error: RuntimeException) {
            hasAudio = false
            recordFailure(error)
        }
    }

    private fun recordFailure(error: Throwable) {
        Log.w(TAG, "Audio recording failed", error)
        failure = failure ?: error
    }

    private fun release() {
        runCatching { audioRecord?.stop() }
            .onFailure { Log.w(TAG, "Failed to stop AudioRecord", it) }
        audioRecord?.release()
        noiseSuppressor?.release()
        runCatching { bgmDecoder?.close() }
            .onFailure { Log.w(TAG, "Failed to close the BGM decoder", it) }
    }

    companion object {
        /**
         * 音声ファイル [outputFile] へ書く録音を準備する。
         *
         * [useMicrophone] が true のときだけマイクを録り、[isNoiseSuppressionEnabled] ならマイクへ雑音抑制を
         * かける（端末が対応しないときは抑制なしで続ける）。[bgmFile] があれば [bgmStartPositionMs] から
         * マイクには [micGain]、BGM には [bgmGain] の音量を適用する。準備に失敗したときは確保した資源を解放して例外を投げる。
         */
        @SuppressLint("MissingPermission")
        fun create(
            outputFile: File,
            useMicrophone: Boolean,
            isNoiseSuppressionEnabled: Boolean,
            bgmFile: File?,
            bgmStartPositionMs: Long,
            bgmGain: Float,
            micGain: Float = 1f,
        ): MixedAudioRecorder {
            val audioRecord = if (useMicrophone) createAudioRecord() else null
            val noiseSuppressor = audioRecord?.let { createNoiseSuppressor(it, isNoiseSuppressionEnabled) }
            var bgmDecoder: BgmPcmDecoder? = null
            try {
                bgmDecoder = bgmFile?.let { BgmPcmDecoder.open(it, bgmStartPositionMs) }
                val writer = AacFileWriter(outputFile, MIX_SAMPLE_RATE)
                return MixedAudioRecorder(audioRecord, noiseSuppressor, bgmDecoder, bgmGain, micGain, writer, outputFile)
            } catch (error: IOException) {
                releaseOnCreateFailure(audioRecord, noiseSuppressor, bgmDecoder)
                throw error
            } catch (error: RuntimeException) {
                releaseOnCreateFailure(audioRecord, noiseSuppressor, bgmDecoder)
                throw error
            }
        }

        @SuppressLint("MissingPermission")
        private fun createAudioRecord(): AudioRecord {
            val minBufferBytes = AudioRecord.getMinBufferSize(
                MIX_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferBytes <= 0) {
                throw IOException("AudioRecord is not supported on this device: $minBufferBytes")
            }
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                MIX_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferBytes, CHUNK_SAMPLES * BYTES_PER_SAMPLE * MIC_BUFFER_CHUNKS),
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                throw IOException("AudioRecord could not be initialized")
            }
            return audioRecord
        }

        // 雑音抑制は端末の対応状況に左右されるため、使えなくても録音自体は止めず、その旨を記録して続ける。
        private fun createNoiseSuppressor(audioRecord: AudioRecord, isEnabled: Boolean): NoiseSuppressor? {
            if (!isEnabled) return null
            if (!NoiseSuppressor.isAvailable()) {
                Log.w(TAG, "Noise suppression is not available on this device")
                return null
            }
            return NoiseSuppressor.create(audioRecord.audioSessionId)?.also { suppressor ->
                suppressor.enabled = true
            } ?: run {
                Log.w(TAG, "Noise suppressor could not be created")
                null
            }
        }

        private fun releaseOnCreateFailure(
            audioRecord: AudioRecord?,
            noiseSuppressor: NoiseSuppressor?,
            bgmDecoder: BgmPcmDecoder?,
        ) {
            noiseSuppressor?.release()
            audioRecord?.release()
            runCatching { bgmDecoder?.close() }
                .onFailure { Log.w(TAG, "Failed to close the BGM decoder", it) }
        }
    }
}

private const val CHUNK_SAMPLES = 1_024
private const val BYTES_PER_SAMPLE = 2
private const val MIC_BUFFER_CHUNKS = 4
private const val STOP_JOIN_TIMEOUT_MS = 5_000L
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MILLI = 1_000_000L
