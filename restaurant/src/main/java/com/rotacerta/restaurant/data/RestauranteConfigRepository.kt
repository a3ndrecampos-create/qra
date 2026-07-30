package com.rotacerta.restaurant.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.restaurantDataStore by preferencesDataStore(name = "restaurant_config")

data class RestauranteConfig(
    val nome: String = "",
    val endereco: String = ""
) {
    val cadastrado: Boolean get() = nome.isNotBlank()
}

class RestauranteConfigRepository(private val context: Context) {

    private object Keys {
        val NOME = stringPreferencesKey("nome")
        val ENDERECO = stringPreferencesKey("endereco")
    }

    val configFlow = context.restaurantDataStore.data.map { prefs ->
        RestauranteConfig(
            nome = prefs[Keys.NOME].orEmpty(),
            endereco = prefs[Keys.ENDERECO].orEmpty()
        )
    }

    suspend fun current(): RestauranteConfig = configFlow.first()

    suspend fun save(nome: String, endereco: String) {
        context.restaurantDataStore.edit { prefs ->
            prefs[Keys.NOME] = nome
            prefs[Keys.ENDERECO] = endereco
        }
    }
}
