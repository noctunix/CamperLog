package app.restvolt.camperlog.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.Conversion
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.checkedCount
import app.restvolt.camperlog.domain.displayTitle
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourMetrics
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.effectiveCosts
import app.restvolt.camperlog.domain.expiringVignettes
import app.restvolt.camperlog.domain.formatAmounts
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.FUEL_SERVICES
import app.restvolt.camperlog.domain.isMapAvailable
import app.restvolt.camperlog.domain.SUPPLY_SERVICES
import app.restvolt.camperlog.domain.sumByCurrency
import app.restvolt.camperlog.domain.tourCountries
import app.restvolt.camperlog.share.openInMaps
import app.restvolt.camperlog.share.shareTour
import app.restvolt.camperlog.share.shareTourExportZip
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.checklists.ChecklistTemplatePickerSheet
import app.restvolt.camperlog.ui.CollapsibleSection
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.FinishTourDialog
import app.restvolt.camperlog.ui.LabeledValue
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.StationTypePickerSheet
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.stationSavedText
import app.restvolt.camperlog.ui.vehicleDisplayName
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Schreibgeschützte Ansicht einer Tour mit Zeitleiste der Stationen. [onDelete] löscht die
 * Tour ohne Rückfrage; die Liste bietet anschließend „Rückgängig" an.
 */
