package com.onvexa.recorder.wear.link

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LinkState { Connected, Disconnected }

/** Estado de la conexión con el celular emparejado. */
interface PhoneLink {
    val state: StateFlow<LinkState>
}

/** Simulado: siempre conectado. La detección real llega con la sincronización. */
@Singleton
class FakePhoneLink @Inject constructor() : PhoneLink {
    private val _state = MutableStateFlow(LinkState.Connected)
    override val state: StateFlow<LinkState> = _state.asStateFlow()
}
