package app.restvolt.camperlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Vehicle

/**
 * Titel der oberen Leiste auf den drei Hauptreitern: ein einfacher Titel mit nur einem Fahrzeug,
 * sonst ein Fahrzeugwechsler. [showAllVehiclesOption] blendet den Eintrag „Alle Fahrzeuge" ein
 * (nur auf dem Touren-Reiter); er ändert das aktuelle Fahrzeug nicht, sondern nur die Ansicht.
 */
@Composable
fun VehicleSwitcherTitle(
    vehicles: List<Vehicle>,
    currentVehicleId: Long,
    title: String,
    showAllVehiclesOption: Boolean = false,
    allVehiclesSelected: Boolean = false,
    onSelectVehicle: (Long) -> Unit = {},
    onSelectAllVehicles: () -> Unit = {},
    onManageVehicles: () -> Unit = {},
) {
    if (vehicles.size <= 1) {
        Text(title, modifier = Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val currentName = if (allVehiclesSelected) {
        stringResource(R.string.vehicle_switcher_all_vehicles)
    } else {
        vehicleDisplayName(vehicles.firstOrNull { it.id == currentVehicleId })
    }
    val description = stringResource(R.string.vehicle_switcher_description, currentName)

    Row(
        modifier = Modifier
            .clickable { expanded = true }
            .semantics { contentDescription = description }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(currentName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (showAllVehiclesOption) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vehicle_switcher_all_vehicles)) },
                leadingIcon = checkIcon(allVehiclesSelected),
                onClick = {
                    expanded = false
                    onSelectAllVehicles()
                },
            )
        }
        vehicles.forEach { vehicle ->
            DropdownMenuItem(
                text = { Text(vehicleMenuLabel(vehicle)) },
                leadingIcon = checkIcon(!allVehiclesSelected && vehicle.id == currentVehicleId),
                onClick = {
                    expanded = false
                    onSelectVehicle(vehicle.id)
                },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.vehicle_switcher_manage)) },
            onClick = {
                expanded = false
                onManageVehicles()
            },
        )
    }
}

private fun checkIcon(show: Boolean): (@Composable () -> Unit)? =
    if (show) {
        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
    } else {
        null
    }
