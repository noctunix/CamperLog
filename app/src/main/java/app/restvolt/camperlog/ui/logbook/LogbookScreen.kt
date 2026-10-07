package app.restvolt.camperlog.ui.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.TabTopBar
import app.restvolt.camperlog.ui.VehicleSwitcherTitle
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.logDateText
import java.time.LocalDate

/** Bordbuch-Reiter: eine Kachel je Bordbuch-Art mit Schnellerfassung für heute und ein beliebiges Datum. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(
    viewModel: LogbookViewModel,
    onOpenHistory: (Long, LogType) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVehicles: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val locale = currentLocale()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TabTopBar(
                titleContent = {
                    VehicleSwitcherTitle(
                        vehicles = state.vehicles,
                        currentVehicleId = state.currentVehicleId,
                        title = stringResource(R.string.logbook_title),
                        onSelectVehicle = viewModel::onSelectVehicle,
                        onManageVehicles = onOpenVehicles,
                    )
                },
                onOpenSearch = onOpenSearch,
                onOpenData = onOpenData,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.tiles.forEach { tile ->
                LogTile(
                    tile = tile,
                    today = today,
                    onToday = { viewModel.recordToday(tile.type) },
                    onOtherDate = { date -> viewModel.recordDate(tile.type, date) },
                    onOpenHistory = { onOpenHistory(state.currentVehicleId, tile.type) },
                )
            }
        }
    }

    // Die Meldung gilt erst nach vollständiger Anzeige als erledigt; wer die Kacheln währenddessen
    // verlässt, sieht sie bei der Rückkehr erneut und kann das Erfassen noch rückgängig machen.
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is LogbookMessage.Recorded -> {
                val typeLabel = resources.getString(current.entry.type.labelRes)
                val text = if (current.wasToday) {
                    resources.getString(R.string.logbook_recorded_today, typeLabel)
                } else {
                    resources.getString(R.string.logbook_recorded_on, typeLabel, formatDate(current.entry.date, locale))
                }
                val result = snackbar.showSnackbar(
                    message = text,
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoRecord(current.entry)
            }
            is LogbookMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }
}

@Composable
private fun LogTile(
    tile: LogTileState,
    today: LocalDate,
    onToday: () -> Unit,
    onOtherDate: (LocalDate) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val locale = currentLocale()
    val historyLabel = stringResource(R.string.logbook_open_history)
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = historyLabel, onClick = onOpenHistory),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(tile.type.iconRes()), contentDescription = null)
            Column {
                Text(stringResource(tile.type.labelRes), style = MaterialTheme.typography.titleMedium)
                val dateText = tile.lastDate?.let { logDateText(it, today, locale) } ?: stringResource(R.string.logbook_not_recorded)
                Text(dateText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onToday) { Text(stringResource(R.string.logbook_today)) }
            OtherDateAction(today = today, onDateSelected = onOtherDate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherDateAction(today: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showPicker = true }) { Text(stringResource(R.string.logbook_other_date)) }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = today.toEpochDay() * MILLIS_PER_DAY,
            selectableDates = notAfter(today),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY))) }
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun notAfter(maxDate: LocalDate) = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) <= maxDate.toEpochDay()
    override fun isSelectableYear(year: Int) = year <= maxDate.year
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun LogType.iconRes() = when (this) {
    LogType.CASSETTE_EMPTIED -> R.drawable.ic_toilet
    LogType.GREY_WATER_EMPTIED -> R.drawable.ic_water_drop
    LogType.DIESEL_HEATER_RUN -> R.drawable.ic_local_fire
    LogType.GAS_HEATER_RUN -> R.drawable.ic_gas_flame
    LogType.GAS_BOTTLE_SWAPPED -> R.drawable.ic_gas_bottle
}
