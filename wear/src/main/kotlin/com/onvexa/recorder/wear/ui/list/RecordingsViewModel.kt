package com.onvexa.recorder.wear.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.core.model.LibraryItem
import com.onvexa.recorder.wear.data.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RecordingsUiState(val items: List<LibraryItem> = emptyList(), val loaded: Boolean = false)

@HiltViewModel
class RecordingsViewModel @Inject constructor(repository: WatchRepository) : ViewModel() {
    val state: StateFlow<RecordingsUiState> = repository.recent()
        .map { RecordingsUiState(it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingsUiState())
}
