package app.restvolt.camperlog.ui.data

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
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
import androidx.compose.runtime.remember
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.BackupError
import app.restvolt.camperlog.backup.BackupReadResult
import app.restvolt.camperlog.share.BACKUP_MIME
import app.restvolt.camperlog.share.backupFileName
import app.restvolt.camperlog.share.shareBackup
import app.restvolt.camperlog.share.shareCsv
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.SectionCard
import kotlinx.coroutines.flow.filterNotNull

/** Datenverwaltung: CSV-Export für Tabellenprogramme sowie JSON-Sicherung und -Import. */
@Composable
fun DataScreen(viewModel: DataViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val saveBackup = rememberLauncherForActivityResult(CreateDocument(BACKUP_MIME)) { target ->
        if (target != null) viewModel.saveBackup(target.toString())
    }
    val chooseBackup = rememberLauncherForActivityResult(OpenDocument()) { source ->
        if (source != null) viewModel.loadBackup(source.toString())
    }
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    pendingImport?.let { pending ->
        ImportDialog(pending = pending, onImport = viewModel::startImport, onCancel = viewModel::cancelImport)
    }
    val share by viewModel.share.collectAsStateWithLifecycle()
    LaunchedEffect(share) {
        val request = share ?: return@LaunchedEffect
        val uri = request.uri.toUri()
        val started = when (request) {
            is ShareRequest.Csv -> context.shareCsv(uri)
            is ShareRequest.Backup -> context.shareBackup(uri)
        }
        viewModel.shareHandled(started)
    }
    LaunchedEffect(viewModel) {
        // Dauerhaft sammeln: Ein Effekt mit der Meldung als Schlüssel würde beim Quittieren die Snackbar abbrechen.
        viewModel.message.filterNotNull().collect { message ->
            viewModel.messageShown()
            snackbar.showSnackbar(resources.dataMessageText(message), withDismissAction = true)
        }
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
                    val defaultVehicleName = stringResource(R.string.vehicle_default_name)
                    OutlinedButton(
                        onClick = { viewModel.exportCsv(defaultVehicleName) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                    OutlinedButton(onClick = viewModel::shareBackup, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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

private fun Resources.dataMessageText(message: DataMessage): String = when (message) {
    is DataMessage.Text -> getString(message.text)
    is DataMessage.LoadFailed -> backupErrorMessage(message.failure)
    is DataMessage.Imported -> message.result.let { result ->
        val logEntries = getQuantityString(R.plurals.import_done_log_entries, result.addedLogEntries, result.addedLogEntries)
        val rates = getQuantityString(R.plurals.import_done_rates, result.importedRates, result.importedRates)
        getString(
            R.string.import_done,
            result.addedTours,
            result.updatedTours,
            result.unchangedTours,
            result.addedVehicles,
            result.updatedVehicles,
            result.addedRepairs,
            result.updatedRepairs,
            logEntries,
            rates,
        )
    }
}

private fun Resources.backupErrorMessage(failure: BackupReadResult.Failure): String = when (failure.error) {
    BackupError.TOO_LARGE -> getString(R.string.import_error_too_large)
    BackupError.NOT_A_BACKUP -> getString(R.string.import_error_not_backup)
    BackupError.NEWER_VERSION -> getString(R.string.import_error_newer_version)
    BackupError.INVALID_DATA -> when {
        failure.tourNumber != null -> getString(R.string.import_error_invalid_tour, failure.tourNumber)
        failure.repairNumber != null -> getString(R.string.import_error_invalid_vehicle_repair, failure.vehicleNumber, failure.repairNumber)
        failure.logEntryNumber != null -> getString(R.string.import_error_invalid_vehicle_log_entry, failure.vehicleNumber, failure.logEntryNumber)
        failure.vehicleNumber != null -> getString(R.string.import_error_invalid_vehicle, failure.vehicleNumber)
        else -> getString(R.string.import_error_invalid)
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
