package com.example.vtubercamera_kmp_ver.camera

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AndroidPcmAudioTest {
    @Test
    fun mixPcm16_addsMicAndScaledBgm() {
        val out = ShortArray(3)

        mixPcm16(
            mic = shortArrayOf(100, -200, 300),
            bgm = shortArrayOf(1000, 1000, -1000),
            sampleCount = 3,
            bgmGain = 0.5f,
            out = out,
        )

        assertContentEquals(shortArrayOf(600, 300, -200), out)
    }

    @Test
    fun mixPcm16_appliesMicrophoneGainForAttenuationAndAmplification() {
        val quieter = ShortArray(2)
        val oneHundredFiftyPercent = ShortArray(2)
        val twoHundredPercent = ShortArray(2)
        val aboveMaximum = ShortArray(2)

        mixPcm16(
            mic = shortArrayOf(1_000, -2_000),
            bgm = null,
            sampleCount = 2,
            bgmGain = 1f,
            out = quieter,
            micGain = 0.5f,
        )
        mixPcm16(
            mic = shortArrayOf(1_000, -2_000),
            bgm = null,
            sampleCount = 2,
            bgmGain = 1f,
            out = oneHundredFiftyPercent,
            micGain = 1.5f,
        )
        mixPcm16(
            mic = shortArrayOf(1_000, -2_000),
            bgm = null,
            sampleCount = 2,
            bgmGain = 1f,
            out = twoHundredPercent,
            micGain = 2f,
        )
        mixPcm16(
            mic = shortArrayOf(1_000, -2_000),
            bgm = null,
            sampleCount = 2,
            bgmGain = 1f,
            out = aboveMaximum,
            micGain = 2.5f,
        )

        assertContentEquals(shortArrayOf(500, -1_000), quieter)
        assertContentEquals(shortArrayOf(1_500, -3_000), oneHundredFiftyPercent)
        assertContentEquals(shortArrayOf(2_000, -4_000), twoHundredPercent)
        assertContentEquals(shortArrayOf(2_000, -4_000), aboveMaximum)
    }

    @Test
    fun mixPcm16_clipsAmplifiedMicrophoneSamples() {
        val out = ShortArray(2)

        mixPcm16(
            mic = shortArrayOf(20_000, -20_000),
            bgm = null,
            sampleCount = 2,
            bgmGain = 1f,
            out = out,
            micGain = 2f,
        )

        assertContentEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), out)
    }

    @Test
    fun mixPcm16_clipsInsteadOfWrappingAround() {
        val out = ShortArray(2)

        mixPcm16(
            mic = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE),
            bgm = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE),
            sampleCount = 2,
            bgmGain = 1f,
            out = out,
        )

        assertContentEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), out)
    }

    @Test
    fun mixPcm16_withoutMicOrBgmTreatsThemAsSilence() {
        val micOnly = ShortArray(2)
        val bgmOnly = ShortArray(2)

        mixPcm16(mic = shortArrayOf(5, 6), bgm = null, sampleCount = 2, bgmGain = 1f, out = micOnly)
        mixPcm16(mic = null, bgm = shortArrayOf(1000, -1000), sampleCount = 2, bgmGain = 0.1f, out = bgmOnly)

        assertContentEquals(shortArrayOf(5, 6), micOnly)
        assertContentEquals(shortArrayOf(100, -100), bgmOnly)
    }

    @Test
    fun mixPcm16_onlyWritesTheRequestedSampleCount() {
        val out = shortArrayOf(7, 7, 7)

        mixPcm16(mic = shortArrayOf(1, 2, 3), bgm = null, sampleCount = 2, bgmGain = 1f, out = out)

        assertContentEquals(shortArrayOf(1, 2, 7), out)
    }

    @Test
    fun downmixToMono_averagesEachFrame() {
        val out = ShortArray(2)

        downmixToMono(shortArrayOf(100, 300, -50, 50), frameCount = 2, channelCount = 2, out = out)

        assertContentEquals(shortArrayOf(200, 0), out)
    }

    @Test
    fun downmixToMono_withOneChannelCopiesTheSamples() {
        val out = ShortArray(3)

        downmixToMono(shortArrayOf(1, 2, 3), frameCount = 3, channelCount = 1, out = out)

        assertContentEquals(shortArrayOf(1, 2, 3), out)
    }

    @Test
    fun linearResampler_atTheSameRateKeepsTheSamples() {
        val resampler = LinearResampler(inputSampleRate = 44_100, outputSampleRate = 44_100)
        val sink = ShortFifo()

        resampler.process(shortArrayOf(10, 20, 30, 40), 4, sink)

        val out = ShortArray(8)
        val count = sink.read(out, 8)
        // 最後のサンプルは次の入力との補間に使うため、次の呼び出しまで出力されない。
        assertEquals(3, count)
        assertContentEquals(shortArrayOf(10, 20, 30), out.copyOf(count))
    }

    @Test
    fun linearResampler_downsamplingToHalfRateHalvesTheLength() {
        val resampler = LinearResampler(inputSampleRate = 48_000, outputSampleRate = 24_000)
        val sink = ShortFifo()
        val input = ShortArray(1_000) { (it * 10).toShort() }

        resampler.process(input, input.size, sink)

        assertEquals(500, sink.size)
    }

    @Test
    fun linearResampler_interpolatesBetweenSamples() {
        val resampler = LinearResampler(inputSampleRate = 24_000, outputSampleRate = 48_000)
        val sink = ShortFifo()

        resampler.process(shortArrayOf(0, 100, 200), 3, sink)

        val out = ShortArray(8)
        val count = sink.read(out, 8)
        assertContentEquals(shortArrayOf(0, 50, 100, 150), out.copyOf(count))
    }

    @Test
    fun linearResampler_isContinuousAcrossBuffers() {
        val whole = ShortFifo()
        LinearResampler(3, 5).process(ShortArray(30) { (it * 7).toShort() }, 30, whole)

        val chunked = ShortFifo()
        val resampler = LinearResampler(3, 5)
        val input = ShortArray(30) { (it * 7).toShort() }
        resampler.process(input.copyOfRange(0, 11), 11, chunked)
        resampler.process(input.copyOfRange(11, 30), 19, chunked)

        val expected = ShortArray(whole.size).also { whole.read(it, whole.size) }
        val actual = ShortArray(chunked.size).also { chunked.read(it, chunked.size) }
        assertContentEquals(expected, actual)
    }

    @Test
    fun shortFifo_readsInFirstInFirstOutOrderAndGrows() {
        val fifo = ShortFifo(initialCapacity = 2)

        fifo.write(shortArrayOf(1, 2, 3, 4, 5), 5)
        val first = ShortArray(2)
        assertEquals(2, fifo.read(first, 2))
        fifo.write(6)
        val rest = ShortArray(10)
        val restCount = fifo.read(rest, 10)

        assertContentEquals(shortArrayOf(1, 2), first)
        assertContentEquals(shortArrayOf(3, 4, 5, 6), rest.copyOf(restCount))
        assertEquals(0, fifo.size)
    }

    @Test
    fun shortFifo_clearDropsEverything() {
        val fifo = ShortFifo()
        fifo.write(shortArrayOf(1, 2, 3), 3)

        fifo.clear()

        assertEquals(0, fifo.size)
        assertEquals(0, fifo.read(ShortArray(3), 3))
    }
}
