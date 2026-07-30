package com.rotacerta.restaurant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rotacerta.core.protocol.MotoboyInfo
import com.rotacerta.core.protocol.MotoboyStatus
import com.rotacerta.restaurant.data.Entregador

@Composable
fun EntregadoresScreen(
    entregadores: List<Entregador>,
    motoboysAoVivo: List<MotoboyInfo>,
    onCadastrar: (motoboyId: String, nome: String) -> Unit,
    onRemover: (Entregador) -> Unit,
    onAlternarAtivo: (Entregador) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var removerAlvo by remember { mutableStateOf<Entregador?>(null) }
    val statusPorId = remember(motoboysAoVivo) { motoboysAoVivo.associateBy { it.motoboyId } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Entregadores", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Novo")
            }
        }

        if (entregadores.isEmpty()) {
            Text(
                "Nenhum entregador cadastrado ainda. Cadastre o ID que cada motoboy vai usar para logar no app dele.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entregadores, key = { it.motoboyId }) { entregador ->
                    EntregadorRow(
                        entregador = entregador,
                        statusAoVivo = statusPorId[entregador.motoboyId]?.status,
                        onRemover = { removerAlvo = entregador },
                        onAlternarAtivo = { onAlternarAtivo(entregador) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEntregadorDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { id, nome ->
                onCadastrar(id, nome)
                showAddDialog = false
            }
        )
    }

    removerAlvo?.let { entregador ->
        AlertDialog(
            onDismissRequest = { removerAlvo = null },
            title = { Text("Remover entregador?") },
            text = { Text("${entregador.nome} não vai conseguir mais logar com esse ID.") },
            confirmButton = {
                Button(onClick = { onRemover(entregador); removerAlvo = null }) { Text("Remover") }
            },
            dismissButton = {
                OutlinedButton(onClick = { removerAlvo = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EntregadorRow(
    entregador: Entregador,
    statusAoVivo: MotoboyStatus?,
    onRemover: () -> Unit,
    onAlternarAtivo: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusAoVivo)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(entregador.nome, style = MaterialTheme.typography.titleMedium)
                    Text("ID: ${entregador.motoboyId}", style = MaterialTheme.typography.bodySmall)
                    Text(statusLabel(statusAoVivo, entregador.ativo), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = entregador.ativo, onCheckedChange = { onAlternarAtivo() })
                IconButton(onClick = onRemover) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover")
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: MotoboyStatus?) {
    val color = when (status) {
        MotoboyStatus.DISPONIVEL -> Color(0xFF4CAF50)
        MotoboyStatus.EM_ENTREGA -> Color(0xFFFFA726)
        MotoboyStatus.PAUSADO -> Color(0xFFBDBDBD)
        MotoboyStatus.OFFLINE, null -> Color(0xFF757575)
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun statusLabel(status: MotoboyStatus?, ativo: Boolean): String {
    if (!ativo) return "Inativo"
    return when (status) {
        MotoboyStatus.DISPONIVEL -> "Disponível"
        MotoboyStatus.EM_ENTREGA -> "Em entrega"
        MotoboyStatus.PAUSADO -> "Pausado"
        MotoboyStatus.OFFLINE, null -> "Desconectado"
    }
}

@Composable
private fun AddEntregadorDialog(onDismiss: () -> Unit, onConfirm: (id: String, nome: String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo entregador") },
        text = {
            Column {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                )
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("ID (usado pelo motoboy pra logar)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = id.isNotBlank() && nome.isNotBlank(),
                onClick = { onConfirm(id.trim(), nome.trim()) }
            ) { Text("Cadastrar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Cancelar")
            }
        }
    )
}
