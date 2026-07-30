package com.rotacerta.restaurant

import android.app.Application
import com.rotacerta.core.protocol.DispatchQueue
import com.rotacerta.restaurant.data.AppDatabase
import com.rotacerta.restaurant.data.PedidoHistorico
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RestaurantApp : Application() {

    // Fila de despacho única para todo o app — vive enquanto o processo existir.
    // O servidor (LocalServerService) e as telas (ViewModels) compartilham essa
    // mesma instância: o servidor escreve nela, a UI apenas observa o StateFlow.
    val dispatchQueue = DispatchQueue()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)

        // Toda vez que um motoboy conclui uma entrega, registra no histórico
        // automaticamente — a UI (HistoricoScreen) só observa o Room.
        dispatchQueue.onDeliveryCompleted = { motoboyId, order ->
            appScope.launch {
                val nome = database.entregadorDao().findById(motoboyId)?.nome
                database.pedidoHistoricoDao().upsert(
                    PedidoHistorico(
                        orderId = order.orderId,
                        endereco = order.endereco,
                        valor = order.valor,
                        motoboyId = motoboyId,
                        motoboyNome = nome,
                        criadoEm = order.criadoEm,
                        concluidoEm = System.currentTimeMillis(),
                        status = "ENTREGUE"
                    )
                )
            }
        }
    }

    companion object {
        lateinit var instance: RestaurantApp
            private set
    }
}
