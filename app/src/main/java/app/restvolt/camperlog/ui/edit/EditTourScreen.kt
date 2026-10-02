package app.restvolt.camperlog.ui.edit

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.messageRes
import java.time.LocalDate

/** Formular zum Anlegen und Bearbeiten einer Tour. [onDone] verlässt es ohne, [onSaved] nach dem Speichern. */
@Composable
fun EditTourScreen(viewModel: EditTourViewModel, onDone: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.edit_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
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
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDone()
                }) { Text(stringResource(R.string.edit_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.edit_discard_keep)) }
            },
        )
    }
}

@Composable
private fun TourForm(state: EditUiState, viewModel: EditTourViewModel, modifier: Modifier) {
    val input = state.input
    val errors = state.errors.mapValues { (field, error) -> stringResource(error.messageRes(field)) }
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
                stringResource(R.string.field_start_date),
                input.startDate,
                errors[TourField.START_DATE],
                viewModel::onStartDateChange,
                modifier = focusOf(TourField.START_DATE),
            )
            DateField(
                stringResource(R.string.field_end_date),
                input.endDate,
                errors[TourField.END_DATE],
                viewModel::onEndDateChange,
                initialDate = input.startDate,
                minDate = input.startDate,
                modifier = focusOf(TourField.END_DATE),
            )
            FormTextField(
                label = stringResource(R.string.field_destination),
                value = input.destination,
                error = errors[TourField.DESTINATION],
                onValueChange = { value -> change { it.copy(destination = value) } },
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
            FormTextField(
                label = stringResource(R.string.field_cost_euro),
                value = input.cost,
                error = errors[TourField.COST],
                onValueChange = { value -> change { it.copy(cost = value) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                placeholder = stringResource(R.string.edit_cost_placeholder),
                modifier = focusOf(TourField.COST),
            )
        }
        SectionCard {
            SwitchRow(stringResource(R.string.field_pitch_assigned), input.pitchAssigned) { value ->
                change { it.copy(pitchAssigned = value) }
            }
            ChoiceField(
                stringResource(R.string.field_electricity),
                ElectricityFlatRate.entries,
                input.electricityFlatRate,
                ElectricityFlatRate::labelRes,
            ) { value -> change { it.copy(electricityFlatRate = value) } }
            ChoiceField(stringResource(R.string.field_lte), LteQuality.entries, input.lteQuality, LteQuality::labelRes) { value ->
                change { it.copy(lteQuality = value) }
            }
            ChoiceField(stringResource(R.string.field_pitch), PitchSlope.entries, input.pitchSlope, PitchSlope::labelRes) { value ->
                change { it.copy(pitchSlope = value) }
            }
            SwitchRow(stringResource(R.string.field_leveling_blocks), input.levelingBlocksUsed) { value ->
                change { it.copy(levelingBlocksUsed = value) }
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
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.action_save)) }
        if (errors.isNotEmpty()) {
            Text(
                stringResource(R.string.edit_check_fields),
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
    val pickLabel = stringResource(R.string.edit_pick_date, label)
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) showPicker = true }
    }

    OutlinedTextField(
        value = date?.let { formatDate(it, currentLocale()) }.orEmpty(),
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
                onClick(label = pickLabel) {
                    showPicker = true
                    true
                }
            },
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = pickLabel)
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
