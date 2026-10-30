package app.restvolt.camperlog.ui.tools

import app.restvolt.camperlog.domain.MAX_LOAD_POSITION_CM
import app.restvolt.camperlog.domain.MIN_LOAD_POSITION_CM
import kotlin.math.roundToInt

/** Anteil des Radstands, der als vorderer Überhang gilt, wenn Länge und Überhang nicht erfasst sind. */
private const val FALLBACK_FRONT_OVERHANG_FACTOR = 0.2

/** Anteil des Radstands bis zum Heckende, wenn Länge und Überhang nicht erfasst sind. */
private const val FALLBACK_REAR_END_FACTOR = 1.55

/** Rasterweite für Positionen in der Seitenansicht in cm. */
private const val LOAD_SIDE_VIEW_SNAP_CM = 5

/** Gezeichneter Längsbereich der Seitenansicht in cm, gemessen von der Vorderachse. */
data class LoadSideViewExtent(val startCm: Int, val endCm: Int)

/**
 * Bestimmt den gezeichneten Längsbereich der Seitenansicht: die echte Ausdehnung, wenn [lengthCm]
 * und [frontOverhangCm] bekannt sind, sonst eine proportionale Schätzung anhand [wheelbaseCm].
 *
 * @param wheelbaseCm Radstand in cm, muss größer als 0 sein
 * @param lengthCm Fahrzeuglänge in cm, oder `null`, wenn nicht erfasst
 * @param frontOverhangCm vorderer Überhang in cm, oder `null`, wenn nicht erfasst
 * @return der Längsbereich von der Vorderachse aus
 */
fun loadSideViewExtent(wheelbaseCm: Int, lengthCm: Int?, frontOverhangCm: Int?): LoadSideViewExtent =
    if (lengthCm != null && frontOverhangCm != null) {
        LoadSideViewExtent(startCm = -frontOverhangCm, endCm = lengthCm - frontOverhangCm)
    } else {
        LoadSideViewExtent(
            startCm = -(wheelbaseCm * FALLBACK_FRONT_OVERHANG_FACTOR).roundToInt(),
            endCm = (wheelbaseCm * FALLBACK_REAR_END_FACTOR).roundToInt(),
        )
    }

/**
 * Rechnet zwischen cm (Abstand von der Vorderachse) und px innerhalb von [extent] um, für die
 * Darstellung und das Ziehen von Markierungen in der Seitenansicht.
 *
 * @param extent gezeichneter Längsbereich, siehe [loadSideViewExtent]
 * @param widthPx Breite der Zeichenfläche in px, muss größer als 0 sein
 */
class LoadSideViewScale(private val extent: LoadSideViewExtent, private val widthPx: Float) {
    private val spanCm = (extent.endCm - extent.startCm).coerceAtLeast(1)
    private val pxPerCm = widthPx / spanCm

    /** Wandelt [cm] in eine x-Koordinate in px um, geklemmt auf [extent]. */
    fun cmToPx(cm: Int): Float {
        val clamped = cm.coerceIn(extent.startCm, extent.endCm)
        return (clamped - extent.startCm) * pxPerCm
    }

    /**
     * Wandelt eine x-Koordinate [px] in cm um: auf 5 cm gerastert und sowohl auf [extent] als auch
     * auf den gültigen Positionsbereich geklemmt.
     */
    fun pxToCm(px: Float): Int {
        val rawCm = extent.startCm + px / pxPerCm
        val snapped = (rawCm / LOAD_SIDE_VIEW_SNAP_CM).roundToInt() * LOAD_SIDE_VIEW_SNAP_CM
        return snapped
            .coerceIn(extent.startCm, extent.endCm)
            .coerceIn(MIN_LOAD_POSITION_CM, MAX_LOAD_POSITION_CM)
    }
}
