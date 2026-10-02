package de.hannes.camperlog.ui

import androidx.annotation.StringRes
import de.hannes.camperlog.R
import de.hannes.camperlog.domain.ElectricityFlatRate
import de.hannes.camperlog.domain.LteQuality
import de.hannes.camperlog.domain.PitchSlope
import de.hannes.camperlog.domain.TourError
import de.hannes.camperlog.domain.TourField
import de.hannes.camperlog.domain.TourType

/** Anzeigetext der Tourart. */
@get:StringRes
val TourType.labelRes: Int
    get() = when (this) {
        TourType.DAY_TRIP -> R.string.tour_type_day_trip
        TourType.WEEKEND -> R.string.tour_type_weekend
        TourType.VACATION -> R.string.tour_type_vacation
    }

/** Anzeigetext der Strompauschale. */
@get:StringRes
val ElectricityFlatRate.labelRes: Int
    get() = when (this) {
        ElectricityFlatRate.YES -> R.string.electricity_yes
        ElectricityFlatRate.NO -> R.string.electricity_no
        ElectricityFlatRate.NOT_USED -> R.string.electricity_not_used
    }

/** Anzeigetext der Netzqualität. */
@get:StringRes
val LteQuality.labelRes: Int
    get() = when (this) {
        LteQuality.GOOD -> R.string.lte_good
        LteQuality.OK -> R.string.lte_ok
        LteQuality.BAD -> R.string.lte_bad
    }

/** Anzeigetext der Platzneigung. */
@get:StringRes
val PitchSlope.labelRes: Int
    get() = when (this) {
        PitchSlope.LEVEL -> R.string.pitch_level
        PitchSlope.SLOPED -> R.string.pitch_sloped
    }

/** Ja/Nein als Anzeigetext. */
@StringRes
fun yesNoRes(value: Boolean): Int = if (value) R.string.yes else R.string.no

/** Fehlermeldung zu [this] am Feld [field]; Pflichtfeld-Fehler nennen das Feld. */
@StringRes
fun TourError.messageRes(field: TourField): Int = when (this) {
    TourError.REQUIRED -> when (field) {
        TourField.START_DATE -> R.string.error_start_date_required
        TourField.END_DATE -> R.string.error_end_date_required
        else -> R.string.error_destination_required
    }
    TourError.END_BEFORE_START -> R.string.error_end_before_start
    TourError.NEGATIVE_NUMBER -> R.string.error_negative_number
    TourError.INVALID_NUMBER -> R.string.error_invalid_number
    TourError.INVALID_AMOUNT -> R.string.error_invalid_amount
    TourError.MORE_NIGHTS_THAN_DAYS -> R.string.error_more_nights_than_days
    TourError.NOT_A_WEB_LINK -> R.string.error_not_a_web_link
}
