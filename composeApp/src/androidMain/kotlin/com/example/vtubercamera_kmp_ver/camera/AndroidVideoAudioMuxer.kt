package com.example.vtubercamera_kmp_ver.camera

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 映像だけのファイル [videoFile] と音声だけのファイル [audioFile] を、1 つの MP4 [outputFile] へ結合する。
 *
 * 圧縮済みのデータをそのまま詰め替えるだけで再エンコードはしない。2 つのトラックは時刻の順に交互に書き、
 * 再生側が先読みしやすい並びにする。どちらかのトラックが読めない、または書き出しに失敗したときは例外を投げる。
 */
internal fun mergeVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    try {
        videoExtractor.setDataSource(videoFile.path)
        audioExtractor.setDataSource(audioFile.path)
        val videoFormat = videoExtractor.selectFirstTrack(VIDEO_MIME_PREFIX)
        val audioFormat = audioExtractor.selectFirstTrack(AUDIO_MIME_PREFIX)

        val outputMuxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = outputMuxer
        val videoTrack = TrackCopier(videoExtractor, outputMuxer.addTrack(videoFormat))
        val audioTrack = TrackCopier(audioExtractor, outputMuxer.addTrack(audioFormat))
        outputMuxer.start()

        val buffer = ByteBuffer.allocateDirect(
            maxOf(MIN_COPY_BUFFER_BYTES, videoFormat.maxInputSizeOrZero()),
        )
        val bufferInfo = MediaCodec.BufferInfo()
        while (!videoTrack.isFinished || !audioTrack.isFinished) {
            val next = if (videoTrack.nextTimeUs <= audioTrack.nextTimeUs) videoTrack else audioTrack
            next.copyNext(outputMuxer, buffer, bufferInfo)
        }
        outputMuxer.stop()
    } finally {
        videoExtractor.release()
        audioExtractor.release()
        muxer?.release()
    }
}

// 1 つのトラックを、読んだ順に muxer へ書き写す。
private class TrackCopier(
    private val extractor: MediaExtractor,
    private val muxerTrackIndex: Int,
) {
    /** 次に書くサンプルの時刻。書き終えたトラックは最後尾として扱うため最大値を返す。 */
    val nextTimeUs: Long
        get() = extractor.sampleTime.takeIf { it >= 0L } ?: Long.MAX_VALUE

    val isFinished: Boolean
        get() = extractor.sampleTime < 0L

    fun copyNext(muxer: MediaMuxer, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            throw IOException("Failed to read a sample while merging the recording")
        }
        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }
        bufferInfo.set(0, size, extractor.sampleTime, flags)
        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
        extractor.advance()
    }
}

// 指定した種類の最初のトラックを選び、その形式を返す。無いときは例外を投げる。
private fun MediaExtractor.selectFirstTrack(mimePrefix: String): MediaFormat {
    for (index in 0 until trackCount) {
        val format = getTrackFormat(index)
        if (format.getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true) {
            selectTrack(index)
            return format
        }
    }
    throw IOException("No $mimePrefix track to merge")
}

private fun MediaFormat.maxInputSizeOrZero(): Int =
    if (containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0

private const val VIDEO_MIME_PREFIX = "video/"
private const val AUDIO_MIME_PREFIX = "audio/"
private const val MIN_COPY_BUFFER_BYTES = 2 * 1024 * 1024
