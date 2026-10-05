package app.restvolt.camperlog.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.TourError
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import java.util.Locale

/** Aktuelle Sprache der App für Datums- und Zahlenformate; ein Sprachwechsel löst Neukomposition aus. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = app.restvolt.camperlog.domain.supportedLocale(LocalConfiguration.current.locales[0])

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

/** Bezeichnung des Formularfelds, z. B. für die Fehlerzusammenfassung. */
@get:StringRes
val TourField.labelRes: Int
    get() = when (this) {
        TourField.START_DATE -> R.string.field_start_date
        TourField.END_DATE -> R.string.field_end_date
        TourField.DESTINATION -> R.string.field_destination
        TourField.TRAVEL_DAYS -> R.string.field_travel_days
        TourField.OVERNIGHT_STAYS -> R.string.field_overnight_stays
        TourField.DISTANCE_KM -> R.string.field_distance
        TourField.COST -> R.string.field_cost
        TourField.MAP_LINK -> R.string.field_map_link
    }

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
    TourError.AMOUNT_TOO_LARGE -> R.string.error_amount_too_large
    TourError.MORE_NIGHTS_THAN_DAYS -> R.string.error_more_nights_than_days
    TourError.NOT_A_WEB_LINK -> R.string.error_not_a_web_link
}

/** Anzeigename eines Fahrzeugs; ein leerer Name wird als „Mein Wohnmobil" angezeigt. */
@Composable
fun vehicleDisplayName(vehicle: Vehicle?): String =
    vehicle?.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.vehicle_default_name)

/** Anzeigename für Auswahllisten; verkaufte Fahrzeuge erhalten einen Zusatz. */
@Composable
fun vehicleMenuLabel(vehicle: Vehicle): String {
    val name = vehicleDisplayName(vehicle)
    return if (vehicle.isSold) stringResource(R.string.vehicle_sold_label, name) else name
}
