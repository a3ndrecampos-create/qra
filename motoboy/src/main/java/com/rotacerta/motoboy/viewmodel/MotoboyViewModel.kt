package com.rotacerta.motoboy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rotacerta.core.data.Delivery
import com.rotacerta.core.data.DeliveryStatus
import com.rotacerta.core.data.Priority
import com.rotacerta.core.protocol.CallOffer
import com.rotacerta.core.protocol.MotoboyStatus
import com.rotacerta.motoboy.client.ConnectionState
import com.rotacerta.motoboy.data.MotoboyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MotoboyUiState(
    val loggedIn: Boolean = false,
    val motoboyId: String = "",
    val nome: String = "",
    val connectionState: ConnectionState = ConnectionState.DESCONECTADO,
    val status: MotoboyStatus = MotoboyStatus.OFFLINE,
    val incomingOffer: CallOffer? = null,
    // Entrega(s) atualmente aceita(s) — reaproveita o mesmo modelo de dados e
    // componentes de UI (DeliveryCard) do sistema de rotas já existente.
    val activeDeliveries: List<Delivery> = emptyList()
)

class MotoboyViewModel(private val repo: MotoboyRepository) : ViewModel() {

    private val _activeDeliveries = MutableStateFlow<List<Delivery>>(emptyList())
    private val _loginInfo = MutableStateFlow<Pair<String, String>?>(null)

    val uiState: StateFlow<MotoboyUiState> = combine(
        repo.connectionState,
        repo.status,
        repo.currentOffer,
        _activeDeliveries
    ) { connState, status, offer, deliveries ->
        val login = _loginInfo.value
        MotoboyUiState(
            loggedIn = login != null,
            motoboyId = login?.first.orEmpty(),
            nome = login?.second.orEmpty(),
            connectionState = connState,
            status = status,
            incomingOffer = offer,
            activeDeliveries = deliveries
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MotoboyUiState()
    )

    init {
        viewModelScope.launch {
            val login = repo.prefs.currentLogin()
            if (login != null) {
                _loginInfo.value = login.motoboyId to login.nome
            }
        }
    }

    fun login(motoboyId: String, nome: String) {
        viewModelScope.launch {
            repo.login(motoboyId, nome)
            _loginInfo.value = motoboyId to nome
        }
    }

    fun toggleAvailability() {
        val novo = if (repo.status.value == MotoboyStatus.PAUSADO) MotoboyStatus.DISPONIVEL else MotoboyStatus.PAUSADO
        repo.setStatus(novo)
    }

    fun acceptOffer() {
        val offer = repo.currentOffer.value ?: return
        repo.respondToOffer(accepted = true)
        _activeDeliveries.update { current ->
            current + offerToDelivery(offer)
        }
    }

    fun rejectOffer() {
        repo.respondToOffer(accepted = false)
    }

    /** Chamado quando o motoboy marca a entrega como concluída na tela de rota. */
    fun markDeliveryDone(delivery: Delivery, orderId: String) {
        _activeDeliveries.update { current -> current.filterNot { it.id == delivery.id } }
        repo.completeCurrentDelivery(orderId)
    }

    private var nextLocalId = 1L
    private fun offerToDelivery(offer: CallOffer): Delivery {
        val order = offer.order
        val fullAddress = buildString {
            append(order.endereco)
            if (!order.numero.isNullOrBlank()) append(", ${order.numero}")
            if (!order.bairro.isNullOrBlank()) append(" - ${order.bairro}")
        }
        return Delivery(
            id = nextLocalId++,
            address = fullAddress,
            lat = 0.0, // geocodificado em seguida pela tela de rota (reaproveita GeocodingService)
            lng = 0.0,
            priority = Priority.ALTA,
            value = order.valor,
            status = DeliveryStatus.PENDENTE,
            order = 1,
            trackingCode = order.orderId
        )
    }
}
