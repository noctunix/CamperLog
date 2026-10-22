package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.MAX_PAYMENT_METHODS
import app.restvolt.camperlog.domain.MAX_PAYMENT_METHOD_LENGTH
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Currency

/** Konfigurierbare Zahlungsarten im JSON-Teil einer Sicherung (ab Formatversion 25): Rundgang und Validierung. */
class BackupPaymentMethodsTest {

    private fun backup(paymentMethods: List<String>?) = Backup(
        exportedAt = Instant.parse("2026-10-22T12:00:00Z"),
        mainCurrency = Currency.getInstance("EUR"),
        rates = emptyList(),
        tours = emptyList(),
        paymentMethods = paymentMethods,
    )

    private fun success(text: String) = (decodeBackup(text) as BackupReadResult.Success).backup
    private fun failure(text: String) = decodeBackup(text) as? BackupReadResult.Failure

    @Test
    fun roundTrip_keepsTheListInOrder() {
        val decoded = success(encodeBackup(backup(listOf("Bar", "Kreditkarte", "Apple Pay"))))

        assertEquals(listOf("Bar", "Kreditkarte", "Apple Pay"), decoded.paymentMethods)
    }

    @Test
    fun roundTrip_keepsAnEmptyListDistinctFromAMissingOne() {
        assertEquals(emptyList<String>(), success(encodeBackup(backup(emptyList()))).paymentMethods)
        assertNull(success(encodeBackup(backup(null))).paymentMethods)
    }

    @Test
    fun missingField_decodesAsNull_forOlderBackups() {
        val withoutField = Json.parseToJsonElement(encodeBackup(backup(listOf("Bar")))).jsonObject.filterKeys { it != "paymentMethods" }

        assertNull(success(JsonObject(withoutField).toString()).paymentMethods)
    }

    @Test
    fun blankOrUntrimmedEntry_isRejected() {
        assertEquals(BackupError.INVALID_DATA, failure(encodeBackup(backup(listOf("Bar", " "))))?.error)
        assertEquals(BackupError.INVALID_DATA, failure(encodeBackup(backup(listOf(" Bar"))))?.error)
    }

    @Test
    fun duplicateEntryIgnoringCase_isRejected() {
        assertEquals(BackupError.INVALID_DATA, failure(encodeBackup(backup(listOf("Bar", "bar"))))?.error)
    }

    @Test
    fun tooLongEntryOrTooManyEntries_isRejected() {
        assertEquals(BackupError.INVALID_DATA, failure(encodeBackup(backup(listOf("x".repeat(MAX_PAYMENT_METHOD_LENGTH + 1)))))?.error)
        assertEquals(
            BackupError.INVALID_DATA,
            failure(encodeBackup(backup((1..MAX_PAYMENT_METHODS + 1).map { "Art $it" })))?.error,
        )
    }
}
