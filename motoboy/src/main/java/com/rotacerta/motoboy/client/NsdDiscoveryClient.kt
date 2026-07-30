package com.rotacerta.motoboy.client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Endereço resolvido do servidor do restaurante encontrado na rede. */
data class DiscoveredServer(val host: String, val port: Int, val name: String)

/**
 * Procura automaticamente pelo servidor do restaurante na rede Wi-Fi local via NSD,
 * o mesmo tipo de serviço (_rotacerta._tcp.) anunciado pelo NsdAdvertiser do app
 * do Restaurante. Isso cumpre o requisito de "conexão automática ao servidor local",
 * sem o motoboy precisar digitar IP nenhum.
 */
class NsdDiscoveryClient(private val context: Context) {

    companion object {
        private const val SERVICE_TYPE = "_rotacerta._tcp."
        private const val TAG = "NsdDiscoveryClient"
    }

    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    /**
     * Emite cada servidor encontrado e resolvido na rede (normalmente só existe um
     * restaurante por rede Wi-Fi). Fica escutando até o coletor cancelar — ex: quando
     * a conexão WebSocket for estabelecida com sucesso.
     */
    fun discover() = callbackFlow<DiscoveredServer> {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Falha ao resolver serviço (erro $errorCode)")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                trySend(DiscoveredServer(host = host, port = serviceInfo.port, name = serviceInfo.serviceName))
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "Buscando restaurante na rede...")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE || serviceInfo.serviceType.startsWith(SERVICE_TYPE)) {
                    runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Restaurante saiu da rede: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Falha ao iniciar busca (erro $errorCode)")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }
}
