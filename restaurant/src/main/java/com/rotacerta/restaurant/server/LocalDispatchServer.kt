package com.rotacerta.restaurant.server

import com.rotacerta.core.protocol.CallCancelled
import com.rotacerta.core.protocol.CallOffer
import com.rotacerta.core.protocol.CallResponse
import com.rotacerta.core.protocol.DeliveryCompletedRequest
import com.rotacerta.core.protocol.DispatchQueue
import com.rotacerta.core.protocol.Envelope
import com.rotacerta.core.protocol.ErrorPayload
import com.rotacerta.core.protocol.MessageType
import com.rotacerta.core.protocol.RegisterAck
import com.rotacerta.core.protocol.RegisterRequest
import com.rotacerta.core.protocol.StatusUpdateRequest
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Servidor local (Wi-Fi) que roda dentro do app do Restaurante.
 * Não depende de internet nem de nenhum serviço externo: os motoboys se conectam
 * diretamente pelo IP do celular do restaurante na mesma rede Wi-Fi.
 *
 * Toda a lógica de fila/regras de negócio fica na [DispatchQueue] (módulo core,
 * sem I/O). Esta classe só cuida do transporte: aceitar conexões, traduzir
 * mensagens JSON <-> chamadas da fila, e mandar as respostas de volta.
 */
class LocalDispatchServer(
    val queue: DispatchQueue,
    private val restauranteNomeProvider: () -> String,
    private val port: Int = 8087
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // motoboyId -> sessão WebSocket ativa
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    // callId -> job do timeout, para poder cancelar se a resposta chegar a tempo
    private val timeoutJobs = ConcurrentHashMap<String, Job>()

    private var engine: ApplicationEngine? = null

    val isRunning: Boolean get() = engine != null
    val listenPort: Int get() = port

    /**
     * Validação contra o cadastro de entregadores do restaurante (Room).
     * Retorna o nome cadastrado se o ID for válido, ou null se não for.
     * Se ficar null (não configurado), qualquer ID é aceito — usado só em testes.
     */
    var isMotoboyAllowed: (suspend (motoboyId: String) -> String?)? = null

    fun start() {
        if (engine != null) return

        queue.onOfferReady = { motoboyId, offer -> handleOfferReady(motoboyId, offer) }
        queue.onCallCancelled = { motoboyId, cancelled -> handleCallCancelled(motoboyId, cancelled) }

        engine = embeddedServer(CIO, port = port) {
            install(WebSockets)
            install(ContentNegotiation) { json(json) }

            routing {
                webSocket("/ws") {
                    var currentMotoboyId: String? = null
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val envelope = runCatching { json.decodeFromString<Envelope>(frame.readText()) }.getOrNull()
                            if (envelope == null) {
                                sendEnvelope(MessageType.ERROR, ErrorPayload("Mensagem inválida"))
                                continue
                            }

                            when (envelope.type) {
                                MessageType.REGISTER -> {
                                    val req = json.decodeFromString<RegisterRequest>(envelope.data)
                                    val validator = isMotoboyAllowed
                                    val nomeCadastrado = validator?.invoke(req.motoboyId)

                                    if (validator != null && nomeCadastrado == null) {
                                        sendEnvelope(
                                            MessageType.REGISTER_ACK,
                                            RegisterAck(
                                                success = false,
                                                motoboyId = req.motoboyId,
                                                restauranteNome = restauranteNomeProvider(),
                                                message = "ID não cadastrado neste restaurante"
                                            )
                                        )
                                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "ID não cadastrado"))
                                        return@webSocket
                                    }

                                    currentMotoboyId = req.motoboyId
                                    sessions[req.motoboyId] = this
                                    queue.registerMotoboy(req.motoboyId, nomeCadastrado ?: req.nome)
                                    sendEnvelope(
                                        MessageType.REGISTER_ACK,
                                        RegisterAck(
                                            success = true,
                                            motoboyId = req.motoboyId,
                                            restauranteNome = restauranteNomeProvider()
                                        )
                                    )
                                }

                                MessageType.STATUS_UPDATE -> {
                                    val req = json.decodeFromString<StatusUpdateRequest>(envelope.data)
                                    val ok = queue.updateStatus(req.motoboyId, req.status)
                                    if (!ok) {
                                        sendEnvelope(MessageType.ERROR, ErrorPayload("Não foi possível mudar o status agora (entrega em andamento?)"))
                                    }
                                }

                                MessageType.CALL_RESPONSE -> {
                                    val req = json.decodeFromString<CallResponse>(envelope.data)
                                    timeoutJobs.remove(req.callId)?.cancel()
                                    queue.respondToCall(req.callId, req.motoboyId, req.accepted)
                                }

                                MessageType.DELIVERY_COMPLETED -> {
                                    val req = json.decodeFromString<DeliveryCompletedRequest>(envelope.data)
                                    queue.completeDelivery(req.motoboyId)
                                }

                                else -> sendEnvelope(MessageType.ERROR, ErrorPayload("Tipo de mensagem desconhecido: ${envelope.type}"))
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        // conexão encerrada normalmente pelo cliente
                    } finally {
                        currentMotoboyId?.let { id ->
                            sessions.remove(id)
                            queue.setOffline(id)
                        }
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(500, 1000)
        engine = null
        sessions.clear()
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
    }

    // -------------------------------------------------------------------
    // Callbacks vindos da DispatchQueue -> envio via rede
    // -------------------------------------------------------------------

    private fun handleOfferReady(motoboyId: String, offer: CallOffer) {
        val session = sessions[motoboyId]
        scope.launch {
            session?.sendEnvelope(MessageType.CALL_OFFER, offer)
        }
        // Timeout: se não responder a tempo, a própria DispatchQueue já sabe
        // repassar pro próximo (expireCall é seguro de chamar mesmo se a
        // resposta já tiver chegado — nesse caso ela só ignora).
        val job = scope.launch {
            delay(offer.timeoutSeconds * 1000L)
            queue.expireCall(offer.callId)
        }
        timeoutJobs[offer.callId] = job
    }

    private fun handleCallCancelled(motoboyId: String, cancelled: CallCancelled) {
        val session = sessions[motoboyId] ?: return
        scope.launch {
            session.sendEnvelope(MessageType.CALL_CANCELLED, cancelled)
        }
    }

    /** Força a desconexão de um motoboy (ex: removido do cadastro pelo restaurante). */
    fun disconnect(motoboyId: String) {
        scope.launch {
            sessions[motoboyId]?.close(CloseReason(CloseReason.Codes.NORMAL, "Desconectado pelo restaurante"))
            sessions.remove(motoboyId)
            queue.setOffline(motoboyId)
        }
    }

    private suspend inline fun <reified T> DefaultWebSocketServerSession.sendEnvelope(type: String, payload: T) {
        val data = json.encodeToString(payload)
        send(Frame.Text(json.encodeToString(Envelope(type, data))))
    }
}
