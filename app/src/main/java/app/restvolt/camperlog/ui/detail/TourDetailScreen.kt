package app.restvolt.camperlog.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.formatAmounts
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.share.openInMaps
import app.restvolt.camperlog.share.shareTour
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.LabeledValue
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.vehicleDisplayName
import app.restvolt.camperlog.ui.yesNoRes
import kotlinx.coroutines.launch

/**
 * Schreibgeschützte Ansicht einer Tour mit Bearbeiten, Teilen, Kartenaufruf und Löschen.
 * [onDelete] löscht ohne Rückfrage; die Liste bietet anschließend „Rückgängig“ an.
 */
@Composable
fun TourDetailScreen(
    viewModel: TourDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (Tour) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val tour = (state as? DetailUiState.Loaded)?.tour

    Scaffold(
        topBar = {
            BackTopBar(title = tour?.destination ?: stringResource(R.string.detail_fallback_title), onBack = onBack) {
                if (tour != null) {
                    IconButton(onClick = { onDelete(tour) }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.detail_delete))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            DetailUiState.Loading -> Unit
            DetailUiState.NotFound -> EmptyHint(stringResource(R.string.tour_not_found), Modifier.padding(padding))
            is DetailUiState.Loaded -> TourDetails(
                tour = current.tour,
                vehicle = current.vehicle,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                onEdit = onEdit,
                onShare = {
                    if (!context.shareTour(current.tour)) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.no_share_app)) }
                    }
                },
                onOpenMaps = {
                    if (!context.openInMaps(current.tour)) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.detail_no_maps_app)) }
                    }
                },
            )
        }
    }
}

@Composable
private fun TourDetails(
    tour: Tour,
    vehicle: Vehicle?,
    modifier: Modifier,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onOpenMaps: () -> Unit,
) {
    val locale = currentLocale()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            if (vehicle != null) {
                LabeledValue(stringResource(R.string.field_vehicle), vehicleDisplayName(vehicle))
            }
            LabeledValue(stringResource(R.string.field_start_date), formatDate(tour.startDate, locale))
            LabeledValue(stringResource(R.string.field_end_date), formatDate(tour.endDate, locale))
            LabeledValue(stringResource(R.string.field_destination), tour.destination)
            LabeledValue(stringResource(R.string.field_tour_type), stringResource(tour.tourType.labelRes))
        }
        SectionCard {
            LabeledValue(stringResource(R.string.field_travel_days), tour.travelDays.toString())
            LabeledValue(stringResource(R.string.field_overnight_stays), tour.overnightStays.toString())
            LabeledValue(stringResource(R.string.field_distance), stringResource(R.string.distance_km, tour.distanceKm))
            LabeledValue(stringResource(R.string.field_cost), formatAmounts(tour.costs, locale))
        }
        SectionCard {
            LabeledValue(stringResource(R.string.field_pitch_assigned), stringResource(yesNoRes(tour.pitchAssigned)))
            LabeledValue(stringResource(R.string.field_electricity), stringResource(tour.electricityFlatRate.labelRes))
            LabeledValue(stringResource(R.string.field_lte), stringResource(tour.lteQuality.labelRes))
            LabeledValue(stringResource(R.string.field_pitch), stringResource(tour.pitchSlope.labelRes))
            LabeledValue(stringResource(R.string.field_leveling_blocks), stringResource(yesNoRes(tour.levelingBlocksUsed)))
        }
        if (tour.notes.isNotBlank() || tour.mapLink != null) {
            SectionCard {
                if (tour.notes.isNotBlank()) {
                    Text(
                        stringResource(R.string.field_notes),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(tour.notes, style = MaterialTheme.typography.bodyLarge)
                }
                tour.mapLink?.let {
                    Text(
                        stringResource(R.string.field_map_link),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val buttonModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
        Button(onClick = onEdit, modifier = buttonModifier) { ButtonContent(R.drawable.ic_edit, stringResource(R.string.detail_edit)) }
        OutlinedButton(onClick = onShare, modifier = buttonModifier) {
            ButtonContent(R.drawable.ic_share, stringResource(R.string.detail_share))
        }
        OutlinedButton(onClick = onOpenMaps, modifier = buttonModifier, contentPadding = PaddingValues(12.dp)) {
            ButtonContent(R.drawable.ic_place, stringResource(R.string.detail_open_maps))
        }
    }
}

@Composable
private fun ButtonContent(icon: Int, label: String) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
}
