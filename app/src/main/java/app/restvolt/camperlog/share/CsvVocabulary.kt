package app.restvolt.camperlog.share

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.supportedLocale
import java.util.Locale

/**
 * Sprache der CSV-Exporte: Deutsch exportiert das bisherige Vokabular (Kopfzeilen, Werte wie
 * `ja`/`nein`) unverändert, jede andere App-Sprache exportiert englische Kopfzeilen und Werte.
 * [fromLocale] wählt anhand der von [supportedLocale] ermittelten App-Sprache.
 */
enum class CsvVocabulary(val yes: String, val no: String) {
    GERMAN("ja", "nein"),
    ENGLISH("yes", "no"),
    ;

    companion object {
        fun fromLocale(locale: Locale): CsvVocabulary = if (supportedLocale(locale).language == "de") GERMAN else ENGLISH
    }
}

/** Exportwert von [TourType] in [vocabulary]; Deutsch bleibt [TourType.csvValue]. */
fun TourType.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        TourType.DAY_TRIP -> "day_trip"
        TourType.WEEKEND -> "weekend"
        TourType.VACATION -> "vacation"
    }
}

/** Exportwert von [ElectricityFlatRate] in [vocabulary]; Deutsch bleibt [ElectricityFlatRate.csvValue]. */
fun ElectricityFlatRate.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        ElectricityFlatRate.YES -> "yes"
        ElectricityFlatRate.NO -> "no"
        ElectricityFlatRate.NOT_USED -> "not_used"
    }
}

/** Exportwert von [LteQuality] in [vocabulary]; Deutsch bleibt [LteQuality.csvValue]. */
fun LteQuality.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        LteQuality.GOOD -> "good"
        LteQuality.OK -> "ok"
        LteQuality.BAD -> "bad"
    }
}

/** Exportwert von [PitchSlope] in [vocabulary]; Deutsch bleibt [PitchSlope.csvValue]. */
fun PitchSlope.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        PitchSlope.LEVEL -> "level"
        PitchSlope.SLOPED -> "sloped"
    }
}

/** Exportwert von [ElectricityBilling] in [vocabulary]; Deutsch bleibt [ElectricityBilling.csvValue]. */
fun ElectricityBilling.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        ElectricityBilling.NONE -> "not_used"
        ElectricityBilling.INCLUDED -> "included"
        ElectricityBilling.FLAT_PER_NIGHT -> "flat_per_night"
        ElectricityBilling.FLAT_PER_STAY -> "flat_per_stay"
        ElectricityBilling.METERED -> "metered"
        ElectricityBilling.BASE_PLUS_METERED -> "base_plus_metered"
        ElectricityBilling.COIN -> "coins"
    }
}

/** Exportwert von [StationType] in [vocabulary]; Deutsch bleibt [StationType.csvValue]. */
fun StationType.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        StationType.OVERNIGHT -> "overnight"
        StationType.SUPPLY -> "supply_disposal"
        StationType.FUEL -> "fuel_charging"
        StationType.TOLL -> "toll"
        StationType.SIGHT -> "sight"
        StationType.FOOD -> "food"
        StationType.FERRY -> "ferry"
        StationType.OTHER -> "other"
    }
}

/** Exportwert von [SiteKind] in [vocabulary]; Deutsch bleibt [SiteKind.csvValue]. */
fun SiteKind.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        SiteKind.CAMPSITE -> "campsite"
        SiteKind.MOTORHOME_AREA -> "motorhome_area"
        SiteKind.WILD -> "wild_camping"
    }
}

/** Exportwert von [CoordinateSource] in [vocabulary]; Deutsch bleibt [CoordinateSource.csvValue]. */
fun CoordinateSource.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        CoordinateSource.GPS -> "gps"
        CoordinateSource.ENTERED -> "entered"
    }
}

/** Exportwert von [StationService] in [vocabulary]; Deutsch bleibt [StationService.csvValue]. */
fun StationService.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        StationService.FRESH_WATER -> "fresh_water"
        StationService.GREY_WATER -> "grey_water"
        StationService.CASSETTE -> "cassette_toilet"
        StationService.GAS -> "gas"
        StationService.DIESEL -> "diesel"
        StationService.PETROL -> "petrol"
        StationService.ADBLUE -> "adblue"
        StationService.LPG -> "lpg"
        StationService.ELECTRICITY -> "electricity"
    }
}

/** Exportwert von [CostCategory] in [vocabulary]; Deutsch bleibt [CostCategory.csvValue]. */
fun CostCategory.csvValue(vocabulary: CsvVocabulary): String = if (vocabulary == CsvVocabulary.GERMAN) {
    csvValue
} else {
    when (this) {
        CostCategory.PITCH -> "pitch"
        CostCategory.ELECTRICITY -> "electricity"
        CostCategory.SUPPLY -> "supply_disposal"
        CostCategory.FUEL -> "fuel_charging"
        CostCategory.TOLL -> "toll"
        CostCategory.FERRY -> "ferry"
        CostCategory.FOOD -> "food"
        CostCategory.OTHER -> "other"
    }
}
