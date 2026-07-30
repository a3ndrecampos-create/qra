package com.rotacerta.restaurant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rotacerta.core.protocol.MotoboyInfo
import com.rotacerta.core.protocol.OrderPayload
import com.rotacerta.restaurant.RestaurantApp
import com.rotacerta.restaurant.data.Entregador
import com.rotacerta.restaurant.data.PedidoHistorico
import com.rotacerta.restaurant.data.RestauranteConfig
import com.rotacerta.restaurant.data.RestauranteConfigRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class RestaurantUiState(
    val config: RestauranteConfig = RestauranteConfig(),
    val entregadores: List<Entregador> = emptyList(),
    val motoboysAoVivo: List<MotoboyInfo> = emptyList(),
    val historico: List<PedidoHistorico> = emptyList()
)

class RestaurantViewModel(private val app: RestaurantApp) : ViewModel() {

    private val configRepo = RestauranteConfigRepository(app)
    private val entregadorDao = app.database.entregadorDao()
    private val historicoDao = app.database.pedidoHistoricoDao()

    val uiState: StateFlow<RestaurantUiState> = combine(
        configRepo.configFlow,
        entregadorDao.observeAll(),
        app.dispatchQueue.snapshot.map { it.motoboys },
        historicoDao.observeAll()
    ) { config, entregadores, motoboysAoVivo, historico ->
        RestaurantUiState(config, entregadores, motoboysAoVivo, historico)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RestaurantUiState()
    )

    fun salvarCadastroRestaurante(nome: String, endereco: String) {
        viewModelScope.launch { configRepo.save(nome, endereco) }
    }

    fun cadastrarEntregador(motoboyId: String, nome: String) {
        viewModelScope.launch {
            entregadorDao.upsert(Entregador(motoboyId = motoboyId.trim(), nome = nome.trim()))
        }
    }

    fun removerEntregador(entregador: Entregador) {
        viewModelScope.launch {
            entregadorDao.delete(entregador)
            app.dispatchQueue.setOffline(entregador.motoboyId)
        }
    }

    fun alternarAtivo(entregador: Entregador) {
        viewModelScope.launch {
            entregadorDao.update(entregador.copy(ativo = !entregador.ativo))
            if (entregador.ativo) app.dispatchQueue.setOffline(entregador.motoboyId)
        }
    }

    /** Botão "Chamar Entregador": cria o pedido e manda pra fila de despacho. */
    fun chamarEntregador(endereco: String, numero: String?, bairro: String?, valor: Double, observacoes: String?) {
        val order = OrderPayload(
            orderId = UUID.randomUUID().toString(),
            endereco = endereco,
            numero = numero,
            bairro = bairro,
            referencia = observacoes,
            valor = valor
        )
        app.dispatchQueue.enqueueOrder(order)
    }

    fun limparHistorico() {
        viewModelScope.launch { historicoDao.clearAll() }
    }
}

class RestaurantViewModelFactory(private val app: RestaurantApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RestaurantViewModel(app) as T
}
