package com.onvexa.recorder.core.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CapturePhase { Idle, Starting, Recording, Paused, Saving }
data class CaptureState(
    val id: String? = null, val phase: CapturePhase = CapturePhase.Idle,
    val elapsedMs: Long = 0, val levels: List<Float> = emptyList(),
    val highlights: Int = 0, val notes: Int = 0, val error: String? = null,
    val savedId: String? = null,
)
object CaptureRuntime {
    internal val mutable = MutableStateFlow(CaptureState())
    val state = mutable.asStateFlow()
}
interface CaptureCompletionListener { fun onRecordingSaved(id: String) }
interface WearChangeListener { fun onWearChanged() }

object CaptureCommands {
    const val START = "onvexa.capture.START"
    const val STOP = "onvexa.capture.STOP"
    const val PAUSE = "onvexa.capture.PAUSE"
    const val RESUME = "onvexa.capture.RESUME"
    const val MARK = "onvexa.capture.MARK"
    const val NOTE = "onvexa.capture.NOTE"
    fun start(context: Context, source: String, templateId: String?, language: String) {
        require(source in listOf("phone", "watch"))
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java).apply {
            action = START; putExtra("source", source); putExtra("template", templateId); putExtra("language", language)
        })
    }
    fun send(context: Context, command: String, text: String? = null) {
        // No crear un servicio nuevo en segundo plano solo para una orden tardia.
        if (CaptureRuntime.state.value.phase == CapturePhase.Idle) return
        context.startService(Intent(context, RecordingService::class.java).apply { action = command; putExtra("text", text) })
    }
}

/** Reloj monotono que excluye las pausas. Independiente del reloj de pared y de los frames UI. */
class CaptureClock {
    private var accumulated = 0L
    private var runningSince: Long? = null
    fun resume(now: Long) { if (runningSince == null) runningSince = now }
    fun pause(now: Long) { runningSince?.let { accumulated += (now - it).coerceAtLeast(0) }; runningSince = null }
    fun elapsed(now: Long) = accumulated + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
}
