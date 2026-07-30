package com.rotacerta.restaurant.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Anuncia o servidor do restaurante na rede local via NSD (mDNS/Bonjour), para que
 * o app do Motoboy encontre automaticamente o restaurante — sem precisar digitar IP.
 *
 * Tipo de serviço: "_rotacerta._tcp." — os apps do Motoboy escutam por esse mesmo tipo.
 */
class NsdAdvertiser(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = "_rotacerta._tcp."
        private const val TAG = "NsdAdvertiser"
    }

    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var registered = false

    fun start(serviceName: String, port: Int) {
        if (registered) return

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registered = true
                Log.i(TAG, "Serviço anunciado na rede: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                registered = false
                Log.e(TAG, "Falha ao anunciar serviço (erro $errorCode)")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                registered = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Falha ao remover anúncio (erro $errorCode)")
            }
        }

        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
        }
        registrationListener = null
        registered = false
    }
}
