package com.onvexa.recorder.wear.ui

import androidx.lifecycle.ViewModel
import com.onvexa.recorder.wear.recorder.RecorderController
import com.onvexa.recorder.wear.recorder.RecorderState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Expone el estado de la grabadora a la raíz de la app (modo ambiente). */
@HiltViewModel
class ShellViewModel @Inject constructor(recorder: RecorderController) : ViewModel() {
    val recorderState: StateFlow<RecorderState> = recorder.state
}
