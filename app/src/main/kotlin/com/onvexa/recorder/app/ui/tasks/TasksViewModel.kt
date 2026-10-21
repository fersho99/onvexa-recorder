package com.onvexa.recorder.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.app.data.RecordingsRepository
import com.onvexa.recorder.core.database.entity.ActionItemRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val open: List<ActionItemRow> = emptyList(),
    val done: List<ActionItemRow> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class TasksViewModel @Inject constructor(private val repository: RecordingsRepository) : ViewModel() {

    val state: StateFlow<TasksUiState> = repository.actionRows()
        .map { rows ->
            val (done, open) = rows.partition { it.status == "done" }
            TasksUiState(
                // Las que vencen antes van primero; sin fecha, al final.
                open = open.sortedBy { it.dueDate ?: "9999-12-31" },
                done = done,
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setDone(id: String, done: Boolean) = viewModelScope.launch { repository.setActionDone(id, done) }
}
