package app.restvolt.camperlog.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.formatAh
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.formatBar
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.formatKg
import app.restvolt.camperlog.domain.formatLitres
import app.restvolt.camperlog.domain.formatMetres
import app.restvolt.camperlog.domain.formatPower
import app.restvolt.camperlog.domain.formatWp
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.LabeledValue
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.TabTopBar
import app.restvolt.camperlog.ui.VehicleSwitcherTitle
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.vehicleDisplayName
import java.util.Locale

/** Fahrzeug-Reiter: Datenblatt des aktuellen Fahrzeugs. Erinnerungen und Reparaturen folgen in Phase 3b. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    viewModel: VehicleViewModel,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVehicles: () -> Unit,
    onEditVehicle: (Long) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vehicle = state.currentVehicle

    Scaffold(
        topBar = {
            TabTopBar(
                titleContent = {
                    VehicleSwitcherTitle(
                        vehicles = state.vehicles,
                        currentVehicleId = state.currentVehicleId,
                        title = vehicle?.let { vehicleDisplayName(it) } ?: stringResource(R.string.vehicle_title),
                        onSelectVehicle = viewModel::onSelectVehicle,
                        onManageVehicles = onOpenVehicles,
                    )
                },
                extraActions = {
                    if (vehicle != null) {
                        IconButton(onClick = { onEditVehicle(vehicle.id) }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.vehicle_edit_action))
                        }
                    }
                },
                overflowMenu = { VehicleOverflowMenu(onManageVehicles = onOpenVehicles) },
                onOpenData = onOpenData,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (vehicle != null) {
            VehicleSheet(
                vehicle = vehicle,
                onAddDetails = { onEditVehicle(vehicle.id) },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VehicleSheet(vehicle: Vehicle, onAddDetails: () -> Unit, modifier: Modifier) {
    val locale = currentLocale()
    val soldLine = vehicle.saleDate?.let { stringResource(R.string.vehicle_sold_on, formatDate(it, locale)) }
    val purchaseSale = purchaseSaleRows(vehicle, locale)
    val sections = listOf(
        stringResource(R.string.section_general) to generalRows(vehicle, locale),
        stringResource(R.string.section_insurance_tax) to insuranceTaxRows(vehicle, locale),
        stringResource(R.string.section_dimensions_weight) to dimensionsRows(vehicle, locale),
        stringResource(R.string.section_engine) to engineRows(vehicle, locale),
        stringResource(R.string.section_tires) to tireRows(vehicle, locale),
        stringResource(R.string.section_tanks) to tankRows(vehicle, locale),
        stringResource(R.string.section_energy) to energyRows(vehicle, locale),
    )
    val hasAnyValue = sections.any { it.second.isNotEmpty() } ||
        purchaseSale.isNotEmpty() || soldLine != null || vehicle.notes.isNotBlank()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!hasAnyValue) {
            EmptyHint(stringResource(R.string.vehicle_sheet_empty_hint))
            Button(
                onClick = onAddDetails,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.vehicle_sheet_add_details)) }
        } else {
            if (sections[0].second.isNotEmpty()) {
                SectionCard {
                    SectionHeading(sections[0].first)
                    sections[0].second.forEach { (label, value) -> LabeledValue(label, value) }
                }
            }
            if (purchaseSale.isNotEmpty() || soldLine != null) {
                SectionCard {
                    SectionHeading(stringResource(R.string.section_purchase_sale))
                    soldLine?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    purchaseSale.forEach { (label, value) -> LabeledValue(label, value) }
                }
            }
            sections.drop(1).forEach { (title, rows) ->
                if (rows.isNotEmpty()) {
                    SectionCard {
                        SectionHeading(title)
                        rows.forEach { (label, value) -> LabeledValue(label, value) }
                    }
                }
            }
            if (vehicle.notes.isNotBlank()) {
                SectionCard {
                    SectionHeading(stringResource(R.string.field_notes))
                    Text(vehicle.notes, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun generalRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    if (vehicle.licensePlate.isNotBlank()) add(stringResource(R.string.field_license_plate) to vehicle.licensePlate)
    if (vehicle.manufacturer.isNotBlank()) add(stringResource(R.string.field_manufacturer) to vehicle.manufacturer)
    if (vehicle.model.isNotBlank()) add(stringResource(R.string.field_model) to vehicle.model)
    if (vehicle.vin.isNotBlank()) add(stringResource(R.string.field_vin) to vehicle.vin)
    vehicle.firstRegistration?.let { add(stringResource(R.string.field_first_registration) to formatDate(it, locale)) }
}

@Composable
private fun purchaseSaleRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.purchaseDate?.let { add(stringResource(R.string.field_purchase_date) to formatDate(it, locale)) }
    vehicle.purchasePrice?.let { add(stringResource(R.string.field_purchase_price) to formatAmount(it.minor, it.currency, locale)) }
    vehicle.purchaseOdometerKm?.let { add(stringResource(R.string.field_purchase_odometer) to stringResource(R.string.distance_km, it)) }
    vehicle.salePrice?.let { add(stringResource(R.string.field_sale_price) to formatAmount(it.minor, it.currency, locale)) }
}

@Composable
private fun insuranceTaxRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    if (vehicle.insurer.isNotBlank()) add(stringResource(R.string.field_insurer) to vehicle.insurer)
    if (vehicle.insurancePolicyNumber.isNotBlank()) {
        add(stringResource(R.string.field_insurance_policy_number) to vehicle.insurancePolicyNumber)
    }
    vehicle.insurancePremiumPerYear?.let { add(stringResource(R.string.field_insurance_premium) to formatAmount(it.minor, it.currency, locale)) }
    vehicle.vehicleTaxPerYear?.let { add(stringResource(R.string.field_vehicle_tax) to formatAmount(it.minor, it.currency, locale)) }
}

@Composable
private fun dimensionsRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.lengthCm?.let { add(stringResource(R.string.field_length) to formatMetres(it, locale)) }
    vehicle.widthCm?.let { add(stringResource(R.string.field_width) to formatMetres(it, locale)) }
    vehicle.heightCm?.let { add(stringResource(R.string.field_height) to formatMetres(it, locale)) }
    vehicle.grossWeightKg?.let { add(stringResource(R.string.field_gross_weight) to formatKg(it, locale)) }
}

@Composable
private fun engineRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.powerKw?.let { add(stringResource(R.string.field_power) to formatPower(it, locale)) }
}

@Composable
private fun tireRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    if (vehicle.tireSize.isNotBlank()) add(stringResource(R.string.field_tire_size) to vehicle.tireSize)
    vehicle.tirePressureFrontMbar?.let { add(stringResource(R.string.field_tire_pressure_front) to formatBar(it, locale)) }
    vehicle.tirePressureRearMbar?.let { add(stringResource(R.string.field_tire_pressure_rear) to formatBar(it, locale)) }
}

@Composable
private fun tankRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.fuelTankDl?.let { add(stringResource(R.string.field_fuel_tank) to formatLitres(it, locale)) }
    vehicle.adBlueTankDl?.let { add(stringResource(R.string.field_ad_blue_tank) to formatLitres(it, locale)) }
    vehicle.freshWaterTankDl?.let { add(stringResource(R.string.field_fresh_water_tank) to formatLitres(it, locale)) }
    vehicle.greyWaterTankDl?.let { add(stringResource(R.string.field_grey_water_tank) to formatLitres(it, locale)) }
    vehicle.boilerDl?.let { add(stringResource(R.string.field_boiler) to formatLitres(it, locale)) }
    vehicle.cassetteDl?.let { add(stringResource(R.string.field_cassette) to formatLitres(it, locale)) }
}

@Composable
private fun energyRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.batteryCapacityAh?.let { add(stringResource(R.string.field_battery_capacity) to formatAh(it, locale)) }
    vehicle.solarPowerWp?.let { add(stringResource(R.string.field_solar_power) to formatWp(it, locale)) }
}

@Composable
private fun VehicleOverflowMenu(onManageVehicles: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vehicle_switcher_manage)) },
                onClick = {
                    expanded = false
                    onManageVehicles()
                },
            )
        }
    }
}
