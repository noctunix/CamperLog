package app.restvolt.camperlog.data

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.MAX_PAYMENT_METHODS
import app.restvolt.camperlog.domain.MAX_PAYMENT_METHOD_LENGTH
import app.restvolt.camperlog.domain.PaymentMethodRepository
import app.restvolt.camperlog.domain.sanitizePaymentMethods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hält die Zahlungsarten in SharedPreferences. Ohne gespeicherte Liste gelten [defaults] (Sprache der App beim ersten Zugriff);
 * erst die erste Änderung speichert die Liste, damit spätere Sprachwechsel die Vorgaben nicht überschreiben.
 */
class PreferencesPaymentMethodRepository(
    context: Context,
    private val defaults: () -> List<String> = { defaultPaymentMethods(context) },
) : PaymentMethodRepository {
    private val preferences = context.applicationContext.getSharedPreferences("payment_methods", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(load())

    override val methods: StateFlow<List<String>> = state.asStateFlow()

    override fun add(name: String): Boolean {
        val trimmed = name.trim()
        val current = state.value
        if (trimmed.isEmpty() || trimmed.length > MAX_PAYMENT_METHOD_LENGTH || current.size >= MAX_PAYMENT_METHODS) return false
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        save(current + trimmed)
        return true
    }

    override fun remove(name: String) {
        save(state.value.filterNot { it == name })
    }

    override fun replaceAll(names: List<String>) {
        save(sanitizePaymentMethods(names))
    }

    override fun resetToDefaults() {
        preferences.edit { remove(KEY_LIST) }
        state.value = load()
    }

    private fun save(list: List<String>) {
        // Newline trennt die Einträge; die Eingabefelder sind einzeilig, trotzdem hier absichern.
        val clean = list.map { it.replace('\n', ' ') }
        preferences.edit { putString(KEY_LIST, clean.joinToString("\n")) }
        state.value = clean
    }

    private fun load(): List<String> {
        val stored = preferences.getString(KEY_LIST, null) ?: return defaults()
        return if (stored.isEmpty()) emptyList() else stored.split('\n')
    }

    private companion object {
        const val KEY_LIST = "list"
    }
}

/** Die vier festen Vorgaben in der aktuellen App-Sprache. */
fun defaultPaymentMethods(context: Context): List<String> = listOf(
    context.getString(R.string.payment_method_credit_card),
    context.getString(R.string.payment_method_cash),
    context.getString(R.string.payment_method_debit_card),
    context.getString(R.string.payment_method_paypal),
)
