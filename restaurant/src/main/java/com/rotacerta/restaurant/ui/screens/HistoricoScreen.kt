package com.rotacerta.restaurant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rotacerta.restaurant.data.PedidoHistorico
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoricoScreen(historico: List<PedidoHistorico>) {
    val totalGanho = historico.sumOf { it.valor }
    val df = androidx.compose.runtime.remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Histórico", style = MaterialTheme.typography.headlineSmall)

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResumoCard("${historico.size}", "Entregas", Modifier.weight(1f))
            ResumoCard(fmtBRL(totalGanho), "Total pago", Modifier.weight(1f))
        }

        if (historico.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma entrega concluída ainda", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historico, key = { it.orderId }) { pedido ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(pedido.endereco, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(fmtBRL(pedido.valor), style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "${pedido.motoboyNome ?: "—"} · ${pedido.concluidoEm?.let { df.format(Date(it)) } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumoCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun fmtBRL(value: Double): String =
    "R$ " + String.format(Locale("pt", "BR"), "%.2f", value)
