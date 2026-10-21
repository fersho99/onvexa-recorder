package com.onvexa.recorder.wear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.wear.data.WatchRepository
import com.onvexa.recorder.wear.settings.WatchSettings
import com.onvexa.recorder.wear.settings.WatchSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: WatchSettingsRepository,
    private val watch: WatchRepository,
) : ViewModel() {

    val settings: StateFlow<WatchSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchSettings())

    val freeBytes: Long get() = watch.freeBytes()

    fun setHaptics(value: Boolean) = viewModelScope.launch { repository.setHaptics(value) }
    fun setDim(value: Boolean) = viewModelScope.launch { repository.setDimWhileRecording(value) }
}

