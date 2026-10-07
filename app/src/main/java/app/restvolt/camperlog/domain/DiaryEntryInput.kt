package app.restvolt.camperlog.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/** Unvalidierte Eingaben des Tagebuch-Formulars. */
@Serializable
data class DiaryEntryInput(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate? = null,
    val text: String = "",
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class DiaryEntryField { DATE, TEXT }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class DiaryEntryError { REQUIRED, DUPLICATE_DATE }

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
    text = text.trim(),
    createdAt = original?.createdAt ?: Instant.EPOCH,
    updatedAt = original?.updatedAt ?: Instant.EPOCH,
)

/** Wandelt einen gespeicherten Tagebucheintrag in editierbare Formulardaten um. */
fun DiaryEntry.toInput(): DiaryEntryInput = DiaryEntryInput(date = date, text = text)

/**
 * Vorbelegtes Datum eines neuen Tagebucheintrags: [today], wenn es innerhalb von [tourStart] und
 * [tourEnd] liegt; sonst der erste Tourtag ohne Eintrag in [otherEntryDates]; sonst [tourStart].
 */
fun defaultDiaryEntryDate(tourStart: LocalDate, tourEnd: LocalDate, otherEntryDates: Set<LocalDate>, today: LocalDate): LocalDate {
    if (today in tourStart..tourEnd) return today
    var date = tourStart
    while (date <= tourEnd) {
        if (date !in otherEntryDates) return date
        date = date.plusDays(1)
    }
    return tourStart
}
