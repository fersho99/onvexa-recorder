package com.onvexa.recorder.wear.recorder

import kotlinx.coroutines.flow.StateFlow

enum class RecorderPhase { Idle, Recording, Paused }

data class RecorderState(
    val phase: RecorderPhase = RecorderPhase.Idle,
    val elapsedMs: Long = 0,
    /** Niveles de audio normalizados 0..1, el más reciente al final. */
    val levels: List<Float> = emptyList(),
    val highlights: Int = 0,
    val lastHighlightMs: Long? = null,
)

/**
 * Grabadora del reloj. Las pantallas solo conocen esta interfaz; la implementación con
 * micrófono real sustituye a la simulada sin cambiar nada de la interfaz de usuario.
 */
interface RecorderController {
    val state: StateFlow<RecorderState>
    fun start()
    fun pause()
    fun resume()
    fun highlight()

    /** Termina la grabación, la guarda y devuelve su duración en milisegundos. */
    suspend fun stop(): Long
}
