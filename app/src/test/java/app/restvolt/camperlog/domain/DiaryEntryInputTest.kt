package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DiaryEntryInputTest {

    private val valid = DiaryEntryInput(date = LocalDate.of(2026, 7, 4), text = "Langer Tag am Fjord.")

    @Test
    fun validInputHasNoErrors() {
        assertTrue(valid.validate(emptySet()).isEmpty())
    }

    @Test
    fun dateIsRequired() {
        assertEquals(DiaryEntryError.REQUIRED, valid.copy(date = null).validate(emptySet())[DiaryEntryField.DATE])
    }

    @Test
    fun textIsRequired() {
        assertEquals(DiaryEntryError.REQUIRED, valid.copy(text = "").validate(emptySet())[DiaryEntryField.TEXT])
        assertEquals(DiaryEntryError.REQUIRED, valid.copy(text = "   ").validate(emptySet())[DiaryEntryField.TEXT])
    }

    @Test
    fun dateAlreadyUsedByAnotherEntryIsRejected() {
        val otherDates = setOf(LocalDate.of(2026, 7, 4))
        assertEquals(DiaryEntryError.DUPLICATE_DATE, valid.validate(otherDates)[DiaryEntryField.DATE])
    }

    @Test
    fun dateOfTheEditedEntryItselfIsNotADuplicate() {
        // otherEntryDates schließt den gerade bearbeiteten Eintrag per Vertrag aus (siehe KDoc).
        assertTrue(valid.validate(emptySet()).isEmpty())
    }

    @Test
    fun roundTripThroughInputIsLossless() {
        val entry = valid.copy(time = LocalTime.of(18, 30), odometerKm = "48300", weather = "Sonnig").toDiaryEntry(null, tourId = 3)
        assertEquals(entry, entry.toInput().toDiaryEntry(entry, tourId = 3))
    }

    @Test
    fun toDiaryEntryKeepsIdUuidAndTimestampsOfOriginal() {
        val original = DiaryEntry(
            id = 9,
            uuid = "entry-9",
            tourId = 3,
            date = LocalDate.of(2025, 1, 1),
            text = "Alt",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val edited = valid.toDiaryEntry(original, tourId = 3)
        assertEquals(9L, edited.id)
        assertEquals("entry-9", edited.uuid)
        assertEquals(Instant.EPOCH, edited.createdAt)
        assertEquals(Instant.EPOCH, edited.updatedAt)
        assertEquals("Langer Tag am Fjord.", edited.text)
    }

    @Test
    fun textIsTrimmed() {
        val entry = DiaryEntryInput(date = LocalDate.of(2026, 1, 1), text = "  Regen den ganzen Tag  ").toDiaryEntry(null, tourId = 1)
        assertEquals("Regen den ganzen Tag", entry.text)
    }

    @Test
    fun weatherIsTrimmed() {
        val entry = valid.copy(weather = "  Sonnig  ").toDiaryEntry(null, tourId = 1)
        assertEquals("Sonnig", entry.weather)
    }

    @Test
    fun timeAndOdometerAreOptional() {
        assertTrue(valid.validate(emptySet()).isEmpty())
        val entry = valid.toDiaryEntry(null, tourId = 1)
        assertEquals(null, entry.time)
        assertEquals(null, entry.odometerKm)
    }

    @Test
    fun timeIsKeptAsSet() {
        val entry = valid.copy(time = LocalTime.of(18, 30)).toDiaryEntry(null, tourId = 1)
        assertEquals(LocalTime.of(18, 30), entry.time)
    }

    @Test
    fun blankOdometerIsNotAnError() {
        assertTrue(valid.copy(odometerKm = "").validate(emptySet()).isEmpty())
    }

    @Test
    fun nonNumericOdometerIsRejected() {
        assertEquals(DiaryEntryError.INVALID_NUMBER, valid.copy(odometerKm = "abc").validate(emptySet())[DiaryEntryField.ODOMETER_KM])
    }

    @Test
    fun negativeOdometerIsRejected() {
        assertEquals(DiaryEntryError.NEGATIVE_NUMBER, valid.copy(odometerKm = "-1").validate(emptySet())[DiaryEntryField.ODOMETER_KM])
    }

    @Test
    fun tooLargeOdometerIsRejected() {
        assertEquals(
            DiaryEntryError.TOO_LARGE,
            valid.copy(odometerKm = (MAX_ODOMETER_KM + 1).toString()).validate(emptySet())[DiaryEntryField.ODOMETER_KM],
        )
    }

    @Test
    fun validOdometerIsKept() {
        val entry = valid.copy(odometerKm = "48300").toDiaryEntry(null, tourId = 1)
        assertEquals(48_300, entry.odometerKm)
    }

    @Test
    fun defaultDateIsTodayWhenWithinTheTour() {
        val tourStart = LocalDate.of(2026, 7, 1)
        val tourEnd = LocalDate.of(2026, 7, 10)
        val today = LocalDate.of(2026, 7, 5)
        assertEquals(today, defaultDiaryEntryDate(tourStart, tourEnd, emptySet(), today))
    }

    @Test
    fun defaultDateIsFirstTourDayWithoutAnEntryWhenTodayIsOutsideTheTour() {
        val tourStart = LocalDate.of(2026, 7, 1)
        val tourEnd = LocalDate.of(2026, 7, 10)
        val today = LocalDate.of(2026, 1, 1)
        val otherDates = setOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))
        assertEquals(LocalDate.of(2026, 7, 3), defaultDiaryEntryDate(tourStart, tourEnd, otherDates, today))
    }

    @Test
    fun defaultDateFallsBackToTheStartDateWhenEveryTourDayHasAnEntry() {
        val tourStart = LocalDate.of(2026, 7, 1)
        val tourEnd = LocalDate.of(2026, 7, 2)
        val today = LocalDate.of(2026, 1, 1)
        val otherDates = setOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))
        assertEquals(tourStart, defaultDiaryEntryDate(tourStart, tourEnd, otherDates, today))
    }
}
