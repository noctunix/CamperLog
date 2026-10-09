package app.restvolt.camperlog.ui.vehicle

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.Reminder
import app.restvolt.camperlog.domain.ReminderKind
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.documentReminders
import app.restvolt.camperlog.domain.dueReminders
import app.restvolt.camperlog.domain.formatAh
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.formatBar
import app.restvolt.camperlog.domain.formatCm
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.formatKg
import app.restvolt.camperlog.domain.formatLitres
import app.restvolt.camperlog.domain.formatPower
import app.restvolt.camperlog.domain.formatWp
import app.restvolt.camperlog.share.copyToClipboard
import app.restvolt.camperlog.share.tryStart
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.LabeledValue
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.TabTopBar
import app.restvolt.camperlog.ui.VehicleSwitcherTitle
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.text
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.vehicleDisplayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** Fahrzeug-Reiter: Datenblatt des aktuellen Fahrzeugs mit Erinnerungen und Reparaturen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    viewModel: VehicleViewModel,
    whereAmIViewModel: WhereAmIViewModel,
    locationEnabled: Boolean,
    reminderSettings: ReminderSettings,
    onOpenSearch: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVehicles: () -> Unit,
    onEditVehicle: (Long) -> Unit,
    onAddRepair: (Long) -> Unit,
    onOpenRepair: (Long, Long) -> Unit,
    onAddDocument: (Long) -> Unit,
    onOpenDocument: (Long, Long) -> Unit,
    onOpenChecklists: (Long) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vehicle = state.currentVehicle
    val today = LocalDate.now()
    val reminderPreferences by reminderSettings.values.collectAsStateWithLifecycle()
    val reminders = vehicle?.let {
        dueReminders(it, today, reminderPreferences.leadDays, reminderPreferences.oilChangeIntervalMonths) +
            documentReminders(state.documents, today, reminderPreferences.leadDays)
    }.orEmpty()

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val noDialerApp = stringResource(R.string.vehicle_no_dialer)
    val copyNumber = stringResource(R.string.vehicle_copy_number)
    var showWhereAmI by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TabTopBar(
                titleContent = {
                    VehicleSwitcherTitle(
                        vehicles = state.vehicles,
                        currentVehicleId = state.currentVehicleId,
                        title = vehicle?.let { vehicleDisplayName(it) } ?: stringResource(R.string.vehicle_title),
                        onSelectVehicle = viewModel::onSelectVehicle,
                        onManageVehicles = onOpenVehicles,
                    )
                },
                extraActions = {
                    if (vehicle != null) {
                        IconButton(onClick = { onEditVehicle(vehicle.id) }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.vehicle_edit_action))
                        }
                    }
                },
                overflowMenu = { VehicleOverflowMenu(onManageVehicles = onOpenVehicles) },
                onOpenSearch = onOpenSearch,
                onOpenData = onOpenData,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (vehicle != null) {
            VehicleSheet(
                vehicle = vehicle,
                reminders = reminders,
                today = today,
                repairs = state.repairs,
                documents = state.documents,
                checklistsWithoutTour = state.checklistsWithoutTour,
                locationEnabled = locationEnabled,
                onWhereAmI = { showWhereAmI = true },
                onAddDetails = { onEditVehicle(vehicle.id) },
                onOpenReminder = { reminder ->
                    val documentId = reminder.documentId
                    if (reminder.kind == ReminderKind.DOCUMENT_EXPIRY && documentId != null) {
                        onOpenDocument(vehicle.id, documentId)
                    } else {
                        onEditVehicle(vehicle.id)
                    }
                },
                onAddRepair = { onAddRepair(vehicle.id) },
                onOpenRepair = { repair -> onOpenRepair(vehicle.id, repair.id) },
                onAddDocument = { onAddDocument(vehicle.id) },
                onOpenDocument = { document -> onOpenDocument(vehicle.id, document.id) },
                onOpenChecklists = { onOpenChecklists(vehicle.id) },
                onCall = { phone -> dialOrOfferCopy(scope, context, snackbar, noDialerApp, copyNumber, phone) },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }

    if (showWhereAmI) {
        WhereAmISheet(viewModel = whereAmIViewModel, onDismiss = { showWhereAmI = false })
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is VehicleMessage.RepairDeleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.vehicle_repair_deleted),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteRepair(current.repair)
            }
            is VehicleMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }
}

