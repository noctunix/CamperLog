package de.hannes.camperlog.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hannes.camperlog.R
import de.hannes.camperlog.domain.ElectricityFlatRate
import de.hannes.camperlog.domain.LteQuality
import de.hannes.camperlog.domain.PitchSlope
import de.hannes.camperlog.domain.TourField
import de.hannes.camperlog.domain.TourType
import de.hannes.camperlog.domain.formatDate
import de.hannes.camperlog.ui.BackTopBar
import de.hannes.camperlog.ui.EmptyHint
import de.hannes.camperlog.ui.SectionCard
import java.time.LocalDate

/** Formular zum Anlegen und Bearbeiten einer Tour. [onDone] verlässt es ohne, [onSaved] nach dem Speichern. */
@Composable
fun EditTourScreen(viewModel: EditTourViewModel, onDone: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar("Tour konnte nicht gespeichert werden.", withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = state.isDirty && !state.isSaved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            BackTopBar(
                title = if (state.isNew) "Neue Tour" else "Tour bearbeiten",
                onBack = requestBack,
                actions = {
                    if (!state.notFound && !state.isLoading) {
                        TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text("Speichern") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.notFound -> EmptyHint("Diese Tour gibt es nicht mehr.", Modifier.padding(padding))
            else -> TourForm(
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
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Änderungen verwerfen?") },
            text = { Text("Deine Eingaben gehen verloren.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDone()
                }) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Weiter bearbeiten") }
            },
        )
    }
}

@Composable
private fun TourForm(state: EditUiState, viewModel: EditTourViewModel, modifier: Modifier) {
    val input = state.input
    val errors = state.errors
    val change = viewModel::onInputChange
    val focus = remember { TourField.entries.associateWith { FocusRequester() } }
    fun focusOf(field: TourField) = Modifier.focusRequester(focus.getValue(field))

    // Nach einem abgelehnten Speichern zum ersten fehlerhaften Feld springen; der Fokus scrollt es ins Bild.
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
            DateField(
                "Startdatum",
                input.startDate,
                errors[TourField.START_DATE],
                viewModel::onStartDateChange,
                modifier = focusOf(TourField.START_DATE),
            )
            DateField(
                "Enddatum",
                input.endDate,
                errors[TourField.END_DATE],
                viewModel::onEndDateChange,
                initialDate = input.startDate,
                minDate = input.startDate,
                modifier = focusOf(TourField.END_DATE),
            )
            FormTextField(
                label = "Ziel",
                value = input.destination,
                error = errors[TourField.DESTINATION],
                onValueChange = { value -> change { it.copy(destination = value) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = focusOf(TourField.DESTINATION),
            )
            ChoiceField("Tourart", TourType.entries, input.tourType, TourType::label) { value ->
                change { it.copy(tourType = value) }
            }
        }
        SectionCard {
            NumberField(
                "Reisetage",
                input.travelDays,
                errors[TourField.TRAVEL_DAYS],
                focusOf(TourField.TRAVEL_DAYS),
            ) { value -> change { it.copy(travelDays = value) } }
            NumberField(
                "Übernachtungen",
                input.overnightStays,
                errors[TourField.OVERNIGHT_STAYS],
                focusOf(TourField.OVERNIGHT_STAYS),
            ) { value -> change { it.copy(overnightStays = value) } }
            NumberField(
                "Kilometer",
                input.distanceKm,
                errors[TourField.DISTANCE_KM],
                focusOf(TourField.DISTANCE_KM),
            ) { value -> change { it.copy(distanceKm = value) } }
            FormTextField(
                label = "Kosten (€)",
                value = input.cost,
                error = errors[TourField.COST],
                onValueChange = { value -> change { it.copy(cost = value) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                placeholder = "z. B. 49,90",
                modifier = focusOf(TourField.COST),
            )
        }
        SectionCard {
            SwitchRow("Stellplatz zugewiesen", input.pitchAssigned) { value ->
                change { it.copy(pitchAssigned = value) }
            }
            ChoiceField(
                "Strompauschale",
                ElectricityFlatRate.entries,
                input.electricityFlatRate,
                ElectricityFlatRate::label,
            ) { value -> change { it.copy(electricityFlatRate = value) } }
            ChoiceField("LTE", LteQuality.entries, input.lteQuality, LteQuality::label) { value ->
                change { it.copy(lteQuality = value) }
            }
            ChoiceField("Stellplatz", PitchSlope.entries, input.pitchSlope, PitchSlope::label) { value ->
                change { it.copy(pitchSlope = value) }
            }
            SwitchRow("Keile genutzt", input.levelingBlocksUsed) { value ->
                change { it.copy(levelingBlocksUsed = value) }
            }
        }
        SectionCard {
            FormTextField(
                label = "Notizen",
                value = input.notes,
                error = null,
                onValueChange = { value -> change { it.copy(notes = value) } },
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            FormTextField(
                label = "Kartenlink",
                value = input.mapLink,
                error = errors[TourField.MAP_LINK],
                onValueChange = { value -> change { it.copy(mapLink = value) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                placeholder = "https://…",
                modifier = focusOf(TourField.MAP_LINK),
            )
        }
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) { Text("Speichern") }
        if (errors.isNotEmpty()) {
            Text(
                "Bitte die markierten Felder prüfen.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
    )
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
private fun DateField(
    label: String,
    date: LocalDate?,
    error: String?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    initialDate: LocalDate? = null,
    minDate: LocalDate? = null,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) showPicker = true }
    }

    OutlinedTextField(
        value = date?.let(::formatDate).orEmpty(),
        onValueChange = {},
        readOnly = true,
        modifier = modifier
            .fillMaxWidth()
            // Tastatur: Enter/Leertaste öffnen den Kalender wie ein Tippen.
            .onPreviewKeyEvent { event ->
                val opens = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
                if (opens && event.type == KeyEventType.KeyUp) showPicker = true
                opens
            }
            // Screenreader: Doppeltippen öffnet den Kalender statt nur den Fokus zu setzen.
            .semantics {
                onClick(label = "$label wählen") {
                    showPicker = true
                    true
                }
            },
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = "$label wählen")
            }
        },
        singleLine = true,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: initialDate)?.let { it.toEpochDay() * MILLIS_PER_DAY },
            selectableDates = minDate?.let(::notBefore) ?: DatePickerDefaults.AllDates,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDateSelected(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY)))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Sperrt im Kalender alle Tage vor [minDate], z. B. Enddaten vor dem Start. */
@OptIn(ExperimentalMaterial3Api::class)
private fun notBefore(minDate: LocalDate) = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) =
        Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) >= minDate.toEpochDay()

    override fun isSelectableYear(year: Int) = year >= minDate.year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                ) {
                    Text(optionLabel(option), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
