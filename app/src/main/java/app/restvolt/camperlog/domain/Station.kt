package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import java.util.Locale

/** Art einer Station; die Reihenfolge ist die Reihenfolge in der Auswahl. Mit stabilem Exportwert [csvValue]. */
enum class StationType(val csvValue: String) {
    OVERNIGHT("schlafplatz"),
    SUPPLY("ver_entsorgung"),
    FUEL("tanken_laden"),
    TOLL("maut"),
    SIGHT("sehenswertes"),
    FOOD("essen"),
    FERRY("faehre"),
    OTHER("sonstiges"),
}

/** Art einer Maut-Station, mit stabilem Exportwert [csvValue]. Abschnitt/Name kommen über den Stationsnamen. */
enum class TollKind(val csvValue: String) {
    MOTORWAY("autobahn"),
    TUNNEL("tunnel"),
    BRIDGE("bruecke"),
    VIGNETTE("vignette"),
    OTHER("sonstiges"),
}

/** Gültige ISO-3166-1-alpha-2-Ländercodes für die Vignetten-Länderauswahl, aus der Java-Locale-Liste. */
val ALL_COUNTRY_CODES: Set<String> by lazy { Locale.getISOCountries().toSet() }

/**
 * Vor Ort genutzte Versorgung. Die "Ver-/Entsorgung"-Gruppe ist auf [StationType.OVERNIGHT],
 * [StationType.SUPPLY] und [StationType.FUEL] erlaubt, die "Tanken"-Gruppe nur auf [StationType.FUEL]
 * (siehe [StationType.allowedServices]). Mit stabilem Exportwert [csvValue].
 */
enum class StationService(val csvValue: String) {
    FRESH_WATER("frischwasser"),
    GREY_WATER("grauwasser"),
    CASSETTE("kassette"),
    GAS("gas"),
    DIESEL("diesel"),
    PETROL("benzin"),
    ADBLUE("adblue"),
    LPG("fluessiggas"),
    ELECTRICITY("strom"),
}

/** Die "Ver-/Entsorgung"-Gruppe, auch für die gleichnamige UI-Sektion. */
val SUPPLY_SERVICES: Set<StationService> = setOf(StationService.FRESH_WATER, StationService.GREY_WATER, StationService.CASSETTE, StationService.GAS)

/** Die "Tanken"-Gruppe, auch für die UI-Sektion "Getankt". */
val FUEL_SERVICES: Set<StationService> = setOf(StationService.DIESEL, StationService.PETROL, StationService.ADBLUE, StationService.LPG, StationService.ELECTRICITY)

/** An diesem [StationType] erlaubte [StationService]-Werte; leer, wenn der Typ keine Versorgung kennt. */
val StationType.allowedServices: Set<StationService>
    get() = when (this) {
        StationType.OVERNIGHT, StationType.SUPPLY -> SUPPLY_SERVICES
        StationType.FUEL -> SUPPLY_SERVICES + FUEL_SERVICES
        StationType.TOLL, StationType.SIGHT, StationType.FOOD, StationType.FERRY, StationType.OTHER -> emptySet()
    }

/** Art des Platzes einer Übernachtungs-Station, mit stabilem Exportwert [csvValue]. */
enum class SiteKind(val csvValue: String) {
    CAMPSITE("campingplatz"),
    MOTORHOME_AREA("stellplatz"),
    WILD("frei_stehen"),
}

/** Herkunft gespeicherter Koordinaten, mit stabilem Exportwert [csvValue]. */
enum class CoordinateSource(val csvValue: String) {
    GPS("gps"),
    ENTERED("erfasst"),
}

/**
 * Einmalige Wetterabfrage zu einer Station (Open-Meteo); [observedAt] ist der einzige Zeitpunktwert,
 * [temperatureDeciC] die Temperatur in Zehntelgrad (14,3 °C -> 143).
 */
@Serializable
data class WeatherSnapshot(
    val temperatureDeciC: Int,
    val weatherCode: Int,
    val windKmh: Int,
    val gustKmh: Int? = null,
    val windDirectionDeg: Int? = null,
    @Serializable(with = InstantSerializer::class) val observedAt: Instant,
)

/**
 * Eine Station einer Tour oder eine eigenständige Station ohne Tour ([tourId] `null`).
 * [date] und [time] sind Wanduhrzeiten am Ort; nur [WeatherSnapshot.observedAt] ist ein [Instant].
 * Die typspezifischen Felder ([nights] bis [levelingBlocksUsed]) sind nur bei [StationType.OVERNIGHT]
 * gesetzt, `null` bedeutet dort "nicht angegeben". [favorite] markiert "gerne wieder".
 *
 * Die Stromabrechnung ([electricityBilling] bis [electricityKwhUsed]) gilt nur bei [StationType.OVERNIGHT]:
 * [electricityBilling] `null` bedeutet "nicht angegeben", alle Beträge teilen sich [electricityCurrency];
 * kWh kommen aus [electricityMeterStart]/[electricityMeterEnd] oder direkt aus [electricityKwhUsed]
 * (siehe [electricityCost] und [electricityKwh]). Die Maut-Felder ([tollKind] bis [tollValidUntil])
 * gelten nur bei [StationType.TOLL], [ferryBookingReference] nur bei [StationType.FERRY]. [costs] sind
 * manuell erfasste Kostenposten unabhängig vom Stationstyp (siehe [CostCategory]); die Stromkosten
 * zählen nicht doppelt dazu, siehe [effectiveCosts].
 */
