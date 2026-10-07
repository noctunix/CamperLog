package app.restvolt.camperlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft die reale, gebündelte `countries.bin` (siehe `scripts/build-country-shapes.py`) an bekannten
 * Punkten, darunter Küstenorte direkt am Wasser und Punkte auf See, die [countryAt]s
 * Grenznähe-Toleranz abdecken soll (siehe [app.restvolt.camperlog.domain.countryAt]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssetCountryLookupRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = AssetCountryLookupRepository(context)

    @Test
    fun lofotenIsNorway() = runTest {
        assertEquals("NO", repository.countryAt(68.2343, 14.5631))
    }

    @Test
    fun reineIsNorway() = runTest {
        // Reine, direkt am Wasser; die vereinfachte Küstenlinie lässt den Ort knapp außerhalb des Polygons.
        assertEquals("NO", repository.countryAt(67.9327, 13.0893))
    }

    @Test
    fun bodoIsNorway() = runTest {
        // Bodø, ebenfalls direkt am Wasser, siehe reineIsNorway.
        assertEquals("NO", repository.countryAt(67.2804, 14.4049))
    }

    @Test
    fun lakeGardaIsItaly() = runTest {
        assertEquals("IT", repository.countryAt(45.6000, 10.6833))
    }

    @Test
    fun blackForestIsGermany() = runTest {
        assertEquals("DE", repository.countryAt(48.0000, 8.2000))
    }

    @Test
    fun engadinIsSwitzerland() = runTest {
        assertEquals("CH", repository.countryAt(46.4908, 9.8355))
    }

    @Test
    fun moselleAtTrierIsGermany() = runTest {
        assertEquals("DE", repository.countryAt(49.7596, 6.6441))
    }

    @Test
    fun strasbourgIsFrance() = runTest {
        assertEquals("FR", repository.countryAt(48.5734, 7.7521))
    }

    @Test
    fun esbjergIsDenmark() = runTest {
        assertEquals("DK", repository.countryAt(55.4761, 8.4594))
    }

    @Test
    fun seaPointIsNull() = runTest {
        // Nordsee, weit von jeder Küste entfernt.
        assertNull(repository.countryAt(56.0, 3.0))
    }

    @Test
    fun pointNearNorwegianCoastIsNorway() = runTest {
        // Rund 3 km westlich von Reine auf See, innerhalb der Grenznähe-Toleranz.
        assertEquals("NO", repository.countryAt(67.9327, 12.8263))
    }

    @Test
    fun pointFarOffNorwegianCoastIsNull() = runTest {
        // Rund 20 km westlich von Reine auf See, außerhalb der Grenznähe-Toleranz.
        assertNull(repository.countryAt(67.9327, 12.3893))
    }

    @Test
    fun kehlIsGermany() = runTest {
        // Kehl liegt auf der deutschen Rheinseite gegenüber Straßburg, direkt an der Grenze.
        assertEquals("DE", repository.countryAt(48.5726, 7.8156))
    }

    @Test
    fun resultIsCachedAfterTheFirstLookup() = runTest {
        assertEquals("DE", repository.countryAt(48.0000, 8.2000))
        assertEquals("IT", repository.countryAt(45.6000, 10.6833))
    }

    @Test
    fun oneHundredLookupsCompleteWellUnderASecond() = runTest {
        repository.countryAt(48.0000, 8.2000) // loads and caches the shapes once, as in normal use
        val elapsedMs = measureTimeMillis {
            repeat(100) { i ->
                repository.countryAt(45.0 + i * 0.1, 8.0 + i * 0.05)
            }
        }
        assertTrue("100 lookups took ${elapsedMs}ms", elapsedMs < 1000)
    }
}
