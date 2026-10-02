package app.restvolt.camperlog.ui.tours

import android.database.SQLException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.period
import app.restvolt.camperlog.share.shareCsv
import app.restvolt.camperlog.share.writeCsvExport
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import kotlinx.coroutines.launch
import java.io.IOException

/** Startseite: Tourenliste mit Suche, Jahresfilter und Einstieg in Eingabe, Übersicht und CSV-Export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToursScreen(
    viewModel: ToursViewModel,
    onAddTour: () -> Unit,
    onOpenOverview: () -> Unit,
    onOpenTour: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    var exporting by remember { mutableStateOf(false) }

    val exportCsv: () -> Unit = {
        exporting = true
        scope.launch {
            val problem = try {
                val tours = viewModel.toursForExport()
                when {
                    tours.isEmpty() -> R.string.export_nothing
                    !context.shareCsv(writeCsvExport(context, tours)) -> R.string.no_share_app
                    else -> null
                }
            } catch (_: IOException) {
                R.string.export_failed
            } catch (_: SQLException) {
                R.string.export_failed
            } finally {
                // Vor der Snackbar freigeben: showSnackbar wartet, bis die Meldung verschwindet.
                exporting = false
            }
            problem?.let { snackbar.showSnackbar(resources.getString(it), withDismissAction = true) }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ToursTopBar(
                scrollBehavior = scrollBehavior,
                onOpenOverview = onOpenOverview,
                onExport = exportCsv,
                exportEnabled = !exporting,
            )
        },
        floatingActionButton = {
            // Content-Überladung statt text/icon: Letztere blendet den Text per
            // clearAndSetSemantics aus, dann hätte der FAB für TalkBack keinen Namen.
            ExtendedFloatingActionButton(onClick = onAddTour) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.tours_new))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // Platz unter der letzten Karte, damit der FAB sie nicht verdeckt.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tours_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                !state.hasAnyTour -> item { EmptyHint(stringResource(R.string.tours_empty)) }
                state.tours.isEmpty() -> item { EmptyHint(stringResource(R.string.tours_no_match)) }
                else -> items(state.tours, key = Tour::id) { tour ->
                    TourCard(tour = tour, onClick = { onOpenTour(tour.id) })
                }
            }
        }
    }

    // Die Meldung gilt erst nach vollständiger Anzeige als erledigt; wer die Liste währenddessen
    // verlässt, sieht sie bei der Rückkehr erneut und kann das Löschen noch rückgängig machen.
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is ToursMessage.Deleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.tours_deleted, current.tour.destination),
                    actionLabel = resources.getString(R.string.tours_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(current.tour)
            }
            ToursMessage.Saved -> snackbar.showSnackbar(resources.getString(R.string.tours_saved))
            is ToursMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToursTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onOpenOverview: () -> Unit,
    onExport: () -> Unit,
    exportEnabled: Boolean,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.tours_title)) },
        actions = {
            IconButton(onClick = onOpenOverview) {
                Icon(painterResource(R.drawable.ic_bar_chart), contentDescription = stringResource(R.string.tours_overview))
            }
            IconButton(onClick = onExport, enabled = exportEnabled) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = stringResource(R.string.tours_export_csv))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.tours_search)) },
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
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.tours_all_years)) })
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
private fun TourCard(tour: Tour, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.tours_open_details), onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tour.destination, style = MaterialTheme.typography.titleMedium)
                Text(
                    tour.period(currentLocale()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.tours_row_meta, tour.year, stringResource(tour.tourType.labelRes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
