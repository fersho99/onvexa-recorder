package com.onvexa.recorder.core.audio

import org.junit.Assert.*
import org.junit.Test

class PcmResamplerTest {
    private fun samples(vararg values: Int) = values.flatMap { listOf((it and 255).toByte(),(it shr 8).toByte()) }.toByteArray()
    @Test fun stereoMixIncludesSignedNegativeSamples() {
        assertArrayEquals(samples(0,-1000,1000),PcmResampler(16000,2).convert(samples(1000,-1000,-2000,0,1000,1000)))
    }
    @Test fun arbitraryByteBoundariesProduceIdenticalAudio() {
        val source = samples(*(0 until 4410).map { (it * 31) % 32768 }.toIntArray())
        val expected = PcmResampler(44100,1).convert(source)
        val streaming = PcmResampler(44100,1)
        val actual = source.toList().chunked(137).flatMap { streaming.convert(it.toByteArray()).toList() }.toByteArray()
        assertEquals(3200,actual.size)
        assertArrayEquals(expected,actual)
    }
    @Test fun nativeRatePreservesEverySample() {
        val source = samples(-32768,-1,0,1,32767)
        assertArrayEquals(source,PcmResampler(16000,1).convert(source))
    }
}
