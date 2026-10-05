package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LogRecencyTest {

    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun sameDateIsToday() {
        assertEquals(LogRecency.Today, logRecency(today, today))
    }

    @Test
    fun oneDayBeforeIsYesterday() {
        assertEquals(LogRecency.Yesterday, logRecency(today.minusDays(1), today))
    }

    @Test
    fun twoDaysBeforeIsDaysAgo() {
        assertEquals(LogRecency.DaysAgo(2), logRecency(today.minusDays(2), today))
    }

    @Test
    fun manyDaysBeforeIsDaysAgo() {
        assertEquals(LogRecency.DaysAgo(42), logRecency(today.minusDays(42), today))
    }
}
