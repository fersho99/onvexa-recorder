package com.onvexa.recorder.app.ui.ask

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.app.R
import com.onvexa.recorder.app.data.ChatMessageView
import com.onvexa.recorder.app.data.RecordingsRepository
import com.onvexa.recorder.app.ui.common.ChatMessages
import com.onvexa.recorder.app.ui.common.AiChatInput
import com.onvexa.recorder.app.ui.common.AiFeedback
import com.onvexa.recorder.app.backend.AiRepository
import com.onvexa.recorder.app.backend.BackendException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.onvexa.recorder.app.ui.common.EmptyState
import com.onvexa.recorder.core.design.OnvexaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AskViewModel @Inject constructor(repository: RecordingsRepository, private val ai: AiRepository) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun ask(question: String) = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true; _error.value = null
        try { ai.ask(null, question) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = (e as? BackendException)?.code ?: "connection_failed" }
        finally { _busy.value = false }
    }
    val messages: StateFlow<List<ChatMessageView>> = repository.globalChat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun AskScreen(viewModel: AskViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = stringResource(R.string.ask_title),
            style = MaterialTheme.typography.headlineMedium,
            color = OnvexaPalette.Text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.ask_global_empty),
                    subtitle = stringResource(R.string.ask_global_empty_hint),
                )
            }
        } else {
            ChatMessages(messages, Modifier.weight(1f).fillMaxWidth())
        }
        AiFeedback(busy, error)
        AiChatInput(busy, viewModel::ask)
    }
}
