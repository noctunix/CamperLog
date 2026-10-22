package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.MAX_PAYMENT_METHODS
import app.restvolt.camperlog.domain.MAX_PAYMENT_METHOD_LENGTH
import app.restvolt.camperlog.domain.PaymentMethodRepository
import app.restvolt.camperlog.domain.sanitizePaymentMethods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Zahlungsarten im Arbeitsspeicher für UI-Tests; Vorgaben wie in der App (deutsch). */
class FakePaymentMethodRepository(
    private val defaults: List<String> = listOf("Kreditkarte", "Bar", "EC-Karte", "PayPal"),
) : PaymentMethodRepository {
    private val state = MutableStateFlow(defaults)

    override val methods: StateFlow<List<String>> = state

    override fun add(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_PAYMENT_METHOD_LENGTH || state.value.size >= MAX_PAYMENT_METHODS) return false
        if (state.value.any { it.equals(trimmed, ignoreCase = true) }) return false
        state.value = state.value + trimmed
        return true
    }

    override fun remove(name: String) {
        state.value = state.value.filterNot { it == name }
    }

    override fun replaceAll(names: List<String>) {
        state.value = sanitizePaymentMethods(names)
    }

    override fun resetToDefaults() {
        state.value = defaults
    }
}
