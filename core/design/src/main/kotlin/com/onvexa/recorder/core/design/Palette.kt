package com.onvexa.recorder.core.design

import androidx.compose.ui.graphics.Color

/** Tokens de color de onvexa_recorder. Ver docs/palette.md para el uso de cada uno. */
object OnvexaPalette {
    val Background = Color(PaletteHex.BACKGROUND)
    val Surface = Color(PaletteHex.SURFACE)
    val SurfaceHigh = Color(PaletteHex.SURFACE_HIGH)
    val Outline = Color(PaletteHex.OUTLINE)
    val OutlineStrong = Color(PaletteHex.OUTLINE_STRONG)

    val Primary = Color(PaletteHex.PRIMARY)
    val OnPrimary = Color(PaletteHex.ON_PRIMARY)
    val PrimaryLight = Color(PaletteHex.PRIMARY_LIGHT)
    val PrimaryContainer = Color(PaletteHex.PRIMARY_CONTAINER)

    val Recording = Color(PaletteHex.RECORDING)
    val OnRecording = Color(PaletteHex.ON_RECORDING)
    val RecordingText = Color(PaletteHex.RECORDING_TEXT)

    val Text = Color(PaletteHex.TEXT)
    val TextMuted = Color(PaletteHex.TEXT_MUTED)

    val Success = Color(PaletteHex.SUCCESS)
    val Warning = Color(PaletteHex.WARNING)
    val Info = Color(PaletteHex.INFO)
    val Error = Color(PaletteHex.ERROR)
    val OnError = Color(PaletteHex.ON_ERROR)

    /** Origen de la grabación: reloj (verde azulado) y celular (azul). */
    val Watch = Color(PaletteHex.WATCH)
    val Phone = Color(PaletteHex.PHONE)

    /** Colores de las ramas del mapa mental y de los hablantes, en orden. */
    val MindMapBranches: List<Color> = PaletteHex.BRANCHES.map { Color(it) }
}
