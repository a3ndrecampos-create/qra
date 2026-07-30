package com.rotacerta.core.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Fila de disponibilidade dos motoboys + controle das chamadas em andamento.
 *
 * Regras (conforme especificação):
 * - O primeiro motoboy disponível recebe a chamada.
 * - Se não aceitar dentro do tempo configurado, passa automaticamente pro próximo.
 * - Ao concluir a entrega, o motoboy volta para o final da fila.
 * - Nunca duas chamadas simultâneas para o mesmo motoboy, nem a mesma chamada
 *   ativa para dois motoboys ao mesmo tempo.
 *
 * Esta classe é pura lógica de domínio (sem I/O de rede), pensada para ser usada
 * pelo servidor embutido no app do Restaurante. Um [lock] garante que operações de
 * fila sejam atômicas mesmo com múltiplas conexões WebSocket concorrentes.
 */
class DispatchQueue {

    private val lock = ReentrantLock()

    // Fila FIFO apenas com os IDs dos motoboys disponíveis, na ordem de disponibilidade.
    private val availableQueue = ArrayDeque<String>()

    // Todos os motoboys conhecidos (conectados ou não), por ID.
    private val motoboys = ConcurrentHashMap<String, MotoboyInfo>()

    // Pedidos aguardando despacho (ainda não foi feita nenhuma oferta, ou a fila estava vazia).
    private val pendingOrders = ArrayDeque<OrderPayload>()

    // Chamada ativa no momento, por motoboyId -> callId. Um motoboy só pode ter 1 chamada ativa.
    private val activeCallsByMotoboy = ConcurrentHashMap<String, String>()
    // callId -> callState (para achar rápido de qual pedido/motoboy se trata).
    private val activeCallState = ConcurrentHashMap<String, CallState>()
    // Pedido que cada motoboy está entregando agora (entre aceitar e concluir),
    // necessário pra registrar o histórico corretamente ao concluir.
    private val activeDeliveryByMotoboy = ConcurrentHashMap<String, OrderPayload>()

    private val _snapshot = MutableStateFlow(MotoboyListSnapshot(emptyList()))
    val snapshot: StateFlow<MotoboyListSnapshot> = _snapshot

    data class CallState(
        val callId: String,
        val order: OrderPayload,
        val motoboyId: String,
        val offeredAt: Long = System.currentTimeMillis()
    )

    /** Callback usado pelo servidor para efetivamente enviar a oferta via WebSocket. */
    var onOfferReady: ((motoboyId: String, offer: CallOffer) -> Unit)? = null
    /** Callback para avisar quem perdeu a chamada / ela expirou. */
    var onCallCancelled: ((motoboyId: String, cancelled: CallCancelled) -> Unit)? = null
    /** Callback disparado sempre que a lista de motoboys muda (para o servidor fazer broadcast). */
    var onListChanged: ((MotoboyListSnapshot) -> Unit)? = null
    /** Callback disparado quando uma entrega é concluída, com o pedido correspondente (para histórico). */
    var onDeliveryCompleted: ((motoboyId: String, order: OrderPayload) -> Unit)? = null

    // ---------------------------------------------------------------------
    // Cadastro / conexão de motoboys
    // ---------------------------------------------------------------------

    fun registerMotoboy(motoboyId: String, nome: String): MotoboyInfo = lock.withLock {
        val existing = motoboys[motoboyId]
        val info = MotoboyInfo(
            motoboyId = motoboyId,
            nome = nome,
            status = MotoboyStatus.DISPONIVEL,
            conectadoEm = System.currentTimeMillis(),
            ultimaAtualizacao = System.currentTimeMillis()
        )
        motoboys[motoboyId] = info
        if (existing?.status != MotoboyStatus.DISPONIVEL && !availableQueue.contains(motoboyId)) {
            availableQueue.addLast(motoboyId)
        }
        publishAndDrain()
        info
    }

    fun setOffline(motoboyId: String) = lock.withLock {
        val info = motoboys[motoboyId] ?: return@withLock
        motoboys[motoboyId] = info.copy(status = MotoboyStatus.OFFLINE, ultimaAtualizacao = System.currentTimeMillis())
        availableQueue.remove(motoboyId)
        publish()
    }

    /** Mudança manual de status pelo motoboy (ex: Pausar / Voltar a ficar disponível). */
    fun updateStatus(motoboyId: String, novoStatus: MotoboyStatus): Boolean = lock.withLock {
        val info = motoboys[motoboyId] ?: return@withLock false
        // Não permite se mudar manualmente para DISPONIVEL enquanto está em uma entrega ativa.
        if (info.status == MotoboyStatus.EM_ENTREGA && novoStatus == MotoboyStatus.DISPONIVEL) {
            return@withLock false
        }
        motoboys[motoboyId] = info.copy(status = novoStatus, ultimaAtualizacao = System.currentTimeMillis())
        when (novoStatus) {
            MotoboyStatus.DISPONIVEL -> {
                if (!availableQueue.contains(motoboyId)) availableQueue.addLast(motoboyId)
            }
            else -> availableQueue.remove(motoboyId)
        }
        publishAndDrain()
        true
    }

