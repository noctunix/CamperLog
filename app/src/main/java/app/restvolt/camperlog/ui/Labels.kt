package app.restvolt.camperlog.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LogRecency
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.MAX_BATTERY_AH
import app.restvolt.camperlog.domain.MAX_DIMENSION_M
import app.restvolt.camperlog.domain.MAX_ODOMETER_KM
import app.restvolt.camperlog.domain.MAX_POWER_KW
import app.restvolt.camperlog.domain.MAX_SOLAR_WP
import app.restvolt.camperlog.domain.MAX_TANK_L
import app.restvolt.camperlog.domain.MAX_TIRE_PRESSURE_BAR
import app.restvolt.camperlog.domain.MAX_WEIGHT_KG
import androidx.annotation.DrawableRes
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Reminder
import app.restvolt.camperlog.domain.ReminderKind
import app.restvolt.camperlog.domain.RepairError
import app.restvolt.camperlog.domain.RepairField
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.StationError
import app.restvolt.camperlog.domain.StationField
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TourError
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleError
import app.restvolt.camperlog.domain.VehicleField
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.logRecency
import java.time.LocalDate
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

/** Anzeigename der Bordbuch-Art. */
@get:StringRes
val LogType.labelRes: Int
    get() = when (this) {
        LogType.CASSETTE_EMPTIED -> R.string.log_type_cassette_emptied
        LogType.GREY_WATER_EMPTIED -> R.string.log_type_grey_water_emptied
        LogType.DIESEL_HEATER_RUN -> R.string.log_type_diesel_heater_run
        LogType.GAS_HEATER_RUN -> R.string.log_type_gas_heater_run
    }

/** Anzeigetext der zeitlichen Einordnung, z. B. „Heute" oder „vor 3 Tagen". */
@Composable
fun LogRecency.text(): String = when (this) {
    LogRecency.Today -> stringResource(R.string.logbook_today)
    LogRecency.Yesterday -> stringResource(R.string.logbook_yesterday)
    is LogRecency.DaysAgo -> pluralStringResource(R.plurals.logbook_days_ago, days, days)
}

/** Datum eines Bordbuch-Eintrags als „Heute", sonst relativ mit Datum („Gestern · <Datum>", „vor x Tagen · <Datum>"). */
@Composable
fun logDateText(date: LocalDate, today: LocalDate, locale: Locale): String {
    val recency = logRecency(date, today)
    val recencyText = recency.text()
    return if (recency == LogRecency.Today) recencyText else "$recencyText · ${formatDate(date, locale)}"
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
        VehicleField.MEASURED_EMPTY_WEIGHT_KG -> R.string.field_measured_empty_weight
        VehicleField.BREAKDOWN_PHONE -> R.string.field_breakdown_phone
        VehicleField.TRAVEL_PROTECTION_PHONE -> R.string.field_travel_protection_phone
        VehicleField.INSURER_CLAIMS_PHONE -> R.string.field_insurer_claims_phone
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
        VehicleField.LAST_OIL_CHANGE_DATE -> R.string.field_last_oil_change
        VehicleField.LAST_OIL_CHANGE_ODOMETER_KM -> R.string.field_last_oil_change_odometer
    }

