package com.rotacerta.restaurant.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rotacerta.restaurant.RestaurantApp
import com.rotacerta.restaurant.data.AppDatabase
import com.rotacerta.restaurant.data.RestauranteConfigRepository
import kotlinx.coroutines.runBlocking

/**
 * Mantém o servidor WebSocket + anúncio NSD rodando em primeiro plano, mesmo com
 * o app em segundo plano ou a tela apagada — essencial pra não perder chamadas
 * de motoboys enquanto o restaurante usa o celular pra outra coisa.
 */
class LocalServerService : Service() {

    private lateinit var server: LocalDispatchServer
    private lateinit var nsdAdvertiser: NsdAdvertiser
    private lateinit var configRepo: RestauranteConfigRepository
    private lateinit var db: AppDatabase

    companion object {
        private const val CHANNEL_ID = "rotacerta_server_channel"
        private const val NOTIFICATION_ID = 1001
        private const val DEFAULT_PORT = 8087
    }

    private var restauranteNomeCache: String = "RotaCerta"

    override fun onCreate() {
        super.onCreate()
        val app = application as RestaurantApp
        configRepo = RestauranteConfigRepository(applicationContext)
        db = AppDatabase.getInstance(applicationContext)

        server = LocalDispatchServer(
            queue = app.dispatchQueue,
            restauranteNomeProvider = { restauranteNomeCache },
            port = DEFAULT_PORT
        )
        // Só aceita conexão de motoboy cujo ID esteja cadastrado (e ativo) no Room.
        server.isMotoboyAllowed = { motoboyId ->
            val entregador = db.entregadorDao().findById(motoboyId)
            if (entregador != null && entregador.ativo) entregador.nome else null
        }
        nsdAdvertiser = NsdAdvertiser(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Nome do restaurante lido de forma síncrona ao subir o serviço (o provider
        // do servidor precisa ser não-suspend). Atualizar o cadastro depois exige
        // reiniciar o servidor — aceitável, já que raramente muda em pleno expediente.
        restauranteNomeCache = runBlocking { configRepo.current().nome.ifBlank { "RotaCerta" } }

        startForeground(NOTIFICATION_ID, buildNotification())
        if (!server.isRunning) {
            server.start()
            nsdAdvertiser.start(serviceName = restauranteNomeCache, port = DEFAULT_PORT)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        nsdAdvertiser.stop()
        server.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servidor de despacho",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Mantém a comunicação com os motoboys ativa" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RotaCerta Restaurante")
            .setContentText("Servidor local ativo — recebendo motoboys")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()
    }
}
