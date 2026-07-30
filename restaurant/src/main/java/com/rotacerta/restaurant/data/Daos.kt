package com.rotacerta.restaurant.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntregadorDao {
    @Query("SELECT * FROM entregadores ORDER BY nome ASC")
    fun observeAll(): Flow<List<Entregador>>

    @Query("SELECT * FROM entregadores WHERE motoboyId = :motoboyId LIMIT 1")
    suspend fun findById(motoboyId: String): Entregador?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entregador: Entregador)

    @Update
    suspend fun update(entregador: Entregador)

    @Delete
    suspend fun delete(entregador: Entregador)
}

@Dao
interface PedidoHistoricoDao {
    @Query("SELECT * FROM pedidos_historico ORDER BY criadoEm DESC")
    fun observeAll(): Flow<List<PedidoHistorico>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pedido: PedidoHistorico)

    @Query("DELETE FROM pedidos_historico")
    suspend fun clearAll()
}
