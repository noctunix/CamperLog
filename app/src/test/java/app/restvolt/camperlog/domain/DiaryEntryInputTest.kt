package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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
        val entry = valid.toDiaryEntry(null, tourId = 3)
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
