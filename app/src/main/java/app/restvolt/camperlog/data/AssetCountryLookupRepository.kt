package app.restvolt.camperlog.data

import android.content.Context
import app.restvolt.camperlog.domain.CountryLookupRepository
import app.restvolt.camperlog.domain.CountryShape
import app.restvolt.camperlog.domain.countryAt
import app.restvolt.camperlog.domain.decodeCountryShapes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val ASSET_NAME = "countries.bin"

/**
 * [CountryLookupRepository] über die gebündelte Datei `assets/countries.bin`. Die Geometrie wird
 * erst beim ersten Aufruf von [countryAt] geladen, auf [dispatcher] (abseits des Hauptthreads),
 * und danach für die Lebensdauer dieser Instanz zwischengehalten.
 */
class AssetCountryLookupRepository(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CountryLookupRepository {

    private val mutex = Mutex()

    @Volatile
    private var shapes: List<CountryShape>? = null

    override suspend fun countryAt(latitude: Double, longitude: Double): String? {
        val loaded = shapes ?: mutex.withLock { shapes ?: loadShapes().also { shapes = it } }
        return countryAt(latitude, longitude, loaded)
    }

    private suspend fun loadShapes(): List<CountryShape> = withContext(dispatcher) {
        context.assets.open(ASSET_NAME).use { decodeCountryShapes(it.readBytes()) }
    }
}
