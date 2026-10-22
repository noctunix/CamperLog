package app.restvolt.camperlog.ui.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.effectiveCosts
import app.restvolt.camperlog.domain.formatAmounts
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.formatLiters
import app.restvolt.camperlog.domain.fuelPricePerLiter
import app.restvolt.camperlog.domain.sumByCurrency
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.attachments.AndroidAttachmentPickers
import app.restvolt.camperlog.ui.attachments.AttachmentPickers
import app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.detail.RatingDisplay
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.logDateText
import java.time.LocalDate

/** Verlauf einer Bordbuch-Art eines Fahrzeugs: alle Einträge, neueste zuerst, mit Löschen und Rückgängig. */
@Composable
fun LogHistoryScreen(
    viewModel: LogHistoryViewModel,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    attachmentPickers: AttachmentPickers = AndroidAttachmentPickers,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val locale = currentLocale()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    var photosEntry by rememberSaveable { mutableStateOf<Long?>(null) }
    var factsEntry by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { BackTopBar(title = stringResource(viewModel.type.labelRes), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.entries.isEmpty()) {
            EmptyHint(stringResource(R.string.logbook_history_empty), modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
            ) {
                items(state.entries, key = LogEntry::id) { entry ->
                    val station = entry.stationId?.let { id -> state.stations.firstOrNull { it.id == id } }
                    HistoryRow(
                        entry = entry,
                        station = station,
                        today = today,
                        onDelete = { viewModel.delete(entry) },
                        onPhotos = { photosEntry = entry.id },
                        onFacts = { factsEntry = entry.id },
                    )
                }
            }
        }
    }

    // Die Meldung gilt erst nach vollständiger Anzeige als erledigt; wer den Verlauf währenddessen
    // verlässt, sieht sie bei der Rückkehr erneut und kann das Löschen noch rückgängig machen.
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is LogHistoryMessage.Deleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.logbook_entry_deleted),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(current)
            }
            is LogHistoryMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }

    factsEntry?.let { entryId ->
        state.entries.firstOrNull { it.id == entryId }?.let { entry ->
            val station = entry.stationId?.let { id -> state.stations.firstOrNull { it.id == id } }
            LogEntryFactsSheet(entry, station, today, onDismiss = { factsEntry = null })
        }
    }

    photosEntry?.let { entryId ->
        LogEntryPhotosSheet(
            entryId = entryId,
            attachments = attachments,
            attachmentFileStore = attachmentFileStore,
            attachmentPickers = attachmentPickers,
            onDismiss = { photosEntry = null },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: LogEntry,
    station: Station?,
    today: LocalDate,
    onDelete: () -> Unit,
    onPhotos: () -> Unit,
    onFacts: () -> Unit,
) {
    val locale = currentLocale()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.logbook_entry_facts_action), onClick = onFacts)
                .padding(vertical = 12.dp),
        ) {
            val dateText = logDateText(entry.date, today, locale)
            val text = station?.let { s ->
                val name = s.name.ifBlank { stringResource(s.type.labelRes) }
                stringResource(R.string.logbook_entry_station, dateText, name)
            } ?: dateText
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onPhotos) {
            Icon(painterResource(R.drawable.ic_add_a_photo), contentDescription = stringResource(R.string.logbook_entry_photos_action))
        }
        IconButton(onClick = onDelete) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.logbook_delete_entry, formatDate(entry.date, locale)),
            )
        }
    }
}

/** Fotos eines einzelnen Bordbuch-Eintrags als Bottom Sheet, damit die Verlaufsliste selbst schlank bleibt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogEntryPhotosSheet(
    entryId: Long,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    attachmentPickers: AttachmentPickers,
    onDismiss: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            PhotoAttachmentsSection(
                ownerType = AttachmentOwnerType.LOG_ENTRY,
                ownerId = entryId,
                repository = attachments,
                fileStore = attachmentFileStore,
                snackbarHostState = snackbar,
                pickers = attachmentPickers,
            )
        }
        SnackbarHost(snackbar)
    }
}

/**
 * Kurzfakten eines Bordbuch-Eintrags als reines Lese-Popup: Datum und, bei einem mit einer Station
 * verknüpften Eintrag, deren Name, Kosten, Bewertung sowie bei Tanken Liter und Literpreis.
 * Es gibt bewusst keine Eingabe, damit das Ein-Tap-Erfassen schlank bleibt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogEntryFactsSheet(entry: LogEntry, station: Station?, today: LocalDate, onDismiss: () -> Unit) {
    val locale = currentLocale()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(entry.type.labelRes), style = MaterialTheme.typography.titleMedium)
            Text(logDateText(entry.date, today, locale), style = MaterialTheme.typography.bodyLarge)
            if (station == null) {
                Text(stringResource(R.string.logbook_facts_none), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(station.name.ifBlank { stringResource(station.type.labelRes) }, style = MaterialTheme.typography.bodyLarge)
            station.fuelLiters?.let {
                Text(
                    stringResource(R.string.station_summary_field, stringResource(R.string.field_fuel_liters), formatLiters(it, locale)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            fuelPricePerLiter(station, locale)?.let {
                Text(
                    stringResource(R.string.station_summary_field, stringResource(R.string.station_fuel_price_per_liter), it),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            val costs = station.effectiveCosts().map { it.amount }.sumByCurrency()
            if (costs.isNotEmpty()) {
                Text(
                    stringResource(R.string.station_summary_field, stringResource(R.string.logbook_facts_costs), formatAmounts(costs, locale)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            station.rating?.let { RatingDisplay(it) }
        }
    }
}
