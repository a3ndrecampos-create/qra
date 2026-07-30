package com.rotacerta.motoboy.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rotacerta.core.protocol.CallOffer
import kotlinx.coroutines.delay

/**
 * Chamada recebida do restaurante: mostra o endereço e valor, com contagem
 * regressiva visual até o tempo configurado (offer.timeoutSeconds) — depois
 * disso o servidor já repassa pro próximo motoboy sozinho, então aqui só
 * fechamos o diálogo (chamado via onExpired) se o tempo acabar sem resposta.
 */
@Composable
fun IncomingCallDialog(
    offer: CallOffer,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var secondsLeft by remember(offer.callId) { mutableIntStateOf(offer.timeoutSeconds) }
    var progress by remember(offer.callId) { mutableFloatStateOf(1f) }

    LaunchedEffect(offer.callId) {
        val total = offer.timeoutSeconds
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
            progress = secondsLeft.toFloat() / total.toFloat()
        }
    }

    AlertDialog(
        onDismissRequest = { /* não deixa fechar sem responder */ },
        title = { Text("Nova entrega!") },
        text = {
            Column {
                Text(offer.order.endereco, modifier = Modifier.padding(bottom = 4.dp))
                if (!offer.order.bairro.isNullOrBlank()) {
                    Text(offer.order.bairro!!, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("Valor: R$ %.2f".format(offer.order.valor), modifier = Modifier.padding(bottom = 12.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("$secondsLeft s para responder", modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            Button(onClick = onAccept) { Text("Aceitar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onReject) { Text("Recusar") }
        }
    )
}
