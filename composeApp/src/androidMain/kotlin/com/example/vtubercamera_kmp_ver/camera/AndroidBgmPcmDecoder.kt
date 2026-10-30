package com.example.vtubercamera_kmp_ver.camera

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BGM の音声ファイルを、ミックス用の 1 チャンネル・[MIX_SAMPLE_RATE] の 16 bit PCM として読み出すデコーダー。
 *
 * [MediaExtractor] と [MediaCodec] で同期的にデコードし、元のチャンネル数とサンプルレートが何であっても
 * [read] で受け取る PCM は同じ形に揃える。ファイルの終端へ達したら頭へ巻き戻して続けるため、
 * 録画が BGM より長くても途切れない。呼び出しは 1 つのスレッドに限る。
 */
internal class BgmPcmDecoder private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    inputFormat: MediaFormat,
) : AutoCloseable {
    private val decoded = ShortFifo()
    private val bufferInfo = MediaCodec.BufferInfo()
    private var sourceChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    private var resampler = LinearResampler(inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), MIX_SAMPLE_RATE)
    private var interleavedScratch = ShortArray(0)
    private var monoScratch = ShortArray(0)

    /**
     * 先頭から [count] 個のサンプルを [out] へ読み出し、読み出せた数を返す。
     * デコードが追いつかず足りないときは [count] より少なく返すので、呼び出し側が残りを無音で埋める。
     */
    fun read(out: ShortArray, count: Int): Int {
        var attempts = 0
        while (decoded.size < count && attempts < MAX_DECODE_ATTEMPTS_PER_READ) {
            decodeStep()
            attempts++
        }
        return decoded.read(out, count)
    }

    // 入力を 1 つ詰め、出力を 1 つ取り出す。どちらも待たずに戻るので、足りない間は呼び出し側が繰り返す。
    private fun decodeStep() {
        feedInput()
        drainOutput()
    }

    // 圧縮データをデコーダーへ渡す。終端なら先頭へ巻き戻してデコーダーを空にし、次の呼び出しから流し直す。
    private fun feedInput() {
        val inputIndex = codec.dequeueInputBuffer(0)
        if (inputIndex < 0) return
        val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
        val size = extractor.readSampleData(inputBuffer, 0)
        if (size < 0) {
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            codec.flush()
            resampler.reset()
            return
        }
        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
        extractor.advance()
    }

    // デコード済みの PCM を取り出し、モノラル化とサンプルレート変換をして貯める。
    private fun drainOutput() {
        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
        when {
            outputIndex >= 0 -> {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    appendDecoded(outputBuffer, bufferInfo)
                }
                codec.releaseOutputBuffer(outputIndex, false)
            }

            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> applyOutputFormat(codec.outputFormat)
        }
    }

    // デコーダーが実際に出力する形式へ追従する。圧縮形式によっては入力側の宣言と異なることがある。
    private fun applyOutputFormat(format: MediaFormat) {
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        check(encoding == AudioFormat.ENCODING_PCM_16BIT) {
            "Unsupported PCM encoding from the BGM decoder: $encoding"
        }
        sourceChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        resampler = LinearResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), MIX_SAMPLE_RATE)
    }

    private fun appendDecoded(outputBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        outputBuffer.position(info.offset)
        outputBuffer.limit(info.offset + info.size)
        val samples = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val sampleCount = samples.remaining()
        if (interleavedScratch.size < sampleCount) {
            interleavedScratch = ShortArray(sampleCount)
        }
        samples.get(interleavedScratch, 0, sampleCount)

        val frameCount = sampleCount / sourceChannelCount
        if (monoScratch.size < frameCount) {
            monoScratch = ShortArray(frameCount)
        }
        downmixToMono(interleavedScratch, frameCount, sourceChannelCount, monoScratch)
        resampler.process(monoScratch, frameCount, decoded)
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }

    companion object {
        /**
         * [file] の音声トラックを [startPositionMs] の位置から読むデコーダーを開く。
         * 音声トラックが無い、またはデコードできない形式のときは例外を投げる。
         */
        fun open(file: File, startPositionMs: Long): BgmPcmDecoder {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.path)
                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw IOException("The BGM file has no audio track")
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: throw IOException("The BGM track has no MIME type")

                val codec = MediaCodec.createDecoderByType(mime)
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()
                    if (startPositionMs > 0L) {
                        extractor.seekTo(startPositionMs * MICROS_PER_MILLI, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    }
                    return BgmPcmDecoder(extractor, codec, format)
                } catch (error: RuntimeException) {
                    codec.release()
                    throw error
                }
            } catch (error: IOException) {
                extractor.release()
                throw error
            } catch (error: RuntimeException) {
                extractor.release()
                throw error
            }
        }
    }
}

internal const val MIX_SAMPLE_RATE = 44_100
private const val MICROS_PER_MILLI = 1_000L

// 1 回の読み出しでデコードを試みる上限。壊れたファイルで無限に待たないための歯止め。
private const val MAX_DECODE_ATTEMPTS_PER_READ = 64
private const val DEQUEUE_TIMEOUT_US = 2_000L
