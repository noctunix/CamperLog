package app.restvolt.camperlog.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Unvalidierte Eingaben des Tagebuch-Formulars. Der Kilometerstand liegt als Text vor. */
@Serializable
data class DiaryEntryInput(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate? = null,
    @Serializable(with = LocalTimeSerializer::class) val time: LocalTime? = null,
    val text: String = "",
    val odometerKm: String = "",
    val weather: String = "",
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class DiaryEntryField { DATE, TEXT, ODOMETER_KM }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class DiaryEntryError { REQUIRED, DUPLICATE_DATE, NEGATIVE_NUMBER, INVALID_NUMBER, TOO_LARGE }

/**
 * Prüft die fachlichen Regeln einer Tagebuch-Eingabe.
 *
 * @param otherEntryDates Daten der übrigen Einträge derselben Tour (ohne den gerade bearbeiteten)
 * @return Fehlergrund je fehlerhaftem Feld; leer, wenn die Eingabe gültig ist
 */
fun DiaryEntryInput.validate(otherEntryDates: Set<LocalDate>): Map<DiaryEntryField, DiaryEntryError> = buildMap {
    when {
        date == null -> put(DiaryEntryField.DATE, DiaryEntryError.REQUIRED)
        date in otherEntryDates -> put(DiaryEntryField.DATE, DiaryEntryError.DUPLICATE_DATE)
    }
    if (text.isBlank()) put(DiaryEntryField.TEXT, DiaryEntryError.REQUIRED)
    if (odometerKm.isNotBlank()) {
        val value = odometerKm.trim().toIntOrNull()
        when {
            value == null -> put(DiaryEntryField.ODOMETER_KM, DiaryEntryError.INVALID_NUMBER)
            value < 0 -> put(DiaryEntryField.ODOMETER_KM, DiaryEntryError.NEGATIVE_NUMBER)
            value > MAX_ODOMETER_KM -> put(DiaryEntryField.ODOMETER_KM, DiaryEntryError.TOO_LARGE)
        }
    }
}

/**
 * Erzeugt aus einer gültigen Eingabe einen [DiaryEntry]. Vorher muss [validate] leer sein.
 *
 * @param original der bearbeitete Eintrag oder `null` für einen neuen Eintrag
 * @param tourId die Tour, zu der der Eintrag gehört
 */
fun DiaryEntryInput.toDiaryEntry(original: DiaryEntry?, tourId: Long): DiaryEntry = DiaryEntry(
    id = original?.id ?: 0,
    uuid = original?.uuid.orEmpty(),
    tourId = tourId,
    date = checkNotNull(date) { "Datum muss vor dem Speichern validiert sein" },
    time = time,
    text = text.trim(),
    odometerKm = parseOptionalInt(odometerKm),
    weather = weather.trim(),
    createdAt = original?.createdAt ?: Instant.EPOCH,
    updatedAt = original?.updatedAt ?: Instant.EPOCH,
)

/** Wandelt einen gespeicherten Tagebucheintrag in editierbare Formulardaten um. */
fun DiaryEntry.toInput(): DiaryEntryInput = DiaryEntryInput(
    date = date,
    time = time,
    text = text,
    odometerKm = odometerKm?.toString().orEmpty(),
    weather = weather,
)

/**
 * Vorbelegtes Datum eines neuen Tagebucheintrags: [today], wenn es innerhalb von [tourStart] und
 * [tourEnd] liegt; bei einer laufenden Tour gilt [today] als vorläufiges Ende. Sonst der erste
 * Tourtag ohne Eintrag in [otherEntryDates], abschließend [tourStart].
 */
fun defaultDiaryEntryDate(tourStart: LocalDate, tourEnd: LocalDate?, otherEntryDates: Set<LocalDate>, today: LocalDate): LocalDate {
    val effectiveEnd = tourEnd ?: maxOf(today, tourStart)
    if (today in tourStart..effectiveEnd) return today
    var date = tourStart
    while (date <= effectiveEnd) {
        if (date !in otherEntryDates) return date
        date = date.plusDays(1)
    }
    return tourStart
}
