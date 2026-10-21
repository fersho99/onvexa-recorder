package com.onvexa.recorder.core.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Tokens de color de onvexa_recorder. Ver docs/palette.md para el uso de cada uno. */
object OnvexaPalette {
    val Background = Color(PaletteHex.BACKGROUND)
    val Surface = Color(PaletteHex.SURFACE)
    val SurfaceHigh = Color(PaletteHex.SURFACE_HIGH)
    val Outline = Color(PaletteHex.OUTLINE)

    val Primary = Color(PaletteHex.PRIMARY)
    val OnPrimary = Color(PaletteHex.ON_PRIMARY)
    val PrimaryLight = Color(PaletteHex.PRIMARY_LIGHT)

    val Secondary = Color(PaletteHex.SECONDARY)
    val OnSecondary = Color(PaletteHex.ON_SECONDARY)

    val Recording = Color(PaletteHex.RECORDING)
    val OnRecording = Color(PaletteHex.ON_RECORDING)

    /** Bookmarks, resaltados y avisos. */
    val Accent = Color(PaletteHex.ACCENT)

    val Text = Color(PaletteHex.TEXT)
    val TextMuted = Color(PaletteHex.TEXT_MUTED)

    val Error = Color(PaletteHex.ERROR)
    val OnError = Color(PaletteHex.ON_ERROR)

    /** Colores de las ramas principales del mapa mental, en orden. */
    val MindMapBranches = listOf(
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color(0xFFA78BFA),
        Color(0xFFF0ABFC),
        Color(0xFF6A11F2),
    )
}

object OnvexaGradients {
    /** Onda de audio y glow: violeta nebula a rosa. Visible sobre el fondo oscuro. */
    val Wave: Brush
        get() = Brush.horizontalGradient(listOf(OnvexaPalette.Secondary, OnvexaPalette.Recording))

    /** Degradado de marca para encabezados y tarjetas destacadas. */
    val Brand: Brush
        get() = Brush.linearGradient(
            listOf(OnvexaPalette.Surface, OnvexaPalette.Primary, OnvexaPalette.Recording),
        )
}
