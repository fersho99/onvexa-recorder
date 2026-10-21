package com.onvexa.recorder.core.design

/**
 * Valores ARGB de la paleta. Se mantienen como constantes puras (sin tipos de Compose)
 * para poder validarlos con pruebas JVM de contraste.
 */
internal object PaletteHex {
    const val BACKGROUND: Long = 0xFF0F1115
    const val SURFACE: Long = 0xFF171A21
    const val SURFACE_HIGH: Long = 0xFF1F232C
    const val OUTLINE: Long = 0xFF2A2F3A
    const val OUTLINE_STRONG: Long = 0xFF3A4150

    const val PRIMARY: Long = 0xFF6A11F2
    const val ON_PRIMARY: Long = 0xFFFFFFFF
    const val PRIMARY_LIGHT: Long = 0xFFA99BFF
    const val PRIMARY_CONTAINER: Long = 0xFF241E3F

    /** Relleno del botón de grabar (texto o ícono blanco encima). */
    const val RECORDING: Long = 0xFFDC2626
    const val ON_RECORDING: Long = 0xFFFFFFFF

    /** Rojo claro para texto o íconos de "grabando" sobre fondo oscuro. */
    const val RECORDING_TEXT: Long = 0xFFF87171

    const val TEXT: Long = 0xFFECEEF3
    const val TEXT_MUTED: Long = 0xFF9AA3B2

    const val SUCCESS: Long = 0xFF34C58A
    const val WARNING: Long = 0xFFE8B04B
    const val INFO: Long = 0xFF4DA3FF
    const val ERROR: Long = 0xFFFF8A75
    const val ON_ERROR: Long = 0xFF0F1115

    const val WATCH: Long = 0xFF2DD4BF
    const val PHONE: Long = 0xFF60A5FA

    val BRANCHES: List<Long> = listOf(
        0xFF8B7CF6,
        0xFF2DD4BF,
        0xFFF5B84B,
        0xFF5AA9FF,
        0xFFF472B6,
        0xFF4ADE80,
    )
}
