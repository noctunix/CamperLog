package app.restvolt.camperlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.MAX_PAYMENT_METHODS
import app.restvolt.camperlog.domain.MAX_PAYMENT_METHOD_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class PreferencesPaymentMethodRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val defaults = listOf("Kreditkarte", "Bar", "EC-Karte", "PayPal")

    @Before
    fun setUp() {
        context.getSharedPreferences("payment_methods", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun repository() = PreferencesPaymentMethodRepository(context)

    @Test
    fun withoutSavedList_theLocalizedDefaultsApply() {
        assertEquals(defaults, repository().methods.value)
    }

    @Test
    fun add_appendsTrimmedNameAndPersists() {
        val repository = repository()

        assertTrue(repository.add("  Apple Pay "))

        assertEquals(defaults + "Apple Pay", repository.methods.value)
        assertEquals(defaults + "Apple Pay", repository().methods.value)
    }

    @Test
    fun add_rejectsBlankDuplicateAndOverlongNames() {
        val repository = repository()

        assertFalse(repository.add("   "))
        assertFalse(repository.add("paypal"))
        assertFalse(repository.add("x".repeat(MAX_PAYMENT_METHOD_LENGTH + 1)))
        assertEquals(defaults, repository.methods.value)
    }

    @Test
    fun add_rejectsNamesBeyondTheLimit() {
        val repository = repository()
        repository.replaceAll((1..MAX_PAYMENT_METHODS).map { "Art $it" })

        assertFalse(repository.add("Eine zu viel"))
        assertEquals(MAX_PAYMENT_METHODS, repository.methods.value.size)
    }

    @Test
    fun remove_persistsAnEmptyListWithoutFallingBackToDefaults() {
        val repository = repository()

        defaults.forEach(repository::remove)

        assertEquals(emptyList<String>(), repository.methods.value)
        assertEquals(emptyList<String>(), repository().methods.value)
    }

    @Test
    fun resetToDefaults_dropsTheCustomList() {
        val repository = repository()
        repository.add("Apple Pay")

        repository.resetToDefaults()

        assertEquals(defaults, repository.methods.value)
        assertEquals(defaults, repository().methods.value)
    }

    @Test
    fun replaceAll_sanitizesTheList() {
        val repository = repository()

        repository.replaceAll(listOf(" Bar ", "", "bar", "Karte"))

        assertEquals(listOf("Bar", "Karte"), repository.methods.value)
    }
}
