package com.example.vtubercamera_kmp_ver.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/**
 * 1 チャンネルの 16 bit PCM を AAC へエンコードし、音声だけの MP4（m4a）ファイルへ書き出す。
 *
 * 書き込みはすべて 1 つのスレッドから行う。[finish] を呼ぶと残りを書き切ってファイルを確定し、
 * 以降は使えない。
 */
internal class AacFileWriter(
    outputFile: File,
    private val sampleRate: Int,
) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer: MediaMuxer
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var isMuxerStarted = false
    private var hasWrittenSample = false
    private var writtenSampleCount = 0L

    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (error: IOException) {
            codec.release()
            throw error
        } catch (error: RuntimeException) {
            codec.release()
            throw error
        }
    }

    /** [samples] の先頭 [count] 個をエンコーダーへ渡し、できあがった AAC をファイルへ書く。 */
    fun write(samples: ShortArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val inputIndex = awaitInputBuffer()
            val inputBuffer = codec.getInputBuffer(inputIndex)
                ?: throw IOException("The AAC encoder returned no input buffer")
            inputBuffer.clear()
            val chunkCount = minOf(count - offset, inputBuffer.capacity() / BYTES_PER_SAMPLE)
            inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(samples, offset, chunkCount)
            codec.queueInputBuffer(inputIndex, 0, chunkCount * BYTES_PER_SAMPLE, presentationTimeUs(), 0)
            writtenSampleCount += chunkCount
            offset += chunkCount
            drainOutput(isEndOfStream = false)
        }
    }

    /**
     * 残りを書き切ってファイルを確定し、資源を解放する。1 サンプルでも書き出せたときだけ true を返す。
     * 何も書けていないファイルは再生できないため、呼び出し側は使わずに捨てる。
     */
    fun finish(): Boolean {
        try {
            val inputIndex = awaitInputBuffer()
            codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drainOutput(isEndOfStream = true)
            if (isMuxerStarted) {
                muxer.stop()
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            muxer.release()
        }
        return hasWrittenSample
    }

    // 空きの入力バッファが出るまで、出力を排出しながら待つ。詰まったままのときは例外を投げる。
    private fun awaitInputBuffer(): Int {
        repeat(MAX_STALLED_ATTEMPTS) {
            val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex >= 0) return inputIndex
            drainOutput(isEndOfStream = false)
        }
        throw IOException("The AAC encoder did not accept input")
    }

    // エンコード済みの AAC を取り出して muxer へ書く。終端のときは終端フラグが来るまで待つ。
    private fun drainOutput(isEndOfStream: Boolean) {
        var stalledAttempts = 0
        while (true) {
            val timeoutUs = if (isEndOfStream) DEQUEUE_TIMEOUT_US else 0L
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!isEndOfStream) return
                    stalledAttempts++
                    if (stalledAttempts >= MAX_STALLED_ATTEMPTS) {
                        throw IOException("The AAC encoder did not reach the end of the stream")
                    }
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    isMuxerStarted = true
                }

                outputIndex >= 0 -> {
                    val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0 && !isConfig && isMuxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        hasWrittenSample = true
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun presentationTimeUs(): Long = writtenSampleCount * MICROS_PER_SECOND / sampleRate
}

private const val AAC_BIT_RATE = 128_000
private const val BYTES_PER_SAMPLE = 2
private const val MAX_INPUT_BYTES = 16_384
private const val MICROS_PER_SECOND = 1_000_000L
private const val DEQUEUE_TIMEOUT_US = 10_000L
private const val MAX_STALLED_ATTEMPTS = 200
