package com.onvexa.recorder.wear.recorder

import android.os.SystemClock
import com.onvexa.recorder.core.database.dao.RecordingDao
import com.onvexa.recorder.core.database.entity.RecordingEntity
import com.onvexa.recorder.core.database.entity.RecordingSource
import com.onvexa.recorder.core.database.entity.RecordingStatus
import com.onvexa.recorder.core.database.entity.SyncState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
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
 * Grabadora simulada: corre el tiempo y genera niveles de audio sin usar el micrófono.
 * Al detener, guarda en la base local una grabación sin audio, pendiente de sincronizar,
 * para que aparezca en la lista igual que lo hará una real.
 */
@Singleton
class FakeRecorderController @Inject constructor(
    private val recordingDao: RecordingDao,
) : RecorderController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(RecorderState())
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var startedAtWallClock = 0L
    private var level = 0.3f

    override fun start() {
        if (_state.value.phase != RecorderPhase.Idle) return
        startedAtWallClock = System.currentTimeMillis()
        _state.value = RecorderState(phase = RecorderPhase.Recording)
        runTicker()
    }

    override fun pause() {
        if (_state.value.phase != RecorderPhase.Recording) return
        ticker?.cancel()
        _state.update { it.copy(phase = RecorderPhase.Paused) }
    }

    override fun resume() {
        if (_state.value.phase != RecorderPhase.Paused) return
        _state.update { it.copy(phase = RecorderPhase.Recording) }
        runTicker()
    }

    override fun highlight() {
        _state.update {
            if (it.phase == RecorderPhase.Idle) it
            else it.copy(highlights = it.highlights + 1, lastHighlightMs = it.elapsedMs)
        }
    }

    override suspend fun stop(): Long {
        ticker?.cancel()
        val finished = _state.value
        _state.value = RecorderState()
        if (finished.phase != RecorderPhase.Idle) {
            val now = System.currentTimeMillis()
            recordingDao.upsert(
                RecordingEntity(
                    id = UUID.randomUUID().toString(),
                    deviceId = null, folderId = null, templateId = null, title = null,
                    source = RecordingSource.WATCH, status = RecordingStatus.RECORDED, language = null,
                    durationMs = finished.elapsedMs, startedAt = startedAtWallClock,
                    localAudioPath = null, remoteAudioPath = null, audioBytes = null, audioSha256 = null,
                    createdAt = now, updatedAt = now, syncState = SyncState.PENDING_UPSERT,
                ),
            )
        }
        return finished.elapsedMs
    }

    private fun runTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val delta = now - last
                last = now
                level = (level + (Random.nextFloat() - 0.5f) * 0.5f).coerceIn(0.05f, 1f)
                _state.update {
                    it.copy(elapsedMs = it.elapsedMs + delta, levels = (it.levels + level).takeLast(MAX_LEVELS))
                }
            }
        }
    }

    private companion object {
        const val TICK_MS = 100L
        const val MAX_LEVELS = 48
    }
}
