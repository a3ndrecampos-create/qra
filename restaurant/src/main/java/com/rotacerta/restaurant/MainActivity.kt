package com.rotacerta.restaurant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rotacerta.core.protocol.MotoboyStatus
import com.rotacerta.core.theme.RotaCertaTheme
import com.rotacerta.restaurant.server.LocalServerService
import com.rotacerta.restaurant.ui.screens.CadastroRestauranteScreen
import com.rotacerta.restaurant.ui.screens.EntregadoresScreen
import com.rotacerta.restaurant.ui.screens.HistoricoScreen
import com.rotacerta.restaurant.ui.screens.NovoPedidoScreen
import com.rotacerta.restaurant.viewmodel.RestaurantViewModel
import com.rotacerta.restaurant.viewmodel.RestaurantViewModelFactory

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Pedido", Icons.Filled.DeliveryDining),
    Tab("Entregadores", Icons.Filled.TwoWheeler),
    Tab("Histórico", Icons.Filled.History),
    Tab("Restaurante", Icons.Filled.Storefront)
)

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* segue mesmo se negado; a notificação do serviço só fica invisível */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        startServerService()

        val app = application as RestaurantApp
        val factory = RestaurantViewModelFactory(app)

        setContent {
            RotaCertaTheme {
                val viewModel: RestaurantViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsState()
                var selectedTab by remember { mutableIntStateOf(0) }

                Surface(color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                tabs.forEachIndexed { index, tab ->
                                    NavigationBarItem(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.padding(padding)) {
                            when (selectedTab) {
                                0 -> NovoPedidoScreen(
                                    motoboysDisponiveis = state.motoboysAoVivo.count { it.status == MotoboyStatus.DISPONIVEL },
                                    onChamarEntregador = viewModel::chamarEntregador
                                )
                                1 -> EntregadoresScreen(
                                    entregadores = state.entregadores,
                                    motoboysAoVivo = state.motoboysAoVivo,
                                    onCadastrar = viewModel::cadastrarEntregador,
                                    onRemover = viewModel::removerEntregador,
                                    onAlternarAtivo = viewModel::alternarAtivo
                                )
                                2 -> HistoricoScreen(historico = state.historico)
                                3 -> CadastroRestauranteScreen(
                                    config = state.config,
                                    onSalvar = viewModel::salvarCadastroRestaurante
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startServerService() {
        val intent = Intent(this, LocalServerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(perm))
            }
        }
    }
}
