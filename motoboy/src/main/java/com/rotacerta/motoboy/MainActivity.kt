package com.rotacerta.motoboy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rotacerta.core.theme.RotaCertaTheme
import com.rotacerta.motoboy.client.MotoboyConnectionService
import com.rotacerta.motoboy.ui.screens.HomeScreen
import com.rotacerta.motoboy.ui.screens.IncomingCallDialog
import com.rotacerta.motoboy.ui.screens.LoginScreen
import com.rotacerta.motoboy.viewmodel.MotoboyViewModel

class MotoboyViewModelFactory(private val app: MotoboyApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MotoboyViewModel(app.repository) as T
    }
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* segue mesmo se alguma permissão for negada; pedimos de novo quando necessário */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissionsIfNeeded()

        val app = application as MotoboyApp
        val factory = MotoboyViewModelFactory(app)

        setContent {
            RotaCertaTheme {
                val viewModel: MotoboyViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsState()

                if (!state.loggedIn) {
                    LoginScreen(onLogin = { id, nome ->
                        viewModel.login(id, nome)
                        startConnectionService()
                    })
                } else {
                    HomeScreen(
                        nome = state.nome,
                        connectionState = state.connectionState,
                        status = state.status,
                        activeDeliveries = state.activeDeliveries,
                        onToggleAvailability = viewModel::toggleAvailability,
                        onDeliveryDone = { delivery ->
                            viewModel.markDeliveryDone(delivery, orderId = delivery.trackingCode)
                        }
                    )

                    state.incomingOffer?.let { offer ->
                        IncomingCallDialog(
                            offer = offer,
                            onAccept = viewModel::acceptOffer,
                            onReject = viewModel::rejectOffer
                        )
                    }
                }
            }
        }

        // Se já tinha login salvo de uma sessão anterior, reconecta automaticamente.
        startConnectionService()
    }

    private fun startConnectionService() {
        val intent = Intent(this, MotoboyConnectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        val toCheck = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            toCheck += Manifest.permission.POST_NOTIFICATIONS
        }
        toCheck.forEach { perm ->
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed += perm
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