/** Höchstwert und Einheit eines Feldes mit Obergrenze, für die Fehlermeldung zu [VehicleError.TOO_LARGE]. */
private fun vehicleFieldBound(field: VehicleField): Pair<Double, String> = when (field) {
    VehicleField.LENGTH, VehicleField.WIDTH, VehicleField.HEIGHT -> MAX_DIMENSION_M to "m"
    VehicleField.GROSS_WEIGHT_KG, VehicleField.MEASURED_EMPTY_WEIGHT_KG -> MAX_WEIGHT_KG.toDouble() to "kg"
    VehicleField.POWER_KW -> MAX_POWER_KW.toDouble() to "kW"
    VehicleField.TIRE_PRESSURE_FRONT, VehicleField.TIRE_PRESSURE_REAR -> MAX_TIRE_PRESSURE_BAR to "bar"
    VehicleField.FUEL_TANK, VehicleField.AD_BLUE_TANK, VehicleField.FRESH_WATER_TANK,
    VehicleField.GREY_WATER_TANK, VehicleField.BOILER, VehicleField.CASSETTE,
    -> MAX_TANK_L to "l"
    VehicleField.BATTERY_CAPACITY_AH -> MAX_BATTERY_AH.toDouble() to "Ah"
    VehicleField.SOLAR_POWER_WP -> MAX_SOLAR_WP.toDouble() to "Wp"
    VehicleField.PURCHASE_ODOMETER_KM, VehicleField.LAST_OIL_CHANGE_ODOMETER_KM -> MAX_ODOMETER_KM.toDouble() to "km"
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
        VehicleError.DATE_IN_FUTURE -> when (field) {
            VehicleField.LAST_OIL_CHANGE_DATE -> stringResource(R.string.error_last_oil_change_future)
            else -> stringResource(R.string.error_first_registration_future)
        }
        VehicleError.SALE_BEFORE_PURCHASE -> stringResource(R.string.error_sale_before_purchase)
        VehicleError.NEGATIVE_NUMBER -> stringResource(R.string.error_negative_number)
        VehicleError.INVALID_NUMBER -> stringResource(R.string.error_invalid_number)
        VehicleError.INVALID_AMOUNT -> stringResource(R.string.error_invalid_amount)
        VehicleError.AMOUNT_TOO_LARGE -> stringResource(R.string.error_amount_too_large, "")
        VehicleError.INVALID_PHONE -> stringResource(R.string.error_invalid_phone)
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

/** Bezeichnung eines Formularfelds des Reparaturformulars, z. B. für die Fehlerzusammenfassung. */
@get:StringRes
val RepairField.labelRes: Int
    get() = when (this) {
        RepairField.DATE -> R.string.field_date
        RepairField.DESCRIPTION -> R.string.field_description
        RepairField.ODOMETER_KM -> R.string.field_odometer_km
        RepairField.COST -> R.string.field_cost
    }

/**
 * Fehlertext zu [this] am Feld [field] des Reparaturformulars. Gilt nicht für Beträge: Deren
 * Fehlertext braucht die Währung der Kostenzeile und wird dort gesondert gebildet.
 */
@Composable
fun RepairError.messageRes(field: RepairField): String = when (this) {
    RepairError.REQUIRED -> when (field) {
        RepairField.DATE -> stringResource(R.string.error_date_required)
        else -> stringResource(R.string.error_description_required)
    }
    RepairError.NEGATIVE_NUMBER -> stringResource(R.string.error_negative_number)
    RepairError.INVALID_NUMBER -> stringResource(R.string.error_invalid_number)
    RepairError.INVALID_AMOUNT -> stringResource(R.string.error_invalid_amount)
    RepairError.AMOUNT_TOO_LARGE -> stringResource(R.string.error_amount_too_large, "")
    RepairError.TOO_LARGE -> {
        val max = java.text.NumberFormat.getIntegerInstance(currentLocale()).format(MAX_ODOMETER_KM)
        stringResource(R.string.error_value_too_large, "$max km")
    }
}

/** Anzeigename der Erinnerungsart. */
@get:StringRes
val ReminderKind.labelRes: Int
    get() = when (this) {
        ReminderKind.INSPECTION -> R.string.reminder_kind_inspection
        ReminderKind.GAS_CHECK -> R.string.reminder_kind_gas_check
        ReminderKind.OIL_CHANGE -> R.string.reminder_kind_oil_change
    }

/** Anzeigetext einer Erinnerung, z. B. „HU (TÜV) fällig in 12 Tagen (1. Nov. 2026)". */
@Composable
fun Reminder.text(today: LocalDate, locale: Locale): String {
    val kindLabel = stringResource(kind.labelRes)
    val date = formatDate(dueDate, locale)
    return if (overdue) {
        stringResource(R.string.reminder_overdue, kindLabel, date)
    } else {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate).toInt()
        pluralStringResource(R.plurals.reminder_due_soon, days, kindLabel, days, date)
    }
}

/** Anzeigename der Stationsart (3.2); auch Fallback-Überschrift, wenn [app.restvolt.camperlog.domain.Station.name] leer ist. */
@get:StringRes
val StationType.labelRes: Int
    get() = when (this) {
        StationType.OVERNIGHT -> R.string.station_type_overnight
        StationType.SUPPLY -> R.string.station_type_supply
        StationType.FUEL -> R.string.station_type_fuel
        StationType.SIGHT -> R.string.station_type_sight
        StationType.FOOD -> R.string.station_type_food
        StationType.FERRY -> R.string.station_type_ferry
        StationType.OTHER -> R.string.station_type_other
    }

