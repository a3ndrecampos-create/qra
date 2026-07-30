package com.rotacerta.restaurant.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rotacerta.restaurant.data.RestauranteConfig

@Composable
fun CadastroRestauranteScreen(
    config: RestauranteConfig,
    onSalvar: (nome: String, endereco: String) -> Unit
) {
    var nome by remember { mutableStateOf(config.nome) }
    var endereco by remember { mutableStateOf(config.endereco) }

    // Sincroniza os campos se o config carregar depois da primeira composição
    LaunchedEffect(config) {
        if (nome.isBlank()) nome = config.nome
        if (endereco.isBlank()) endereco = config.endereco
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Dados do restaurante", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Usado para identificar seu restaurante na rede e para os motoboys reconhecerem quem os chamou.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Nome do restaurante") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = endereco,
            onValueChange = { endereco = it },
            label = { Text("Endereço") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        )

        Button(
            onClick = { onSalvar(nome.trim(), endereco.trim()) },
            enabled = nome.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar")
        }
    }
}
