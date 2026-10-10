package app.restvolt.camperlog.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.EnergyType
import app.restvolt.camperlog.domain.MAX_AMOUNT_MINOR
import app.restvolt.camperlog.domain.TransmissionType
import app.restvolt.camperlog.domain.VehicleError
import app.restvolt.camperlog.domain.VehicleField
import app.restvolt.camperlog.domain.allowsAny
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.formatApproxPs
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.CollapsibleSection
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.DiscardChangesDialog
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.messageRes
import java.util.Currency
import java.util.Locale

/** Formular zum Anlegen und Bearbeiten eines Fahrzeugs. [onDone] verlässt es ohne, [onSaved] nach dem Speichern mit der gespeicherten id. */
@Composable
fun EditVehicleScreen(viewModel: EditVehicleViewModel, onDone: () -> Unit, onSaved: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.isSaved) {
        val savedId = state.savedVehicleId
        if (state.isSaved && savedId != null) onSaved(savedId)
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.vehicle_edit_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = state.isDirty && !state.isSaved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.vehicle_edit_title_new else R.string.vehicle_edit_title_existing),
                onBack = requestBack,
                actions = {
                    if (!state.notFound && !state.isLoading) {
                        TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
                    }
                },
            )
        },
        bottomBar = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.notFound -> EmptyHint(stringResource(R.string.vehicle_not_found), Modifier.padding(padding))
            else -> VehicleForm(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding(),
            )
        }
    }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeep = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onDone()
            },
        )
    }
}

