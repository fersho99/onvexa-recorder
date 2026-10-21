package com.onvexa.recorder.app.player

import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val recordingId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
)

/** Reproductor de la grabación abierta. La implementación con audio real llega con la grabación. */
interface PlaybackController {
    val state: StateFlow<PlaybackState>
    fun load(recordingId: String, durationMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skip(deltaMs: Long)
    fun setSpeed(speed: Float)
}
