package app.restvolt.camperlog.ui.about

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.restvolt.camperlog.BuildConfig
import app.restvolt.camperlog.R
import app.restvolt.camperlog.share.copyToClipboard
import app.restvolt.camperlog.share.tryStart
import app.restvolt.camperlog.ui.BackTopBar
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ein Eintrag der Liste "Geführte Touren" in [AboutScreen]: [titleRes] bezeichnet die Tour,
 * [completed] zeigt das Häkchen, [onStart] startet sie (erneut).
 */
data class GuidedTourEntry(
    @StringRes val titleRes: Int,
    val completed: Boolean,
    val onStart: () -> Unit,
)

/**
 * Über-Bildschirm: Version, Kontakt- und Unterstützungsaktionen sowie aufklappbare Abschnitte zu
 * Quellcode, Lizenz, Drittanbieter-Bibliotheken und Datenschutz. [guidedTours] listet die geführten
 * Touren der App mit Start/Wiederholen-Button und Abschluss-Hinweis.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, guidedTours: List<GuidedTourEntry>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val keepAndroidOpenSettings = remember { KeepAndroidOpenSettings(context) }

    var showKeepAndroidOpen by rememberSaveable { mutableStateOf(false) }
    var showLicenseText by rememberSaveable { mutableStateOf(false) }
    var sourceExpanded by rememberSaveable { mutableStateOf(false) }
    var licenseExpanded by rememberSaveable { mutableStateOf(false) }
    var thirdPartyExpanded by rememberSaveable { mutableStateOf(false) }
    var dataSourcesExpanded by rememberSaveable { mutableStateOf(false) }
    var privacyExpanded by rememberSaveable { mutableStateOf(false) }

    val noAppAvailable = stringResource(R.string.about_no_app_available)
    val repoUri = stringResource(R.string.about_repo_uri)
    val issuesUri = stringResource(R.string.about_issues_uri)
    val licenseGithubUri = stringResource(R.string.about_license_github_uri)
    val osmCopyrightUri = stringResource(R.string.about_osm_copyright_uri)
    val openMeteoUri = stringResource(R.string.about_open_meteo_uri)
    val naturalEarthUri = stringResource(R.string.about_natural_earth_uri)
    val nominatimUri = stringResource(R.string.about_nominatim_uri)
    val feedbackAddress = stringResource(R.string.about_feedback_address)
    val feedbackSubject = stringResource(R.string.about_feedback_subject, BuildConfig.VERSION_NAME)
    val noMailApp = stringResource(R.string.about_no_mail_app)
    val copyAddress = stringResource(R.string.about_copy_address)

    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.about_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorResource(R.color.launcher_background)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Der Vordergrund ist für 108 dp mit Sicherheitsrand gezeichnet; 96 dp zeigen das Motiv wie im Launcher.
                        Image(
                            painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.requiredSize(96.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.app_name),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.about_claim),
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, "mailto:$feedbackAddress".toUri())
                            .putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
                        if (!context.tryStart(intent)) {
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = noMailApp,
                                    actionLabel = copyAddress,
                                    withDismissAction = true,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    context.copyToClipboard(feedbackSubject, feedbackAddress)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.about_action_feedback))
                }
            }
            item {
                OutlinedButton(onClick = { showKeepAndroidOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_action_keep_android_open))
                }
            }
            item {
                Text(
                    stringResource(R.string.about_guided_tours_title),
                    modifier = Modifier.semantics { heading() },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(guidedTours) { tour ->
                GuidedTourRow(tour)
            }
            item {
                AboutSection(
                    title = stringResource(R.string.about_section_source),
                    expanded = sourceExpanded,
                    onToggle = { sourceExpanded = !sourceExpanded },
                ) {
                    Text(stringResource(R.string.about_source_body), style = MaterialTheme.typography.bodyMedium)
                    AboutLink(stringResource(R.string.about_link_repo)) {
                        openExternalLink(scope, context, snackbar, noAppAvailable, repoUri)
                    }
                    AboutLink(stringResource(R.string.about_link_issues)) {
                        openExternalLink(scope, context, snackbar, noAppAvailable, issuesUri)
                    }
                }
            }
            item {
                AboutSection(
                    title = stringResource(R.string.about_section_license),
                    expanded = licenseExpanded,
                    onToggle = { licenseExpanded = !licenseExpanded },
                ) {
                    Text(stringResource(R.string.about_license_body), style = MaterialTheme.typography.bodyMedium)
                    AboutLink(stringResource(R.string.about_link_license_text)) { showLicenseText = true }
                    AboutLink(stringResource(R.string.about_link_license_github)) {
                        openExternalLink(scope, context, snackbar, noAppAvailable, licenseGithubUri)
                    }
                }
            }
            item {
                AboutSection(
                    title = stringResource(R.string.about_section_third_party),
                    expanded = thirdPartyExpanded,
                    onToggle = { thirdPartyExpanded = !thirdPartyExpanded },
                ) {
                    ThirdPartyLibraries.entries.forEach { library ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openExternalLink(scope, context, snackbar, noAppAvailable, library.url) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(library.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                library.license,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                AboutSection(
                    title = stringResource(R.string.about_section_data_sources),
                    expanded = dataSourcesExpanded,
                    onToggle = { dataSourcesExpanded = !dataSourcesExpanded },
                ) {
                    AboutLink(stringResource(R.string.about_link_osm_attribution)) {
                        openExternalLink(scope, context, snackbar, noAppAvailable, osmCopyrightUri)
                    }
                    AboutLink(stringResource(R.string.about_link_open_meteo_attribution)) {
                        openExternalLink(scope, context, snackbar, noAppAvailable, openMeteoUri)
                    }
                    AboutLink(stringResource(R.string.about_link_natural_earth_attribution)) {
                        openExternalLink(scope, context, snackbar, noAppAvailable, naturalEarthUri)
                    }
                    AboutLink(stringResource(R.string.about_link_nominatim_attribution)) {
                        openExternalLink(scope, context, snackbar, noAppAvailable, nominatimUri)
                    }
                }
            }
            item {
                AboutSection(
                    title = stringResource(R.string.about_section_privacy),
                    expanded = privacyExpanded,
                    onToggle = { privacyExpanded = !privacyExpanded },
                ) {
                    listOf(
                        R.string.about_privacy_bullet_opt_in,
                        R.string.about_privacy_bullet_location,
                        R.string.about_privacy_bullet_track,
                        R.string.about_privacy_bullet_weather_map,
                        R.string.about_privacy_bullet_notifications,
                        R.string.about_privacy_bullet_no_tracking,
                        R.string.about_privacy_bullet_local,
                        R.string.about_privacy_bullet_backup,
                        R.string.about_privacy_bullet_uninstall,
                    ).forEach { bullet ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("•", modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(bullet), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showKeepAndroidOpen) {
        KeepAndroidOpenDialog(
            onSupported = {
                keepAndroidOpenSettings.markShown()
                keepAndroidOpenSettings.markSupported()
                showKeepAndroidOpen = false
            },
            onDismiss = {
                keepAndroidOpenSettings.markShown()
                showKeepAndroidOpen = false
            },
        )
    }

    if (showLicenseText) {
        LicenseTextDialog(onDismiss = { showLicenseText = false })
    }
}

/** Öffnet [url] extern über [app.restvolt.camperlog.share.tryStart]; zeigt sonst [noAppMessage]. */
internal fun openExternalLink(scope: CoroutineScope, context: Context, snackbar: SnackbarHostState, noAppMessage: String, url: String) {
    if (!context.tryStart(Intent(Intent.ACTION_VIEW, url.toUri()))) {
        scope.launch { snackbar.showSnackbar(noAppMessage, withDismissAction = true) }
    }
}

@Composable
private fun AboutSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = stringResource(
                    if (expanded) R.string.cd_collapse_about_section else R.string.cd_expand_about_section,
                    title,
                ),
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

@Composable
private fun AboutLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label) }
}

/** Eine Zeile der Liste "Geführte Touren": Titel mit Abschluss-Hinweis links, Start/Wiederholen rechts. */
@Composable
private fun GuidedTourRow(tour: GuidedTourEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(tour.titleRes), style = MaterialTheme.typography.bodyLarge)
            if (tour.completed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.guide_entry_completed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedButton(onClick = tour.onStart) {
            Text(stringResource(if (tour.completed) R.string.guide_entry_repeat else R.string.guide_entry_start))
        }
    }
}

@Composable
private fun LicenseTextDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var licenseText by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val text = try {
            context.assets.open("LICENSE").bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            null
        }
        if (text == null) loadFailed = true else licenseText = text
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_section_license)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    loadFailed -> Text(stringResource(R.string.err_license_load_failed))
                    licenseText == null -> Text(stringResource(R.string.info_license_loading))
                    else -> Text(licenseText.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
