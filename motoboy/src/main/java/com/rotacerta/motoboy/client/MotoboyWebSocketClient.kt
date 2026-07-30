package com.rotacerta.motoboy.client

import com.rotacerta.core.protocol.CallCancelled
import com.rotacerta.core.protocol.CallOffer
import com.rotacerta.core.protocol.CallResponse
import com.rotacerta.core.protocol.DeliveryCompletedRequest
import com.rotacerta.core.protocol.Envelope
import com.rotacerta.core.protocol.ErrorPayload
import com.rotacerta.core.protocol.MessageType
import com.rotacerta.core.protocol.MotoboyStatus
import com.rotacerta.core.protocol.RegisterAck
import com.rotacerta.core.protocol.RegisterRequest
import com.rotacerta.core.protocol.StatusUpdateRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import java.util.concurrent.TimeUnit

enum class ConnectionState { DESCONECTADO, CONECTANDO, CONECTADO, ERRO }

/** Eventos assíncronos vindos do servidor, consumidos pelo ViewModel/UI. */
sealed class ServerEvent {
    data class RegisterAckReceived(val ack: RegisterAck) : ServerEvent()
    data class CallOfferReceived(val offer: CallOffer) : ServerEvent()
    data class CallCancelledReceived(val cancelled: CallCancelled) : ServerEvent()
    data class ErrorReceived(val message: String) : ServerEvent()
}

/**
 * Conexão WebSocket com o servidor local do restaurante (rede Wi-Fi, sem internet).
 * Fica responsável só pelo transporte: enviar/receber [Envelope]s e expor eventos
 * de forma tipada pro resto do app do Motoboy.
 */
class MotoboyWebSocketClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS) // mantém a conexão viva na rede Wi-Fi local
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DESCONECTADO)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ServerEvent> = _events

    fun connect(host: String, port: Int, motoboyId: String, nome: String) {
        disconnect()
        _connectionState.value = ConnectionState.CONECTANDO

        val request = Request.Builder().url("ws://$host:$port/ws").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONECTADO
                sendEnvelope(MessageType.REGISTER, RegisterRequest(motoboyId = motoboyId, nome = nome))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DESCONECTADO
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERRO
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Desconectado pelo app")
        webSocket = null
        _connectionState.value = ConnectionState.DESCONECTADO
    }

    fun updateStatus(motoboyId: String, status: MotoboyStatus) {
        sendEnvelope(MessageType.STATUS_UPDATE, StatusUpdateRequest(motoboyId, status))
    }

    fun respondToCall(callId: String, motoboyId: String, accepted: Boolean) {
        sendEnvelope(MessageType.CALL_RESPONSE, CallResponse(callId, motoboyId, accepted))
    }

    fun notifyDeliveryCompleted(motoboyId: String, orderId: String) {
        sendEnvelope(MessageType.DELIVERY_COMPLETED, DeliveryCompletedRequest(motoboyId, orderId))
    }

    private fun handleIncoming(text: String) {
        val envelope = runCatching { json.decodeFromString<Envelope>(text) }.getOrNull() ?: return
        when (envelope.type) {
            MessageType.REGISTER_ACK -> {
                val ack = json.decodeFromString<RegisterAck>(envelope.data)
                _events.tryEmit(ServerEvent.RegisterAckReceived(ack))
            }
            MessageType.CALL_OFFER -> {
                val offer = json.decodeFromString<CallOffer>(envelope.data)
                _events.tryEmit(ServerEvent.CallOfferReceived(offer))
            }
            MessageType.CALL_CANCELLED -> {
                val cancelled = json.decodeFromString<CallCancelled>(envelope.data)
                _events.tryEmit(ServerEvent.CallCancelledReceived(cancelled))
            }
            MessageType.ERROR -> {
                val error = json.decodeFromString<ErrorPayload>(envelope.data)
                _events.tryEmit(ServerEvent.ErrorReceived(error.message))
            }
        }
    }

    private inline fun <reified T> sendEnvelope(type: String, payload: T) {
        val data = json.encodeToString(payload)
        webSocket?.send(json.encodeToString(Envelope(type, data)))
    }
}
