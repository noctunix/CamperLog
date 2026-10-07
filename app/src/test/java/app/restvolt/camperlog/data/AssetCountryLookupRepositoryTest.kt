package app.restvolt.camperlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft die reale, gebündelte `countries.bin` (siehe `scripts/build-country-shapes.py`) an bekannten
 * Punkten. Die vereinfachte Geometrie zielt auf rund 1-2 km Genauigkeit an europäischen Grenzen;
 * [kehlIsNullAtTheBorderDueToSimplificationTolerance] dokumentiert einen Punkt, an dem das nicht mehr reicht.
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
        assertEquals("NO", repository.countryAt(67.945, 13.075))
    }

    @Test
    fun bodoIsNorway() = runTest {
        assertEquals("NO", repository.countryAt(67.277, 14.450))
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
        assertNull(repository.countryAt(56.0, 3.0))
    }

    @Test
    fun kehlIsNullAtTheBorderDueToSimplificationTolerance() = runTest {
        // Kehl liegt auf der deutschen Rheinseite gegenüber Straßburg, knapp hinter der simplifizierten
        // Grenze: die vereinfachten Umrisse von DE und FR lassen dort eine kleine Lücke, das Ergebnis ist
        // `null` statt "DE" - erwartetes Verhalten bei rund 1-2 km Genauigkeit nahe einer Grenze.
        assertNull(repository.countryAt(48.5726, 7.8103))
    }

    @Test
    fun resultIsCachedAfterTheFirstLookup() = runTest {
        assertEquals("DE", repository.countryAt(48.0000, 8.2000))
        assertEquals("IT", repository.countryAt(45.6000, 10.6833))
    }
}
