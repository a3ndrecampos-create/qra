package com.rotacerta.motoboy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rotacerta.core.components.DeliveryCard
import com.rotacerta.core.data.Delivery
import com.rotacerta.core.domain.NavApp
import com.rotacerta.core.domain.openNavigation
import com.rotacerta.core.protocol.MotoboyStatus
import com.rotacerta.motoboy.client.ConnectionState

@Composable
fun HomeScreen(
    nome: String,
    connectionState: ConnectionState,
    status: MotoboyStatus,
    activeDeliveries: List<Delivery>,
    onToggleAvailability: () -> Unit,
    onDeliveryDone: (Delivery) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Olá, $nome", style = MaterialTheme.typography.headlineSmall)
        Spacer()
        ConnectionBadge(connectionState)
        Spacer()
        StatusCard(status = status, onToggleAvailability = onToggleAvailability)
        Spacer()

        if (activeDeliveries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (status == MotoboyStatus.DISPONIVEL) "Aguardando chamadas..." else "Você está pausado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text("Entrega em andamento", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activeDeliveries, key = { it.id }) { delivery ->
                    // Mesmo componente e mesma lógica de navegação do sistema de rotas
                    // já existente — não foi reimplementado nada aqui.
                    DeliveryCard(
                        delivery = delivery,
                        onDelivered = { onDeliveryDone(delivery) },
                        onRemove = { },
                        onNavigate = { openNavigation(context, delivery.address, NavApp.GOOGLE) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.CONECTADO -> "Conectado ao restaurante" to MaterialTheme.colorScheme.primary
        ConnectionState.CONECTANDO -> "Procurando restaurante na rede..." to MaterialTheme.colorScheme.tertiary
        ConnectionState.DESCONECTADO -> "Desconectado" to MaterialTheme.colorScheme.error
        ConnectionState.ERRO -> "Erro de conexão — tentando novamente" to MaterialTheme.colorScheme.error
    }
    Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun StatusCard(status: MotoboyStatus, onToggleAvailability: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Text(statusLabel(status), style = MaterialTheme.typography.titleMedium)
            }
            if (status != MotoboyStatus.EM_ENTREGA) {
                Button(onClick = onToggleAvailability) {
                    Text(if (status == MotoboyStatus.PAUSADO) "Ficar disponível" else "Pausar")
                }
            }
        }
    }
}

private fun statusLabel(status: MotoboyStatus): String = when (status) {
    MotoboyStatus.DISPONIVEL -> "Disponível"
    MotoboyStatus.EM_ENTREGA -> "Em entrega"
    MotoboyStatus.PAUSADO -> "Pausado"
    MotoboyStatus.OFFLINE -> "Offline"
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
}
