package app.restvolt.camperlog.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.SearchSnippet
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import java.util.Locale

/**
 * Volltextsuche über Touren, Stationen, Tagebuch, Bordbuch, Reparaturen, Checklisten (mit ihren
 * Vorlagen), Fahrzeugdokumente und Fahrzeuge. Ein Treffer öffnet die vorhandene Bildschirmseite
 * dafür, über die jeweilige `onOpen…`-Aktion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenTour: (Long) -> Unit,
    onOpenStop: (Long) -> Unit,
    onOpenDiaryEntry: (tourId: Long, entryId: Long) -> Unit,
    onOpenLogbookHistory: (vehicleId: Long, type: LogType) -> Unit,
    onOpenRepair: (vehicleId: Long, repairId: Long) -> Unit,
    onOpenChecklist: (checklistId: Long, vehicleId: Long, tourId: Long?) -> Unit,
    onOpenChecklistTemplate: (templateId: Long) -> Unit,
    onOpenVehicleDocument: (vehicleId: Long, documentId: Long) -> Unit,
    onOpenVehicle: (vehicleId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val resources = LocalResources.current
    val focusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            SearchTopBar(query = state.query, onQueryChange = viewModel::onQueryChange, onBack = onBack, focusRequester = focusRequester)
        },
    ) { padding ->
        when {
            state.isQueryTooShort -> EmptyHint(stringResource(R.string.search_hint_min_length), modifier = Modifier.padding(padding))
            state.groups.isEmpty() -> EmptyHint(stringResource(R.string.search_no_results), modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header_${group.type}") {
                        Text(
                            stringResource(R.string.search_group_header, stringResource(groupLabelRes(group.type)), group.results.size),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { heading() },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(group.results, key = ::resultKey) { result ->
                        SearchResultRow(
                            result = result,
                            resources = resources,
                            locale = locale,
                            onClick = {
                                openResult(
                                    result,
                                    onOpenTour,
                                    onOpenStop,
                                    onOpenDiaryEntry,
                                    onOpenLogbookHistory,
                                    onOpenRepair,
                                    onOpenChecklist,
                                    onOpenChecklistTemplate,
                                    onOpenVehicleDocument,
                                    onOpenVehicle,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit, focusRequester: FocusRequester) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.action_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear_action))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.medium,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun SearchResultRow(result: SearchResult, resources: android.content.res.Resources, locale: Locale, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.tours_open_details), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(resultTitle(result, resources, locale), style = MaterialTheme.typography.titleMedium)
        Text(resultSupportingText(result, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        result.vehicleName?.let { name ->
            Text(
                name.ifBlank { stringResource(R.string.vehicle_default_name) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Titel einer Ergebniszeile; braucht [resources] für die lokalisierten Rückfall-Titel ohne eigenen Freitext. */
private fun resultTitle(result: SearchResult, resources: android.content.res.Resources, locale: Locale): String = when (result) {
    is SearchResult.TourResult -> result.destination
    is SearchResult.StopResult -> result.name.ifBlank { resources.getString(result.stationType.labelRes) }
    is SearchResult.DiaryResult -> "${formatDate(result.date, locale)} – ${result.tourDestination}"
    is SearchResult.LogbookResult -> result.label
    is SearchResult.RepairResult -> result.description
    is SearchResult.ChecklistResult -> result.title.ifBlank { resources.getString(R.string.checklist_title_fallback) }
    is SearchResult.ChecklistTemplateResult -> result.title
    is SearchResult.VehicleDocumentResult -> result.title
    is SearchResult.VehicleResult -> result.title.ifBlank { resources.getString(R.string.vehicle_default_name) }
}

/** Zusatzzeile einer Ergebniszeile: Datum, ergänzt um den Ausschnitt des Treffers mit fett hervorgehobenen Treffern. */
private fun resultSupportingText(result: SearchResult, locale: Locale): AnnotatedString = buildAnnotatedString {
    append(formatDate(result.date, locale))
    result.snippet?.let { snippet ->
        append(" · ")
        appendSnippet(snippet)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendSnippet(snippet: SearchSnippet) {
    val start = length
    append(snippet.text)
    for (range in snippet.boldRanges) {
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start + range.first, start + range.last + 1)
    }
}

private fun groupLabelRes(type: SearchResultType): Int = when (type) {
    SearchResultType.TOUR -> R.string.search_group_tours
    SearchResultType.STOP -> R.string.search_group_stops
    SearchResultType.DIARY -> R.string.search_group_diary
    SearchResultType.LOGBOOK -> R.string.search_group_logbook
    SearchResultType.REPAIR -> R.string.search_group_repairs
    SearchResultType.CHECKLIST -> R.string.search_group_checklists
    SearchResultType.CHECKLIST_TEMPLATE -> R.string.search_group_checklist_templates
    SearchResultType.VEHICLE_DOCUMENT -> R.string.search_group_vehicle_documents
    SearchResultType.VEHICLE -> R.string.search_group_vehicles
}

private fun resultKey(result: SearchResult): String = when (result) {
    is SearchResult.TourResult -> "tour_${result.tourId}"
    is SearchResult.StopResult -> "stop_${result.stationId}"
    is SearchResult.DiaryResult -> "diary_${result.entryId}"
    is SearchResult.LogbookResult -> "logbook_${result.entryId}"
    is SearchResult.RepairResult -> "repair_${result.repairId}"
    is SearchResult.ChecklistResult -> "checklist_${result.checklistId}"
    is SearchResult.ChecklistTemplateResult -> "template_${result.templateId}"
    is SearchResult.VehicleDocumentResult -> "document_${result.documentId}"
    is SearchResult.VehicleResult -> "vehicle_${result.vehicleId}"
}

private fun openResult(
    result: SearchResult,
    onOpenTour: (Long) -> Unit,
    onOpenStop: (Long) -> Unit,
    onOpenDiaryEntry: (Long, Long) -> Unit,
    onOpenLogbookHistory: (Long, LogType) -> Unit,
    onOpenRepair: (Long, Long) -> Unit,
    onOpenChecklist: (Long, Long, Long?) -> Unit,
    onOpenChecklistTemplate: (Long) -> Unit,
    onOpenVehicleDocument: (Long, Long) -> Unit,
    onOpenVehicle: (Long) -> Unit,
) {
    when (result) {
        is SearchResult.TourResult -> onOpenTour(result.tourId)
        is SearchResult.StopResult -> onOpenStop(result.stationId)
        is SearchResult.DiaryResult -> onOpenDiaryEntry(result.tourId, result.entryId)
        is SearchResult.LogbookResult -> onOpenLogbookHistory(result.vehicleId, result.logType)
        is SearchResult.RepairResult -> onOpenRepair(result.vehicleId, result.repairId)
        is SearchResult.ChecklistResult -> onOpenChecklist(result.checklistId, result.vehicleId, result.tourId)
        is SearchResult.ChecklistTemplateResult -> onOpenChecklistTemplate(result.templateId)
        is SearchResult.VehicleDocumentResult -> onOpenVehicleDocument(result.vehicleId, result.documentId)
        is SearchResult.VehicleResult -> onOpenVehicle(result.vehicleId)
    }
}
