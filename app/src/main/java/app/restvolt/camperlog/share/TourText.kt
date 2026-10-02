package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.formatAmounts
import app.restvolt.camperlog.domain.period
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.yesNoRes

/** Lesbare Zusammenfassung einer Tour zum Teilen per Messenger oder E-Mail, in der Sprache von [res]. */
fun tourShareText(res: Resources, tour: Tour): String = buildString {
    val locale = res.configuration.locales[0]
    appendLine(res.getString(R.string.share_subject, tour.destination))
    appendLine(res.getString(R.string.share_period, tour.period(), res.getString(tour.tourType.labelRes)))
    appendLine(
        res.getString(
            R.string.share_trip_stats,
            res.getQuantityString(R.plurals.share_travel_days, tour.travelDays, tour.travelDays),
            res.getQuantityString(R.plurals.share_overnight_stays, tour.overnightStays, tour.overnightStays),
            res.getString(R.string.distance_km, tour.distanceKm),
        ),
    )
    appendLine(res.getString(R.string.share_cost, formatAmounts(tour.costs, locale)))
    appendLine(res.getString(R.string.share_pitch_assigned, res.getString(yesNoRes(tour.pitchAssigned))))
    appendLine(res.getString(R.string.share_electricity, res.getString(tour.electricityFlatRate.labelRes)))
    appendLine(res.getString(R.string.share_lte, res.getString(tour.lteQuality.labelRes)))
    val blocks = if (tour.levelingBlocksUsed) R.string.share_blocks_used else R.string.share_blocks_not_used
    appendLine(res.getString(R.string.share_pitch, res.getString(tour.pitchSlope.labelRes), res.getString(blocks)))
    if (tour.notes.isNotBlank()) appendLine(res.getString(R.string.share_notes, tour.notes))
    tour.mapLink?.let { appendLine(res.getString(R.string.share_map, it)) }
}.trimEnd()
