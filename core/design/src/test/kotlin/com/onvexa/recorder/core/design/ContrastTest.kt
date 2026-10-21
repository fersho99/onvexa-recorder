package com.onvexa.recorder.core.design

import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica contraste WCAG de los pares de colores que se usan juntos. */
class ContrastTest {

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAtLeast(min: Double, fg: Long, bg: Long, name: String) {
        val r = ratio(fg, bg)
        assertTrue("$name: contraste %.2f < %.1f".format(r, min), r >= min)
    }

    @Test
    fun `texto normal cumple AA (4_5)`() = with(PaletteHex) {
        for ((name, bg) in listOf("background" to BACKGROUND, "surface" to SURFACE, "surfaceHigh" to SURFACE_HIGH)) {
            assertAtLeast(4.5, TEXT, bg, "text/$name")
            assertAtLeast(4.5, TEXT_MUTED, bg, "textMuted/$name")
            assertAtLeast(4.5, PRIMARY_LIGHT, bg, "primaryLight/$name")
            assertAtLeast(4.5, RECORDING_TEXT, bg, "recordingText/$name")
            assertAtLeast(4.5, ERROR, bg, "error/$name")
            assertAtLeast(4.5, SUCCESS, bg, "success/$name")
            assertAtLeast(4.5, WARNING, bg, "warning/$name")
            assertAtLeast(4.5, INFO, bg, "info/$name")
            assertAtLeast(4.5, WATCH, bg, "watch/$name")
            assertAtLeast(4.5, PHONE, bg, "phone/$name")
            BRANCHES.forEachIndexed { i, c -> assertAtLeast(4.5, c, bg, "branch$i/$name") }
        }
        assertAtLeast(4.5, PRIMARY_LIGHT, PRIMARY_CONTAINER, "primaryLight/primaryContainer")
        assertAtLeast(4.5, TEXT, PRIMARY_CONTAINER, "text/primaryContainer")
    }

    @Test
    fun `texto sobre rellenos cumple AA`() = with(PaletteHex) {
        assertAtLeast(4.5, ON_PRIMARY, PRIMARY, "onPrimary/primary")
        assertAtLeast(4.5, ON_RECORDING, RECORDING, "onRecording/recording")
        assertAtLeast(4.5, ON_ERROR, ERROR, "onError/error")
    }

    @Test
    fun `graficos e iconos cumplen 3_1`() = with(PaletteHex) {
        assertAtLeast(3.0, RECORDING, BACKGROUND, "recording/background")
        assertAtLeast(3.0, RECORDING, SURFACE, "recording/surface")
    }
}
