package app.restvolt.camperlog.ui.data

import android.content.res.Resources
import android.database.SQLException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.BackupError
import app.restvolt.camperlog.backup.BackupReadResult
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

/** Datenverwaltung: CSV-Export für Tabellenprogramme sowie JSON-Sicherung und -Import. */
@Composable
fun DataScreen(viewModel: DataViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }

    /** Führt [action] aus und zeigt die gelieferte Meldung oder bei Fehlern [failure]. */
    fun run(@StringRes failure: Int, action: suspend () -> String?) {
        busy = true
        scope.launch {
            val message = try {
                action()
            } catch (_: IOException) {
                resources.getString(failure)
            } catch (_: SQLException) {
                resources.getString(failure)
            } finally {
                // Vor der Snackbar freigeben: showSnackbar wartet, bis die Meldung verschwindet.
                busy = false
            }
            message?.let { snackbar.showSnackbar(it, withDismissAction = true) }
        }
    }

    val exportCsv = {
        run(R.string.export_failed) {
            val tours = viewModel.toursForExport()
            when {
                tours.isEmpty() -> resources.getString(R.string.export_nothing)
                !context.shareCsv(writeCsvExport(context, tours)) -> resources.getString(R.string.no_share_app)
                else -> null
            }
        }
    }
    val saveBackup = rememberLauncherForActivityResult(CreateDocument(BACKUP_MIME)) { target ->
        if (target != null) {
            run(R.string.backup_failed) {
                writeBackupTo(context, target, viewModel.backupJson())
                resources.getString(R.string.backup_saved)
            }
        }
    }
    val shareBackup = {
        run(R.string.backup_failed) {
            if (context.shareBackup(writeBackupExport(context, viewModel.backupJson()))) null else resources.getString(R.string.no_share_app)
        }
    }
    val chooseBackup = rememberLauncherForActivityResult(OpenDocument()) { source ->
        if (source != null) {
            run(R.string.import_unreadable) {
                val failure = viewModel.loadBackup {
                    try {
                        context.contentResolver.openInputStream(source)
                    } catch (e: SecurityException) {
                        throw IOException(e)
                    }
                }
                failure?.let { resources.backupErrorMessage(it) }
            }
        }
    }
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    pendingImport?.let { pending ->
        ImportDialog(pending = pending, onImport = viewModel::startImport, onCancel = viewModel::cancelImport)
    }
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    LaunchedEffect(importResult) {
        val result = importResult ?: return@LaunchedEffect
        val rates = resources.getQuantityString(R.plurals.import_done_rates, result.importedRates, result.importedRates)
        val message = resources.getString(R.string.import_done, result.addedTours, result.updatedTours, result.unchangedTours, rates)
        viewModel.importResultShown()
        snackbar.showSnackbar(message, withDismissAction = true)
    }

    Scaffold(
        topBar = {
            Column {
                BackTopBar(title = stringResource(R.string.data_title), onBack = onBack)
                if (busy || pendingImport?.running == true) {
                    val working = stringResource(R.string.data_working)
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = working
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                }
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
            item {
                SectionCard {
                    SectionTitle(stringResource(R.string.data_import_title))
                    Text(
                        stringResource(R.string.data_import_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { chooseBackup.launch(BACKUP_OPEN_MIMES) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.data_import_choose))
                    }
                }
            }
        }
    }
}

/** Viele Dateimanager und Messenger melden JSON-Dateien nicht als `application/json`; geprüft wird der Inhalt. */
private val BACKUP_OPEN_MIMES = arrayOf(BACKUP_MIME, "application/octet-stream", "text/plain")

private fun Resources.backupErrorMessage(failure: BackupReadResult.Failure): String = when (failure.error) {
    BackupError.TOO_LARGE -> getString(R.string.import_error_too_large)
    BackupError.NOT_A_BACKUP -> getString(R.string.import_error_not_backup)
    BackupError.NEWER_VERSION -> getString(R.string.import_error_newer_version)
    BackupError.INVALID_DATA -> failure.tourNumber
        ?.let { getString(R.string.import_error_invalid_tour, it) }
        ?: getString(R.string.import_error_invalid)
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
