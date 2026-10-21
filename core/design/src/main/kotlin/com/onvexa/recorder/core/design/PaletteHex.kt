package com.onvexa.recorder.core.design

/**
 * Valores ARGB de la paleta Nebula. Se mantienen como constantes puras (sin tipos de Compose)
 * para poder validarlos con pruebas JVM de contraste.
 */
internal object PaletteHex {
    const val BACKGROUND: Long = 0xFF120A2E
    const val SURFACE: Long = 0xFF1A0F3D
    const val SURFACE_HIGH: Long = 0xFF2A1A63
    const val OUTLINE: Long = 0xFF4A3A8C

    const val PRIMARY: Long = 0xFF6A11F2
    const val ON_PRIMARY: Long = 0xFFFFFFFF
    const val PRIMARY_LIGHT: Long = 0xFFA78BFA

    const val SECONDARY: Long = 0xFF8B5CF6
    const val ON_SECONDARY: Long = 0xFF0B0620

    const val RECORDING: Long = 0xFFEC4899
    const val ON_RECORDING: Long = 0xFF120A2E

    const val ACCENT: Long = 0xFFF0ABFC

    const val TEXT: Long = 0xFFE9D5FF
    const val TEXT_MUTED: Long = 0xFFB4A5D6

    const val ERROR: Long = 0xFFFF8A6B
    const val ON_ERROR: Long = 0xFF120A2E
}
