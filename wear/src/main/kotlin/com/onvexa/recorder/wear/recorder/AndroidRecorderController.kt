package com.onvexa.recorder.wear.recorder

import android.content.Context
import com.onvexa.recorder.core.audio.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Singleton
class AndroidRecorderController @Inject constructor(@ApplicationContext private val context: Context) : RecorderController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val state = CaptureRuntime.state.map { s -> RecorderState(
        phase = when (s.phase) { CapturePhase.Idle -> RecorderPhase.Idle; CapturePhase.Paused -> RecorderPhase.Paused; else -> RecorderPhase.Recording },
        elapsedMs = s.elapsedMs, levels = s.levels, highlights = s.highlights, lastHighlightMs = s.elapsedMs, error = s.error,
    ) }.stateIn(scope, SharingStarted.Eagerly, RecorderState())
    override fun start() = CaptureCommands.start(context,"watch",null,java.util.Locale.getDefault().language.let { if (it == "en") "en" else "es" })
    override fun pause() = CaptureCommands.send(context,CaptureCommands.PAUSE)
    override fun resume() = CaptureCommands.send(context,CaptureCommands.RESUME)
    override fun highlight() = CaptureCommands.send(context,CaptureCommands.MARK)
    override suspend fun stop(): Long {
        withTimeout(30_000) { CaptureRuntime.state.first { it.phase != CapturePhase.Starting } }
        CaptureCommands.send(context,CaptureCommands.STOP)
        val saved = withTimeout(30_000) { CaptureRuntime.state.first { it.phase == CapturePhase.Idle } }
        check(saved.error == null) { saved.error.orEmpty() }
        return saved.elapsedMs
    }
}
