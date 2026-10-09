package app.restvolt.camperlog.ui.detail

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TollKind
import app.restvolt.camperlog.domain.allowedServices
import app.restvolt.camperlog.domain.countryDisplayName
import app.restvolt.camperlog.domain.displayTitle
import app.restvolt.camperlog.domain.effectiveCosts
import app.restvolt.camperlog.domain.electricityCost
import app.restvolt.camperlog.domain.electricityKwh
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.formatCoordinates
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.formatKwh
import app.restvolt.camperlog.share.openInMaps
import app.restvolt.camperlog.share.tryStart
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.WeatherSummary
import app.restvolt.camperlog.ui.attachments.AndroidAttachmentPickers
import app.restvolt.camperlog.ui.attachments.AttachmentPickers
import app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection
import app.restvolt.camperlog.ui.coordinatesContentDescription
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.stationSavedText
import app.restvolt.camperlog.ui.yesNoRes
import kotlinx.coroutines.launch

/**
 * Schreibgeschützte Ansicht einer Station. [onDelete] löscht ohne Rückfrage; die Tourdetailseite
 * bietet danach „Rückgängig" an. [onOpenTour] navigiert zur zugehörigen Tour, sofern vorhanden.
 */
@Composable
fun StationDetailScreen(
    viewModel: StationDetailViewModel,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    attachmentPickers: AttachmentPickers = AndroidAttachmentPickers,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (Station) -> Unit,
    onOpenTour: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val station = (state as? StationDetailUiState.Loaded)?.station
    var overflowExpanded by remember { mutableStateOf(false) }
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbar.showSnackbar(stationSavedText(resources, current.loggedServices))
        viewModel.onMessageShown(current)
    }

    Scaffold(
        topBar = {
            val title = station?.name?.ifBlank { station.let { stringResource(it.type.labelRes) } }
                ?: stringResource(R.string.station_fallback_title)
            BackTopBar(title = title, onBack = onBack) {
                if (station != null) {
                    IconButton(onClick = onEdit) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.station_detail_edit))
                    }
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.station_detail_delete)) },
                            onClick = {
                                overflowExpanded = false
                                onDelete(station)
                            },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            StationDetailUiState.Loading -> Unit
            StationDetailUiState.NotFound -> EmptyHint(stringResource(R.string.station_not_found), Modifier.padding(padding))
            is StationDetailUiState.Loaded -> StationDetails(
                station = current.station,
                tourTitle = current.tour?.displayTitle(stringResource(R.string.detail_fallback_title)),
                attachments = attachments,
                attachmentFileStore = attachmentFileStore,
                attachmentPickers = attachmentPickers,
                snackbarHostState = snackbar,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                onOpenTour = { current.station.tourId?.let(onOpenTour) },
                onOpenMaps = {
                    if (!context.openInMaps(current.station)) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.station_detail_no_maps_app)) }
                    }
                },
                onOpenLink = { current.station.link?.let { context.tryStart(Intent(Intent.ACTION_VIEW, it.toUri())) } },
            )
        }
    }
}

