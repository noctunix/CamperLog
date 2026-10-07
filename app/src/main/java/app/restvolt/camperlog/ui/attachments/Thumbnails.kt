package app.restvolt.camperlog.ui.attachments

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.restvolt.camperlog.data.calculateInSampleSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Lange Kante eines decodierten Vorschaubilds. */
internal const val THUMBNAIL_MAX_DIMENSION = 256

/** Prozessweiter Vorschaubild-Zwischenspeicher, Schlüssel ist der absolute Dateipfad. */
private val thumbnailCache = LruCache<String, ImageBitmap>(64)

/**
 * Liefert das Vorschaubild von [file], heruntergerechnet auf [maxDimension]; decodiert abseits des
 * Haupt-Thread und merkt sich das Ergebnis in [thumbnailCache], da Dateinamen (UUIDs) wiederverwendet
 * werden, sobald eine Anhangsdatei einmal geschrieben ist. `null`, wenn die Datei (noch) nicht lesbar ist.
 */
@Composable
fun rememberThumbnail(file: File, maxDimension: Int = THUMBNAIL_MAX_DIMENSION): State<ImageBitmap?> {
    val key = "${file.absolutePath}@$maxDimension"
    val cached = thumbnailCache.get(key)
    return produceState(initialValue = cached, key) {
        if (cached != null) return@produceState
        value = withContext(Dispatchers.IO) {
            decodeThumbnail(file, maxDimension)?.also { thumbnailCache.put(key, it) }
        }
    }
}

private fun decodeThumbnail(file: File, maxDimension: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}.getOrNull()
