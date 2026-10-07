package app.restvolt.camperlog.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.PlaceSearchController
import app.restvolt.camperlog.domain.PlaceSearchHit
import app.restvolt.camperlog.domain.PlaceSearchState

/**
 * Ortssuche über Nominatim als Sheet: Eingabefeld mit [initialQuery], ausschließlich auf
 * Tastendruck ausgelöst (nie während der Eingabe), Trefferliste und die vorgeschriebene
 * OpenStreetMap-Attribution. [onPick] übernimmt einen Treffer, lässt das Sheet aber offen für
 * [onDismiss] durch den Aufrufer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchSheet(
    controller: PlaceSearchController,
    initialQuery: String,
    onSearch: (String) -> Unit,
    onPick: (PlaceSearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchLabel = stringResource(R.string.station_search_place_button)

    fun submit() {
        keyboard?.hide()
        onSearch(query)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(searchLabel, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.field_place)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = {
                    IconButton(onClick = ::submit) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = searchLabel)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            PlaceSearchResults(state, onPick)
            Text(
                stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceSearchResults(state: PlaceSearchState, onPick: (PlaceSearchHit) -> Unit) {
    when (state) {
        PlaceSearchState.Ready -> Unit
        PlaceSearchState.Loading -> Row(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.station_search_place_loading), style = MaterialTheme.typography.bodyMedium)
        }
        PlaceSearchState.Empty -> Text(
            stringResource(R.string.station_search_place_empty),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
        )
        PlaceSearchState.Offline -> Text(
            stringResource(R.string.weather_offline_body),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
        )
        PlaceSearchState.ServiceUnavailable -> Text(
            stringResource(R.string.weather_service_unavailable_body),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
        )
        is PlaceSearchState.Results -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(state.hits) { hit ->
                ListItem(
                    headlineContent = { Text(hit.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = placeSearchKind(hit)?.let { { Text(it) } },
                    modifier = Modifier.clickable { onPick(hit) },
                )
            }
        }
    }
}

/** Nominatims Klassifikation als Nebentext, z. B. "place · city"; `null`, wenn beides fehlt. */
private fun placeSearchKind(hit: PlaceSearchHit): String? =
    listOfNotNull(hit.category, hit.type).distinct().joinToString(" · ").ifBlank { null }
