package app.restvolt.camperlog.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.MAX_BATTERY_AH
import app.restvolt.camperlog.domain.MAX_DIMENSION_M
import app.restvolt.camperlog.domain.MAX_ODOMETER_KM
import app.restvolt.camperlog.domain.MAX_POWER_KW
import app.restvolt.camperlog.domain.MAX_SOLAR_WP
import app.restvolt.camperlog.domain.MAX_TANK_L
import app.restvolt.camperlog.domain.MAX_TIRE_PRESSURE_BAR
import app.restvolt.camperlog.domain.MAX_WEIGHT_KG
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.TourError
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleError
import app.restvolt.camperlog.domain.VehicleField
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

/** Bezeichnung eines Formularfelds des Fahrzeugformulars, z. B. für die Fehlerzusammenfassung. */
@get:StringRes
val VehicleField.labelRes: Int
    get() = when (this) {
        VehicleField.NAME -> R.string.field_name
        VehicleField.FIRST_REGISTRATION -> R.string.field_first_registration
        VehicleField.PURCHASE_PRICE -> R.string.field_purchase_price
        VehicleField.PURCHASE_ODOMETER_KM -> R.string.field_purchase_odometer
        VehicleField.SALE_DATE -> R.string.field_sale_date
        VehicleField.SALE_PRICE -> R.string.field_sale_price
        VehicleField.INSURANCE_PREMIUM_PER_YEAR -> R.string.field_insurance_premium
        VehicleField.VEHICLE_TAX_PER_YEAR -> R.string.field_vehicle_tax
        VehicleField.LENGTH -> R.string.field_length
        VehicleField.WIDTH -> R.string.field_width
        VehicleField.HEIGHT -> R.string.field_height
        VehicleField.GROSS_WEIGHT_KG -> R.string.field_gross_weight
        VehicleField.POWER_KW -> R.string.field_power
        VehicleField.TIRE_PRESSURE_FRONT -> R.string.field_tire_pressure_front
        VehicleField.TIRE_PRESSURE_REAR -> R.string.field_tire_pressure_rear
        VehicleField.FUEL_TANK -> R.string.field_fuel_tank
        VehicleField.AD_BLUE_TANK -> R.string.field_ad_blue_tank
        VehicleField.FRESH_WATER_TANK -> R.string.field_fresh_water_tank
        VehicleField.GREY_WATER_TANK -> R.string.field_grey_water_tank
        VehicleField.BOILER -> R.string.field_boiler
        VehicleField.CASSETTE -> R.string.field_cassette
        VehicleField.BATTERY_CAPACITY_AH -> R.string.field_battery_capacity
        VehicleField.SOLAR_POWER_WP -> R.string.field_solar_power
    }

/** Höchstwert und Einheit eines Feldes mit Obergrenze, für die Fehlermeldung zu [VehicleError.TOO_LARGE]. */
private fun vehicleFieldBound(field: VehicleField): Pair<Double, String> = when (field) {
    VehicleField.LENGTH, VehicleField.WIDTH, VehicleField.HEIGHT -> MAX_DIMENSION_M to "m"
    VehicleField.GROSS_WEIGHT_KG -> MAX_WEIGHT_KG.toDouble() to "kg"
    VehicleField.POWER_KW -> MAX_POWER_KW.toDouble() to "kW"
    VehicleField.TIRE_PRESSURE_FRONT, VehicleField.TIRE_PRESSURE_REAR -> MAX_TIRE_PRESSURE_BAR to "bar"
    VehicleField.FUEL_TANK, VehicleField.AD_BLUE_TANK, VehicleField.FRESH_WATER_TANK,
    VehicleField.GREY_WATER_TANK, VehicleField.BOILER, VehicleField.CASSETTE,
    -> MAX_TANK_L to "l"
    VehicleField.BATTERY_CAPACITY_AH -> MAX_BATTERY_AH.toDouble() to "Ah"
    VehicleField.SOLAR_POWER_WP -> MAX_SOLAR_WP.toDouble() to "Wp"
    VehicleField.PURCHASE_ODOMETER_KM -> MAX_ODOMETER_KM.toDouble() to "km"
    else -> 0.0 to ""
}

/**
 * Fehlertext zu [this] am Feld [field] des Fahrzeugformulars. Gilt nicht für Beträge: Deren
 * Fehlertext braucht die Währung der jeweiligen Zeile und wird dort gesondert gebildet.
 */
@Composable
fun VehicleError.messageRes(field: VehicleField): String {
    val locale = currentLocale()
    return when (this) {
        VehicleError.REQUIRED -> stringResource(R.string.error_vehicle_name_required)
        VehicleError.DATE_IN_FUTURE -> stringResource(R.string.error_first_registration_future)
        VehicleError.SALE_BEFORE_PURCHASE -> stringResource(R.string.error_sale_before_purchase)
        VehicleError.NEGATIVE_NUMBER -> stringResource(R.string.error_negative_number)
        VehicleError.INVALID_NUMBER -> stringResource(R.string.error_invalid_number)
        VehicleError.INVALID_AMOUNT -> stringResource(R.string.error_invalid_amount)
        VehicleError.AMOUNT_TOO_LARGE -> stringResource(R.string.error_amount_too_large, "")
        VehicleError.NOT_POSITIVE -> stringResource(R.string.error_must_be_positive, vehicleFieldBound(field).second)
        VehicleError.TOO_LARGE -> {
            val (max, unit) = vehicleFieldBound(field)
            val number = if (max == max.toLong().toDouble()) {
                java.text.NumberFormat.getIntegerInstance(locale).format(max.toLong())
            } else {
                java.text.NumberFormat.getNumberInstance(locale).format(max)
            }
            stringResource(R.string.error_value_too_large, "$number $unit")
        }
    }
}
