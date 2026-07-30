package com.rotacerta.restaurant.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rotacerta.core.domain.buscarEnderecoPorCep
import com.rotacerta.restaurant.data.RestauranteConfig
import kotlinx.coroutines.launch

@Composable
fun CadastroRestauranteScreen(
    config: RestauranteConfig,
    onSalvar: (nome: String, endereco: String) -> Unit
) {
    var nome by remember { mutableStateOf(config.nome) }
    var cep by remember { mutableStateOf("") }
    var endereco by remember { mutableStateOf(config.endereco) }
    var buscandoCep by remember { mutableStateOf(false) }
    var cepNaoEncontrado by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

        // Busca por CEP — preenche o endereço automaticamente.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            OutlinedTextField(
                value = cep,
                onValueChange = { cep = it.filter { c -> c.isDigit() || c == '-' }.take(9); cepNaoEncontrado = false },
                label = { Text("CEP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    if (buscandoCep) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            if (cep.filter { it.isDigit() }.length == 8) {
                                cepNaoEncontrado = false
                                buscandoCep = true
                                scope.launch {
                                    val resultado = buscarEnderecoPorCep(cep)
                                    buscandoCep = false
                                    if (resultado != null) {
                                        val partes = listOfNotNull(
                                            resultado.logradouro?.ifBlank { null },
                                            resultado.bairro?.ifBlank { null },
                                            resultado.localidade?.ifBlank { null },
                                            resultado.uf?.ifBlank { null }
                                        )
                                        endereco = partes.joinToString(", ")
                                    } else {
                                        cepNaoEncontrado = true
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar CEP")
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        if (cepNaoEncontrado) {
            Text(
                "CEP não encontrado — preencha o endereço manualmente",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = endereco,
            onValueChange = { endereco = it },
            label = { Text("Endereço") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp)
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
