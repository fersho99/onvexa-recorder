package com.onvexa.recorder.app.player

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reproductor simulado: avanza la posición sin reproducir sonido. Permite probar el reproductor,
 * las marcas y la transcripción sincronizada antes de contar con audio real.
 */
@Singleton
class FakePlaybackController @Inject constructor() : PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var ticker: Job? = null

    override fun load(recordingId: String, durationMs: Long) {
        if (_state.value.recordingId == recordingId) return
        ticker?.cancel()
        _state.value = PlaybackState(recordingId = recordingId, durationMs = durationMs)
    }

    override fun play() {
        val current = _state.value
        if (current.recordingId == null || current.durationMs <= 0) return
        if (current.positionMs >= current.durationMs) _state.update { it.copy(positionMs = 0) }
        ticker?.cancel()
        _state.update { it.copy(isPlaying = true) }
        ticker = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val s = _state.value
                val position = (s.positionMs + ((now - last) * s.speed).toLong()).coerceAtMost(s.durationMs)
                last = now
                val finished = position >= s.durationMs
                _state.value = s.copy(positionMs = position, isPlaying = !finished)
                if (finished) break
            }
        }
    }

    override fun pause() {
        ticker?.cancel()
        _state.update { it.copy(isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs.coerceIn(0, it.durationMs)) }
    }

    override fun skip(deltaMs: Long) {
        _state.update { it.copy(positionMs = (it.positionMs + deltaMs).coerceIn(0, it.durationMs)) }
    }

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    abstract fun bindPlayback(impl: FakePlaybackController): PlaybackController
}