@Composable
private fun StationDetails(
    station: Station,
    tourTitle: String?,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    attachmentPickers: AttachmentPickers,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
    onOpenTour: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenLink: () -> Unit,
) {
    val locale = currentLocale()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            val typeLabel = stringResource(station.type.labelRes)
            val rowText = if (tourTitle != null) stringResource(R.string.station_open_tour, typeLabel, tourTitle) else typeLabel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (tourTitle != null) it.clickable(onClickLabel = stringResource(R.string.tours_open_details), onClick = onOpenTour) else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(station.type.iconRes), contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                Text(rowText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }

            val dateText = station.time?.let { "${formatDate(station.date, locale)}, %02d:%02d".format(it.hour, it.minute) }
                ?: formatDate(station.date, locale)
            val nightsText = station.nights?.let { pluralStringResource(R.plurals.station_nights, it, it) }
            Text(listOfNotNull(dateText, nightsText).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)

            if (station.place.isNotBlank()) Text(station.place, style = MaterialTheme.typography.bodyLarge)

            if (station.latitude != null && station.longitude != null) {
                val description = coordinatesContentDescription(station.latitude, station.longitude, station.accuracyM, locale)
                Text(
                    formatCoordinates(station.latitude, station.longitude, locale),
                    modifier = Modifier.semantics { contentDescription = description },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (hasMapTarget(station)) {
                OutlinedButton(onClick = onOpenMaps) { Text(stringResource(R.string.station_detail_open_maps)) }
            }

            pitchDetailsText(station)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            servicesText(station)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            station.weather?.let { WeatherSummary(it) }
            station.rating?.let { RatingDisplay(it) }
            station.odometerKm?.let { Text(stringResource(R.string.distance_km, it), style = MaterialTheme.typography.bodyLarge) }
            station.manualTemperatureDeciC?.let {
                val whole = Math.round(it / 10.0).toInt()
                Text(stringResource(R.string.weather_temperature_c, whole), style = MaterialTheme.typography.bodyLarge)
            }
            if (station.link != null) {
                OutlinedButton(onClick = onOpenLink) { Text(stringResource(R.string.station_detail_open_link)) }
            }
        }
        if (station.electricityBilling != null) {
            SectionCard {
                DetailHeading(stringResource(R.string.station_section_electricity))
                Text(
                    stringResource(R.string.station_summary_field, stringResource(R.string.field_electricity), stringResource(station.electricityBilling.labelRes)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val cost = electricityCost(station)
                val kwh = electricityKwh(station)
                if (cost != null || kwh != null) {
                    val parts = listOfNotNull(cost?.let { formatAmount(it.minor, it.currency, locale) }, kwh?.let { formatKwh(it, locale) })
                    Text(stringResource(R.string.electricity_result_value, parts.joinToString(" · ")), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (station.type == StationType.TOLL) {
            SectionCard {
                DetailHeading(stringResource(R.string.station_section_toll))
                station.tollKind?.let {
                    Text(stringResource(R.string.station_summary_field, stringResource(R.string.field_toll_kind), stringResource(it.labelRes)), style = MaterialTheme.typography.bodyLarge)
                }
                if (station.tollKind == TollKind.VIGNETTE) {
                    station.tollCountry?.let {
                        Text(
                            stringResource(R.string.station_summary_field, stringResource(R.string.field_toll_country), countryDisplayName(it, locale)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    station.tollValidFrom?.let {
                        Text(
                            stringResource(R.string.station_summary_field, stringResource(R.string.field_toll_valid_from), formatDate(it, locale)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    station.tollValidUntil?.let {
                        Text(
                            stringResource(R.string.station_summary_field, stringResource(R.string.field_toll_valid_until), formatDate(it, locale)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                if (station.tollPaymentMethod.isNotBlank()) {
                    Text(
                        stringResource(R.string.station_summary_field, stringResource(R.string.field_toll_payment_method), station.tollPaymentMethod),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (station.type == StationType.FERRY && station.ferryBookingReference.isNotBlank()) {
            SectionCard {
                DetailHeading(stringResource(R.string.station_section_ferry))
                Text(
                    stringResource(R.string.station_summary_field, stringResource(R.string.field_ferry_booking_reference), station.ferryBookingReference),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        val costs = station.effectiveCosts()
        if (costs.isNotEmpty()) {
            SectionCard {
                DetailHeading(stringResource(R.string.station_section_costs))
                costs.sortedBy { it.category.ordinal }.forEach { cost ->
                    val amountText = formatAmount(cost.amount.minor, cost.amount.currency, locale)
                    val valueText = if (cost.note.isBlank()) amountText else "$amountText · ${cost.note}"
                    Text(
                        stringResource(R.string.station_summary_field, stringResource(cost.category.labelRes), valueText),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (station.notes.isNotBlank()) {
            SectionCard {
                DetailHeading(stringResource(R.string.field_notes))
                Text(station.notes, style = MaterialTheme.typography.bodyLarge)
            }
        }
        PhotoAttachmentsSection(
            ownerType = AttachmentOwnerType.STATION,
            ownerId = station.id,
            repository = attachments,
            fileStore = attachmentFileStore,
            snackbarHostState = snackbarHostState,
            stopLocation = if (station.latitude != null && station.longitude != null) station.latitude to station.longitude else null,
            pickers = attachmentPickers,
        )
    }
}

/** Fünf Camper-Symbole, gefüllt bis [rating]; nur lesend, eine Sprechform für alle fünf zusammen. */
@Composable
private fun RatingDisplay(rating: Int) {
    val description = stringResource(R.string.station_rating_content_description, rating)
    Row(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        (1..5).forEach { value ->
            Icon(
                painterResource(if (value <= rating) R.drawable.ic_rv_hookup_filled else R.drawable.ic_rv_hookup),
                contentDescription = null,
                tint = if (value <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

@Composable
private fun DetailHeading(title: String) {
    Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

private fun hasMapTarget(station: Station): Boolean =
    (station.latitude != null && station.longitude != null) || station.mapLink != null || station.name.isNotBlank() || station.place.isNotBlank()

@Composable
private fun pitchDetailsText(station: Station): String? {
    val parts = listOfNotNull(
        station.siteKind?.let { stringResource(it.labelRes) },
        station.pitchAssigned?.let {
            stringResource(R.string.station_summary_field, stringResource(R.string.field_pitch_assigned), stringResource(yesNoRes(it)))
        },
        station.lteQuality?.let { stringResource(R.string.station_summary_field, stringResource(R.string.field_lte), stringResource(it.labelRes)) },
        station.pitchSlope?.let { stringResource(it.labelRes) },
        station.levelingBlocksUsed?.let {
            stringResource(R.string.station_summary_field, stringResource(R.string.field_leveling_blocks), stringResource(yesNoRes(it)))
        },
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

@Composable
private fun servicesText(station: Station): String? {
    if (station.services.isEmpty()) return null
    val label = stringResource(R.string.station_type_supply)
    val values = station.type.allowedServices.filter { it in station.services }.map { stringResource(it.labelRes) }.joinToString(", ")
    return stringResource(R.string.station_summary_field, label, values)
}
