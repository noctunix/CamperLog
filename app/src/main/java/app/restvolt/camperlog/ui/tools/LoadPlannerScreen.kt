package app.restvolt.camperlog.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LoadCategory
import app.restvolt.camperlog.domain.LoadForecast
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadLimitCheck
import app.restvolt.camperlog.domain.LoadPlan
import app.restvolt.camperlog.domain.LoadProfile
import app.restvolt.camperlog.domain.LoadProfileError
import app.restvolt.camperlog.domain.LoadProfileField
import app.restvolt.camperlog.domain.LoadProfileInput
import app.restvolt.camperlog.domain.LoadTankKind
import app.restvolt.camperlog.domain.LoadTankSettings
import app.restvolt.camperlog.domain.LoadTankSettingsError
import app.restvolt.camperlog.domain.LoadTankSettingsField
import app.restvolt.camperlog.domain.LoadTankSettingsInput
import app.restvolt.camperlog.domain.LoadWarning
import app.restvolt.camperlog.domain.MAX_LOAD_TANK_DENSITY_GRAMS_PER_LITRE
import app.restvolt.camperlog.domain.MIN_LOAD_POSITION_CM
import app.restvolt.camperlog.domain.MAX_LOAD_POSITION_CM
import app.restvolt.camperlog.domain.MIN_LOAD_TANK_DENSITY_GRAMS_PER_LITRE
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.capacityDlOf
import app.restvolt.camperlog.domain.densityGramsPerLitreToKgPerLitreInput
import app.restvolt.camperlog.domain.formatCm
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.formatKg
import app.restvolt.camperlog.domain.formatLitres
import app.restvolt.camperlog.domain.parseKgPerLitreToDensityGramsPerLitre
import app.restvolt.camperlog.domain.tankLevelDl
import app.restvolt.camperlog.domain.tankSettingsOrDefault
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toSettings
import app.restvolt.camperlog.domain.validate
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.LabeledValue
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Beladungsplaner eines Fahrzeugs: Profile, Hochrechnung gegenüber der Wiegung, Tanks und Gegenstände. */
@Composable
fun LoadPlannerScreen(
    viewModel: LoadPlannerViewModel,
    onBack: () -> Unit,
    onEditVehicle: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vehicle = state.vehicle
    val locale = currentLocale()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    val defaultProfileName = stringResource(R.string.load_profile_default_name)
    LaunchedEffect(vehicle?.id) {
        vehicle?.let { viewModel.ensureProfile(it.id, defaultProfileName) }
    }

    var showAddProfile by rememberSaveable { mutableStateOf(false) }
    var renamingProfile by remember { mutableStateOf<LoadProfile?>(null) }
    var duplicatingProfile by remember { mutableStateOf<LoadProfile?>(null) }
    var confirmDeleteProfile by remember { mutableStateOf<LoadProfile?>(null) }
    var showAddItem by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<LoadItem?>(null) }
    var tankSettingsKind by remember { mutableStateOf<LoadTankKind?>(null) }

    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.load_planner_title), onBack = onBack) },
        floatingActionButton = {
            if (vehicle != null) {
                ExtendedFloatingActionButton(onClick = { showAddItem = true }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Text(stringResource(R.string.load_items_add_fab), Modifier.padding(start = 8.dp))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (vehicle == null) {
            Box(Modifier.padding(padding).fillMaxSize())
        } else {
            val plan = state.plan
            val selectedProfileId = state.selectedProfileId
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "profiles") {
                    ProfileChipsRow(
                        profiles = plan.profiles,
                        selectedProfileId = selectedProfileId,
                        onSelect = { viewModel.selectProfile(vehicle.id, it) },
                        onAdd = { showAddProfile = true },
                        onRename = { renamingProfile = it },
                        onDuplicate = { duplicatingProfile = it },
                        onDelete = { confirmDeleteProfile = it },
                    )
                }
                state.forecast?.let { forecast ->
                    item(key = "result") { ResultCard(vehicle, forecast, locale, onEditVehicle = { onEditVehicle(vehicle.id) }) }
                }
                item(key = "tanks") {
                    TanksSection(
                        vehicle = vehicle,
                        plan = plan,
                        profileId = selectedProfileId,
                        locale = locale,
                        onLevelChange = { kind, levelDl -> viewModel.setTankLevel(selectedProfileId, kind, levelDl) },
                        onOpenSettings = { tankSettingsKind = it },
                    )
                }
                LoadCategory.entries.forEach { category ->
                    val items = plan.items.filter { it.category == category }.sortedBy { it.name }
                    if (items.isNotEmpty()) {
                        item(key = "category_$category") {
                            SectionCard {
                                CategoryHeading(category)
                                items.forEach { item ->
                                    val active = plan.itemStates.firstOrNull { it.profileId == selectedProfileId && it.itemId == item.id }
                                        ?.active ?: item.inWeighing
                                    LoadItemRow(
                                        item = item,
                                        active = active,
                                        locale = locale,
                                        onToggle = { viewModel.setItemActive(selectedProfileId, item, it) },
                                        onClick = { editingItem = item },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProfile && vehicle != null) {
        LoadProfileNameDialog(
            title = stringResource(R.string.load_profile_add),
            initialName = "",
            onDismiss = { showAddProfile = false },
            onConfirm = { name ->
                viewModel.addProfile(vehicle.id, name)
                showAddProfile = false
            },
        )
    }
    renamingProfile?.let { profile ->
        LoadProfileNameDialog(
            title = stringResource(R.string.load_profile_rename_title),
            initialName = profile.name,
            onDismiss = { renamingProfile = null },
            onConfirm = { name ->
                viewModel.renameProfile(profile, name)
                renamingProfile = null
            },
        )
    }
    duplicatingProfile?.let { profile ->
        LoadProfileNameDialog(
            title = stringResource(R.string.load_profile_duplicate),
            initialName = stringResource(R.string.load_profile_duplicate_copy_suffix, profile.name),
            onDismiss = { duplicatingProfile = null },
            onConfirm = { name ->
                viewModel.duplicateProfile(profile, name)
                duplicatingProfile = null
            },
        )
    }
    confirmDeleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { confirmDeleteProfile = null },
            title = { Text(stringResource(R.string.load_profile_delete_confirm_title)) },
            text = { Text(stringResource(R.string.load_profile_delete_confirm_text, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteProfile = null
                    viewModel.deleteProfile(profile)
                }) { Text(stringResource(R.string.load_profile_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteProfile = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showAddItem && vehicle != null) {
        LoadItemDialog(
            vehicleId = vehicle.id,
            original = null,
            onDismiss = { showAddItem = false },
            onSave = { item ->
                viewModel.saveItem(item, state.selectedProfileId)
                showAddItem = false
            },
        )
    }
    editingItem?.let { item ->
        if (vehicle != null) {
            LoadItemDialog(
                vehicleId = vehicle.id,
                original = item,
                onDismiss = { editingItem = null },
                onSave = { saved ->
                    viewModel.saveItem(saved, state.selectedProfileId)
                    editingItem = null
                },
                onDelete = {
                    viewModel.deleteItem(item)
                    editingItem = null
                },
            )
        }
    }
    tankSettingsKind?.let { kind ->
        if (vehicle != null) {
            val capacityDl = checkNotNull(kind.capacityDlOf(vehicle))
            LoadTankSettingsDialog(
                vehicle = vehicle,
                kind = kind,
                settings = state.plan.tankSettingsOrDefault(vehicle, kind),
                capacityDl = capacityDl,
                onDismiss = { tankSettingsKind = null },
                onSave = { settings ->
                    viewModel.saveTankSettings(settings)
                    tankSettingsKind = null
                },
            )
        }
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is LoadPlannerMessage.ItemDeleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.load_item_deleted),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreItem(current.item)
            }
        }
        viewModel.onMessageShown(current)
    }
}

@Composable
private fun ProfileChipsRow(
    profiles: List<LoadProfile>,
    selectedProfileId: Long,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onRename: (LoadProfile) -> Unit,
    onDuplicate: (LoadProfile) -> Unit,
    onDelete: (LoadProfile) -> Unit,
) {
    val selected = profiles.firstOrNull { it.id == selectedProfileId }
    var menuExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = LoadProfile::id) { profile ->
                FilterChip(selected = profile.id == selectedProfileId, onClick = { onSelect(profile.id) }, label = { Text(profile.name) })
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.load_profile_menu_description))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.load_profile_add)) }, onClick = { menuExpanded = false; onAdd() })
                if (selected != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.load_profile_rename)) }, onClick = { menuExpanded = false; onRename(selected) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.load_profile_duplicate)) }, onClick = { menuExpanded = false; onDuplicate(selected) })
                    if (profiles.size > 1) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.load_profile_delete)) }, onClick = { menuExpanded = false; onDelete(selected) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadProfileNameDialog(title: String, initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf(LoadProfileInput(name = initialName)) }
    var touched by remember { mutableStateOf(false) }
    val errors = if (touched) input.validate() else emptyMap()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input.name,
                onValueChange = { input = input.copy(name = it) },
                label = { Text(stringResource(R.string.field_name)) },
                isError = errors.containsKey(LoadProfileField.NAME),
                supportingText = errors[LoadProfileField.NAME]?.let { { Text(profileErrorText(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                touched = true
                if (input.validate().isEmpty()) onConfirm(input.name.trim())
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun profileErrorText(error: LoadProfileError): String = when (error) {
    LoadProfileError.REQUIRED -> stringResource(R.string.load_name_required)
    LoadProfileError.TOO_LONG -> stringResource(R.string.error_text_too_long)
}

@Composable
private fun ResultCard(vehicle: Vehicle, forecast: LoadForecast, locale: Locale, onEditVehicle: () -> Unit) {
    SectionCard {
        SectionHeading(stringResource(R.string.load_planner_title))
        forecast.totalKg?.let { LoadLimitRow(stringResource(R.string.load_result_total), it, forecast.totalCheck, locale) }
        forecast.frontAxleKg?.let { LoadLimitRow(stringResource(R.string.vehicle_axle_front), it, forecast.frontAxleCheck, locale) }
        forecast.rearAxleKg?.let { LoadLimitRow(stringResource(R.string.vehicle_axle_rear), it, forecast.rearAxleCheck, locale) }
        Text(
            stringResource(R.string.load_result_delta, formatSignedKg(forecast.deltaKg, locale)),
            style = MaterialTheme.typography.bodyLarge,
        )
        val measuredFront = vehicle.measuredFrontAxleKg
        val measuredRear = vehicle.measuredRearAxleKg
        if (measuredFront != null && measuredRear != null) {
            val frontText = formatKg(measuredFront, locale)
            val rearText = formatKg(measuredRear, locale)
            val provenance = vehicle.weighingDate?.let {
                stringResource(R.string.load_result_based_on_weighing, formatDate(it, locale), frontText, rearText)
            } ?: stringResource(R.string.load_result_based_on_weighing_no_date, frontText, rearText)
            Text(provenance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        forecast.warnings.forEach { warning -> WarningLine(warning, locale, onEditVehicle) }
        Text(
            stringResource(R.string.load_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadLimitRow(label: String, valueKg: Double, check: LoadLimitCheck?, locale: Locale) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledValue(label, formatKg(valueKg.roundToInt(), locale))
        if (check != null) {
            LabeledValue(stringResource(R.string.load_result_limit), formatKg(check.limitKg, locale))
            val overloaded = !check.ok
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painterResource(if (overloaded) R.drawable.ic_warning else R.drawable.ic_check),
                    contentDescription = null,
                    tint = if (overloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                val text = if (overloaded) {
                    stringResource(R.string.load_result_over_limit, formatKg(abs(check.reserveKg).roundToInt(), locale))
                } else {
                    stringResource(R.string.load_result_within_limit, formatKg(check.reserveKg.roundToInt(), locale), formatPercent(check.reservePercent, locale))
                }
                Text(
                    text,
                    color = if (overloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun WarningLine(warning: LoadWarning, locale: Locale, onEditVehicle: () -> Unit) {
    val (text, showEditAction) = when (warning) {
        LoadWarning.NoWeighing -> stringResource(R.string.load_warning_no_weighing) to true
        LoadWarning.NoWheelbase -> stringResource(R.string.load_warning_no_wheelbase) to true
        is LoadWarning.ItemsWithoutPosition ->
            pluralStringResource(R.plurals.load_warning_items_without_position, warning.count, warning.count, formatKg(warning.massKg.roundToInt(), locale)) to false
        is LoadWarning.TankWithoutPosition ->
            stringResource(R.string.load_warning_tank_without_position, stringResource(warning.kind.labelRes), formatKg(warning.massKg.roundToInt(), locale)) to false
        LoadWarning.FrontAxleNonPositive -> stringResource(R.string.load_warning_front_axle_non_positive) to false
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showEditAction) {
            TextButton(onClick = onEditVehicle) { Text(stringResource(R.string.vehicle_edit_action)) }
        }
    }
}

@Composable
private fun TanksSection(
    vehicle: Vehicle,
    plan: LoadPlan,
    profileId: Long,
    locale: Locale,
    onLevelChange: (LoadTankKind, Int) -> Unit,
    onOpenSettings: (LoadTankKind) -> Unit,
) {
    val kinds = LoadTankKind.entries.filter { it.capacityDlOf(vehicle) != null }
    SectionCard {
        SectionHeading(stringResource(R.string.load_tanks_title))
        if (kinds.isEmpty()) {
            Text(
                stringResource(R.string.load_tanks_empty_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            kinds.forEach { kind ->
                val capacityDl = checkNotNull(kind.capacityDlOf(vehicle))
                val settings = plan.tankSettingsOrDefault(vehicle, kind)
                val levelDl = plan.tankLevelDl(vehicle, profileId, kind)
                val massDeltaKg = (levelDl - settings.weighingLevelDl) * settings.densityGramsPerLitre / 10_000.0
                TankRow(
                    kind = kind,
                    capacityDl = capacityDl,
                    levelDl = levelDl,
                    massDeltaKg = massDeltaKg,
                    locale = locale,
                    onLevelChange = { onLevelChange(kind, it) },
                    onOpenSettings = { onOpenSettings(kind) },
                )
            }
        }
    }
}

@Composable
private fun TankRow(
    kind: LoadTankKind,
    capacityDl: Int,
    levelDl: Int,
    massDeltaKg: Double,
    locale: Locale,
    onLevelChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val label = stringResource(kind.labelRes)
    val capacityL = (capacityDl / 10).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(formatLitres(levelDl, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.load_tank_settings_action, label))
            }
        }
        Slider(
            value = (levelDl / 10f).coerceIn(0f, capacityL.toFloat()),
            onValueChange = { onLevelChange((it.roundToInt() * 10).coerceIn(0, capacityDl)) },
            valueRange = 0f..capacityL.toFloat(),
            steps = (capacityL - 1).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$label ${formatLitres(levelDl, locale)}" },
        )
        Text(
            stringResource(R.string.load_tank_mass_change, formatSignedKg(massDeltaKg, locale)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadTankSettingsDialog(
    vehicle: Vehicle,
    kind: LoadTankKind,
    settings: LoadTankSettings,
    capacityDl: Int,
    onDismiss: () -> Unit,
    onSave: (LoadTankSettings) -> Unit,
) {
    val locale = currentLocale()
    var positionText by remember { mutableStateOf(settings.positionCm?.toString().orEmpty()) }
    var densityText by remember { mutableStateOf(densityGramsPerLitreToKgPerLitreInput(settings.densityGramsPerLitre, locale)) }
    var weighingLevelText by remember { mutableStateOf(settings.toInput(locale).weighingLevelL) }
    var touched by remember { mutableStateOf(false) }

    fun buildInput(): LoadTankSettingsInput {
        val gramsText = parseKgPerLitreToDensityGramsPerLitre(densityText, locale)?.toString() ?: densityText
        return LoadTankSettingsInput(positionCm = positionText, densityGramsPerLitre = gramsText, weighingLevelL = weighingLevelText)
    }
    val errors = if (touched) buildInput().validate(capacityDl, locale) else emptyMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.load_tank_settings_title, stringResource(kind.labelRes))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = positionText,
                    onValueChange = { positionText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.load_position_label)) },
                    isError = errors.containsKey(LoadTankSettingsField.POSITION),
                    supportingText = {
                        Text(
                            errors[LoadTankSettingsField.POSITION]?.let { tankErrorText(it, LoadTankSettingsField.POSITION, locale, capacityDl) }
                                ?: stringResource(R.string.load_position_supporting),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = densityText,
                    onValueChange = { densityText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.load_tank_density_label)) },
                    isError = errors.containsKey(LoadTankSettingsField.DENSITY),
                    supportingText = errors[LoadTankSettingsField.DENSITY]?.let { { Text(tankErrorText(it, LoadTankSettingsField.DENSITY, locale, capacityDl)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = weighingLevelText,
                    onValueChange = { weighingLevelText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.load_tank_weighing_level_label)) },
                    isError = errors.containsKey(LoadTankSettingsField.WEIGHING_LEVEL),
                    supportingText = errors[LoadTankSettingsField.WEIGHING_LEVEL]?.let {
                        { Text(tankErrorText(it, LoadTankSettingsField.WEIGHING_LEVEL, locale, capacityDl)) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                touched = true
                val input = buildInput()
                if (input.validate(capacityDl, locale).isEmpty()) {
                    onSave(input.toSettings(vehicle.id, kind, locale))
                    onDismiss()
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun tankErrorText(error: LoadTankSettingsError, field: LoadTankSettingsField, locale: Locale, capacityDl: Int): String = when (error) {
    LoadTankSettingsError.INVALID_NUMBER -> stringResource(R.string.error_invalid_number)
    LoadTankSettingsError.NEGATIVE_NUMBER -> stringResource(R.string.error_negative_number)
    LoadTankSettingsError.TOO_LARGE -> stringResource(R.string.error_value_too_large, formatLitres(capacityDl, locale))
    LoadTankSettingsError.OUT_OF_RANGE -> if (field == LoadTankSettingsField.DENSITY) {
        stringResource(
            R.string.error_out_of_range,
            "${densityGramsPerLitreToKgPerLitreInput(MIN_LOAD_TANK_DENSITY_GRAMS_PER_LITRE, locale)} kg/l",
            "${densityGramsPerLitreToKgPerLitreInput(MAX_LOAD_TANK_DENSITY_GRAMS_PER_LITRE, locale)} kg/l",
        )
    } else {
        stringResource(
            R.string.error_out_of_range,
            "${NumberFormat.getIntegerInstance(locale).format(MIN_LOAD_POSITION_CM)} cm",
            "${NumberFormat.getIntegerInstance(locale).format(MAX_LOAD_POSITION_CM)} cm",
        )
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
private fun CategoryHeading(category: LoadCategory) {
    Text(
        stringResource(category.labelRes),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LoadItemRow(item: LoadItem, active: Boolean, locale: Locale, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val massText = stringResource(R.string.load_item_mass_times, item.quantity, formatMassKg(item.massGrams, locale))
    val activeDescription = stringResource(R.string.load_item_active_description, item.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.load_item_dialog_title_edit), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(massText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.positionCm == null) {
                    Icon(
                        painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.load_item_position_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(formatCm(item.positionCm, locale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.inWeighing) {
                    Text(
                        "· " + stringResource(R.string.load_item_in_weighing_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .toggleable(value = active, role = Role.Switch, onValueChange = onToggle)
                .semantics(mergeDescendants = true) { contentDescription = activeDescription },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = active, onCheckedChange = null)
        }
    }
}

/** Signierte Masse in kg, gerundet auf ganze Kilogramm, z. B. `+124 kg` oder `-50 kg`. */
private fun formatSignedKg(kg: Double, locale: Locale): String {
    val rounded = kg.roundToInt()
    val sign = if (rounded > 0) "+" else ""
    return "$sign${formatKg(rounded, locale)}"
}

/** Reserve in Prozent mit einer Nachkommastelle, z. B. `12,5%`. */
private fun formatPercent(percent: Double, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.minimumFractionDigits = 1
    format.maximumFractionDigits = 1
    return "${format.format(percent)}%"
}

/** Masse eines einzelnen Stücks in kg, ohne überflüssige Nachkommastellen, z. B. `3,5 kg`. */
private fun formatMassKg(massGrams: Long, locale: Locale): String {
    val value = java.math.BigDecimal.valueOf(massGrams, 3).stripTrailingZeros()
    val symbols = java.text.DecimalFormatSymbols.getInstance(locale)
    return "${value.toPlainString().replace('.', symbols.decimalSeparator)} kg"
}
