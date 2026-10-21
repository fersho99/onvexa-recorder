package com.onvexa.recorder.wear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.wear.device.BatteryInfo
import com.onvexa.recorder.wear.device.BatteryMonitor
import com.onvexa.recorder.wear.link.LinkState
import com.onvexa.recorder.wear.link.PhoneLink
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val link: LinkState = LinkState.Disconnected,
    val battery: BatteryInfo? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    link: PhoneLink,
    battery: BatteryMonitor,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(link.state, battery.state) { l, b -> HomeUiState(l, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
