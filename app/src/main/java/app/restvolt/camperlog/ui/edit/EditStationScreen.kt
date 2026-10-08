package app.restvolt.camperlog.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.res.painterResource
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
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.FUEL_SERVICES
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.SUPPLY_SERVICES
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.StationField
import app.restvolt.camperlog.domain.StationInput
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.SYNCED_SERVICE_LOG_TYPES
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.allowedServices
import app.restvolt.camperlog.domain.electricityPreview
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.formatCoordinates
import app.restvolt.camperlog.domain.isApproximateFix
import app.restvolt.camperlog.domain.ParsedLocation
import app.restvolt.camperlog.domain.parseLocationText
import app.restvolt.camperlog.domain.period
import app.restvolt.camperlog.share.openAppDetailsSettings
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.CollapsibleSection
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.DiscardChangesDialog
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.LocationCaptureSection
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.WeatherFetchRow
import app.restvolt.camperlog.ui.WeatherRefreshRow
import app.restvolt.camperlog.ui.WeatherSummary
import app.restvolt.camperlog.ui.attachments.AndroidAttachmentPickers
import app.restvolt.camperlog.ui.attachments.AttachmentPickers
import app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection
import app.restvolt.camperlog.ui.coordinatesContentDescription
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.locationFixSummary
import app.restvolt.camperlog.ui.messageRes
import app.restvolt.camperlog.ui.settings.LocationSettings
import app.restvolt.camperlog.ui.settings.WeatherSettings
import app.restvolt.camperlog.ui.vehicleDisplayName
import app.restvolt.camperlog.ui.yesNoRes
import java.time.LocalDate
import java.time.LocalTime

/**
 * Formular zum Anlegen und Bearbeiten einer Station. [onDone] verlässt es ohne, [onSaved] nach
 * dem Speichern mit den gerade ins Bordbuch eingetragenen Ver-/Entsorgungs-Häkchen.
 */
