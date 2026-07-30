package com.rotacerta.core.protocol

import kotlinx.serialization.Serializable

/**
 * Protocolo de comunicação em rede local entre o app do Restaurante (servidor)
 * e os apps dos Motoboys (clientes), via WebSocket em texto JSON.
 *
 * Padrão de envelope: toda mensagem trocada é um [Envelope], com um campo "type"
 * que identifica qual payload (serializado como JSON) está dentro de "data".
 * Isso evita depender de polimorfismo do kotlinx.serialization entre módulos,
 * mantendo o protocolo simples de versionar no futuro (ex: modo online).
 */
object MessageType {
    // Motoboy -> Restaurante
    const val REGISTER = "REGISTER"                 // motoboy se identifica ao conectar
    const val STATUS_UPDATE = "STATUS_UPDATE"        // motoboy muda seu status manualmente
    const val CALL_RESPONSE = "CALL_RESPONSE"        // motoboy aceita ou recusa uma chamada
    const val DELIVERY_COMPLETED = "DELIVERY_COMPLETED" // motoboy concluiu a entrega

    // Restaurante -> Motoboy
    const val REGISTER_ACK = "REGISTER_ACK"          // confirmação de conexão
    const val CALL_OFFER = "CALL_OFFER"              // oferta de nova entrega
    const val CALL_CANCELLED = "CALL_CANCELLED"      // chamada expirou ou foi repassada
    const val QUEUE_POSITION = "QUEUE_POSITION"      // posição atual na fila (informativo)

    // Bidirecional / broadcast
    const val MOTOBOY_LIST = "MOTOBOY_LIST"          // snapshot da lista de motoboys (restaurante -> UI)
    const val ERROR = "ERROR"
}

@Serializable
data class Envelope(
    val type: String,
    val data: String // JSON serializado do payload correspondente ao "type"
)

/** Status possíveis de um motoboy, conforme especificação. */
@Serializable
enum class MotoboyStatus {
    DISPONIVEL,
    EM_ENTREGA,
    PAUSADO,
    OFFLINE
}

/** Enviado pelo motoboy ao conectar no servidor do restaurante. */
@Serializable
data class RegisterRequest(
    val motoboyId: String,   // ID cadastrado previamente pelo restaurante
    val nome: String,
    val deviceLabel: String? = null // ex: modelo do aparelho, útil para depuração
)

@Serializable
data class RegisterAck(
    val success: Boolean,
    val motoboyId: String,
    val restauranteNome: String,
    val message: String? = null
)

/** Snapshot de um motoboy, usado tanto na lista do restaurante quanto internamente na fila. */
@Serializable
data class MotoboyInfo(
    val motoboyId: String,
    val nome: String,
    val status: MotoboyStatus,
    val conectadoEm: Long = System.currentTimeMillis(),
    val ultimaAtualizacao: Long = System.currentTimeMillis()
)

@Serializable
data class MotoboyListSnapshot(
    val motoboys: List<MotoboyInfo>
)

/** Motoboy avisando manualmente que quer pausar/voltar a ficar disponível. */
@Serializable
data class StatusUpdateRequest(
    val motoboyId: String,
    val status: MotoboyStatus
)

/** Pedido cadastrado pelo restaurante, que vira uma chamada para o motoboy. */
@Serializable
data class OrderPayload(
    val orderId: String,
    val endereco: String,
    val numero: String? = null,
    val bairro: String? = null,
    val referencia: String? = null,
    val valor: Double = 0.0,
    val observacoes: String? = null,
    val criadoEm: Long = System.currentTimeMillis()
)

/** Restaurante -> motoboy: oferta de chamada com prazo para responder. */
@Serializable
data class CallOffer(
    val callId: String,
    val order: OrderPayload,
    val timeoutSeconds: Int = 30
)

/** Motoboy -> restaurante: resposta a uma oferta. */
@Serializable
data class CallResponse(
    val callId: String,
    val motoboyId: String,
    val accepted: Boolean
)

/** Restaurante -> motoboy (broadcast só para quem perdeu a chamada, ou expirou). */
@Serializable
data class CallCancelled(
    val callId: String,
    val reason: String // "EXPIRADA" | "RECUSADA" | "ACEITA_POR_OUTRO"
)

/** Motoboy -> restaurante: avisa que concluiu a entrega e volta para o fim da fila. */
@Serializable
data class DeliveryCompletedRequest(
    val motoboyId: String,
    val orderId: String,
    val concluidoEm: Long = System.currentTimeMillis()
)

@Serializable
data class ErrorPayload(
    val message: String
)
