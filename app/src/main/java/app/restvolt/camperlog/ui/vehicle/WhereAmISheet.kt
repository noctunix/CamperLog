package app.restvolt.camperlog.ui.vehicle

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LocationCaptureState
import app.restvolt.camperlog.share.locationShareText
import app.restvolt.camperlog.share.shareLocation
import app.restvolt.camperlog.ui.LocationCaptureSection
import app.restvolt.camperlog.ui.coordinatesContentDescription
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.locationFixSummary
import app.restvolt.camperlog.ui.rememberLocationCaptureHandlers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Wo bin ich?": öffnet mit einem automatisch angestoßenen Fix und zeigt dieselben Zustände wie die
 * Standortbestimmung im Stationsformular; gefunden wird die Position groß mit Kopieren und Teilen angezeigt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhereAmISheet(viewModel: WhereAmIViewModel, onDismiss: () -> Unit) {
    val controller = viewModel.locationCapture
    val state by controller.state.collectAsStateWithLifecycle()
    val handlers = rememberLocationCaptureHandlers(controller)
    val locale = currentLocale()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { handlers.onTap() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.where_am_i_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            when (val current = state) {
                is LocationCaptureState.Found -> {
                    val description = coordinatesContentDescription(current.fix.latitude, current.fix.longitude, current.fix.accuracyM, locale)
                    Text(
                        locationFixSummary(current.fix, locale),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            context.copyLocationToClipboard(current.fix.latitude, current.fix.longitude, locale)
                            scope.launch { snackbar.showSnackbar(resources.getString(R.string.where_am_i_copied)) }
                        }) { Text(stringResource(R.string.where_am_i_copy)) }
                        Button(onClick = { context.shareLocation(current.fix.latitude, current.fix.longitude, locale) }) {
                            Text(stringResource(R.string.where_am_i_share))
                        }
                    }
                }
                else -> LocationCaptureSection(
                    controller = controller,
                    buttonLabel = stringResource(R.string.vehicle_where_am_i),
                    handlers = handlers,
                )
            }
            SnackbarHost(snackbar)
        }
    }
}

private fun Context.copyLocationToClipboard(latitude: Double, longitude: Double, locale: Locale) {
    copyToClipboard(getString(R.string.where_am_i_copy), locationShareText(latitude, longitude, locale))
}
