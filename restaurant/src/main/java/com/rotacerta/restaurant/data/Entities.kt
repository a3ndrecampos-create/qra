package com.rotacerta.restaurant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entregador próprio cadastrado pelo restaurante. O [motoboyId] é o ID que o
 * motoboy vai digitar no login do app dele — precisa bater com este cadastro
 * pra conseguir se conectar ao servidor local.
 */
@Entity(tableName = "entregadores")
data class Entregador(
    @PrimaryKey val motoboyId: String,
    val nome: String,
    val endereco: String = "",
    val cadastradoEm: Long = System.currentTimeMillis(),
    val ativo: Boolean = true
)

/** Histórico de entregas concluídas (ou canceladas), pra tela de Histórico. */
@Entity(tableName = "pedidos_historico")
data class PedidoHistorico(
    @PrimaryKey val orderId: String,
    val endereco: String,
    val valor: Double,
    val motoboyId: String?,
    val motoboyNome: String?,
    val criadoEm: Long,
    val concluidoEm: Long? = null,
    val status: String // "ENTREGUE" | "CANCELADO"
)
