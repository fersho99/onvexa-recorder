package com.onvexa.recorder.wear.link

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class LinkState { Connected, Disconnected }

/** Estado de la conexión con el celular emparejado. */
interface PhoneLink {
    val state: StateFlow<LinkState>
}

/** Simulado: siempre conectado. La detección real llega con la sincronización. */
@Singleton
class WearPhoneLink @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context) : PhoneLink {
    private val _state = MutableStateFlow(LinkState.Disconnected)
    override val state: StateFlow<LinkState> = _state.asStateFlow()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    init { scope.launch {
        while (true) {
            _state.value = try { if (com.google.android.gms.wearable.Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()) LinkState.Connected else LinkState.Disconnected }
                catch (_: Exception) { LinkState.Disconnected }
            kotlinx.coroutines.delay(10_000)
        }
    } }
}
