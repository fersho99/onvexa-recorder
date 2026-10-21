package com.onvexa.recorder.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.app.data.RecordingsRepository
import com.onvexa.recorder.core.database.entity.TemplateEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RecordUiState(
    val started: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0,
    val levels: List<Float> = emptyList(),
    val highlights: Int = 0,
    val notes: Int = 0,
    val templateId: String? = null,
)

/**
 * Maqueta de la pantalla de grabación: simula el tiempo y el nivel de audio, sin tocar el micrófono.
 * La grabación real llega en la etapa 2.
 */
@HiltViewModel
class RecordViewModel @Inject constructor(repository: RecordingsRepository) : ViewModel() {

    private val _state = MutableStateFlow(RecordUiState())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    val templates: StateFlow<List<TemplateEntity>> = repository.templates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var ticker: Job? = null
    private var level = 0.3f

    fun start() {
        _state.update { it.copy(started = true, paused = false) }
        runTicker()
    }

    fun togglePause() {
        val paused = !_state.value.paused
        _state.update { it.copy(paused = paused) }
        if (paused) ticker?.cancel() else runTicker()
    }

    fun stop() {
        ticker?.cancel()
        _state.update { RecordUiState(templateId = it.templateId) }
    }

    fun highlight() = _state.update { it.copy(highlights = it.highlights + 1) }
    fun addNote() = _state.update { it.copy(notes = it.notes + 1) }
    fun selectTemplate(id: String) = _state.update { it.copy(templateId = id) }

    private fun runTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                level = (level + (Random.nextFloat() - 0.5f) * 0.5f).coerceIn(0.05f, 1f)
                _state.update {
                    it.copy(elapsedMs = it.elapsedMs + TICK_MS, levels = (it.levels + level).takeLast(MAX_LEVELS))
                }
            }
        }
    }

    private companion object {
        const val TICK_MS = 100L
        const val MAX_LEVELS = 120
    }
}
