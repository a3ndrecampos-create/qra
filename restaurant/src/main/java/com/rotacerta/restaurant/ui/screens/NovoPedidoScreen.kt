package com.rotacerta.restaurant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
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
import com.rotacerta.core.protocol.PedidoEmAndamento
import com.rotacerta.core.protocol.PedidoStatus
import com.rotacerta.core.scanner.CepScannerDialog
import kotlinx.coroutines.launch

@Composable
fun NovoPedidoScreen(
    motoboysDisponiveis: Int,
    pedidosEmAndamento: List<PedidoEmAndamento>,
    onChamarEntregador: (endereco: String, numero: String?, bairro: String?, valor: Double, observacoes: String?) -> Unit
) {
    var cep by remember { mutableStateOf("") }
    var endereco by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var bairro by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var observacoes by remember { mutableStateOf("") }
    var confirmado by remember { mutableStateOf(false) }
    var buscandoCep by remember { mutableStateOf(false) }
    var cepNaoEncontrado by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun buscarCep(valorCep: String) {
        cepNaoEncontrado = false
        buscandoCep = true
        scope.launch {
            val resultado = buscarEnderecoPorCep(valorCep)
            buscandoCep = false
            if (resultado != null) {
                endereco = resultado.logradouro.orEmpty()
                bairro = resultado.bairro.orEmpty()
            } else {
                cepNaoEncontrado = true
            }
        }
    }

    LaunchedEffect(confirmado) {
        if (confirmado) {
            kotlinx.coroutines.delay(2500)
            confirmado = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Novo pedido", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (motoboysDisponiveis > 0)
                "$motoboysDisponiveis entregador(es) disponível(is) agora"
            else
                "Nenhum entregador disponível agora — o pedido entra na fila e é chamado assim que alguém ficar livre",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // Busca por CEP (digitado ou escaneado pela câmera) — preenche endereço e bairro sozinho.
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
                        IconButton(onClick = { if (cep.filter { it.isDigit() }.length == 8) buscarCep(cep) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar CEP")
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { showScanner = true }) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Escanear CEP na etiqueta")
            }
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
            label = { Text("Endereço de entrega") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
        )
        Row(modifier = Modifier.padding(bottom = 12.dp)) {
            OutlinedTextField(
                value = numero,
                onValueChange = { numero = it },
                label = { Text("Número") },
                modifier = Modifier.weight(1f)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                value = bairro,
                onValueChange = { bairro = it },
                label = { Text("Bairro") },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = valor,
            onValueChange = { valor = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            label = { Text("Valor da entrega (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = observacoes,
            onValueChange = { observacoes = it },
            label = { Text("Observações (opcional)") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        )

        Button(
            onClick = {
                val valorDouble = valor.replace(",", ".").toDoubleOrNull() ?: 0.0
                onChamarEntregador(
                    endereco.trim(),
                    numero.trim().ifBlank { null },
                    bairro.trim().ifBlank { null },
                    valorDouble,
                    observacoes.trim().ifBlank { null }
                )
                cep = ""; endereco = ""; numero = ""; bairro = ""; valor = ""; observacoes = ""
                confirmado = true
            },
            enabled = endereco.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.DeliveryDining, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("Chamar Entregador")
        }

        if (confirmado) {
            Snackbar(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text("Pedido enviado para a fila de despacho!")
            }
        }

        if (pedidosEmAndamento.isNotEmpty()) {
            Text(
                "Pedidos aguardando entregador",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pedidosEmAndamento, key = { it.order.orderId }) { pedido ->
                    PedidoEmAndamentoCard(pedido)
                }
            }
        }
    }

    if (showScanner) {
        CepScannerDialog(
            onResult = { cepLido, numeroLido, _ ->
                cep = cepLido
                if (!numeroLido.isNullOrBlank()) numero = numeroLido
                showScanner = false
                buscarCep(cepLido)
            },
            onDismiss = { showScanner = false }
        )
    }
}

@Composable
private fun PedidoEmAndamentoCard(pedido: PedidoEmAndamento) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pedido.order.endereco, style = MaterialTheme.typography.titleSmall)
                Text(
                    "R$ %.2f".format(pedido.order.valor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PedidoStatusBadge(pedido)
        }
    }
}

@Composable
private fun PedidoStatusBadge(pedido: PedidoEmAndamento) {
    val label = when (pedido.status) {
        PedidoStatus.AGUARDANDO -> "Na fila"
        PedidoStatus.CHAMANDO -> "Chamando ${pedido.motoboyNome ?: ""}".trim()
        PedidoStatus.EM_ENTREGA -> "A caminho — ${pedido.motoboyNome ?: ""}".trim()
    }
    val color = when (pedido.status) {
        PedidoStatus.AGUARDANDO -> MaterialTheme.colorScheme.error
        PedidoStatus.CHAMANDO -> MaterialTheme.colorScheme.tertiary
        PedidoStatus.EM_ENTREGA -> MaterialTheme.colorScheme.primary
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
}
