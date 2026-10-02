package de.hannes.camperlog.ui.detail

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hannes.camperlog.R
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.formatDate
import de.hannes.camperlog.domain.formatEuro
import de.hannes.camperlog.share.openInMaps
import de.hannes.camperlog.share.shareTour
import de.hannes.camperlog.ui.BackTopBar
import de.hannes.camperlog.ui.EmptyHint
import de.hannes.camperlog.ui.LabeledValue
import de.hannes.camperlog.ui.SectionCard
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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val tour = (state as? DetailUiState.Loaded)?.tour

    Scaffold(
        topBar = {
            BackTopBar(title = tour?.destination ?: "Tour", onBack = onBack) {
                if (tour != null) {
                    IconButton(onClick = { onDelete(tour) }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Tour löschen")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            DetailUiState.Loading -> Unit
            DetailUiState.NotFound -> EmptyHint("Diese Tour gibt es nicht mehr.", Modifier.padding(padding))
            is DetailUiState.Loaded -> TourDetails(
                tour = current.tour,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                onEdit = onEdit,
                onShare = {
                    if (!context.shareTour(current.tour)) {
                        scope.launch { snackbar.showSnackbar("Keine App zum Teilen gefunden") }
                    }
                },
                onOpenMaps = {
                    if (!context.openInMaps(current.tour)) {
                        scope.launch { snackbar.showSnackbar("Keine Karten-App gefunden") }
                    }
                },
            )
        }
    }
}

@Composable
private fun TourDetails(
    tour: Tour,
    modifier: Modifier,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onOpenMaps: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            LabeledValue("Startdatum", formatDate(tour.startDate))
            LabeledValue("Enddatum", formatDate(tour.endDate))
            LabeledValue("Ziel", tour.destination)
            LabeledValue("Tourart", tour.tourType.label)
        }
        SectionCard {
            LabeledValue("Reisetage", tour.travelDays.toString())
            LabeledValue("Übernachtungen", tour.overnightStays.toString())
            LabeledValue("Kilometer", "${tour.distanceKm} km")
            LabeledValue("Kosten", formatEuro(tour.costCents))
        }
        SectionCard {
            LabeledValue("Stellplatz zugewiesen", yesNo(tour.pitchAssigned))
            LabeledValue("Strompauschale", tour.electricityFlatRate.label)
            LabeledValue("LTE", tour.lteQuality.label)
            LabeledValue("Stellplatz", tour.pitchSlope.label)
            LabeledValue("Keile genutzt", yesNo(tour.levelingBlocksUsed))
        }
        if (tour.notes.isNotBlank() || tour.mapLink != null) {
            SectionCard {
                if (tour.notes.isNotBlank()) {
                    Text("Notizen", style = MaterialTheme.typography.titleMedium)
                    Text(tour.notes, style = MaterialTheme.typography.bodyLarge)
                }
                tour.mapLink?.let {
                    Text("Kartenlink", style = MaterialTheme.typography.titleMedium)
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val buttonModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
        Button(onClick = onEdit, modifier = buttonModifier) { ButtonContent(R.drawable.ic_edit, "Bearbeiten") }
        OutlinedButton(onClick = onShare, modifier = buttonModifier) {
            ButtonContent(R.drawable.ic_share, "Tour teilen")
        }
        OutlinedButton(onClick = onOpenMaps, modifier = buttonModifier, contentPadding = PaddingValues(12.dp)) {
            ButtonContent(R.drawable.ic_place, "In Maps öffnen")
        }
    }
}

@Composable
private fun ButtonContent(icon: Int, label: String) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
}

private fun yesNo(value: Boolean) = if (value) "ja" else "nein"
