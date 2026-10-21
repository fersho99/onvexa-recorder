package com.onvexa.recorder.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAndTranscriptTest {

    @Test
    fun `formato de reloj`() {
        assertEquals("00:00", formatClock(0))
        assertEquals("01:23", formatClock(83_000))
        assertEquals("59:59", formatClock(3_599_999))
        assertEquals("1:02:05", formatClock(3_725_000))
        assertEquals("00:00", formatClock(-500))
    }

    @Test
    fun `segmento activo`() {
        val starts = listOf(0L, 5_000L, 12_000L, 30_000L)
        assertEquals(0, activeSegmentIndex(starts, 0))
        assertEquals(0, activeSegmentIndex(starts, 4_999))
        assertEquals(1, activeSegmentIndex(starts, 5_000))
        assertEquals(2, activeSegmentIndex(starts, 29_999))
        assertEquals(3, activeSegmentIndex(starts, 999_999))
    }

    @Test
    fun `antes del primer segmento y lista vacia`() {
        assertEquals(-1, activeSegmentIndex(listOf(1_000L, 2_000L), 500))
        assertEquals(-1, activeSegmentIndex(emptyList(), 500))
    }
}
