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

data class DetailUiState(val detail: RecordingDetail? = null, val loaded: Boolean = false)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: RecordingsRepository,
    private val player: PlaybackController,
) : ViewModel() {

    private val id: String = checkNotNull(savedState["id"])

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
