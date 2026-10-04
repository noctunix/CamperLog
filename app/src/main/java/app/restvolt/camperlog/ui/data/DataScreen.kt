package app.restvolt.camperlog.ui.data

import android.database.SQLException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.share.shareCsv
import app.restvolt.camperlog.share.writeCsvExport
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.SectionCard
import kotlinx.coroutines.launch
import java.io.IOException

/** Datenverwaltung: CSV-Export für Tabellenprogramme. */
@Composable
fun DataScreen(viewModel: DataViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
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

    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.data_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard {
                    SectionTitle(stringResource(R.string.data_csv_title))
                    Text(
                        stringResource(R.string.data_csv_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = exportCsv, enabled = !exporting, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tours_export_csv))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
