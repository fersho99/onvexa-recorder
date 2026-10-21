package com.onvexa.recorder.wear.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.wear.device.HapticKind
import com.onvexa.recorder.wear.device.Haptics
import com.onvexa.recorder.wear.recorder.RecorderController
import com.onvexa.recorder.wear.recorder.RecorderPhase
import com.onvexa.recorder.wear.recorder.RecorderState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Confirmación breve que aparece al resaltar un momento. */
data class HighlightMark(val number: Int, val atMs: Long)

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val recorder: RecorderController,
    private val haptics: Haptics,
) : ViewModel() {

    val state: StateFlow<RecorderState> = recorder.state

    private val _mark = MutableStateFlow<HighlightMark?>(null)
    val mark: StateFlow<HighlightMark?> = _mark.asStateFlow()
    private var markJob: Job? = null

    /** Empieza a grabar si aún no hay una grabación en curso. */
    fun ensureStarted() {
        if (recorder.state.value.phase == RecorderPhase.Idle) {
            recorder.start()
            haptics.perform(HapticKind.Strong)
        }
    }

    fun togglePause() {
        haptics.perform(HapticKind.Click)
        if (recorder.state.value.phase == RecorderPhase.Paused) recorder.resume() else recorder.pause()
    }

    fun highlight() {
        haptics.perform(HapticKind.Click)
        recorder.highlight()
        val s = recorder.state.value
        _mark.value = HighlightMark(s.highlights, s.lastHighlightMs ?: s.elapsedMs)
        markJob?.cancel()
        markJob = viewModelScope.launch {
            delay(MARK_MS)
            _mark.value = null
        }
    }

    /** Detiene, guarda y avisa con la duración para mostrar la pantalla de cierre. */
    fun stop(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val duration = recorder.stop()
            haptics.perform(HapticKind.Success)
            onSaved(duration)
        }
    }

    private companion object {
        const val MARK_MS = 1_800L
    }
}