/** Symbol der Stationsart (3.2), amtliche Material-Symbols-Pfaddaten auf das 24-Einheiten-Raster skaliert. */
@get:DrawableRes
val StationType.iconRes: Int
    get() = when (this) {
        StationType.OVERNIGHT -> R.drawable.ic_bed
        StationType.SUPPLY -> R.drawable.ic_rv_hookup
        StationType.FUEL -> R.drawable.ic_local_gas_station
        StationType.SIGHT -> R.drawable.ic_photo_camera
        StationType.FOOD -> R.drawable.ic_restaurant
        StationType.FERRY -> R.drawable.ic_directions_boat
        StationType.OTHER -> R.drawable.ic_place
    }

/** Anzeigetext der vor Ort genutzten Versorgung. */
@get:StringRes
val StationService.labelRes: Int
    get() = when (this) {
        StationService.FRESH_WATER -> R.string.station_service_fresh_water
        StationService.GREY_WATER -> R.string.station_service_grey_water
        StationService.CASSETTE -> R.string.station_service_cassette
        StationService.GAS -> R.string.station_service_gas
        StationService.DIESEL -> R.string.station_service_diesel
        StationService.PETROL -> R.string.station_service_petrol
        StationService.ADBLUE -> R.string.station_service_adblue
        StationService.LPG -> R.string.station_service_lpg
        StationService.ELECTRICITY -> R.string.station_service_electricity
    }

/** Anzeigetext der Platzart einer Übernachtungsstation. */
@get:StringRes
val SiteKind.labelRes: Int
    get() = when (this) {
        SiteKind.CAMPSITE -> R.string.site_kind_campsite
        SiteKind.MOTORHOME_AREA -> R.string.site_kind_motorhome_area
        SiteKind.WILD -> R.string.site_kind_wild
    }

/** Bezeichnung des Formularfelds, z. B. für die Fehlerzusammenfassung. */
@get:StringRes
val StationField.labelRes: Int
    get() = when (this) {
        StationField.DATE -> R.string.field_date
        StationField.COORDINATES -> R.string.field_coordinates
        StationField.NIGHTS -> R.string.field_nights
        StationField.NAME -> R.string.field_name
        StationField.PLACE -> R.string.field_place
        StationField.NOTES -> R.string.field_notes
        StationField.MAP_LINK -> R.string.field_coordinates
    }

/** Fehlermeldung zu [this] am Feld [field]. */
@StringRes
fun StationError.messageRes(field: StationField): Int = when (this) {
    StationError.REQUIRED -> R.string.error_date_required
    StationError.INVALID_NUMBER -> R.string.error_invalid_number
    StationError.TOO_SMALL -> R.string.error_nights_too_small
    StationError.COORDINATES_INCOMPLETE -> R.string.error_coordinates_incomplete
    StationError.COORDINATES_OUT_OF_RANGE -> R.string.error_coordinates_out_of_range
    StationError.TOO_LONG -> R.string.error_text_too_long
    StationError.NOT_A_WEB_LINK -> R.string.error_not_a_web_link
}

/**
 * Sprechform von Koordinaten für TalkBack, z. B. „68,0912 Grad Nord, 13,1023 Grad Ost, Genauigkeit
 * 8 Meter" (7); [accuracyM] wird nur bei einer GPS-Herkunft mitgesprochen.
 */
@Composable
fun coordinatesContentDescription(latitude: Double, longitude: Double, accuracyM: Int?, locale: Locale): String {
    val latValue = app.restvolt.camperlog.domain.formatDegrees(kotlin.math.abs(latitude), locale)
    val lonValue = app.restvolt.camperlog.domain.formatDegrees(kotlin.math.abs(longitude), locale)
    val latDirection = stringResource(if (latitude < 0) R.string.direction_south else R.string.direction_north)
    val lonDirection = stringResource(if (longitude < 0) R.string.direction_west else R.string.direction_east)
    val base = stringResource(R.string.station_coordinates_description, latValue, latDirection, lonValue, lonDirection)
    return if (accuracyM != null) {
        "$base, ${pluralStringResource(R.plurals.station_coordinates_accuracy, accuracyM, accuracyM)}"
    } else {
        base
    }
}
