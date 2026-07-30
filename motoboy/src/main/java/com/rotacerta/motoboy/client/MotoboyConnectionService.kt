package com.rotacerta.motoboy.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rotacerta.motoboy.MotoboyApp

/**
 * Mantém a conexão com o servidor do restaurante viva mesmo com o app em segundo
 * plano ou tela apagada — pra não perder nenhuma chamada de entrega.
 */
class MotoboyConnectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "rotacerta_motoboy_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        (application as MotoboyApp).repository.startAutoConnect()
        return START_STICKY
    }

    override fun onDestroy() {
        (application as MotoboyApp).repository.stopAutoConnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conexão com o restaurante",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Mantém a conexão ativa para receber chamadas de entrega" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RotaCerta Motoboy")
            .setContentText("Conectado — pronto para receber chamadas")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()
    }
}
