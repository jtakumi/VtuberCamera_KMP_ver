package com.example.vtubercamera_kmp_ver.camera

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * マイクと BGM の 16 bit PCM を 1 チャンネルへ混ぜ合わせる。
 *
 * [mic] が null のときはマイクを無音として扱い、[bgm] が null のときは BGM を無音として扱う。
 * BGM には [bgmGain] を掛ける。和が 16 bit の範囲を超える場合は、波形が折り返して耳障りな音にならないよう
 * 範囲の端へ収める。結果は [out] の先頭 [sampleCount] 個へ書く。
 */
internal fun mixPcm16(
    mic: ShortArray?,
    bgm: ShortArray?,
    sampleCount: Int,
    bgmGain: Float,
    out: ShortArray,
) {
    for (index in 0 until sampleCount) {
        val micSample = mic?.get(index)?.toFloat() ?: 0f
        val bgmSample = bgm?.get(index)?.toFloat()?.times(bgmGain) ?: 0f
        out[index] = (micSample + bgmSample)
            .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            .roundToInt()
            .toShort()
    }
}

/**
 * インターリーブされた [channelCount] チャンネルの PCM から、各フレームの平均を取って 1 チャンネルへ変換する。
 * [frameCount] はフレーム数（チャンネルを 1 組とした数）。結果は [out] の先頭 [frameCount] 個へ書く。
 */
internal fun downmixToMono(
    interleaved: ShortArray,
    frameCount: Int,
    channelCount: Int,
    out: ShortArray,
) {
    if (channelCount == 1) {
        interleaved.copyInto(out, destinationOffset = 0, startIndex = 0, endIndex = frameCount)
        return
    }
    for (frame in 0 until frameCount) {
        var sum = 0
        for (channel in 0 until channelCount) {
            sum += interleaved[frame * channelCount + channel]
        }
        out[frame] = (sum / channelCount).toShort()
    }
}

/**
 * 1 チャンネル PCM を線形補間でサンプルレート変換する。
 *
 * 入力を細切れに渡しても境界で音が途切れないよう、直前の入力の最後の 1 サンプルと出力位置の端数を
 * 持ち越す。BGM の巻き戻し（ループ）などで流れが途切れたときは [reset] で持ち越しを捨てる。
 */
internal class LinearResampler(inputSampleRate: Int, outputSampleRate: Int) {
    init {
        require(inputSampleRate > 0 && outputSampleRate > 0) { "Sample rates must be positive" }
    }

    private val step = inputSampleRate.toDouble() / outputSampleRate
    private var position = 0.0
    private var carry: Short = 0

    /** [input] の先頭 [count] 個を変換し、得られたサンプルを [sink] へ追加する。 */
    fun process(input: ShortArray, count: Int, sink: ShortFifo) {
        if (count <= 0) return
        while (true) {
            val baseIndex = floor(position).toInt()
            if (baseIndex + 1 >= count) break
            val fraction = position - baseIndex
            val from = if (baseIndex < 0) carry.toInt() else input[baseIndex].toInt()
            val to = input[baseIndex + 1].toInt()
            sink.write((from + (to - from) * fraction).roundToInt().toShort())
            position += step
        }
        position -= count
        carry = input[count - 1]
    }

    /** 持ち越した状態を捨てる。 */
    fun reset() {
        position = 0.0
        carry = 0
    }
}

/** 16 bit サンプルを先入れ先出しで貯める、伸長する配列。デコード済みの BGM を読み出しの粒度へ合わせるのに使う。 */
internal class ShortFifo(initialCapacity: Int = DEFAULT_CAPACITY) {
    private var buffer = ShortArray(initialCapacity)
    private var readIndex = 0
    private var writeIndex = 0

    /** 貯まっているサンプル数。 */
    val size: Int
        get() = writeIndex - readIndex

    fun write(sample: Short) {
        ensureCapacity(1)
        buffer[writeIndex++] = sample
    }

    fun write(samples: ShortArray, count: Int) {
        ensureCapacity(count)
        samples.copyInto(buffer, destinationOffset = writeIndex, startIndex = 0, endIndex = count)
        writeIndex += count
    }

    /** 先頭から最大 [count] 個を [out] へ取り出し、取り出した数を返す。 */
    fun read(out: ShortArray, count: Int): Int {
        val readCount = minOf(count, size)
        buffer.copyInto(out, destinationOffset = 0, startIndex = readIndex, endIndex = readIndex + readCount)
        readIndex += readCount
        return readCount
    }

    /** 貯まっているサンプルをすべて捨てる。 */
    fun clear() {
        readIndex = 0
        writeIndex = 0
    }

    // 末尾に [extra] 個書けるようにする。読み出し済みの領域を詰めてもなお足りないときだけ配列を伸ばす。
    private fun ensureCapacity(extra: Int) {
        if (writeIndex + extra <= buffer.size) return
        if (readIndex > 0) {
            buffer.copyInto(buffer, destinationOffset = 0, startIndex = readIndex, endIndex = writeIndex)
            writeIndex -= readIndex
            readIndex = 0
        }
        if (writeIndex + extra > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, writeIndex + extra))
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16_384
    }
}
