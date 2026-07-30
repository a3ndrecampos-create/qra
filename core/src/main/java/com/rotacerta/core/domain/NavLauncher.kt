package com.rotacerta.core.domain

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.net.URLEncoder

/** Qual app de navegação usar para abrir o trajeto até a entrega. */
enum class NavApp { GOOGLE, WAZE }

/**
 * Abre o app de navegação (Google Maps ou Waze) com o endereço de destino.
 * Extraído do RotaScreen original do RotaCerta, para ser reaproveitado tanto
 * pelo app do Motoboy quanto por qualquer outra tela que precise navegar.
 */
fun openNavigation(context: Context, address: String, navApp: NavApp) {
    val enderecoCodificado = URLEncoder.encode(address, "UTF-8")
    val url = if (navApp == NavApp.WAZE)
        "https://waze.com/ul?q=$enderecoCodificado&navigate=yes"
    else
        "https://www.google.com/maps/dir/?api=1&destination=$enderecoCodificado&travelmode=driving"
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