@Composable
private fun VehicleSheet(
    vehicle: Vehicle,
    reminders: List<Reminder>,
    today: LocalDate,
    repairs: List<Repair>,
    documents: List<VehicleDocument>,
    checklistsWithoutTour: List<Checklist>,
    locationEnabled: Boolean,
    onWhereAmI: () -> Unit,
    onAddDetails: () -> Unit,
    onOpenReminder: (Reminder) -> Unit,
    onAddRepair: () -> Unit,
    onOpenRepair: (Repair) -> Unit,
    onAddDocument: () -> Unit,
    onOpenDocument: (VehicleDocument) -> Unit,
    onOpenChecklists: () -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier,
) {
    val locale = currentLocale()
    val soldLine = vehicle.saleDate?.let { stringResource(R.string.vehicle_sold_on, formatDate(it, locale)) }
    val purchaseSale = purchaseSaleRows(vehicle, locale)
    val dimensionsWeightTitle = stringResource(R.string.section_dimensions_weight)
    val sections = listOf(
        stringResource(R.string.section_general) to generalRows(vehicle, locale),
        stringResource(R.string.section_insurance_tax) to insuranceTaxRows(vehicle, locale),
        dimensionsWeightTitle to dimensionsRows(vehicle, locale),
        stringResource(R.string.section_engine) to engineRows(vehicle, locale),
        stringResource(R.string.section_tires) to tireRows(vehicle, locale),
        stringResource(R.string.section_tanks) to tankRows(vehicle, locale),
        stringResource(R.string.section_energy) to energyRows(vehicle, locale),
        stringResource(R.string.section_maintenance) to maintenanceRows(vehicle, locale),
    )
    val hasAnyValue = sections.any { it.second.isNotEmpty() } ||
        purchaseSale.isNotEmpty() || soldLine != null || vehicle.notes.isNotBlank()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (vehicle.hasBreakdownInfo) BreakdownAssistanceCard(vehicle, onCall, locationEnabled, onWhereAmI)
        reminders.forEach { reminder -> ReminderCard(reminder, today, onClick = { onOpenReminder(reminder) }) }
        if (!hasAnyValue) {
            EmptyHint(stringResource(R.string.vehicle_sheet_empty_hint))
            Button(
                onClick = onAddDetails,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.vehicle_sheet_add_details)) }
        } else {
            if (sections[0].second.isNotEmpty()) {
                SectionCard {
                    SectionHeading(sections[0].first)
                    sections[0].second.forEach { (label, value) -> LabeledValue(label, value) }
                }
            }
            if (purchaseSale.isNotEmpty() || soldLine != null) {
                SectionCard {
                    SectionHeading(stringResource(R.string.section_purchase_sale))
                    soldLine?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    purchaseSale.forEach { (label, value) -> LabeledValue(label, value) }
                }
            }
            sections.drop(1).forEach { (title, rows) ->
                if (rows.isNotEmpty()) {
                    SectionCard {
                        SectionHeading(title)
                        rows.forEach { (label, value) -> LabeledValue(label, value) }
                        if (title == dimensionsWeightTitle) {
                            vehicle.remainingPayloadKg?.takeIf { it < 0 }?.let { over ->
                                OverweightWarning(formatKg(abs(over), locale))
                            }
                        }
                    }
                }
            }
            if (vehicle.notes.isNotBlank()) {
                SectionCard {
                    SectionHeading(stringResource(R.string.field_notes))
                    Text(vehicle.notes, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        RepairsSection(repairs, onAddRepair, onOpenRepair)
        DocumentsSection(documents, today, onAddDocument, onOpenDocument)
        ChecklistsSummaryCard(checklistsWithoutTour, onOpenChecklists)
    }
}

@Composable
private fun ChecklistsSummaryCard(checklists: List<Checklist>, onOpen: () -> Unit) {
    val openLabel = stringResource(R.string.vehicle_checklists_open)
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = openLabel, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_checklist), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                SectionHeading(stringResource(R.string.section_checklists))
                Text(
                    pluralStringResource(R.plurals.vehicle_checklists_open_count, checklists.size, checklists.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DocumentsSection(documents: List<VehicleDocument>, today: LocalDate, onAdd: () -> Unit, onOpen: (VehicleDocument) -> Unit) {
    val locale = currentLocale()
    SectionCard {
        SectionHeading(stringResource(R.string.section_documents))
        if (documents.isEmpty()) {
            Text(
                stringResource(R.string.vehicle_documents_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Bereits nach Ablaufdatum sortiert, siehe VehicleDocumentRepository.observeForVehicle.
            documents.forEach { document -> DocumentRow(document, today, locale, onClick = { onOpen(document) }) }
        }
        TextButton(onClick = onAdd) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.vehicle_documents_add), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun DocumentRow(document: VehicleDocument, today: LocalDate, locale: Locale, onClick: () -> Unit) {
    val overdue = document.expiryDate?.isBefore(today) == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.document_detail_edit), onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(document.kind.iconRes), contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(document.title, style = MaterialTheme.typography.bodyLarge)
            document.expiryDate?.let { expiry ->
                Text(
                    stringResource(R.string.document_expiry_value, formatDate(expiry, locale)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(reminder: Reminder, today: LocalDate, onClick: () -> Unit) {
    val colors = if (reminder.overdue) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
    val editLabel = stringResource(R.string.vehicle_edit_action)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = editLabel, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            reminder.text(today, currentLocale()),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun RepairsSection(repairs: List<Repair>, onAddRepair: () -> Unit, onOpenRepair: (Repair) -> Unit) {
    val locale = currentLocale()
    SectionCard {
        SectionHeading(stringResource(R.string.section_repairs))
        if (repairs.isEmpty()) {
            Text(
                stringResource(R.string.vehicle_repairs_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            repairs.forEach { repair -> RepairRow(repair, locale, onClick = { onOpenRepair(repair) }) }
        }
        TextButton(onClick = onAddRepair) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.vehicle_repairs_add), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun RepairRow(repair: Repair, locale: Locale, onClick: () -> Unit) {
    val supportingParts = listOfNotNull(
        repair.odometerKm?.let { stringResource(R.string.distance_km, it) },
        repair.cost?.let { formatAmount(it.minor, it.currency, locale) },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.vehicle_repair_edit_action), onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(formatDate(repair.date, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(repair.description, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (supportingParts.isNotEmpty()) {
            Text(
                supportingParts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Karte „Panne & Unfall" am Kopf des Datenblatts; nur sichtbar, wenn [Vehicle.hasBreakdownInfo] gilt.
 * "Wo bin ich?" erscheint nur, wenn [locationEnabled] an ist.
 */
@Composable
private fun BreakdownAssistanceCard(vehicle: Vehicle, onCall: (String) -> Unit, locationEnabled: Boolean, onWhereAmI: () -> Unit) {
    val showBreakdown = vehicle.breakdownProvider.isNotBlank() || vehicle.breakdownMembershipNumber.isNotBlank() ||
        vehicle.breakdownPhone.isNotBlank()
    val showTravelProtection = vehicle.travelProtectionProvider.isNotBlank() ||
        vehicle.travelProtectionContractNumber.isNotBlank() || vehicle.travelProtectionPhone.isNotBlank()
    val showInsurerClaims = vehicle.insurerClaimsPhone.isNotBlank()

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_car_crash), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            SectionHeading(stringResource(R.string.section_breakdown_accident))
        }
        if (showBreakdown) {
            AssistanceGroup(
                groupTitle = stringResource(R.string.vehicle_breakdown_group),
                provider = vehicle.breakdownProvider,
                extraLine = vehicle.breakdownMembershipNumber.takeIf(String::isNotBlank)
                    ?.let { stringResource(R.string.vehicle_breakdown_membership_line, it) },
                phone = vehicle.breakdownPhone,
                fallbackSubject = stringResource(R.string.field_breakdown_phone),
                onCall = onCall,
            )
        }
        if (showBreakdown && (showTravelProtection || showInsurerClaims)) HorizontalDivider()
        if (showTravelProtection) {
            AssistanceGroup(
                groupTitle = stringResource(R.string.vehicle_travel_protection_group),
                provider = vehicle.travelProtectionProvider,
                extraLine = vehicle.travelProtectionContractNumber.takeIf(String::isNotBlank)
                    ?.let { stringResource(R.string.vehicle_travel_protection_contract_line, it) },
                phone = vehicle.travelProtectionPhone,
                fallbackSubject = stringResource(R.string.field_travel_protection_phone),
                onCall = onCall,
            )
        }
        if (showTravelProtection && showInsurerClaims) HorizontalDivider()
        if (showInsurerClaims) {
            AssistanceGroup(
                groupTitle = stringResource(R.string.field_insurer_claims_phone),
                provider = vehicle.insurer,
                extraLine = null,
                phone = vehicle.insurerClaimsPhone,
                fallbackSubject = stringResource(R.string.field_insurer_claims_phone),
                onCall = onCall,
            )
        }
        if (locationEnabled) {
            TextButton(onClick = onWhereAmI) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = null)
                Text(stringResource(R.string.vehicle_where_am_i), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Ein Block der Panne-&-Unfall-Karte: Titel mit Anbieter, optionale Zusatzzeile, Telefonzeile. */
@Composable
private fun AssistanceGroup(
    groupTitle: String,
    provider: String,
    extraLine: String?,
    phone: String,
    fallbackSubject: String,
    onCall: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (provider.isNotBlank()) "$groupTitle · $provider" else groupTitle,
            style = MaterialTheme.typography.bodyLarge,
        )
        extraLine?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (phone.isNotBlank()) {
            PhoneRow(subject = provider.ifBlank { fallbackSubject }, phone = phone, onCall = { onCall(phone) })
        }
    }
}

/** Telefonnummer mit Wähl-Symbol; die ganze Zeile ist der 48 dp hohe Tippbereich mit zusammengefasster Semantik. */
@Composable
private fun PhoneRow(subject: String, phone: String, onCall: () -> Unit) {
    val callLabel = stringResource(R.string.vehicle_call_action, subject, phone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = callLabel, onClick = onCall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(phone, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(painterResource(R.drawable.ic_call), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Warnung bei negativer Restzuladung: Symbol und Fehlertext, nicht nur Farbe. */
@Composable
private fun OverweightWarning(overweightText: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(
            stringResource(R.string.vehicle_overloaded_by, overweightText),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Öffnet den Wähler für [phone] über `ACTION_DIAL`; ohne Telefon-App bietet die Snackbar das Kopieren an. */
private fun dialOrOfferCopy(
    scope: CoroutineScope,
    context: Context,
    snackbar: SnackbarHostState,
    noDialerApp: String,
    copyNumber: String,
    phone: String,
) {
    val intent = Intent(Intent.ACTION_DIAL, "tel:${phone.replace(" ", "")}".toUri())
    if (!context.tryStart(intent)) {
        scope.launch {
            val result = snackbar.showSnackbar(message = noDialerApp, actionLabel = copyNumber, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) context.copyToClipboard(phone, phone)
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun generalRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    if (vehicle.licensePlate.isNotBlank()) add(stringResource(R.string.field_license_plate) to vehicle.licensePlate)
    if (vehicle.manufacturer.isNotBlank()) add(stringResource(R.string.field_manufacturer) to vehicle.manufacturer)
    if (vehicle.model.isNotBlank()) add(stringResource(R.string.field_model) to vehicle.model)
    if (vehicle.vin.isNotBlank()) add(stringResource(R.string.field_vin) to vehicle.vin)
    vehicle.firstRegistration?.let { add(stringResource(R.string.field_first_registration) to formatDate(it, locale)) }
}

@Composable
private fun purchaseSaleRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.purchaseDate?.let { add(stringResource(R.string.field_purchase_date) to formatDate(it, locale)) }
    vehicle.purchasePrice?.let { add(stringResource(R.string.field_purchase_price) to formatAmount(it.minor, it.currency, locale)) }
    vehicle.purchaseOdometerKm?.let { add(stringResource(R.string.field_purchase_odometer) to stringResource(R.string.distance_km, it)) }
    vehicle.salePrice?.let { add(stringResource(R.string.field_sale_price) to formatAmount(it.minor, it.currency, locale)) }
    vehicle.saleOdometerKm?.let { add(stringResource(R.string.field_sale_odometer) to stringResource(R.string.distance_km, it)) }
}

@Composable
private fun insuranceTaxRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    if (vehicle.insurer.isNotBlank()) add(stringResource(R.string.field_insurer) to vehicle.insurer)
    if (vehicle.insurancePolicyNumber.isNotBlank()) {
        add(stringResource(R.string.field_insurance_policy_number) to vehicle.insurancePolicyNumber)
    }
    vehicle.insurancePremiumPerYear?.let { add(stringResource(R.string.field_insurance_premium) to formatAmount(it.minor, it.currency, locale)) }
    vehicle.vehicleTaxPerYear?.let { add(stringResource(R.string.field_vehicle_tax) to formatAmount(it.minor, it.currency, locale)) }
}

@Composable
private fun dimensionsRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.lengthCm?.let { add(stringResource(R.string.field_length) to formatCm(it, locale)) }
    vehicle.widthCm?.let { add(stringResource(R.string.field_width) to formatCm(it, locale)) }
    vehicle.heightCm?.let { add(stringResource(R.string.field_height) to formatCm(it, locale)) }
    vehicle.grossWeightKg?.let { add(stringResource(R.string.field_gross_weight) to formatKg(it, locale)) }
    vehicle.measuredEmptyWeightKg?.let { add(stringResource(R.string.field_measured_empty_weight) to formatKg(it, locale)) }
    vehicle.remainingPayloadKg?.let { add(stringResource(R.string.field_remaining_payload) to formatKg(abs(it), locale)) }
}

@Composable
private fun engineRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.powerKw?.let { add(stringResource(R.string.field_power) to formatPower(it, locale)) }
    vehicle.displacementCc?.let { add(stringResource(R.string.field_displacement) to stringResource(R.string.vehicle_displacement_value, it)) }
    vehicle.transmission?.let { add(stringResource(R.string.field_transmission) to stringResource(it.labelRes)) }
}

@Composable
private fun tireRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    if (vehicle.tireSize.isNotBlank()) add(stringResource(R.string.field_tire_size) to vehicle.tireSize)
    vehicle.tirePressureFrontMbar?.let { add(stringResource(R.string.field_tire_pressure_front) to formatBar(it, locale)) }
    vehicle.tirePressureRearMbar?.let { add(stringResource(R.string.field_tire_pressure_rear) to formatBar(it, locale)) }
}

@Composable
private fun tankRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.fuelTankDl?.let { add(stringResource(R.string.field_fuel_tank) to formatLitres(it, locale)) }
    vehicle.adBlueTankDl?.let { add(stringResource(R.string.field_ad_blue_tank) to formatLitres(it, locale)) }
    vehicle.freshWaterTankDl?.let { add(stringResource(R.string.field_fresh_water_tank) to formatLitres(it, locale)) }
    vehicle.greyWaterTankDl?.let { add(stringResource(R.string.field_grey_water_tank) to formatLitres(it, locale)) }
    vehicle.boilerDl?.let { add(stringResource(R.string.field_boiler) to formatLitres(it, locale)) }
    vehicle.cassetteDl?.let { add(stringResource(R.string.field_cassette) to formatLitres(it, locale)) }
}

@Composable
private fun energyRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.batteryCapacityAh?.let { add(stringResource(R.string.field_battery_capacity) to formatAh(it, locale)) }
    vehicle.solarPowerWp?.let { add(stringResource(R.string.field_solar_power) to formatWp(it, locale)) }
}

@Composable
private fun maintenanceRows(vehicle: Vehicle, locale: Locale): List<Pair<String, String>> = buildList {
    vehicle.nextInspectionDate?.let { add(stringResource(R.string.field_next_inspection) to formatDate(it, locale)) }
    vehicle.nextGasCheckDate?.let { add(stringResource(R.string.field_next_gas_check) to formatDate(it, locale)) }
    vehicle.nextLeakTestDate?.let { add(stringResource(R.string.field_next_leak_test) to formatDate(it, locale)) }
    vehicle.lastOilChangeDate?.let { add(stringResource(R.string.field_last_oil_change) to formatDate(it, locale)) }
    vehicle.lastOilChangeOdometerKm?.let { add(stringResource(R.string.field_last_oil_change_odometer) to stringResource(R.string.distance_km, it)) }
}

@Composable
private fun VehicleOverflowMenu(onManageVehicles: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vehicle_switcher_manage)) },
                onClick = {
                    expanded = false
                    onManageVehicles()
                },
            )
        }
    }
}
