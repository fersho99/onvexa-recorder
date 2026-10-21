package com.onvexa.recorder.wear.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.wear.data.WatchDetail
import com.onvexa.recorder.wear.data.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(val detail: WatchDetail? = null, val loaded: Boolean = false)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: WatchRepository,
) : ViewModel() {

    private val id: String = checkNotNull(savedState["id"])

    val state: StateFlow<DetailUiState> = repository.detail(id)
        .map { DetailUiState(it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun setDone(actionId: String, done: Boolean) =
        viewModelScope.launch { repository.setActionDone(actionId, done) }
}
