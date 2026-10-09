package app.restvolt.camperlog.ui.edit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.tracking.TrackRecordingService
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.CollapsibleSection
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.DiscardChangesDialog
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.messageRes
import app.restvolt.camperlog.ui.settings.HomeLocationSettings
import app.restvolt.camperlog.ui.settings.trackPermissions
import app.restvolt.camperlog.ui.vehicleDisplayName
import app.restvolt.camperlog.ui.vehicleMenuLabel

/** Formular zum Anlegen und Bearbeiten einer Tour. [onDone] verlässt es ohne, [onSaved] nach dem Speichern. */
@Composable
fun EditTourScreen(
    viewModel: EditTourViewModel,
    trackSettings: TrackRecordingSettings,
    homeLocationSettings: HomeLocationSettings,
    onDone: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trackPreferences by trackSettings.values.collectAsStateWithLifecycle()
    val homeLocationPreferences by homeLocationSettings.values.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current

    val trackPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val tourId = state.savedTourId
        if (tourId != null && TrackRecordingService.hasLocationPermission(context)) {
            TrackRecordingService.startForTour(context, tourId)
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            val tourId = state.savedTourId
            if (state.trackSwitch && tourId != null) {
                if (TrackRecordingService.hasLocationPermission(context)) {
                    TrackRecordingService.startForTour(context, tourId)
                } else {
                    trackPermissionLauncher.launch(trackPermissions())
                }
            }
            onSaved()
        }
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.edit_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }
    LaunchedEffect(state.runningTourConflict) {
        if (state.runningTourConflict) {
            snackbar.showSnackbar(resources.getString(R.string.edit_running_tour_conflict), withDismissAction = true)
            viewModel.onRunningTourConflictShown()
        }
    }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = state.isDirty && !state.isSaved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title_existing),
                onBack = requestBack,
                actions = {
                    if (!state.notFound && !state.isLoading) {
                        TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
                    }
                },
            )
        },
        // Als bottomBar statt Overlay: Das Formular endet über der Snackbar, der untere Button bleibt frei.
        bottomBar = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.notFound -> EmptyHint(stringResource(R.string.tour_not_found), Modifier.padding(padding))
            else -> TourForm(
                state = state,
                viewModel = viewModel,
                showTrackSwitch = state.isNew && state.input.endDate == null && trackPreferences.enabled,
                showHomeSwitch = state.isNew && homeLocationPreferences.hasLocation,
                homeName = homeLocationPreferences.name.ifBlank { stringResource(R.string.home_location_default_name) },
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

private val METRICS_FIELDS = setOf(TourField.TRAVEL_DAYS, TourField.OVERNIGHT_STAYS, TourField.DISTANCE_KM)

@Composable
private fun TourForm(
    state: EditUiState,
    viewModel: EditTourViewModel,
    showTrackSwitch: Boolean,
    showHomeSwitch: Boolean,
    homeName: String,
    modifier: Modifier,
) {
    val input = state.input
    val errors = state.errors.mapValues { (field, error) -> stringResource(error.messageRes(field)) }
    val change = viewModel::onInputChange
    val required = stringResource(R.string.edit_required)
    val focus = remember { TourField.entries.associateWith { FocusRequester() } }
    fun focusOf(field: TourField) = Modifier.focusRequester(focus.getValue(field))

    // Initial aufgeklappt, wenn der Abschnitt schon Inhalt hat; ein Fehler klappt ihn zusätzlich
    // reaktiv auf, auch nachträglich - siehe den Fokus-Sprung unten.
    var metricsOverride by rememberSaveable {
        mutableStateOf(input.travelDays.isNotBlank() || input.overnightStays.isNotBlank() || input.distanceKm.isNotBlank())
    }
    var costsOverride by rememberSaveable { mutableStateOf(input.costs.any { it.amount.isNotBlank() }) }
    var advancedOverride by rememberSaveable {
        mutableStateOf(input.slug.isNotBlank() || (state.showLegacyMapLink && input.mapLink.isNotBlank()))
    }
    val metricsExpanded = metricsOverride || state.errors.keys.any { it in METRICS_FIELDS }
    val costsExpanded = costsOverride || TourField.COST in state.errors
    val advancedExpanded = advancedOverride || TourField.MAP_LINK in state.errors

    // Nach einem abgelehnten Speichern zum ersten fehlerhaften Feld springen; der Fokus scrollt es
    // ins Bild. Ein eingeklappter Abschnitt ist zu diesem Zeitpunkt schon aufgeklappt, siehe oben.
    LaunchedEffect(state.rejectedSaves) {
        if (state.rejectedSaves > 0) {
            errors.keys.minByOrNull(TourField::ordinal)?.let { focus.getValue(it).requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            SectionHeading(stringResource(R.string.edit_section_tour))
            if (state.vehicles.size > 1) {
                VehicleField(state.vehicles, input.vehicleId, viewModel::onVehicleChange)
            }
            FormTextField(
                label = stringResource(R.string.field_name),
                value = input.name,
                error = null,
                onValueChange = viewModel::onNameChange,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )
            FormTextField(
                label = stringResource(R.string.field_destination),
                value = input.destination,
                error = errors[TourField.DESTINATION],
                onValueChange = viewModel::onDestinationChange,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = focusOf(TourField.DESTINATION),
            )
            ChoiceField(stringResource(R.string.field_tour_type), TourType.entries, input.tourType, TourType::labelRes) { value ->
                change { it.copy(tourType = value) }
            }
        }
        SectionCard {
            SectionHeading(stringResource(R.string.edit_section_period))
            DateField(
                stringResource(R.string.field_start_date),
                input.startDate,
                errors[TourField.START_DATE],
                viewModel::onStartDateChange,
                modifier = focusOf(TourField.START_DATE),
                hint = required,
            )
            DateField(
                stringResource(R.string.field_end_date),
                input.endDate,
                errors[TourField.END_DATE],
                viewModel::onEndDateChange,
                initialDate = input.startDate,
                minDate = input.startDate,
                modifier = focusOf(TourField.END_DATE),
                onClear = { viewModel.onEndDateChange(null) },
                hint = stringResource(R.string.edit_end_date_hint),
            )
            if (input.endDate == null) {
                Text(
                    stringResource(R.string.tour_metrics_automatic_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (input.endDate != null) {
            SectionCard {
                val metricsHint = stringResource(R.string.tour_metrics_editable_hint)
                CollapsibleSection(
                    title = stringResource(R.string.tour_metrics_title),
                    expanded = metricsExpanded,
                    onToggle = { metricsOverride = !metricsExpanded },
                    summary = metricsHint,
                ) {
                    Text(
                        metricsHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NumberField(
                        stringResource(R.string.field_travel_days),
                        input.travelDays,
                        errors[TourField.TRAVEL_DAYS],
                        focusOf(TourField.TRAVEL_DAYS),
                    ) { value -> change { it.copy(travelDays = value) } }
                    NumberField(
                        stringResource(R.string.field_overnight_stays),
                        input.overnightStays,
                        errors[TourField.OVERNIGHT_STAYS],
                        focusOf(TourField.OVERNIGHT_STAYS),
                    ) { value -> change { it.copy(overnightStays = value) } }
                    NumberField(
                        stringResource(R.string.field_distance),
                        input.distanceKm,
                        errors[TourField.DISTANCE_KM],
                        focusOf(TourField.DISTANCE_KM),
                    ) { value -> change { it.copy(distanceKm = value) } }
                }
            }
        }
        if (state.isNew && (showTrackSwitch || showHomeSwitch)) {
            SectionCard {
                SectionHeading(stringResource(R.string.edit_section_on_save))
                if (showTrackSwitch) {
                    CheckboxSettingRow(
                        title = stringResource(R.string.edit_track_switch_title),
                        supportingText = stringResource(R.string.edit_track_switch_support),
                        checked = state.trackSwitch,
                        onCheckedChange = viewModel::onTrackSwitchChange,
                    )
                }
                if (showHomeSwitch) {
                    CheckboxSettingRow(
                        title = stringResource(if (input.endDate != null) R.string.edit_home_switch_title_both else R.string.edit_home_switch_title_start),
                        supportingText = stringResource(
                            if (input.endDate != null) R.string.edit_home_switch_support_both else R.string.edit_home_switch_support_start,
                            homeName,
                        ),
                        checked = state.homeSwitch,
                        onCheckedChange = viewModel::onHomeSwitchChange,
                    )
                }
            }
        }
        SectionCard {
            CollapsibleSection(
                title = stringResource(R.string.tour_section_other_costs),
                expanded = costsExpanded,
                onToggle = { costsOverride = !costsExpanded },
                summary = input.costs.filter { it.amount.isNotBlank() }
                    .joinToString(", ") { "${it.amount} ${it.currency.currencyCode}" }
                    .ifBlank { null },
            ) {
                CostFields(
                    costs = input.costs,
                    errors = state.costErrors,
                    focusRequester = focus.getValue(TourField.COST),
                    onAmountChange = viewModel::onCostAmountChange,
                    onCurrencyChange = viewModel::onCostCurrencyChange,
                    onAdd = viewModel::onAddCost,
                    onRemove = viewModel::onRemoveCost,
                )
            }
        }
        SectionCard {
            CollapsibleSection(
                title = stringResource(R.string.edit_section_advanced),
                expanded = advancedExpanded,
                onToggle = { advancedOverride = !advancedExpanded },
                summary = listOfNotNull(
                    input.slug.takeIf(String::isNotBlank),
                    input.mapLink.takeIf { state.showLegacyMapLink && it.isNotBlank() },
                ).joinToString(" · ").ifBlank { null },
            ) {
                FormTextField(
                    label = stringResource(R.string.field_slug),
                    value = input.slug,
                    error = null,
                    onValueChange = viewModel::onSlugChange,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next,
                    ),
                    hint = stringResource(R.string.edit_slug_hint),
                )
                if (state.showLegacyMapLink) {
                    FormTextField(
                        label = stringResource(R.string.field_map_link),
                        value = input.mapLink,
                        error = errors[TourField.MAP_LINK],
                        onValueChange = { value -> change { it.copy(mapLink = value) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        placeholder = stringResource(R.string.edit_map_link_placeholder),
                        modifier = focusOf(TourField.MAP_LINK),
                    )
                }
            }
        }
        SectionCard {
            FormTextField(
                label = stringResource(R.string.field_notes),
                value = input.notes,
                error = null,
                onValueChange = { value -> change { it.copy(notes = value) } },
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.action_save)) }
        if (errors.isNotEmpty()) {
            // Nennt die betroffenen Felder; ändert sich die Liste, sagt TalkBack sie erneut an.
            val fields = errors.keys.sortedBy(TourField::ordinal).map { stringResource(it.labelRes) }
            Text(
                stringResource(R.string.edit_check_fields, fields.joinToString(", ")),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Überschrift einer nicht aufklappbaren Formulargruppe, z. B. "Tour" oder "Zeitraum". */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    singleLine: Boolean = true,
    placeholder: String? = null,
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

/**
 * Formularoption mit Checkbox statt Schalter: Anders als ein Schalter wirkt sie erst beim Speichern
 * des Formulars, nicht sofort (siehe Material-Konvention für Schalter vs. Checkbox).
 */
@Composable
private fun CheckboxSettingRow(title: String, supportingText: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** Fahrzeugauswahl des Formulars; wird nur bei mehr als einem Fahrzeug angezeigt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleField(vehicles: List<Vehicle>, selectedId: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = vehicles.firstOrNull { it.id == selectedId } ?: vehicles.first()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = vehicleDisplayName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_vehicle)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = { Text(vehicleMenuLabel(vehicle)) },
                    onClick = {
                        expanded = false
                        onSelect(vehicle.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    FormTextField(
        label = label,
        value = value,
        error = error,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Die Überschrift steckt in jeder Option (siehe unten), sonst liest TalkBack sie doppelt.
        Text(
            label,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val text = stringResource(optionLabel(option))
                val description = stringResource(R.string.edit_choice_option, label, text)
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    // Kräftige Füllung plus Standard-Häkchen: Auswahl ist nicht nur am Farbton erkennbar.
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Text(text, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
