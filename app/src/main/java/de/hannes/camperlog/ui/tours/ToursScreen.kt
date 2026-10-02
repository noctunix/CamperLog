package de.hannes.camperlog.ui.tours

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hannes.camperlog.R
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.period
import de.hannes.camperlog.share.shareCsv
import de.hannes.camperlog.share.writeCsvExport
import de.hannes.camperlog.ui.EmptyHint
import kotlinx.coroutines.launch
import java.io.IOException

/** Startseite: Tourenliste mit Suche, Jahresfilter und Einstieg in Eingabe, Übersicht und CSV-Export. */
@Composable
fun ToursScreen(
    viewModel: ToursViewModel,
    onAddTour: () -> Unit,
    onOpenOverview: () -> Unit,
    onOpenTour: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exporting by remember { mutableStateOf(false) }

    val exportCsv: () -> Unit = {
        exporting = true
        scope.launch {
            try {
                val tours = viewModel.toursForExport()
                if (tours.isEmpty()) {
                    snackbar.showSnackbar("Noch keine Touren zum Exportieren")
                } else {
                    context.shareCsv(writeCsvExport(context, tours))
                }
            } catch (_: IOException) {
                snackbar.showSnackbar("CSV-Export fehlgeschlagen")
            } finally {
                exporting = false
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }
            item {
                ActionButtons(
                    onAddTour = onAddTour,
                    onOpenOverview = onOpenOverview,
                    onExport = exportCsv,
                    exportEnabled = !exporting,
                )
            }
            if (state.hasAnyTour) {
                item {
                    SearchField(state.query, viewModel::onQueryChange)
                }
                item {
                    YearFilter(state.years, state.selectedYear, viewModel::onYearSelected)
                }
            }
            when {
                state.isLoading -> Unit
                !state.hasAnyTour -> item { EmptyHint("Noch keine Touren. Lege mit „Eingabe“ die erste Fahrt an.") }
                state.tours.isEmpty() -> item { EmptyHint("Keine Tour passt zu Suche und Filter.") }
                else -> items(state.tours, key = Tour::id) { tour ->
                    TourCard(
                        tour = tour,
                        onClick = { onOpenTour(tour.id) },
                        onDelete = { pendingDeleteId = tour.id },
                    )
                }
            }
        }
    }

    val tourToDelete = state.tours.firstOrNull { it.id == pendingDeleteId }
    if (tourToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Tour löschen?") },
            text = { Text("„${tourToDelete.destination}“ (${tourToDelete.period}) wird endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(tourToDelete)
                    pendingDeleteId = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Touren", style = MaterialTheme.typography.displaySmall)
        Text(
            "Logbuch Hannes – unsere Wohnmobil-Fahrten",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionButtons(
    onAddTour: () -> Unit,
    onOpenOverview: () -> Unit,
    onExport: () -> Unit,
    exportEnabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val buttonModifier = Modifier.weight(1f).heightIn(min = 56.dp)
        val contentPadding = PaddingValues(horizontal = 8.dp)
        Button(onClick = onAddTour, modifier = buttonModifier, contentPadding = contentPadding) {
            ButtonContent(R.drawable.ic_add, "Eingabe")
        }
        OutlinedButton(onClick = onOpenOverview, modifier = buttonModifier, contentPadding = contentPadding) {
            ButtonContent(R.drawable.ic_bar_chart, "Übersicht")
        }
        OutlinedButton(
            onClick = onExport,
            enabled = exportEnabled,
            modifier = buttonModifier,
            contentPadding = contentPadding,
        ) {
            ButtonContent(R.drawable.ic_download, "CSV")
        }
    }
}

@Composable
private fun ButtonContent(icon: Int, label: String) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(label, maxLines = 1)
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Ziel suchen") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun YearFilter(years: List<Int>, selected: Int?, onSelect: (Int?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Alle Jahre") })
        }
        items(years) { year ->
            FilterChip(
                selected = selected == year,
                onClick = { onSelect(if (selected == year) null else year) },
                label = { Text(year.toString()) },
            )
        }
    }
}

@Composable
private fun TourCard(tour: Tour, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Details öffnen", onClick = onClick)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tour.destination, style = MaterialTheme.typography.titleMedium)
                Text(
                    tour.period,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Jahr ${tour.year} · ${tour.tourType.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "Tour nach ${tour.destination} löschen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
