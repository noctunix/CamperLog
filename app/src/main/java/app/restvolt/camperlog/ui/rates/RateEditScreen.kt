package app.restvolt.camperlog.ui.rates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.DiscardChangesDialog
import app.restvolt.camperlog.ui.currentLocale
import java.util.Currency
import java.util.Locale

/** Formular für einen Kurs: Währung (nur bei neuen Kursen wählbar), Betrag pro Euro, Stand und Quelle. */
@Composable
fun RateEditScreen(viewModel: RateEditViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.rate_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = state.isDirty && !state.isSaved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            BackTopBar(
                title = state.currency.takeUnless { state.isNew }
                    ?.let { stringResource(R.string.rate_edit_title, it.currencyCode) }
                    ?: stringResource(R.string.rates_add),
                onBack = requestBack,
                actions = {
                    if (!state.isLoading) {
                        TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
                    }
                },
            )
        },
        bottomBar = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        if (!state.isLoading) {
            RateForm(
                state = state,
                viewModel = viewModel,
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
private fun RateForm(state: RateEditUiState, viewModel: RateEditViewModel, modifier: Modifier) {
    val locale = currentLocale()
    val currencyFocus = remember { FocusRequester() }
    val rateFocus = remember { FocusRequester() }
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    val code = state.currency?.currencyCode

    LaunchedEffect(state.rejectedSaves) {
        if (state.rejectedSaves == 0) return@LaunchedEffect
        when {
            RateError.CURRENCY_REQUIRED in state.errors -> currencyFocus.requestFocus()
            RateError.RATE_INVALID in state.errors -> rateFocus.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isNew) {
            CurrencyField(
                currency = state.currency,
                locale = locale,
                error = RateError.CURRENCY_REQUIRED in state.errors,
                onClick = { pickCurrency = true },
                modifier = Modifier.focusRequester(currencyFocus),
            )
        }
        val rateError = RateError.RATE_INVALID in state.errors
        OutlinedTextField(
            value = state.rate,
            onValueChange = viewModel::onRateChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rateFocus),
            label = {
                Text(code?.let { stringResource(R.string.rate_field_rate, it) } ?: stringResource(R.string.rate_field_rate_unknown))
            },
            supportingText = {
                when {
                    rateError -> Text(
                        stringResource(R.string.rate_error_rate),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    code != null -> Text(stringResource(R.string.rate_field_rate_hint, code))
                }
            },
            isError = rateError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.medium,
        )
        DateField(
            label = stringResource(R.string.rate_field_date),
            date = state.date,
            error = null,
            onDateSelected = viewModel::onDateChange,
        )
        OutlinedTextField(
            value = state.source,
            onValueChange = viewModel::onSourceChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.rate_field_source)) },
            supportingText = { Text(stringResource(R.string.rate_field_source_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
            shape = MaterialTheme.shapes.medium,
        )
        Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_save))
        }
    }

    if (pickCurrency) {
        CurrencyPicker(
            selected = state.currency ?: EUR,
            excluded = state.unavailable,
            locale = locale,
            onSelect = {
                pickCurrency = false
                viewModel.onCurrencyChange(it)
            },
            onDismiss = { pickCurrency = false },
        )
    }
}

/** Schaltfläche zur Währungswahl mit Fehlermeldung darunter. */
@Composable
private fun CurrencyField(
    currency: Currency?,
    locale: Locale,
    error: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.rate_field_currency)
    val text = currency?.let { stringResource(R.string.rates_currency_option, it.currencyCode, it.getDisplayName(locale)) }
        ?: stringResource(R.string.edit_cost_pick_currency)
    val errorText = stringResource(R.string.rate_error_currency)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$label: $text"
                    if (error) error(errorText)
                },
            border = if (error) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonBorder(),
        ) {
            Text(text)
        }
        if (error) {
            Text(
                errorText,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
