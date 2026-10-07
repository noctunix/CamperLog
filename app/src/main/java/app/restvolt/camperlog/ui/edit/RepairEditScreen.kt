package app.restvolt.camperlog.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.MAX_AMOUNT_MINOR
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.RepairError
import app.restvolt.camperlog.domain.RepairField
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.DiscardChangesDialog
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.messageRes
import java.util.Currency
import java.util.Locale

/**
 * Formular zum Anlegen und Bearbeiten einer Reparatur. [onDone] verlässt es ohne, [onSaved] nach
 * dem Speichern; [onDelete] löscht eine bestehende Reparatur und wird mit ihr aufgerufen.
 */
@Composable
fun RepairEditScreen(
    viewModel: RepairEditViewModel,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    onDone: () -> Unit,
    onSaved: () -> Unit,
    onDelete: (Repair) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.repair_edit_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = state.isDirty && !state.isSaved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.repair_edit_title_new else R.string.repair_edit_title_existing),
                onBack = requestBack,
                actions = {
                    if (!state.notFound && !state.isLoading) {
                        state.original?.let { repair ->
                            IconButton(onClick = { onDelete(repair) }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.vehicle_repair_delete_action),
                                )
                            }
                        }
                        TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
                    }
                },
            )
        },
        bottomBar = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.notFound -> EmptyHint(stringResource(R.string.repair_not_found), Modifier.padding(padding))
            else -> RepairForm(
                state = state,
                viewModel = viewModel,
                attachments = attachments,
                attachmentFileStore = attachmentFileStore,
                snackbarHostState = snackbar,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding(),
            )
        }
    }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeep = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onDone()
            },
        )
    }
}

@Composable
private fun RepairForm(
    state: RepairEditUiState,
    viewModel: RepairEditViewModel,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    val input = state.input
    val change = viewModel::onInputChange
    val required = stringResource(R.string.edit_required)
    val focus = remember { RepairField.entries.associateWith { FocusRequester() } }
    fun focusOf(field: RepairField) = Modifier.focusRequester(focus.getValue(field))
    @Composable fun errorOf(field: RepairField): String? = state.errors[field]?.messageRes(field)

    // Nach einem abgelehnten Speichern zum ersten fehlerhaften Feld springen; der Fokus scrollt es ins Bild.
    LaunchedEffect(state.rejectedSaves) {
        if (state.rejectedSaves > 0) {
            state.errors.keys.minByOrNull(RepairField::ordinal)?.let { focus.getValue(it).requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            DateField(
                stringResource(R.string.field_date),
                input.date,
                errorOf(RepairField.DATE),
                { date -> change { it.copy(date = date) } },
                modifier = focusOf(RepairField.DATE),
            )
            FormTextField(
                label = stringResource(R.string.field_description),
                value = input.description,
                onValueChange = { value -> change { it.copy(description = value) } },
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                modifier = focusOf(RepairField.DESCRIPTION),
                error = errorOf(RepairField.DESCRIPTION),
                hint = required,
            )
            UnitField(
                label = stringResource(R.string.field_odometer_km),
                value = input.odometerKm,
                unit = "km",
                keyboardType = KeyboardType.Number,
                error = errorOf(RepairField.ODOMETER_KM),
                modifier = focusOf(RepairField.ODOMETER_KM),
                onValueChange = { value -> change { it.copy(odometerKm = value) } },
            )
            MoneyField(
                label = stringResource(R.string.field_cost),
                amount = input.cost,
                currency = input.costCurrency,
                error = state.errors[RepairField.COST],
                onAmountChange = { value -> change { it.copy(cost = value) } },
                onCurrencyChange = { value -> change { it.copy(costCurrency = value) } },
                modifier = focusOf(RepairField.COST),
            )
        }
        PhotoAttachmentsSection(
            ownerType = AttachmentOwnerType.REPAIR,
            ownerId = state.savedRepairId,
            repository = attachments,
            fileStore = attachmentFileStore,
            snackbarHostState = snackbarHostState,
        )
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.action_save)) }
        if (state.errors.isNotEmpty()) {
            // Nennt die betroffenen Felder; ändert sich die Liste, sagt TalkBack sie erneut an.
            val fields = state.errors.keys.sortedBy(RepairField::ordinal).map { stringResource(it.labelRes) }
            Text(
                stringResource(R.string.edit_check_fields, fields.joinToString(", ")),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    singleLine: Boolean = true,
    error: String? = null,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
    )
}

/** Zahlenfeld mit Einheit als Suffix. */
@Composable
private fun UnitField(
    label: String,
    value: String,
    unit: String,
    keyboardType: KeyboardType,
    error: String?,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        trailingIcon = { Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Betragsfeld mit Währungsauswahl. */
@Composable
private fun MoneyField(
    label: String,
    amount: String,
    currency: Currency,
    error: RepairError?,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val currencyDescription = stringResource(R.string.edit_cost_currency, currency.getDisplayName(locale))
    Row(modifier = modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.field_amount_with_currency, label, currency.getSymbol(locale))) },
            isError = error != null,
            supportingText = error?.let { { Text(moneyErrorText(it, currency, locale)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = currencyDescription },
        ) { Text(currency.currencyCode) }
    }
    if (showPicker) {
        CurrencyPicker(
            selected = currency,
            excluded = emptySet(),
            locale = locale,
            onSelect = {
                onCurrencyChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun moneyErrorText(error: RepairError, currency: Currency, locale: Locale): String = when (error) {
    RepairError.AMOUNT_TOO_LARGE -> stringResource(R.string.error_amount_too_large, formatAmount(MAX_AMOUNT_MINOR, currency, locale))
    else -> stringResource(R.string.error_invalid_amount)
}