    // ---------------------------------------------------------------------
    // Pedidos / despacho
    // ---------------------------------------------------------------------

    /** Restaurante cria um pedido e pede pra despachar (botão "Chamar Entregador"). */
    fun enqueueOrder(order: OrderPayload) = lock.withLock {
        pendingOrders.addLast(order)
        drainPendingOrders()
    }

    /** Tenta casar pedidos pendentes com motoboys disponíveis, um a um. */
    private fun drainPendingOrders() {
        while (pendingOrders.isNotEmpty() && availableQueue.isNotEmpty()) {
            val motoboyId = availableQueue.removeFirst()
            val info = motoboys[motoboyId]
            // Segurança: se por algum motivo esse motoboy já não está mais disponível
            // de verdade (ex.: desconectou), pula pro próximo sem consumir o pedido.
            if (info == null || info.status != MotoboyStatus.DISPONIVEL) continue

            val order = pendingOrders.removeFirst()
            offerToMotoboy(motoboyId, order)
        }
    }

    private fun offerToMotoboy(motoboyId: String, order: OrderPayload, timeoutSeconds: Int = 30) {
        val callId = UUID.randomUUID().toString()
        activeCallsByMotoboy[motoboyId] = callId
        activeCallState[callId] = CallState(callId, order, motoboyId)
        val offer = CallOffer(callId = callId, order = order, timeoutSeconds = timeoutSeconds)
        onOfferReady?.invoke(motoboyId, offer)
    }

    /** Chamado pelo servidor quando o timeout de uma oferta estoura sem resposta. */
    fun expireCall(callId: String) = lock.withLock {
        val state = activeCallState[callId] ?: return@withLock
        activeCallsByMotoboy.remove(state.motoboyId)
        activeCallState.remove(callId)

        onCallCancelled?.invoke(state.motoboyId, CallCancelled(callId, "EXPIRADA"))

        // Motoboy que não respondeu continua disponível, mas vai para o final da fila
        // (evita ficar sempre recebendo a próxima chamada em looping caso esteja com
        // o app fechado/sem sinal).
        if (motoboys[state.motoboyId]?.status == MotoboyStatus.DISPONIVEL) {
            availableQueue.remove(state.motoboyId)
            availableQueue.addLast(state.motoboyId)
        }

        // Repassa o pedido para o próximo da fila.
        pendingOrders.addFirst(state.order)
        drainPendingOrders()
        publish()
    }

    /** Resposta do motoboy a uma oferta (aceitar/recusar). */
    fun respondToCall(callId: String, motoboyId: String, accepted: Boolean): Boolean = lock.withLock {
        val state = activeCallState[callId] ?: return@withLock false
        if (state.motoboyId != motoboyId) return@withLock false // resposta de quem não foi chamado

        activeCallsByMotoboy.remove(motoboyId)
        activeCallState.remove(callId)

        if (accepted) {
            val info = motoboys[motoboyId]
            if (info != null) {
                motoboys[motoboyId] = info.copy(status = MotoboyStatus.EM_ENTREGA, ultimaAtualizacao = System.currentTimeMillis())
            }
            activeDeliveryByMotoboy[motoboyId] = state.order
            availableQueue.remove(motoboyId) // garantia extra
            publish()
            true
        } else {
            onCallCancelled?.invoke(motoboyId, CallCancelled(callId, "RECUSADA"))
            // Recusou: continua disponível, mas vai pro final da fila.
            if (motoboys[motoboyId]?.status == MotoboyStatus.DISPONIVEL) {
                availableQueue.remove(motoboyId)
                availableQueue.addLast(motoboyId)
            }
            pendingOrders.addFirst(state.order)
            drainPendingOrders()
            publish()
            true
        }
    }

    /** Motoboy concluiu a entrega: volta a ficar disponível, no final da fila. */
    fun completeDelivery(motoboyId: String): Boolean = lock.withLock {
        val info = motoboys[motoboyId] ?: return@withLock false
        val order = activeDeliveryByMotoboy.remove(motoboyId)
        motoboys[motoboyId] = info.copy(status = MotoboyStatus.DISPONIVEL, ultimaAtualizacao = System.currentTimeMillis())
        availableQueue.remove(motoboyId)
        availableQueue.addLast(motoboyId)
        publishAndDrain()
        if (order != null) onDeliveryCompleted?.invoke(motoboyId, order)
        true
    }

    fun currentSnapshotList(): List<MotoboyInfo> = motoboys.values.sortedBy { it.nome }

    private fun publish() {
        val snap = MotoboyListSnapshot(currentSnapshotList())
        _snapshot.update { snap }
        onListChanged?.invoke(snap)
    }

    private fun publishAndDrain() {
        drainPendingOrders()
        publish()
    }
}
