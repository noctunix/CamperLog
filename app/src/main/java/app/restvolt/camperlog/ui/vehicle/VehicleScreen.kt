package app.restvolt.camperlog.ui.vehicle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.ui.TabTopBar
import app.restvolt.camperlog.ui.VehicleSwitcherTitle
import app.restvolt.camperlog.ui.VehicleSwitcherViewModel

/** Fahrzeug-Reiter; das Datenblatt mit Erinnerungen und Reparaturen folgt in einer späteren Phase. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    viewModel: VehicleSwitcherViewModel,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVehicles: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TabTopBar(
                titleContent = {
                    VehicleSwitcherTitle(
                        vehicles = state.vehicles,
                        currentVehicleId = state.currentVehicleId,
                        title = stringResource(R.string.vehicle_title),
                        onSelectVehicle = viewModel::onSelectVehicle,
                        onManageVehicles = onOpenVehicles,
                    )
                },
                onOpenData = onOpenData,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding))
    }
}