data class Station(
    val id: Long = 0,
    val uuid: String = "",
    val vehicleId: Long,
    val tourId: Long? = null,
    val type: StationType,
    val date: LocalDate,
    val time: LocalTime? = null,
    val name: String = "",
    val place: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateSource: CoordinateSource? = null,
    val accuracyM: Int? = null,
    val mapLink: String? = null,
    val notes: String = "",
    val nights: Int? = null,
    val siteKind: SiteKind? = null,
    val pitchAssigned: Boolean? = null,
    val lteQuality: LteQuality? = null,
    val pitchSlope: PitchSlope? = null,
    val levelingBlocksUsed: Boolean? = null,
    val electricityBilling: ElectricityBilling? = null,
    val electricityCurrency: Currency? = null,
    val electricityFlatAmount: Money? = null,
    val electricityBaseFee: Money? = null,
    val electricityPricePerKwh: BigDecimal? = null,
    val electricityCoinPrice: Money? = null,
    val electricityCoinsUsed: Int? = null,
    val electricityKwhPerCoin: BigDecimal? = null,
    val electricityMeterStart: BigDecimal? = null,
    val electricityMeterEnd: BigDecimal? = null,
    val electricityKwhUsed: BigDecimal? = null,
    val tollKind: TollKind? = null,
    val tollPaymentMethod: String = "",
    val tollCountry: String? = null,
    val tollValidFrom: LocalDate? = null,
    val tollValidUntil: LocalDate? = null,
    val ferryBookingReference: String = "",
    val costs: List<StationCost> = emptyList(),
    val services: Set<StationService> = emptySet(),
    val weather: WeatherSnapshot? = null,
    val favorite: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Zugriff auf alle gespeicherten Stationen. */
interface StationRepository {

    /** Liefert die Stationen einer Tour, aufsteigend nach `(date, time NULLS LAST, createdAt)`. */
    fun observeForTour(tourId: Long): Flow<List<Station>>

    /**
     * Liefert Stationen neuester zuerst; [vehicleId] `null` liefert die aller Fahrzeuge
     * ("Alle Fahrzeuge"), sonst nur die von [vehicleId].
     */
    fun observeForVehicle(vehicleId: Long?): Flow<List<Station>>

    /** Liefert die Station mit [id] oder `null`, falls sie nicht (mehr) existiert. */
    fun observeStation(id: Long): Flow<Station?>

    /** Liefert alle Stationen für den Sicherungs-Export, ohne festgelegte Reihenfolge. */
    suspend fun allStations(): List<Station>

    /**
     * Legt [station] an, wenn ihre id 0 ist, sonst wird sie aktualisiert.
     * Zeitstempel werden dabei vom Repository gesetzt.
     *
     * @return die id der gespeicherten Station
     */
    suspend fun save(station: Station): Long

    /** Löscht die Station mit [id]. */
    suspend fun delete(id: Long)

    /** Legt eine zuvor gelöschte [station] mit ihrer bisherigen id und ihren Zeitstempeln wieder an. */
    suspend fun restore(station: Station)

    /**
     * Löst die Tour für eine neue Station auf: die Tour von [vehicleId], deren Zeitraum
     * [date] enthält, bei mehreren Treffern die mit dem spätesten Start; `null`, wenn keine passt.
     */
    suspend fun defaultTourId(vehicleId: Long, date: LocalDate): Long?

    /** Liefert die aktuell mit [stationId] verknüpften Bordbuch-Einträge, für ein Rückgängig nach dem Löschen. */
    suspend fun linkedLogEntries(stationId: Long): List<LogEntry>

    /** Verknüpft die Bordbuch-Einträge mit den ids [entryIds] wieder mit [stationId] (Rückgängig). */
    suspend fun relinkLogEntries(entryIds: List<Long>, stationId: Long)

    /**
     * Ordnet alle Stationen der Tour [tourId] dem Fahrzeug [vehicleId] zu, wenn die Tour das Fahrzeug wechselt.
     * Verknüpfte Bordbuch-Einträge ziehen nach denselben Regeln mit wie beim Bearbeiten einer einzelnen Station.
     *
     * @param tourId die Tour, deren Fahrzeug sich geändert hat
     * @param vehicleId das neue Fahrzeug der Tour
     */
    suspend fun moveTourToVehicle(tourId: Long, vehicleId: Long)
}

/**
 * Datumsvorschlag für eine neue Station einer Tour: [today], falls das in den Tourzeitraum
 * fällt, sonst der Tag nach [stations]' letzter Station (bei einer Übernachtung inklusive ihrer
 * Nächte), gekappt auf das Tourende. Ohne Stationen ist der Vorschlag der Tourstart.
 */
fun defaultStationDate(tour: Tour, stations: List<Station>, today: LocalDate): LocalDate {
    if (today in tour.startDate..tour.endDate) return today
    if (stations.isEmpty()) return tour.startDate
    val last = stations.maxWith(compareBy({ it.date }, { it.time ?: LocalTime.MAX }, { it.createdAt }))
    val daysToAdd = if (last.type == StationType.OVERNIGHT) (last.nights ?: 1).toLong() else 1L
    val next = last.date.plusDays(daysToAdd)
    return minOf(next, tour.endDate)
}
