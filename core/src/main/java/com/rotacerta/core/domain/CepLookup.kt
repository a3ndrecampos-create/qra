package com.rotacerta.core.domain

import com.rotacerta.core.network.CepResponse
import com.rotacerta.core.network.NetworkModule

/**
 * Busca o endereço a partir do CEP via ViaCEP. Retorna null se o CEP for
 * inválido, não existir, ou a busca falhar (sem internet, etc — quem chama
 * decide como avisar o usuário).
 */
suspend fun buscarEnderecoPorCep(cep: String): CepResponse? {
    val digits = cep.filter { it.isDigit() }
    if (digits.length != 8) return null
    return runCatching { NetworkModule.viaCep.lookup(digits) }
        .getOrNull()
        ?.takeIf { it.erro != true }
}
