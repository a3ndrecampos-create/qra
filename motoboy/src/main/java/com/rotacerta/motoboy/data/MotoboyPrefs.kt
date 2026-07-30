package com.rotacerta.motoboy.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "motoboy_prefs")

data class MotoboyLogin(val motoboyId: String, val nome: String)

/** Guarda o ID/nome do motoboy já cadastrado pelo restaurante, pra não pedir login toda vez. */
class MotoboyPrefs(private val context: Context) {

    private object Keys {
        val MOTOBOY_ID = stringPreferencesKey("motoboy_id")
        val NOME = stringPreferencesKey("nome")
    }

    val loginFlow = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.MOTOBOY_ID]
        val nome = prefs[Keys.NOME]
        if (id.isNullOrBlank() || nome.isNullOrBlank()) null else MotoboyLogin(id, nome)
    }

    suspend fun currentLogin(): MotoboyLogin? = loginFlow.first()

    suspend fun saveLogin(motoboyId: String, nome: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MOTOBOY_ID] = motoboyId
            prefs[Keys.NOME] = nome
        }
    }

    suspend fun clearLogin() {
        context.dataStore.edit { it.clear() }
    }
}
