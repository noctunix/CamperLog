package app.restvolt.camperlog.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.restvolt.camperlog.R
import java.time.LocalDate

/** Datumsdialog zum Abschließen einer Tour; erlaubt nur Tage vom früheren von Tourstart/heute bis heute. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinishTourDialog(
    tourStart: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectableDates = remember(tourStart, today) {
        val first = minOf(tourStart, today).toEpochDay()
        val last = today.toEpochDay()
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) in first..last
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = today.toEpochDay() * MILLIS_PER_DAY,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY))) }
                },
            ) { Text(stringResource(R.string.tour_finish)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(
            state = state,
            title = { Text(stringResource(R.string.tour_finish_dialog_title)) },
        )
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