@Composable
private fun VehicleForm(state: EditVehicleUiState, viewModel: EditVehicleViewModel, modifier: Modifier) {
    val input = state.input
    val change = viewModel::onInputChange
    val required = stringResource(R.string.edit_required)
    val focus = remember { VehicleField.entries.associateWith { FocusRequester() } }
    fun focusOf(field: VehicleField) = Modifier.focusRequester(focus.getValue(field))
    @Composable fun errorOf(field: VehicleField): String? = state.errors[field]?.messageRes(field)

    // Nach einem abgelehnten Speichern zum ersten fehlerhaften Feld springen; der Fokus scrollt es ins Bild.
    LaunchedEffect(state.rejectedSaves) {
        if (state.rejectedSaves > 0) {
            state.errors.keys.minByOrNull(VehicleField::ordinal)?.let { focus.getValue(it).requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            SectionHeading(stringResource(R.string.section_general))
            FormTextField(
                label = stringResource(R.string.field_name),
                value = input.name,
                onValueChange = { value -> change { it.copy(name = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = focusOf(VehicleField.NAME),
                placeholder = if (!state.isNew) stringResource(R.string.vehicle_default_name) else null,
                error = errorOf(VehicleField.NAME),
                hint = if (state.isNew) required else null,
            )
            FormTextField(
                label = stringResource(R.string.field_license_plate),
                value = input.licensePlate,
                onValueChange = { value -> change { it.copy(licensePlate = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            )
            FormTextField(
                label = stringResource(R.string.field_manufacturer),
                value = input.manufacturer,
                onValueChange = { value -> change { it.copy(manufacturer = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            FormTextField(
                label = stringResource(R.string.field_model),
                value = input.model,
                onValueChange = { value -> change { it.copy(model = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            FormTextField(
                label = stringResource(R.string.field_vin),
                value = input.vin,
                onValueChange = { value -> change { it.copy(vin = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            )
            DateField(
                stringResource(R.string.field_first_registration),
                input.firstRegistration,
                errorOf(VehicleField.FIRST_REGISTRATION),
                { date -> change { it.copy(firstRegistration = date) } },
                modifier = focusOf(VehicleField.FIRST_REGISTRATION),
            )
        }
        SectionCard {
            SectionHeading(stringResource(R.string.section_purchase_sale))
            DateField(
                stringResource(R.string.field_purchase_date),
                input.purchaseDate,
                null,
                { date -> change { it.copy(purchaseDate = date) } },
            )
            MoneyField(
                label = stringResource(R.string.field_purchase_price),
                amount = input.purchasePrice,
                currency = input.purchasePriceCurrency,
                error = state.errors[VehicleField.PURCHASE_PRICE],
                onAmountChange = { value -> change { it.copy(purchasePrice = value) } },
                onCurrencyChange = { value -> change { it.copy(purchasePriceCurrency = value) } },
                modifier = focusOf(VehicleField.PURCHASE_PRICE),
            )
            UnitField(
                label = stringResource(R.string.field_purchase_odometer),
                value = input.purchaseOdometerKm,
                unit = "km",
                keyboardType = KeyboardType.Number,
                error = errorOf(VehicleField.PURCHASE_ODOMETER_KM),
                modifier = focusOf(VehicleField.PURCHASE_ODOMETER_KM),
                onValueChange = { value -> change { it.copy(purchaseOdometerKm = value) } },
            )
            DateField(
                stringResource(R.string.field_sale_date),
                input.saleDate,
                errorOf(VehicleField.SALE_DATE),
                { date -> change { it.copy(saleDate = date) } },
                modifier = focusOf(VehicleField.SALE_DATE),
                minDate = input.purchaseDate,
            )
            MoneyField(
                label = stringResource(R.string.field_sale_price),
                amount = input.salePrice,
                currency = input.salePriceCurrency,
                error = state.errors[VehicleField.SALE_PRICE],
                onAmountChange = { value -> change { it.copy(salePrice = value) } },
                onCurrencyChange = { value -> change { it.copy(salePriceCurrency = value) } },
                modifier = focusOf(VehicleField.SALE_PRICE),
            )
            UnitField(
                label = stringResource(R.string.field_sale_odometer),
                value = input.saleOdometerKm,
                unit = "km",
                keyboardType = KeyboardType.Number,
                error = errorOf(VehicleField.SALE_ODOMETER_KM),
                modifier = focusOf(VehicleField.SALE_ODOMETER_KM),
                onValueChange = { value -> change { it.copy(saleOdometerKm = value) } },
            )
        }
        SectionCard {
            SectionHeading(stringResource(R.string.section_insurance_tax))
            FormTextField(
                label = stringResource(R.string.field_insurer),
                value = input.insurer,
                onValueChange = { value -> change { it.copy(insurer = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            FormTextField(
                label = stringResource(R.string.field_insurance_policy_number),
                value = input.insurancePolicyNumber,
                onValueChange = { value -> change { it.copy(insurancePolicyNumber = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            )
            MoneyField(
                label = stringResource(R.string.field_insurance_premium),
                amount = input.insurancePremiumPerYear,
                currency = input.insurancePremiumPerYearCurrency,
                error = state.errors[VehicleField.INSURANCE_PREMIUM_PER_YEAR],
                onAmountChange = { value -> change { it.copy(insurancePremiumPerYear = value) } },
                onCurrencyChange = { value -> change { it.copy(insurancePremiumPerYearCurrency = value) } },
                modifier = focusOf(VehicleField.INSURANCE_PREMIUM_PER_YEAR),
            )
            MoneyField(
                label = stringResource(R.string.field_vehicle_tax),
                amount = input.vehicleTaxPerYear,
                currency = input.vehicleTaxPerYearCurrency,
                error = state.errors[VehicleField.VEHICLE_TAX_PER_YEAR],
                onAmountChange = { value -> change { it.copy(vehicleTaxPerYear = value) } },
                onCurrencyChange = { value -> change { it.copy(vehicleTaxPerYearCurrency = value) } },
                modifier = focusOf(VehicleField.VEHICLE_TAX_PER_YEAR),
            )
        }
        SectionCard {
            SectionHeading(stringResource(R.string.section_breakdown_accident))
            FormTextField(
                label = stringResource(R.string.field_breakdown_provider),
                value = input.breakdownProvider,
                onValueChange = { value -> change { it.copy(breakdownProvider = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            FormTextField(
                label = stringResource(R.string.field_breakdown_membership_number),
                value = input.breakdownMembershipNumber,
                onValueChange = { value -> change { it.copy(breakdownMembershipNumber = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            )
            FormTextField(
                label = stringResource(R.string.field_breakdown_phone),
                value = input.breakdownPhone,
                onValueChange = { value -> change { it.copy(breakdownPhone = value) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = focusOf(VehicleField.BREAKDOWN_PHONE),
                error = errorOf(VehicleField.BREAKDOWN_PHONE),
                hint = stringResource(R.string.field_phone_hint),
            )
            FormTextField(
                label = stringResource(R.string.field_travel_protection_provider),
                value = input.travelProtectionProvider,
                onValueChange = { value -> change { it.copy(travelProtectionProvider = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            FormTextField(
                label = stringResource(R.string.field_travel_protection_contract_number),
                value = input.travelProtectionContractNumber,
                onValueChange = { value -> change { it.copy(travelProtectionContractNumber = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            )
            FormTextField(
                label = stringResource(R.string.field_travel_protection_phone),
                value = input.travelProtectionPhone,
                onValueChange = { value -> change { it.copy(travelProtectionPhone = value) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = focusOf(VehicleField.TRAVEL_PROTECTION_PHONE),
                error = errorOf(VehicleField.TRAVEL_PROTECTION_PHONE),
                hint = stringResource(R.string.field_phone_hint),
            )
            FormTextField(
                label = stringResource(R.string.field_insurer_claims_phone),
                value = input.insurerClaimsPhone,
                onValueChange = { value -> change { it.copy(insurerClaimsPhone = value) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = focusOf(VehicleField.INSURER_CLAIMS_PHONE),
                error = errorOf(VehicleField.INSURER_CLAIMS_PHONE),
                hint = stringResource(R.string.field_phone_hint),
            )
        }
        SectionCard {
            SectionHeading(stringResource(R.string.section_dimensions_weight))
            UnitField(
                stringResource(R.string.field_length), input.lengthCm, "cm", KeyboardType.Number,
                errorOf(VehicleField.LENGTH), focusOf(VehicleField.LENGTH),
            ) { value -> change { it.copy(lengthCm = value) } }
            UnitField(
                stringResource(R.string.field_width), input.widthCm, "cm", KeyboardType.Number,
                errorOf(VehicleField.WIDTH), focusOf(VehicleField.WIDTH),
            ) { value -> change { it.copy(widthCm = value) } }
            UnitField(
                stringResource(R.string.field_height), input.heightCm, "cm", KeyboardType.Number,
                errorOf(VehicleField.HEIGHT), focusOf(VehicleField.HEIGHT),
            ) { value -> change { it.copy(heightCm = value) } }
            UnitField(
                stringResource(R.string.field_gross_weight), input.grossWeightKg, "kg", KeyboardType.Number,
                errorOf(VehicleField.GROSS_WEIGHT_KG), focusOf(VehicleField.GROSS_WEIGHT_KG),
            ) { value -> change { it.copy(grossWeightKg = value) } }
            UnitField(
                stringResource(R.string.field_measured_empty_weight), input.measuredEmptyWeightKg, "kg", KeyboardType.Number,
                errorOf(VehicleField.MEASURED_EMPTY_WEIGHT_KG), focusOf(VehicleField.MEASURED_EMPTY_WEIGHT_KG),
            ) { value -> change { it.copy(measuredEmptyWeightKg = value) } }
        }
        SectionCard {
            SectionHeading(stringResource(R.string.section_engine))
            UnitField(
                stringResource(R.string.field_power), input.powerKw, "kW", KeyboardType.Number,
                errorOf(VehicleField.POWER_KW), focusOf(VehicleField.POWER_KW),
            ) { value -> change { it.copy(powerKw = value) } }
            PowerPsHint(input.powerKw)

            var engineDetailsExpanded by rememberSaveable {
                mutableStateOf(input.displacementCc.isNotBlank() || input.transmission != null)
            }
            val engineDetailsSummary = listOfNotNull(
                input.displacementCc.takeIf(String::isNotBlank)?.let { "$it cm³" },
                input.transmission?.let { stringResource(it.labelRes) },
            ).joinToString(" · ").ifEmpty { stringResource(R.string.station_summary_none) }
            CollapsibleSection(
                title = stringResource(R.string.section_engine_details),
                expanded = engineDetailsExpanded,
                onToggle = { engineDetailsExpanded = !engineDetailsExpanded },
                summary = engineDetailsSummary,
            ) {
                UnitField(
                    stringResource(R.string.field_displacement), input.displacementCc, "cm³", KeyboardType.Number,
                    errorOf(VehicleField.DISPLACEMENT_CC), focusOf(VehicleField.DISPLACEMENT_CC),
                ) { value -> change { it.copy(displacementCc = value) } }
                TransmissionField(input.transmission) { value -> change { it.copy(transmission = value) } }
            }
        }
        SectionCard {
            SectionHeading(stringResource(R.string.section_tires))
            FormTextField(
                label = stringResource(R.string.field_tire_size),
                value = input.tireSize,
                onValueChange = { value -> change { it.copy(tireSize = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            )
            UnitField(
                stringResource(R.string.field_tire_pressure_front), input.tirePressureFrontBar, "bar", KeyboardType.Decimal,
                errorOf(VehicleField.TIRE_PRESSURE_FRONT), focusOf(VehicleField.TIRE_PRESSURE_FRONT),
            ) { value -> change { it.copy(tirePressureFrontBar = value) } }
            UnitField(
                stringResource(R.string.field_tire_pressure_rear), input.tirePressureRearBar, "bar", KeyboardType.Decimal,
                errorOf(VehicleField.TIRE_PRESSURE_REAR), focusOf(VehicleField.TIRE_PRESSURE_REAR),
            ) { value -> change { it.copy(tirePressureRearBar = value) } }
        }
        SectionCard {
            SectionHeading(stringResource(R.string.section_tanks))
            EnergyTypesField(input.requiredEnergyTypes) { type ->
                change { it.copy(requiredEnergyTypes = it.requiredEnergyTypes.toggled(type)) }
            }
            if (input.requiredEnergyTypes.allowsAny(EnergyType.PETROL, EnergyType.DIESEL)) {
                UnitField(
                    stringResource(R.string.field_fuel_tank), input.fuelTankL, "l", KeyboardType.Decimal,
                    errorOf(VehicleField.FUEL_TANK), focusOf(VehicleField.FUEL_TANK),
                ) { value -> change { it.copy(fuelTankL = value) } }
            }
            if (input.requiredEnergyTypes.allowsAny(EnergyType.ADBLUE)) {
                UnitField(
                    stringResource(R.string.field_ad_blue_tank), input.adBlueTankL, "l", KeyboardType.Decimal,
                    errorOf(VehicleField.AD_BLUE_TANK), focusOf(VehicleField.AD_BLUE_TANK),
                ) { value -> change { it.copy(adBlueTankL = value) } }
            }
            UnitField(
                stringResource(R.string.field_fresh_water_tank), input.freshWaterTankL, "l", KeyboardType.Decimal,
                errorOf(VehicleField.FRESH_WATER_TANK), focusOf(VehicleField.FRESH_WATER_TANK),
            ) { value -> change { it.copy(freshWaterTankL = value) } }
            UnitField(
                stringResource(R.string.field_grey_water_tank), input.greyWaterTankL, "l", KeyboardType.Decimal,
                errorOf(VehicleField.GREY_WATER_TANK), focusOf(VehicleField.GREY_WATER_TANK),
            ) { value -> change { it.copy(greyWaterTankL = value) } }
            UnitField(
                stringResource(R.string.field_boiler), input.boilerL, "l", KeyboardType.Decimal,
                errorOf(VehicleField.BOILER), focusOf(VehicleField.BOILER),
            ) { value -> change { it.copy(boilerL = value) } }
            UnitField(
                stringResource(R.string.field_cassette), input.cassetteL, "l", KeyboardType.Decimal,
                errorOf(VehicleField.CASSETTE), focusOf(VehicleField.CASSETTE),
            ) { value -> change { it.copy(cassetteL = value) } }
        }
        if (input.requiredEnergyTypes.allowsAny(EnergyType.ELECTRICITY)) {
            SectionCard {
                SectionHeading(stringResource(R.string.section_energy))
                UnitField(
                    stringResource(R.string.field_battery_capacity), input.batteryCapacityAh, "Ah", KeyboardType.Number,
                    errorOf(VehicleField.BATTERY_CAPACITY_AH), focusOf(VehicleField.BATTERY_CAPACITY_AH),
                ) { value -> change { it.copy(batteryCapacityAh = value) } }
                UnitField(
                    stringResource(R.string.field_solar_power), input.solarPowerWp, "Wp", KeyboardType.Number,
                    errorOf(VehicleField.SOLAR_POWER_WP), focusOf(VehicleField.SOLAR_POWER_WP),
                ) { value -> change { it.copy(solarPowerWp = value) } }
            }
        }
        SectionCard {
            SectionHeading(stringResource(R.string.section_maintenance))
            DateField(
                stringResource(R.string.field_next_inspection),
                input.nextInspectionDate,
                null,
                { date -> change { it.copy(nextInspectionDate = date) } },
            )
            DateField(
                stringResource(R.string.field_next_gas_check),
                input.nextGasCheckDate,
                null,
                { date -> change { it.copy(nextGasCheckDate = date) } },
            )
            DateField(
                stringResource(R.string.field_next_leak_test),
                input.nextLeakTestDate,
                null,
                { date -> change { it.copy(nextLeakTestDate = date) } },
            )
            DateField(
                stringResource(R.string.field_last_oil_change),
                input.lastOilChangeDate,
                errorOf(VehicleField.LAST_OIL_CHANGE_DATE),
                { date -> change { it.copy(lastOilChangeDate = date) } },
                modifier = focusOf(VehicleField.LAST_OIL_CHANGE_DATE),
            )
            UnitField(
                stringResource(R.string.field_last_oil_change_odometer), input.lastOilChangeOdometerKm, "km", KeyboardType.Number,
                errorOf(VehicleField.LAST_OIL_CHANGE_ODOMETER_KM), focusOf(VehicleField.LAST_OIL_CHANGE_ODOMETER_KM),
            ) { value -> change { it.copy(lastOilChangeOdometerKm = value) } }
        }
        SectionCard {
            SectionHeading(stringResource(R.string.field_notes))
            FormTextField(
                label = stringResource(R.string.field_notes),
                value = input.notes,
                onValueChange = { value -> change { it.copy(notes = value) } },
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
        }
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.action_save)) }
        if (state.errors.isNotEmpty()) {
            // Nennt die betroffenen Felder; ändert sich die Liste, sagt TalkBack sie erneut an.
            val fields = state.errors.keys.sortedBy(VehicleField::ordinal).map { stringResource(it.labelRes) }
            Text(
                stringResource(R.string.edit_check_fields, fields.joinToString(", ")),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
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

/** Mehrfachauswahl der benötigten Energiearten; steuert, welche Tank-/Kapazitätsfelder darunter erscheinen. */
@Composable
private fun EnergyTypesField(selected: Set<EnergyType>, onToggle: (EnergyType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.field_required_energy_types),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnergyType.entries.forEach { type ->
                FilterChip(
                    selected = type in selected,
                    onClick = { onToggle(type) },
                    label = { Text(stringResource(type.labelRes)) },
                )
            }
        }
    }
}

private fun Set<EnergyType>.toggled(type: EnergyType): Set<EnergyType> = if (type in this) this - type else this + type

/** Nicht editierbare "≈ X PS"-Anzeige unterhalb des kW-Felds; aktualisiert sich live mit der Eingabe. */
@Composable
private fun PowerPsHint(powerKw: String) {
    val kw = powerKw.trim().toIntOrNull()?.takeIf { it > 0 } ?: return
    Text(
        formatApproxPs(kw, currentLocale()),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Einfachauswahl der Schaltungsart; erneutes Tippen auf die gewählte Option hebt sie wieder auf. */
@Composable
private fun TransmissionField(selected: TransmissionType?, onSelect: (TransmissionType?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.field_transmission),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransmissionType.entries.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(if (type == selected) null else type) },
                    label = { Text(stringResource(type.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    singleLine: Boolean = true,
    placeholder: String? = null,
    error: String? = null,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
    )
}

/** Zahlenfeld mit Einheit als Suffix, z. B. Länge in Metern oder Gewicht in Kilogramm. */
@Composable
private fun UnitField(
    label: String,
    value: String,
    unit: String,
    keyboardType: KeyboardType,
    error: String?,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        trailingIcon = { Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Betragsfeld mit Währungsauswahl, für die Geldfelder des Fahrzeugformulars. */
@Composable
private fun MoneyField(
    label: String,
    amount: String,
    currency: Currency,
    error: VehicleError?,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val currencyDescription = stringResource(R.string.edit_cost_currency, currency.getDisplayName(locale))
    Row(modifier = modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.field_amount_with_currency, label, currency.getSymbol(locale))) },
            isError = error != null,
            supportingText = error?.let { { Text(moneyErrorText(it, currency, locale)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = currencyDescription },
        ) { Text(currency.currencyCode) }
    }
    if (showPicker) {
        CurrencyPicker(
            selected = currency,
            excluded = emptySet(),
            locale = locale,
            onSelect = {
                onCurrencyChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun moneyErrorText(error: VehicleError, currency: Currency, locale: Locale): String = when (error) {
    VehicleError.AMOUNT_TOO_LARGE -> stringResource(R.string.error_amount_too_large, formatAmount(MAX_AMOUNT_MINOR, currency, locale))
    else -> stringResource(R.string.error_invalid_amount)
}
