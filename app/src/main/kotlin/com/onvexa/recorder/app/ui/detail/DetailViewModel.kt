package com.onvexa.recorder.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.app.data.RecordingDetail
import com.onvexa.recorder.app.data.RecordingsRepository
import com.onvexa.recorder.app.player.PlaybackController
import com.onvexa.recorder.app.player.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.onvexa.recorder.app.backend.AiRepository
import com.onvexa.recorder.app.backend.BackendException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

data class DetailUiState(val detail: RecordingDetail? = null, val loaded: Boolean = false)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: RecordingsRepository,
    private val player: PlaybackController,
    private val ai: AiRepository,
    private val processing: com.onvexa.recorder.app.transcription.ProcessingRepository,
    private val media: com.onvexa.recorder.app.backend.MediaRepository,
    private val scheduler: com.onvexa.recorder.app.backend.SyncScheduler,
    private val transfer: com.onvexa.recorder.core.audio.WearTransfer,
    database: com.onvexa.recorder.core.database.AppDatabase,
) : ViewModel() {
    private val _draft = MutableStateFlow<String?>(null)
    val draft = _draft.asStateFlow()
    fun transcribe() = runAi { processing.enqueue(id,replace = true) }
    fun retryPlayback() { player.load(id,state.value.detail?.recording?.durationMs ?: 0) }
    fun addImage(uri: android.net.Uri) { val atMs = playback.value.positionMs; runAi { media.addImage(id,atMs,uri); scheduler.schedule() } }
    suspend fun image(id: String) = media.image(id)
    fun describeImage(attachmentId: String) = runAi { ai.describeImage(id,attachmentId); transfer.schedule() }
    fun followupEmail() = runAi {
        val result = ai.followupEmail(id)
        _draft.value = result["subject"]?.jsonPrimitive?.content.orEmpty() + "\n\n" + result["body"]?.jsonPrimitive?.content.orEmpty()
    }
    fun dismissDraft() { _draft.value = null }
    fun editDraft(value: String) { _draft.value = value }

    private val id: String = checkNotNull(savedState["id"])
    val jobs = database.jobDao().observeFor(id).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _aiBusy = MutableStateFlow(false)
    val aiBusy = _aiBusy.asStateFlow()
    private val _aiError = MutableStateFlow<String?>(null)
    val aiError = _aiError.asStateFlow()
    fun summarize() = runAi { ai.summarize(id) }
    fun mindMap() = runAi { ai.mindMap(id) }
    fun ask(question: String) = runAi { ai.ask(id, question) }
    private fun runAi(action: suspend () -> Unit) = viewModelScope.launch {
        if (_aiBusy.value) return@launch
        _aiBusy.value = true; _aiError.value = null
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _aiError.value = (e as? BackendException)?.code ?: "connection_failed" }
        finally { _aiBusy.value = false; transfer.schedule() }
    }

    val state: StateFlow<DetailUiState> = repository.detail(id)
        .onEach { detail -> detail?.let { player.load(it.recording.id, it.recording.durationMs) } }
        .map { DetailUiState(it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    val playback: StateFlow<PlaybackState> = player.state

    fun togglePlay() = if (player.state.value.isPlaying) player.pause() else player.play()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun skip(deltaMs: Long) = player.skip(deltaMs)
    fun cycleSpeed() {
        val speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
        val next = speeds[(speeds.indexOf(player.state.value.speed) + 1).mod(speeds.size)]
        player.setSpeed(next)
    }

    fun rename(title: String) = viewModelScope.launch { repository.rename(id, title) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        player.pause()
        repository.deleteRecording(id)
        scheduler.schedule(); transfer.schedule()
        onDone()
    }
    fun setActionDone(actionId: String, done: Boolean) =
        viewModelScope.launch { repository.setActionDone(actionId, done) }
    fun renameSpeaker(speakerId: String, name: String) =
        viewModelScope.launch { repository.renameSpeaker(speakerId, name) }

    override fun onCleared() {
        player.pause()
    }
}
