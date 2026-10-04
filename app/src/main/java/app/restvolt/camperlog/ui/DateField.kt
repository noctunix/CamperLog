package app.restvolt.camperlog.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.formatDate
import java.time.LocalDate

/**
 * Schreibgeschütztes Datumsfeld, das per Tippen, Tastatur oder Screenreader einen Kalender öffnet.
 * [initialDate] ist der vorausgewählte Tag, solange [date] leer ist; [minDate] sperrt frühere Tage.
 * [hint] steht unter dem Feld, solange kein [error] angezeigt wird.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    label: String,
    date: LocalDate?,
    error: String?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    initialDate: LocalDate? = null,
    minDate: LocalDate? = null,
    hint: String? = null,
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
        supportingText = (error ?: hint)?.let { { Text(it) } },
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

private const val MILLIS_PER_DAY = 86_400_000L
