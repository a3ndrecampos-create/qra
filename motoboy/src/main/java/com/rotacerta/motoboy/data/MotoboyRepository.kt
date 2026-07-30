package com.rotacerta.motoboy.data

import android.content.Context
import com.rotacerta.core.protocol.CallOffer
import com.rotacerta.core.protocol.MotoboyStatus
import com.rotacerta.motoboy.client.ConnectionState
import com.rotacerta.motoboy.client.MotoboyWebSocketClient
import com.rotacerta.motoboy.client.NsdDiscoveryClient
import com.rotacerta.motoboy.client.ServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Orquestra: achar o restaurante na rede (NSD) -> conectar via WebSocket -> manter
 * reconectando se cair. É a única classe que o [MotoboyConnectionService] e o
 * ViewModel precisam falar com.
 */
class MotoboyRepository(context: Context) {

    private val appContext = context.applicationContext
    private val discovery = NsdDiscoveryClient(appContext)
    val wsClient = MotoboyWebSocketClient()
    val prefs = MotoboyPrefs(appContext)

    val connectionState: StateFlow<ConnectionState> = wsClient.connectionState
    val events: SharedFlow<ServerEvent> = wsClient.events

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectionJob: Job? = null

    private val _status = MutableStateFlow(MotoboyStatus.OFFLINE)
    val status: StateFlow<MotoboyStatus> = _status

    private val _currentOffer = MutableStateFlow<CallOffer?>(null)
    val currentOffer: StateFlow<CallOffer?> = _currentOffer

    /** Começa a procurar o restaurante e conectar; reconecta sozinho se cair. */
    fun startAutoConnect() {
        if (connectionJob != null) return
        connectionJob = scope.launch {
            val login = prefs.currentLogin() ?: return@launch
            while (true) {
                if (wsClient.connectionState.value != ConnectionState.CONECTADO) {
                    val server = discovery.discover().firstOrNull()
                    if (server != null) {
                        wsClient.connect(server.host, server.port, login.motoboyId, login.nome)
                    }
                }
                kotlinx.coroutines.delay(5_000) // reavalia a cada 5s (reconexão simples e robusta)
            }
        }

        scope.launch {
            events.collect { event ->
                when (event) {
                    is ServerEvent.RegisterAckReceived -> {
                        if (event.ack.success) _status.value = MotoboyStatus.DISPONIVEL
                    }
                    is ServerEvent.CallOfferReceived -> _currentOffer.value = event.offer
                    is ServerEvent.CallCancelledReceived -> {
                        if (_currentOffer.value?.callId == event.cancelled.callId) {
                            _currentOffer.value = null
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun stopAutoConnect() {
        connectionJob?.cancel()
        connectionJob = null
        wsClient.disconnect()
    }

    suspend fun login(motoboyId: String, nome: String) {
        prefs.saveLogin(motoboyId, nome)
    }

    fun setStatus(novoStatus: MotoboyStatus) {
        scope.launch {
            val current = prefs.currentLogin() ?: return@launch
            wsClient.updateStatus(current.motoboyId, novoStatus)
            _status.value = novoStatus
        }
    }

    fun respondToOffer(accepted: Boolean) {
        val offer = _currentOffer.value ?: return
        scope.launch {
            val login = prefs.currentLogin() ?: return@launch
            wsClient.respondToCall(offer.callId, login.motoboyId, accepted)
            _currentOffer.value = null
            if (accepted) _status.value = MotoboyStatus.EM_ENTREGA
        }
    }

    fun completeCurrentDelivery(orderId: String) {
        scope.launch {
            val login = prefs.currentLogin() ?: return@launch
            wsClient.notifyDeliveryCompleted(login.motoboyId, orderId)
            _status.value = MotoboyStatus.DISPONIVEL
        }
    }
}