@Composable
fun TourDetailScreen(
    viewModel: TourDetailViewModel,
    weatherMapEnabled: Boolean,
    checklistTemplates: List<ChecklistTemplate>,
    /** Karte "Track" nach den Ländern; `null`, solange die Trackaufzeichnung ausgeschaltet ist. */
    trackCard: (@Composable () -> Unit)? = null,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (Tour) -> Unit,
    onAddStation: (Long, StationType) -> Unit,
    onOpenStation: (Long) -> Unit,
    onOpenMap: () -> Unit,
    onAddDiaryEntry: (Long) -> Unit,
    onOpenDiaryEntry: (Long) -> Unit,
    onAddSuggestedChecklistTemplates: () -> Unit,
    onOpenChecklist: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val diaryMessage by viewModel.diaryMessage.collectAsStateWithLifecycle()
    val checklistMessage by viewModel.checklistMessage.collectAsStateWithLifecycle()
    val startedChecklistId by viewModel.startedChecklistId.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val exportRequest by viewModel.exportRequest.collectAsStateWithLifecycle()
    val finishing by viewModel.finishing.collectAsStateWithLifecycle()
    val finishMessage by viewModel.finishMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val tour = (state as? DetailUiState.Loaded)?.tour
    var overflowExpanded by remember { mutableStateOf(false) }
    var showTypePicker by rememberSaveable { mutableStateOf(false) }
    var showChecklistPicker by rememberSaveable { mutableStateOf(false) }
    var showFinishDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                val fallbackTitle = stringResource(R.string.detail_fallback_title)
                BackTopBar(title = tour?.displayTitle(fallbackTitle) ?: fallbackTitle, onBack = onBack) {
                    if (tour != null) {
                        val loaded = state as DetailUiState.Loaded
                        IconButton(onClick = onEdit) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.detail_edit))
                        }
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_share)) },
                                onClick = {
                                    overflowExpanded = false
                                    val countries = tourCountries(loaded.autoDetectedCountries, tour.manualCountriesAdded, tour.manualCountriesRemoved)
                                    if (!context.shareTour(tour, loaded.stations, countries)) {
                                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.no_share_app)) }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_export_tour)) },
                                enabled = !exporting,
                                onClick = {
                                    overflowExpanded = false
                                    val countries = tourCountries(loaded.autoDetectedCountries, tour.manualCountriesAdded, tour.manualCountriesRemoved)
                                    viewModel.exportTour(resources, tour, loaded.stations, countries, loaded.diaryEntries)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_open_maps)) },
                                onClick = {
                                    overflowExpanded = false
                                    if (!context.openInMaps(tour)) {
                                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.detail_no_maps_app)) }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_delete)) },
                                onClick = {
                                    overflowExpanded = false
                                    onDelete(tour)
                                },
                            )
                        }
                    }
                }
                if (exporting) {
                    val working = stringResource(R.string.data_working)
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = working
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tour != null) {
                // Content-Überladung statt text/icon, siehe ToursScreen: sonst hätte der FAB für TalkBack keinen Namen.
                ExtendedFloatingActionButton(onClick = { showTypePicker = true }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.station_fab_add))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            DetailUiState.Loading -> Unit
            DetailUiState.NotFound -> EmptyHint(stringResource(R.string.tour_not_found), Modifier.padding(padding))
            is DetailUiState.Loaded -> TourDetails(
                tour = current.tour,
                vehicle = current.vehicle,
                stations = current.stations,
                stopCosts = current.stopCosts,
                totalCosts = current.totalCosts,
                categoryCosts = current.categoryCosts,
                conversion = current.conversion,
                autoDetectedCountries = current.autoDetectedCountries,
                diaryEntries = current.diaryEntries,
                checklists = current.checklists,
                metrics = current.metrics,
                weatherMapEnabled = weatherMapEnabled,
                modifier = Modifier.fillMaxSize(),
                padding = padding,
                onOpenStation = onOpenStation,
                onAddStop = { showTypePicker = true },
                onOpenMap = onOpenMap,
                onSaveCountries = { added, removed -> viewModel.saveCountries(current.tour, added, removed) },
                trackCard = trackCard,
                onAddDiaryEntry = { onAddDiaryEntry(current.tour.id) },
                onOpenDiaryEntry = onOpenDiaryEntry,
                onStartChecklist = { showChecklistPicker = true },
                onOpenChecklist = onOpenChecklist,
                finishing = finishing,
                onFinish = { showFinishDialog = true },
            )
        }
    }

    if (showTypePicker && tour != null) {
        StationTypePickerSheet(
            onSelect = { type ->
                showTypePicker = false
                onAddStation(tour.id, type)
            },
            onDismiss = { showTypePicker = false },
        )
    }

    if (showChecklistPicker) {
        ChecklistTemplatePickerSheet(
            templates = checklistTemplates,
            onSelect = { template ->
                showChecklistPicker = false
                viewModel.startChecklist(template)
            },
            onAddSuggested = onAddSuggestedChecklistTemplates,
            onDismiss = { showChecklistPicker = false },
        )
    }

    if (showFinishDialog && tour != null && tour.endDate == null) {
        FinishTourDialog(
            tourStart = tour.startDate,
            today = LocalDate.now(),
            onConfirm = {
                showFinishDialog = false
                viewModel.finish(it)
            },
            onDismiss = { showFinishDialog = false },
        )
    }

    LaunchedEffect(startedChecklistId) {
        val id = startedChecklistId ?: return@LaunchedEffect
        onOpenChecklist(id)
        viewModel.onChecklistStartHandled()
    }

    LaunchedEffect(exportRequest) {
        val request = exportRequest ?: return@LaunchedEffect
        val started = context.shareTourExportZip(request.uri.toUri(), request.destination)
        viewModel.exportRequestHandled(started)
    }

    // Die Meldung gilt erst nach vollständiger Anzeige als erledigt, siehe ToursScreen.
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is StationMessage.Deleted -> {
                val label = current.station.name.ifBlank { resources.getString(current.station.type.labelRes) }
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.station_deleted, label),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteStation(current)
            }
            is StationMessage.Saved -> snackbar.showSnackbar(stationSavedText(resources, current.loggedServices))
            is StationMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }

    LaunchedEffect(diaryMessage) {
        val current = diaryMessage ?: return@LaunchedEffect
        when (current) {
            is DiaryMessage.Deleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.diary_entry_deleted),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteDiaryEntry(current.entry)
            }
            is DiaryMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onDiaryMessageShown(current)
    }

    LaunchedEffect(checklistMessage) {
        val current = checklistMessage ?: return@LaunchedEffect
        when (current) {
            is ChecklistMessage.Deleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.checklist_deleted),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteChecklist(current.checklist)
            }
            is ChecklistMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onChecklistMessageShown(current)
    }

    LaunchedEffect(finishMessage) {
        val current = finishMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(
            resources.getString(
                when (current) {
                    FinishMessage.Finished -> R.string.tour_finished
                    FinishMessage.Failed -> R.string.tour_finish_failed
                },
            ),
            withDismissAction = current == FinishMessage.Failed,
        )
        viewModel.onFinishMessageShown(current)
    }
}

