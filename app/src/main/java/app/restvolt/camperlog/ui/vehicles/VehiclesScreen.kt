package app.restvolt.camperlog.ui.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDeleteResult
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard

/** Fahrzeugverwaltung: Liste mit Anlegen, Bearbeiten, Setzen als aktuell und Löschen. */
@Composable
fun VehiclesScreen(viewModel: VehiclesViewModel, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<Vehicle?>(null) }
    val defaultVehicleName = stringResource(R.string.vehicle_default_name)
    fun displayNameOf(vehicle: Vehicle) = vehicle.name.ifBlank { defaultVehicleName }

    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.vehicles_manage_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.vehicles_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.vehicles.isEmpty()) {
                item { EmptyHint(stringResource(R.string.vehicles_empty)) }
            } else {
                items(state.vehicles, key = Vehicle::id) { vehicle ->
                    VehicleRow(
                        vehicle = vehicle,
                        isCurrent = vehicle.id == state.currentVehicleId,
                        onClick = { onEdit(vehicle.id) },
                        onSetCurrent = { viewModel.setCurrent(vehicle.id) },
                        onDelete = { confirmDelete = vehicle },
                    )
                }
            }
        }
    }

    confirmDelete?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.vehicles_delete_confirm_title)) },
            text = { Text(stringResource(R.string.vehicles_delete_confirm_text, displayNameOf(vehicle))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.delete(vehicle)
                }) { Text(stringResource(R.string.vehicles_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    var refused by remember { mutableStateOf<VehiclesMessage.DeleteRefused?>(null) }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is VehiclesMessage.Deleted -> snackbar.showSnackbar(
                resources.getString(R.string.vehicles_deleted, displayNameOf(current.vehicle)),
                withDismissAction = true,
            )
            is VehiclesMessage.DeleteRefused -> refused = current
            VehiclesMessage.Failed -> snackbar.showSnackbar(resources.getString(R.string.vehicles_delete_failed), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }

    refused?.let { result ->
        AlertDialog(
            onDismissRequest = { refused = null },
            title = { Text(stringResource(R.string.vehicles_delete_refused_title)) },
            text = {
                val text = if (result.reason == VehicleDeleteResult.HAS_TOURS_OR_STATIONS) {
                    stringResource(R.string.vehicles_delete_refused_tours_text, displayNameOf(result.vehicle))
                } else {
                    stringResource(R.string.vehicles_delete_refused_last_text)
                }
                Text(text)
            },
            confirmButton = { TextButton(onClick = { refused = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun VehicleRow(
    vehicle: Vehicle,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onSetCurrent: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val name = vehicle.name.ifBlank { stringResource(R.string.vehicle_default_name) }
    val supportingText = listOf(vehicle.licensePlate, vehicle.model).filter(String::isNotBlank).joinToString(" · ")
    val menuDescription = stringResource(R.string.vehicles_row_actions, name)

    SectionCard(
        Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = stringResource(R.string.vehicle_edit_action), onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (supportingText.isNotBlank()) {
                    Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vehicle.isSold) {
                    Text(
                        stringResource(R.string.vehicles_sold_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (isCurrent) {
                Text(
                    stringResource(R.string.vehicles_current_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = menuDescription)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (!isCurrent) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vehicles_set_current)) },
                        onClick = {
                            menuExpanded = false
                            onSetCurrent()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vehicles_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}
