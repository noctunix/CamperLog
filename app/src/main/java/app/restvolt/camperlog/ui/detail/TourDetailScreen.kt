package app.restvolt.camperlog.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import app.restvolt.camperlog.domain.period
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
    /** Status- und Pausieren-Zeile der Kopfkarte; `null` ohne laufende Aufzeichnung dieser Tour. */
    trackStatusContent: (@Composable () -> Unit)? = null,
    /** Inhalt des Abschnitts "GPS-Track"; `null`, solange die Trackaufzeichnung ausgeschaltet ist. */
    trackSectionContent: (@Composable () -> Unit)? = null,
    /**
     * Tutorial-Ausnahme für die Demo-Tour: zeigt den "Karte"-Button unabhängig von
     * [weatherMapEnabled], damit der Rundgang den GPS-Track auch bei ausgeschaltetem "Wetter &
     * Karte" vorführen kann. Gilt nie für echte Touren.
     */
    forceVisibleForTutorial: Boolean = false,
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
                trackStatusContent = trackStatusContent,
                trackSectionContent = trackSectionContent,
                forceVisibleForTutorial = forceVisibleForTutorial,
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
    trackStatusContent: (@Composable () -> Unit)?,
    trackSectionContent: (@Composable () -> Unit)?,
    forceVisibleForTutorial: Boolean,
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
            TourHeaderCard(
                tour = tour,
                vehicle = vehicle,
                metrics = metrics,
                totalCosts = totalCosts,
                locale = locale,
                finishing = finishing,
                onFinish = onFinish,
                trackStatusContent = trackStatusContent,
            )
        }
        if (vignetteWarnings.isNotEmpty()) {
            item { VignetteWarningsCard(warnings = vignetteWarnings, locale = locale, onOpenStation = onOpenStation) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.stations_section_title, stations.size),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (isMapAvailable(weatherMapEnabled, stations) || forceVisibleForTutorial) {
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
            var expanded by rememberSaveable { mutableStateOf(false) }
            SectionCard {
                CollapsibleSection(
                    title = stringResource(R.string.diary_section_title),
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    summary = diaryEntries.takeIf { it.isNotEmpty() }
                        ?.let { pluralStringResource(R.plurals.diary_entry_count, it.size, it.size) },
                ) {
                    DiarySection(entries = diaryEntries, locale = locale, onOpenEntry = onOpenDiaryEntry, onAdd = onAddDiaryEntry)
                }
            }
        }
        item {
            var expanded by rememberSaveable { mutableStateOf(false) }
            SectionCard {
                CollapsibleSection(
                    title = stringResource(R.string.section_checklists),
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    summary = checklistsSummary(checklists),
                ) {
                    ChecklistsSection(checklists = checklists, onOpenChecklist = onOpenChecklist, onStart = onStartChecklist)
                }
            }
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
        if (trackSectionContent != null) {
            item {
                var expanded by rememberSaveable { mutableStateOf(false) }
                SectionCard {
                    CollapsibleSection(
                        title = stringResource(R.string.tour_track_title),
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        summary = null,
                    ) {
                        trackSectionContent()
                    }
                }
            }
        }
        if (tour.notes.isNotBlank() || tour.mapLink != null) {
            item {
                var expanded by rememberSaveable { mutableStateOf(false) }
                SectionCard {
                    CollapsibleSection(
                        title = stringResource(R.string.field_notes),
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        summary = tour.notes.takeIf(String::isNotBlank)?.take(NOTES_SUMMARY_LENGTH),
                    ) {
                        if (tour.notes.isNotBlank()) {
                            Text(tour.notes, style = MaterialTheme.typography.bodyLarge)
                        }
                        tour.mapLink?.let {
                            Text(
                                stringResource(R.string.field_map_link),
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private const val NOTES_SUMMARY_LENGTH = 80

/**
 * Getönte Kopfkarte der Tourdetailseite: Status und Pausieren-Button der laufenden Aufzeichnung
 * ([trackStatusContent], nur bei aktiver Aufzeichnung dieser Tour), Zeitraum, Tourart und
 * Fahrzeugname (nur bei mehr als einem Fahrzeug) in einer Zeile, die Kennzahlen als Kacheln, die
 * Gesamtkosten und – für eine laufende Tour – der Beenden-Button. Sekundärfarbe (secondaryContainer)
 * nur bei laufender Tour, sonst neutral wie die übrigen Karten.
 */
@Composable
private fun TourHeaderCard(
    tour: Tour,
    vehicle: Vehicle?,
    metrics: TourMetrics,
    totalCosts: List<Money>,
    locale: Locale,
    finishing: Boolean,
    onFinish: () -> Unit,
    trackStatusContent: (@Composable () -> Unit)?,
) {
    val running = tour.endDate == null
    val contentColor = if (running) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (running) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Text(stringResource(R.string.tour_running_day, metrics.travelDays), style = MaterialTheme.typography.titleSmall, color = contentColor)
            }
            if (trackStatusContent != null) trackStatusContent()
            val periodParts = listOfNotNull(
                tour.period(locale),
                stringResource(tour.tourType.labelRes),
                vehicle?.let { vehicleDisplayName(it) },
            )
            Text(periodParts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = contentColor)
            // "Ziel" ist nur dann eine eigene Zeile wert, wenn der Titel (siehe TopBar) stattdessen den Namen zeigt.
            if (tour.name.isNotBlank() && tour.destination.isNotBlank()) {
                Text(
                    "${stringResource(R.string.field_destination)}: ${tour.destination}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    value = metrics.travelDays.toString(),
                    label = stringResource(R.string.field_travel_days),
                    contentDescription = pluralStringResource(R.plurals.share_travel_days, metrics.travelDays, metrics.travelDays),
                )
                MetricTile(
                    value = metrics.overnightStays.toString(),
                    label = stringResource(R.string.field_overnight_stays),
                    contentDescription = pluralStringResource(R.plurals.share_overnight_stays, metrics.overnightStays, metrics.overnightStays),
                )
                val distanceRes = if (metrics.distanceIsEstimated) R.string.distance_km_estimated else R.string.distance_km
                val distanceCdRes = if (metrics.distanceIsEstimated) R.plurals.distance_km_estimated_cd else R.plurals.distance_km_cd
                MetricTile(
                    value = stringResource(distanceRes, metrics.distanceKm),
                    label = stringResource(R.string.label_distance),
                    contentDescription = pluralStringResource(distanceCdRes, metrics.distanceKm, metrics.distanceKm),
                )
            }
            Text(
                stringResource(R.string.tour_header_total_costs, formatAmounts(totalCosts, locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            if (running) {
                FilledTonalButton(onClick = onFinish, enabled = !finishing) {
                    Text(stringResource(R.string.tour_finish))
                }
            }
        }
    }
}

/** Eine Kennzahlen-Kachel der Kopfkarte: großer Wert über kleinem Bezeichner, als eine Sprachausgabe [contentDescription]. */
@Composable
private fun MetricTile(value: String, label: String, contentDescription: String) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Kostenkarte der Tourdetailseite, aufklappbar: manuelle Tourkosten ("Sonstige Kosten"), die Summe
 * der Stationskosten ("Stationen"), die Gesamtsumme je Währung (auch die Kurzfassung im
 * eingeklappten Zustand), eine Umrechnung in die Hauptwährung (siehe [Conversion]) sowie eine
 * weitere aufklappbare Aufschlüsselung der Stationskosten nach Kategorie.
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        CollapsibleSection(
            title = stringResource(R.string.field_cost),
            expanded = expanded,
            onToggle = { expanded = !expanded },
            summary = formatAmounts(totalCosts, locale),
        ) {
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
                var categoryExpanded by rememberSaveable { mutableStateOf(false) }
                CollapsibleSection(
                    title = stringResource(R.string.cost_breakdown_by_category),
                    expanded = categoryExpanded,
                    onToggle = { categoryExpanded = !categoryExpanded },
                    summary = null,
                ) {
                    categoryCosts.entries.sortedBy { it.key.ordinal }.forEach { (category, amounts) ->
                        LabeledValue(stringResource(category.labelRes), formatAmounts(amounts, locale))
                    }
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

/**
 * Kurzfassung für den eingeklappten Checklisten-Abschnitt: bei genau einer Checkliste ihr Titel
 * mit Fortschritt (z. B. "Packliste · Erledigt: 12/20"), bei mehreren der Gesamtfortschritt.
 */
@Composable
private fun checklistsSummary(checklists: List<Checklist>): String? {
    if (checklists.isEmpty()) return null
    val single = checklists.singleOrNull()
    return if (single != null) {
        stringResource(
            R.string.checklist_summary_with_title,
            single.title,
            stringResource(R.string.checklist_progress, single.checkedCount, single.items.size),
        )
    } else {
        stringResource(R.string.checklist_progress, checklists.sumOf { it.checkedCount }, checklists.sumOf { it.items.size })
    }
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