@Composable
private fun TourDetails(
    tour: Tour,
    vehicle: Vehicle?,
    stations: List<Station>,
    stopCosts: List<Money>,
    totalCosts: List<Money>,
    categoryCosts: Map<CostCategory, List<Money>>,
    conversion: Conversion?,
    autoDetectedCountries: Set<String>,
    diaryEntries: List<DiaryEntry>,
    checklists: List<Checklist>,
    metrics: TourMetrics,
    weatherMapEnabled: Boolean,
    modifier: Modifier,
    padding: PaddingValues,
    onOpenStation: (Long) -> Unit,
    onAddStop: () -> Unit,
    onOpenMap: () -> Unit,
    onSaveCountries: (Set<String>, Set<String>) -> Unit,
    trackCard: (@Composable () -> Unit)?,
    onAddDiaryEntry: () -> Unit,
    onOpenDiaryEntry: (Long) -> Unit,
    onStartChecklist: () -> Unit,
    onOpenChecklist: (Long) -> Unit,
    finishing: Boolean,
    onFinish: () -> Unit,
) {
    val locale = currentLocale()
    val vignetteWarnings = remember(tour, stations) { expiringVignettes(tour, stations) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            // Der Scaffold reserviert keinen Platz für den FAB; siehe ToursScreen.
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard {
                if (vehicle != null) {
                    LabeledValue(stringResource(R.string.field_vehicle), vehicleDisplayName(vehicle))
                }
                LabeledValue(stringResource(R.string.field_start_date), formatDate(tour.startDate, locale))
                tour.endDate?.let { LabeledValue(stringResource(R.string.field_end_date), formatDate(it, locale)) }
                tour.name.takeIf(String::isNotBlank)?.let { LabeledValue(stringResource(R.string.field_name), it) }
                tour.destination.takeIf(String::isNotBlank)?.let { LabeledValue(stringResource(R.string.field_destination), it) }
                LabeledValue(stringResource(R.string.field_tour_type), stringResource(tour.tourType.labelRes))
                LabeledValue(stringResource(R.string.field_travel_days), metrics.travelDays.toString())
                LabeledValue(stringResource(R.string.field_overnight_stays), metrics.overnightStays.toString())
                val distanceRes = if (metrics.distanceIsEstimated) R.string.distance_km_estimated else R.string.distance_km
                LabeledValue(stringResource(R.string.label_distance), stringResource(distanceRes, metrics.distanceKm))
                if (tour.endDate == null) {
                    Button(onClick = onFinish, enabled = !finishing, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tour_finish))
                    }
                }
            }
        }
        if (vignetteWarnings.isNotEmpty()) {
            item { VignetteWarningsCard(warnings = vignetteWarnings, locale = locale, onOpenStation = onOpenStation) }
        }
        item { CostsCard(tour.costs, stopCosts, totalCosts, categoryCosts, conversion, locale) }
        item {
            TourCountriesCard(
                autoDetected = autoDetectedCountries,
                manuallyAdded = tour.manualCountriesAdded,
                manuallyRemoved = tour.manualCountriesRemoved,
                locale = locale,
                onSave = onSaveCountries,
            )
        }
        if (trackCard != null) {
            item { trackCard() }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.stations_section_title, stations.size),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (isMapAvailable(weatherMapEnabled, stations)) {
                    TextButton(onClick = onOpenMap) {
                        Icon(painterResource(R.drawable.ic_map), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_map))
                    }
                }
            }
        }
        if (stations.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.stations_timeline_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = onAddStop) {
                        Text(stringResource(R.string.stations_add_stop))
                    }
                }
            }
        } else {
            items(stations, key = Station::id) { station ->
                StationTimelineRow(
                    station = station,
                    locale = locale,
                    isLast = station.id == stations.last().id,
                    onClick = { onOpenStation(station.id) },
                )
            }
        }
        item {
            var expanded by rememberSaveable { mutableStateOf(true) }
            SectionCard {
                CollapsibleSection(
                    title = stringResource(R.string.section_checklists),
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    summary = null,
                ) {
                    ChecklistsSection(checklists = checklists, onOpenChecklist = onOpenChecklist, onStart = onStartChecklist)
                }
            }
        }
        item {
            var expanded by rememberSaveable { mutableStateOf(true) }
            SectionCard {
                CollapsibleSection(
                    title = stringResource(R.string.diary_section_title),
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    summary = null,
                ) {
                    DiarySection(entries = diaryEntries, locale = locale, onOpenEntry = onOpenDiaryEntry, onAdd = onAddDiaryEntry)
                }
            }
        }
        if (tour.notes.isNotBlank() || tour.mapLink != null) {
            item {
                SectionCard {
                    if (tour.notes.isNotBlank()) {
                        Text(
                            stringResource(R.string.field_notes),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(tour.notes, style = MaterialTheme.typography.bodyLarge)
                    }
                    tour.mapLink?.let {
                        Text(
                            stringResource(R.string.field_map_link),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Kostenkarte der Tourdetailseite: manuelle Tourkosten ("Sonstige Kosten"), die Summe der
 * Stationskosten ("Stationen"), die Gesamtsumme je Währung, eine Umrechnung in die Hauptwährung
 * (siehe [Conversion]) sowie eine aufklappbare Aufschlüsselung der Stationskosten nach Kategorie.
 */
@Composable
private fun CostsCard(
    otherCosts: List<Money>,
    stopCosts: List<Money>,
    totalCosts: List<Money>,
    categoryCosts: Map<CostCategory, List<Money>>,
    conversion: Conversion?,
    locale: Locale,
) {
    SectionCard {
        LabeledValue(stringResource(R.string.tour_section_other_costs), formatAmounts(otherCosts, locale))
        LabeledValue(stringResource(R.string.tour_section_stops_costs), formatAmounts(stopCosts, locale))
        LabeledValue(stringResource(R.string.tour_section_total_costs), formatAmounts(totalCosts, locale))
        if (conversion != null) {
            val converted = conversion.total
            if (converted != null) {
                LabeledValue(stringResource(R.string.overview_converted, converted.currency.currencyCode), formatAmounts(listOf(converted), locale))
            } else {
                Text(
                    if (conversion.tooLarge) {
                        stringResource(R.string.overview_conversion_too_large)
                    } else {
                        stringResource(R.string.overview_missing_rates, conversion.missing.joinToString(", ") { it.currencyCode })
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (categoryCosts.isNotEmpty()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            CollapsibleSection(
                title = stringResource(R.string.cost_breakdown_by_category),
                expanded = expanded,
                onToggle = { expanded = !expanded },
                summary = null,
            ) {
                categoryCosts.entries.sortedBy { it.key.ordinal }.forEach { (category, amounts) ->
                    LabeledValue(stringResource(category.labelRes), formatAmounts(amounts, locale))
                }
            }
        }
    }
}

@Composable
private fun StationTimelineRow(station: Station, locale: Locale, isLast: Boolean, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.tours_open_details)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(station.type.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = 24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 8.dp else 20.dp),
        ) {
            val headline = station.name.ifBlank { stringResource(station.type.labelRes) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    headline,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (station.favorite) {
                    Icon(
                        painterResource(R.drawable.ic_favorite),
                        contentDescription = stringResource(R.string.station_would_return),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stationSupportingText(station, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun stationSupportingText(station: Station, locale: Locale): String {
    val date = formatDate(station.date, locale)
    val dateTime = station.time?.let { "$date, %02d:%02d".format(it.hour, it.minute) } ?: date
    val detail = when (station.type) {
        StationType.OVERNIGHT -> station.nights?.let { pluralStringResource(R.plurals.station_nights, it, it) }
        StationType.SUPPLY -> servicesLabel(SUPPLY_SERVICES, station.services)
        StationType.FUEL -> servicesLabel(FUEL_SERVICES + SUPPLY_SERVICES, station.services)
        StationType.TOLL, StationType.SIGHT, StationType.FOOD, StationType.FERRY, StationType.OTHER -> null
    }
    val costs = station.effectiveCosts().map { it.amount }.sumByCurrency().takeIf { it.isNotEmpty() }?.let { formatAmounts(it, locale) }
    return listOfNotNull(dateTime, detail, costs).joinToString(" · ")
}

@Composable
private fun ChecklistsSection(checklists: List<Checklist>, onOpenChecklist: (Long) -> Unit, onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        checklists.forEach { checklist -> ChecklistRow(checklist, onClick = { onOpenChecklist(checklist.id) }) }
        TextButton(onClick = onStart) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.checklist_start))
        }
    }
}

@Composable
private fun ChecklistRow(checklist: Checklist, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.tours_open_details)
    val progress = "${checklist.checkedCount}/${checklist.items.size}"
    val description = stringResource(R.string.checklist_progress_cd, checklist.title, checklist.checkedCount, checklist.items.size)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(checklist.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiarySection(entries: List<DiaryEntry>, locale: Locale, onOpenEntry: (Long) -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { entry -> DiaryEntryRow(entry, locale, onClick = { onOpenEntry(entry.id) }) }
        TextButton(onClick = onAdd) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.diary_add_entry))
        }
    }
}

@Composable
private fun DiaryEntryRow(entry: DiaryEntry, locale: Locale, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.tours_open_details)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(formatDate(entry.date, locale), style = MaterialTheme.typography.titleSmall)
        Text(
            entry.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun servicesLabel(allowed: Set<StationService>, selected: Set<StationService>): String? {
    val present = allowed.filter { it in selected }
    if (present.isEmpty()) return null
    return present.map { stringResource(it.labelRes) }.joinToString(", ")
}
