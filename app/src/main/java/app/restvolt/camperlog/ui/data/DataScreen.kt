package app.restvolt.camperlog.ui.data

import android.database.SQLException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import app.restvolt.camperlog.share.BACKUP_MIME
import app.restvolt.camperlog.share.backupFileName
import app.restvolt.camperlog.share.shareBackup
import app.restvolt.camperlog.share.shareCsv
import app.restvolt.camperlog.share.writeBackupExport
import app.restvolt.camperlog.share.writeBackupTo
import app.restvolt.camperlog.share.writeCsvExport
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.SectionCard
import kotlinx.coroutines.launch
import java.io.IOException

/** Datenverwaltung: CSV-Export für Tabellenprogramme und JSON-Sicherung. */
@Composable
fun DataScreen(viewModel: DataViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }

    /** Führt [action] aus und zeigt die gelieferte oder bei Fehlern [failure] als Meldung. */
    fun run(@StringRes failure: Int, action: suspend () -> Int?) {
        busy = true
        scope.launch {
            val message = try {
                action()
            } catch (_: IOException) {
                failure
            } catch (_: SQLException) {
                failure
            } finally {
                // Vor der Snackbar freigeben: showSnackbar wartet, bis die Meldung verschwindet.
                busy = false
            }
            message?.let { snackbar.showSnackbar(resources.getString(it), withDismissAction = true) }
        }
    }

    val exportCsv = {
        run(R.string.export_failed) {
            val tours = viewModel.toursForExport()
            when {
                tours.isEmpty() -> R.string.export_nothing
                !context.shareCsv(writeCsvExport(context, tours)) -> R.string.no_share_app
                else -> null
            }
        }
    }
    val saveBackup = rememberLauncherForActivityResult(CreateDocument(BACKUP_MIME)) { target ->
        if (target != null) {
            run(R.string.backup_failed) {
                writeBackupTo(context, target, viewModel.backupJson())
                R.string.backup_saved
            }
        }
    }
    val shareBackup = {
        run(R.string.backup_failed) {
            if (context.shareBackup(writeBackupExport(context, viewModel.backupJson()))) null else R.string.no_share_app
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
                    OutlinedButton(onClick = exportCsv, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tours_export_csv))
                    }
                }
            }
            item {
                SectionCard {
                    SectionTitle(stringResource(R.string.data_backup_title))
                    Text(
                        stringResource(R.string.data_backup_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { saveBackup.launch(backupFileName()) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.data_backup_save))
                    }
                    OutlinedButton(onClick = shareBackup, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.data_backup_share))
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
