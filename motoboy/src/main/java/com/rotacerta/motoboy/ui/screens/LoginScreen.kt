package com.rotacerta.motoboy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Login simples: o motoboy digita o ID que o restaurante já cadastrou para ele
 * (ver requisito "Login utilizando o ID cadastrado pelo restaurante"). Não há
 * senha nem servidor de autenticação — a validação de fato acontece quando o
 * restaurante aceita a conexão (o ID precisa bater com um entregador cadastrado).
 */
@Composable
fun LoginScreen(onLogin: (motoboyId: String, nome: String) -> Unit) {
    var motoboyId by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.TwoWheeler, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
        Text("RotaCerta Motoboy", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Entre com o ID que o restaurante cadastrou pra você",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
        )

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Seu nome") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = motoboyId,
            onValueChange = { motoboyId = it },
            label = { Text("ID cadastrado pelo restaurante") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        Button(
            onClick = { onLogin(motoboyId.trim(), nome.trim()) },
            enabled = motoboyId.isNotBlank() && nome.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar")
        }
    }
}
