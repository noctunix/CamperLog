package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.StateFlow

/** Höchstzahl der Zahlungsarten in der konfigurierbaren Liste (auch Obergrenze beim Backup-Import). */
const val MAX_PAYMENT_METHODS = 30

/**
 * Konfigurierbare Liste der Zahlungsarten, aus der das Stationsformular Vorschläge macht. Die Zahlungsart
 * einer Station bleibt Freitext ([Station.paymentMethod]) und ist auch ohne Eintrag in dieser Liste gültig.
 */
interface PaymentMethodRepository {
    /** Aktuelle Liste in Anzeigereihenfolge; solange der Nutzer sie nie geändert hat, die lokalisierten Vorgaben. */
    val methods: StateFlow<List<String>>

    /** Hängt [name] an. `false`, wenn er leer, zu lang, schon enthalten (ohne Beachtung der Großschreibung) oder die Liste voll ist. */
    fun add(name: String): Boolean

    /** Entfernt [name] aus der Liste; bereits an Stationen gespeicherte Zahlungsarten bleiben unberührt. */
    fun remove(name: String)

    /** Ersetzt die Liste vollständig (Backup-Import); bereinigt Leerzeichen, Duplikate und die Obergrenze. */
    fun replaceAll(names: List<String>)

    /** Verwirft die eigene Liste und kehrt zu den lokalisierten Vorgaben zurück. */
    fun resetToDefaults()
}

/** Bereinigt eine Liste von Zahlungsarten: getrimmt, ohne Leere und Duplikate (ohne Beachtung der Großschreibung), höchstens [MAX_PAYMENT_METHODS]. */
fun sanitizePaymentMethods(names: List<String>): List<String> =
    names.map { it.trim() }
        .filter { it.isNotEmpty() && it.length <= MAX_PAYMENT_METHOD_LENGTH }
        .distinctBy { it.lowercase() }
        .take(MAX_PAYMENT_METHODS)
