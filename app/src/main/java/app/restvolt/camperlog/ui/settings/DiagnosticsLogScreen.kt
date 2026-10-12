package app.restvolt.camperlog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.DiagnosticsLogEntry
import app.restvolt.camperlog.domain.DiagnosticsLogRepository
import app.restvolt.camperlog.domain.DiagnosticsLogSource
import app.restvolt.camperlog.domain.supportedLocale
import app.restvolt.camperlog.share.shareDiagnosticsLog
import app.restvolt.camperlog.share.writeDiagnosticsLogExport
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.currentLocale
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Das lokale Diagnoseprotokoll ("Fehlerprotokoll" in den Einstellungen): Liste, Teilen, Löschen. */
@Composable
fun DiagnosticsLogScreen(repository: DiagnosticsLogRepository, onBack: () -> Unit) {
    val entries by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale()
    val crashLabel = stringResource(R.string.diagnostics_log_source_crash)
    val trackRecordingLabel = stringResource(R.string.diagnostics_log_source_track_recording)
    fun sourceLabel(source: DiagnosticsLogSource) = if (source == DiagnosticsLogSource.CRASH) crashLabel else trackRecordingLabel

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.diagnostics_log_title),
                onBack = onBack,
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val text = entries.reversed().joinToString("\n\n") { it.toShareText(locale, sourceLabel(it.source)) }
                                    val uri = writeDiagnosticsLogExport(context, text)
                                    if (!context.shareDiagnosticsLog(uri)) {
                                        snackbar.showSnackbar(resources.getString(R.string.no_share_app))
                                    }
                                }
                            },
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_share),
                                contentDescription = stringResource(R.string.diagnostics_log_share),
                            )
                        }
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.diagnostics_log_clear),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyHint(stringResource(R.string.diagnostics_log_empty), modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.reversed(), key = { "${it.timestamp}-${it.tag}-${it.message}" }) { entry ->
                    DiagnosticsLogEntryCard(entry, locale)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.diagnostics_log_clear_title)) },
            text = { Text(stringResource(R.string.diagnostics_log_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch { repository.clear() }
                    },
                ) { Text(stringResource(R.string.diagnostics_log_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DiagnosticsLogEntryCard(entry: DiagnosticsLogEntry, locale: Locale) {
    var expanded by rememberSaveable(entry.timestamp, entry.tag) { mutableStateOf(false) }
    val colors = if (entry.fatal) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDateTime(entry.timestamp, locale), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(entry.source.labelRes), style = MaterialTheme.typography.labelLarge)
                if (entry.fatal) {
                    Text(stringResource(R.string.diagnostics_log_fatal), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text("${entry.tag}: ${entry.message}", style = MaterialTheme.typography.bodyLarge)
            val stackTrace = entry.stackTrace
            if (stackTrace != null) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.diagnostics_log_hide_details else R.string.diagnostics_log_show_details))
                }
                if (expanded) {
                    SelectionContainer {
                        Text(stackTrace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private val DiagnosticsLogSource.labelRes: Int
    get() = when (this) {
        DiagnosticsLogSource.CRASH -> R.string.diagnostics_log_source_crash
        DiagnosticsLogSource.TRACK_RECORDING -> R.string.diagnostics_log_source_track_recording
    }

private fun DiagnosticsLogEntry.toShareText(locale: Locale, sourceLabel: String): String = buildString {
    appendLine(formatDateTime(timestamp, locale))
    appendLine(sourceLabel)
    appendLine("$tag: $message")
    stackTrace?.let { appendLine(it) }
}

private fun formatDateTime(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(supportedLocale(locale))
        .format(instant.atZone(ZoneId.systemDefault()))
