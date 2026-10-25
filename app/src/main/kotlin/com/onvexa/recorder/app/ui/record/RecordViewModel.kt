package com.onvexa.recorder.app.ui.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.app.data.RecordingsRepository
import com.onvexa.recorder.core.audio.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecordUiState(
    val started: Boolean = false, val paused: Boolean = false, val elapsedMs: Long = 0,
    val levels: List<Float> = emptyList(), val highlights: Int = 0, val notes: Int = 0,
    val templateId: String? = null, val saving: Boolean = false, val error: String? = null,
)
@HiltViewModel
class RecordViewModel @Inject constructor(repository: RecordingsRepository, @ApplicationContext private val context: Context,
    private val media: com.onvexa.recorder.app.backend.MediaRepository) : ViewModel() {
    private val mutableError = MutableStateFlow<String?>(null)
    val mediaError = mutableError.asStateFlow()
    fun addImage(uri: android.net.Uri) { val capture = CaptureRuntime.state.value; val id = capture.id ?: return
        viewModelScope.launch { try { media.addImage(id,capture.elapsedMs,uri); mutableError.value = null } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { mutableError.value = "No se pudo guardar la foto" }
            finally { if (uri.authority == context.packageName + ".photos") runCatching { context.contentResolver.delete(uri,null,null) } }
        }
    }
    private val selectedTemplate = MutableStateFlow<String?>(null)
    val state = combine(CaptureRuntime.state, selectedTemplate) { capture, template ->
        RecordUiState(started = capture.phase != CapturePhase.Idle,
            paused = capture.phase == CapturePhase.Paused, elapsedMs = capture.elapsedMs,
            levels = capture.levels, highlights = capture.highlights, notes = capture.notes,
            templateId = template, saving = capture.phase in listOf(CapturePhase.Starting,CapturePhase.Saving), error = capture.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordUiState())
    val templates = repository.templates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun start() = CaptureCommands.start(context,"phone",selectedTemplate.value ?: templates.value.firstOrNull()?.id,
        if (java.util.Locale.getDefault().language == "en") "en" else "es")
    fun togglePause() = CaptureCommands.send(context, if (state.value.paused) CaptureCommands.RESUME else CaptureCommands.PAUSE)
    fun stop() = CaptureCommands.send(context,CaptureCommands.STOP)
    fun highlight() = CaptureCommands.send(context,CaptureCommands.MARK)
    fun addNote(text: String) = CaptureCommands.send(context,CaptureCommands.NOTE,text)
    fun selectTemplate(id: String) { selectedTemplate.value = id }
}