@Composable
fun EditStationScreen(
    viewModel: EditStationViewModel,
    locationSettings: LocationSettings,
    weatherSettings: WeatherSettings,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    attachmentPickers: AttachmentPickers = AndroidAttachmentPickers,
    onDone: () -> Unit,
    onSaved: (Set<StationService>) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locationEnabled by locationSettings.values.collectAsStateWithLifecycle()
    val weatherEnabled by weatherSettings.values.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved(state.loggedServices)
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.station_edit_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = state.isDirty && !state.isSaved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.station_edit_title_new else R.string.station_edit_title_existing),
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
            state.notFound -> EmptyHint(stringResource(R.string.station_not_found), Modifier.padding(padding))
            else -> StationForm(
                state = state,
                viewModel = viewModel,
                locationEnabled = locationEnabled,
                locationSettings = locationSettings,
                weatherEnabled = weatherEnabled,
                attachments = attachments,
                attachmentFileStore = attachmentFileStore,
                attachmentPickers = attachmentPickers,
                snackbarHostState = snackbar,
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
private fun StationForm(
    state: StationEditUiState,
    viewModel: EditStationViewModel,
    locationEnabled: Boolean,
    locationSettings: LocationSettings,
    weatherEnabled: Boolean,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    attachmentPickers: AttachmentPickers,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    val input = state.input
    // Kostenzeilen-Fehler brauchen die Währung der jeweiligen Zeile und werden gesondert über state.costErrors gerendert.
    val errors = state.errors.filterKeys { it != StationField.COST }.mapValues { (field, error) -> stringResource(error.messageRes(field)) }
    val change = viewModel::onInputChange
    val required = stringResource(R.string.edit_required)
    val locale = currentLocale()

    val coordinatesFocus = remember { FocusRequester() }
    val focus = remember {
        mapOf(
            StationField.DATE to FocusRequester(),
            StationField.COORDINATES to coordinatesFocus,
            StationField.MAP_LINK to coordinatesFocus,
            StationField.NIGHTS to FocusRequester(),
            StationField.NAME to FocusRequester(),
            StationField.PLACE to FocusRequester(),
            StationField.NOTES to FocusRequester(),
            StationField.TOLL_COUNTRY to FocusRequester(),
            StationField.TOLL_VALID_UNTIL to FocusRequester(),
            StationField.TOLL_PAYMENT_METHOD to FocusRequester(),
            StationField.FERRY_BOOKING_REFERENCE to FocusRequester(),
            StationField.COST to FocusRequester(),
        )
    }
    fun focusOf(field: StationField) = Modifier.focusRequester(focus.getValue(field))

    // Nach einem abgelehnten Speichern zum ersten fehlerhaften Feld springen; der Fokus scrollt es ins Bild.
    LaunchedEffect(state.rejectedSaves) {
        if (state.rejectedSaves > 0) {
            errors.keys.minByOrNull(StationField::ordinal)?.let { focus[it]?.requestFocus() }
        }
    }

    val selectedTour = state.tours.firstOrNull { it.id == input.tourId }
    val toursOfVehicle = state.tours.filter { it.vehicleId == input.vehicleId }.sortedByDescending { it.startDate }
    val dateWarning = input.date?.let { date ->
        selectedTour?.takeIf { date < it.startDate || (it.endDate != null && date > it.endDate) }
            ?.let { stringResource(R.string.station_date_outside_tour, it.period(locale)) }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            TypeField(input.type, viewModel::onTypeChange)
            FormTextField(
                label = stringResource(R.string.field_name),
                value = input.name,
                error = null,
                onValueChange = { value -> change { it.copy(name = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = focusOf(StationField.NAME),
            )
            DateField(
                stringResource(R.string.field_date),
                input.date,
                errors[StationField.DATE],
                { date -> change { it.copy(date = date) } },
                modifier = focusOf(StationField.DATE),
                initialDate = LocalDate.now(),
                hint = dateWarning ?: required,
            )
            TimeField(stringResource(R.string.field_time), input.time) { time -> change { it.copy(time = time) } }
            TourField(toursOfVehicle, input.tourId, viewModel::onTourChange)
            if (state.vehicles.size > 1 && input.tourId == null) {
                VehicleField(state.vehicles, input.vehicleId) { id -> change { it.copy(vehicleId = id) } }
            }
        }
        var revealCoordinates by rememberSaveable { mutableStateOf(input.locationText.isNotBlank()) }
        var showPlaceSearch by rememberSaveable { mutableStateOf(false) }
        SectionCard {
            FormTextField(
                label = stringResource(R.string.field_place),
                value = input.place,
                error = null,
                onValueChange = { value -> change { it.copy(place = value) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = focusOf(StationField.PLACE),
            )
            if (locationEnabled) {
                if (input.coordinateSource == CoordinateSource.GPS && input.latitude != null && input.longitude != null) {
                    GpsSuccessRow(
                        fix = LocationFix(input.latitude, input.longitude, input.accuracyM),
                        locationSettings = locationSettings,
                        onRemove = viewModel::onRemoveGpsCoordinates,
                    )
                } else {
                    LocationCaptureSection(
                        controller = viewModel.locationCapture,
                        buttonLabel = stringResource(R.string.location_use_current_button),
                        onEnterManually = { revealCoordinates = true },
                    )
                }
            }
            if (weatherEnabled) {
                TextButton(onClick = { showPlaceSearch = true }) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.station_search_place_button), modifier = Modifier.padding(start = 8.dp))
                }
            }
            CoordinatesField(
                locationText = input.locationText,
                latitude = input.latitude,
                longitude = input.longitude,
                mapLink = input.mapLink,
                error = errors[StationField.COORDINATES] ?: errors[StationField.MAP_LINK],
                onValueChange = viewModel::onLocationTextChange,
                revealed = revealCoordinates,
                onReveal = { revealCoordinates = true },
                modifier = focusOf(StationField.COORDINATES),
            )
            if (weatherEnabled && input.latitude == null) {
                Text(stringResource(R.string.weather_no_coordinates_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (showPlaceSearch) {
            PlaceSearchSheet(
                controller = viewModel.placeSearch,
                initialQuery = input.place.ifBlank { input.name },
                onSearch = viewModel::onSearchPlace,
                onPick = { hit ->
                    viewModel.onPlaceSearchPick(hit)
                    revealCoordinates = true
                    showPlaceSearch = false
                },
                onDismiss = {
                    viewModel.placeSearch.reset()
                    showPlaceSearch = false
                },
            )
        }
        val servicesError = errors[StationField.SERVICES]
        when (input.type) {
            StationType.OVERNIGHT -> OvernightSection(
                input,
                change,
                focusOf(StationField.NIGHTS),
                errors[StationField.NIGHTS],
                servicesError,
                errors[StationField.ELECTRICITY],
                viewModel::onElectricityBillingChange,
            )
            StationType.SUPPLY -> SectionCard {
                SectionHeading(stringResource(R.string.station_section_used_here))
                ServicesChips(SUPPLY_SERVICES, input.services, servicesError) { service -> change { it.copy(services = it.services.toggled(service)) } }
            }
            StationType.FUEL -> FuelSection(input, change, servicesError)
            StationType.TOLL -> TollSection(input, errors, change)
            StationType.FERRY -> FerrySection(input, errors[StationField.FERRY_BOOKING_REFERENCE], change)
            StationType.SIGHT, StationType.FOOD, StationType.OTHER -> Unit
        }
        if (weatherEnabled && input.latitude != null && input.longitude != null) {
            WeatherSection(input, viewModel)
        }
        SectionCard {
            SectionHeading(stringResource(R.string.station_section_costs))
            StationCostFields(
                costs = input.costs,
                errors = state.costErrors,
                excludeElectricity = input.type == StationType.OVERNIGHT && input.electricityPreview(locale).cost != null,
                focusRequester = focus.getValue(StationField.COST),
                onCategoryChange = viewModel::onCostCategoryChange,
                onAmountChange = viewModel::onCostAmountChange,
                onCurrencyChange = viewModel::onCostCurrencyChange,
                onNoteChange = viewModel::onCostNoteChange,
                onAdd = viewModel::onAddCost,
                onRemove = viewModel::onRemoveCost,
            )
        }
        PhotoAttachmentsSection(
            ownerType = AttachmentOwnerType.STATION,
            ownerId = state.savedStationId,
            repository = attachments,
            fileStore = attachmentFileStore,
            snackbarHostState = snackbarHostState,
            stopLocation = if (input.latitude != null && input.longitude != null) input.latitude to input.longitude else null,
            pickers = attachmentPickers,
        )
        SectionCard {
            FormTextField(
                label = stringResource(R.string.field_notes),
                value = input.notes,
                error = errors[StationField.NOTES],
                onValueChange = { value -> change { it.copy(notes = value) } },
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = focusOf(StationField.NOTES),
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
            val fields = errors.keys.sortedBy(StationField::ordinal).map { stringResource(it.labelRes) }
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
private fun OvernightSection(
    input: StationInput,
    change: ((StationInput) -> StationInput) -> Unit,
    modifier: Modifier,
    nightsError: String?,
    servicesError: String?,
    electricityError: String?,
    onElectricityBillingChange: (ElectricityBilling?) -> Unit,
) {
    val locale = currentLocale()
    SectionCard {
        SectionHeading(stringResource(R.string.station_section_overnight))
        FormTextField(
            label = stringResource(R.string.field_nights),
            value = input.nights,
            error = nightsError,
            onValueChange = { value -> change { it.copy(nights = value) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = modifier,
        )
        SiteKindField(input.siteKind) { kind -> change { it.copy(siteKind = kind) } }
        FavoriteRow(input.favorite) { value -> change { it.copy(favorite = value) } }

        var pitchExpanded by rememberSaveable(input.type) {
            mutableStateOf(
                input.pitchAssigned != null || input.lteQuality != null || input.pitchSlope != null || input.levelingBlocksUsed != null,
            )
        }
        val pitchSummary = listOfNotNull(
            input.pitchAssigned?.let { summaryPair(stringResource(R.string.field_pitch_assigned), stringResource(yesNoRes(it))) },
            input.lteQuality?.let { summaryPair(stringResource(R.string.field_lte), stringResource(it.labelRes)) },
            input.pitchSlope?.let { summaryPair(stringResource(R.string.field_pitch_slope), stringResource(it.labelRes)) },
            input.levelingBlocksUsed?.let { summaryPair(stringResource(R.string.field_leveling_blocks), stringResource(yesNoRes(it))) },
        ).let { parts -> if (parts.isEmpty()) stringResource(R.string.station_summary_none) else parts.joinToString(" · ") }

        CollapsibleSection(
            title = stringResource(R.string.station_section_pitch_details),
            expanded = pitchExpanded,
            onToggle = { pitchExpanded = !pitchExpanded },
            summary = pitchSummary,
        ) {
            NullableChoiceField(stringResource(R.string.field_pitch_assigned), listOf(true, false), input.pitchAssigned, ::yesNoRes) { value ->
                change { it.copy(pitchAssigned = value) }
            }
            NullableChoiceField(stringResource(R.string.field_lte), LteQuality.entries, input.lteQuality, LteQuality::labelRes) { value ->
                change { it.copy(lteQuality = value) }
            }
            NullableChoiceField(stringResource(R.string.field_pitch_slope), PitchSlope.entries, input.pitchSlope, PitchSlope::labelRes) { value ->
                change { it.copy(pitchSlope = value) }
            }
            NullableChoiceField(stringResource(R.string.field_leveling_blocks), listOf(true, false), input.levelingBlocksUsed, ::yesNoRes) { value ->
                change { it.copy(levelingBlocksUsed = value) }
            }
        }

        var electricityExpanded by rememberSaveable(input.type) { mutableStateOf(input.electricityBilling != null) }
        val electricitySummary = input.electricityBilling?.let { billing ->
            val preview = input.electricityPreview(locale)
            val resultPart = preview.cost?.let { formatAmount(it.minor, it.currency, locale) }
            listOfNotNull(stringResource(billing.labelRes), resultPart).joinToString(" · ")
        } ?: stringResource(R.string.station_summary_none)
        CollapsibleSection(
            title = stringResource(R.string.station_section_electricity),
            expanded = electricityExpanded,
            onToggle = { electricityExpanded = !electricityExpanded },
            summary = electricitySummary,
        ) {
            ElectricityFields(input, electricityError, change, onElectricityBillingChange)
        }

        var usedHereExpanded by rememberSaveable(input.type) { mutableStateOf(input.services.isNotEmpty()) }
        CollapsibleSection(
            title = stringResource(R.string.station_section_used_here),
            expanded = usedHereExpanded,
            onToggle = { usedHereExpanded = !usedHereExpanded },
            summary = servicesSummary(StationType.OVERNIGHT.allowedServices, input.services),
        ) {
            ServicesChips(SUPPLY_SERVICES, input.services, servicesError) { service -> change { it.copy(services = it.services.toggled(service)) } }
        }
    }
}

@Composable
private fun FuelSection(input: StationInput, change: ((StationInput) -> StationInput) -> Unit, servicesError: String?) {
    SectionCard {
        SectionHeading(stringResource(R.string.station_section_fuelled))
        ServicesChips(FUEL_SERVICES, input.services, error = null) { service -> change { it.copy(services = it.services.toggled(service)) } }

        var usedHereExpanded by rememberSaveable(input.type) { mutableStateOf(input.services.any { it in SUPPLY_SERVICES }) }
        CollapsibleSection(
            title = stringResource(R.string.station_section_used_here),
            expanded = usedHereExpanded,
            onToggle = { usedHereExpanded = !usedHereExpanded },
            summary = servicesSummary(SUPPLY_SERVICES, input.services),
        ) {
            ServicesChips(SUPPLY_SERVICES, input.services, servicesError) { service -> change { it.copy(services = it.services.toggled(service)) } }
        }
    }
}

/** "Wetter"-Karte; nur sichtbar, wenn der Wetter-Schalter an ist und Koordinaten vorliegen. */
@Composable
private fun WeatherSection(input: StationInput, viewModel: EditStationViewModel) {
    SectionCard {
        SectionHeading(stringResource(R.string.station_section_weather))
        if (input.date != LocalDate.now()) {
            Text(stringResource(R.string.weather_date_not_today_body), style = MaterialTheme.typography.bodyMedium)
        } else {
            val weather = input.weather
            if (weather != null) {
                WeatherSummary(weather)
                WeatherRefreshRow(viewModel.weatherCapture, onRefresh = viewModel::onFetchWeather, onRemove = viewModel::onRemoveWeather)
            } else {
                WeatherFetchRow(viewModel.weatherCapture, stringResource(R.string.weather_fetch_button), onFetch = viewModel::onFetchWeather)
            }
        }
    }
}

private fun Set<StationService>.toggled(service: StationService): Set<StationService> =
    if (service in this) this - service else this + service

@Composable
private fun summaryPair(label: String, value: String): String = stringResource(R.string.station_summary_field, label, value)

@Composable
private fun servicesSummary(allowed: Set<StationService>, selected: Set<StationService>): String {
    val present = allowed.filter { it in selected }
    return if (present.isEmpty()) stringResource(R.string.station_summary_none) else present.map { stringResource(it.labelRes) }.joinToString(", ")
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun FavoriteRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_favorite), contentDescription = null)
        Text(
            stringResource(R.string.station_would_return),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Chip-Reihe der Ver-/Entsorgung oder des Tankens. Wenn [allowed] mindestens einen mit dem Bordbuch
 * synchronisierten Dienst enthält, zeigt sie darunter den Hinweistext bzw. [error].
 */
@Composable
private fun ServicesChips(allowed: Set<StationService>, selected: Set<StationService>, error: String?, onToggle: (StationService) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        allowed.forEach { service ->
            FilterChip(
                selected = service in selected,
                onClick = { onToggle(service) },
                label = { Text(stringResource(service.labelRes)) },
            )
        }
    }
    if (allowed.any { it in SYNCED_SERVICE_LOG_TYPES }) {
        Text(
            error ?: stringResource(R.string.station_services_logbook_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SiteKindField(selected: SiteKind?, onSelect: (SiteKind?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.field_site_kind),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SiteKind.entries.forEach { kind ->
                FilterChip(
                    selected = selected == kind,
                    onClick = { onSelect(if (selected == kind) null else kind) },
                    label = { Text(stringResource(kind.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun CoordinatesField(
    locationText: String,
    latitude: Double?,
    longitude: Double?,
    mapLink: String?,
    error: String?,
    onValueChange: (String) -> Unit,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!revealed) {
        TextButton(onClick = onReveal) { Text(stringResource(R.string.station_add_coordinates)) }
        return
    }
    val locale = currentLocale()
    val feedback = error ?: when {
        latitude != null && longitude != null -> stringResource(R.string.station_coordinates_recognized, formatCoordinates(latitude, longitude, locale))
        mapLink != null -> stringResource(R.string.station_coordinates_link_saved)
        locationText.isBlank() -> null
        parseLocationText(locationText) == ParsedLocation.ShortLinkUnsupported -> stringResource(R.string.station_coordinates_short_link)
        else -> stringResource(R.string.station_coordinates_not_found)
    }
    OutlinedTextField(
        value = locationText,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.field_coordinates)) },
        isError = error != null,
        supportingText = feedback?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.medium,
    )
}

/**
 * Erfolgszustand der Standortbestimmung: Koordinaten, Genauigkeit und "Koordinaten entfernen".
 * Bei nur ungefährer Genauigkeit zeigt sie einmalig den Hinweis auf "Genauer Standort".
 */
@Composable
private fun GpsSuccessRow(fix: LocationFix, locationSettings: LocationSettings, onRemove: () -> Unit) {
    val locale = currentLocale()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val description = coordinatesContentDescription(fix.latitude, fix.longitude, fix.accuracyM, locale)
            Text(
                locationFixSummary(fix, locale),
                modifier = Modifier.weight(1f).semantics { contentDescription = description },
                style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.location_remove_coordinates))
            }
        }
        if (isApproximateFix(fix.accuracyM) && !locationSettings.approximateHintShown) {
            LaunchedEffect(Unit) { locationSettings.approximateHintShown = true }
            Text(stringResource(R.string.location_approximate_hint_body), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.openAppDetailsSettings() }) { Text(stringResource(R.string.location_action_app_settings)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, time: LocalTime?, onTimeSelected: (LocalTime?) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val pickLabel = stringResource(R.string.edit_pick_date, label)
    OutlinedTextField(
        value = time?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                onClick(label = pickLabel) {
                    showPicker = true
                    true
                }
            },
        label = { Text(label) },
        trailingIcon = {
            if (time != null) {
                IconButton(onClick = { onTimeSelected(null) }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.station_clear_time))
                }
            } else {
                IconButton(onClick = { showPicker = true }) {
                    Icon(painterResource(R.drawable.ic_schedule), contentDescription = pickLabel)
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
    if (showPicker) {
        val now = LocalTime.now()
        val pickerState = rememberTimePickerState(initialHour = time?.hour ?: now.hour, initialMinute = time?.minute ?: now.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(LocalTime.of(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeField(selected: StationType, onSelect: (StationType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_type)) },
            leadingIcon = { Icon(painterResource(selected.iconRes), contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StationType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes)) },
                    leadingIcon = { Icon(painterResource(type.iconRes), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect(type)
                    },
                )
            }
        }
    }
}

/** Tourauswahl des Formulars; "Keine Tour" steht an erster Stelle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TourField(tours: List<Tour>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val noTourLabel = stringResource(R.string.station_no_tour)
    val selected = tours.firstOrNull { it.id == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.destination ?: noTourLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_tour)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noTourLabel) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            tours.forEach { tour ->
                DropdownMenuItem(
                    text = { Text(tour.destination) },
                    onClick = {
                        expanded = false
                        onSelect(tour.id)
                    },
                )
            }
        }
    }
}

/** Fahrzeugauswahl des Formulars; wird nur bei mehr als einem Fahrzeug und ohne gewählte Tour angezeigt. */
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
                    text = { Text(vehicleDisplayName(vehicle)) },
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
internal fun FormTextField(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> NullableChoiceField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> Int,
    onSelect: (T?) -> Unit,
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
                    onClick = { onSelect(if (option == selected) null else option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
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
