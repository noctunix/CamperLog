package app.restvolt.camperlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.DiagnosticsLogEntry
import app.restvolt.camperlog.domain.DiagnosticsLogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileDiagnosticsLogRepositoryTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val logFile get() = File(context.filesDir, "diagnostics_log.jsonl")

    private fun entry(message: String) = DiagnosticsLogEntry.capture(
        timestamp = Instant.EPOCH,
        source = DiagnosticsLogSource.TRACK_RECORDING,
        fatal = false,
        tag = "Test",
        rawMessage = message,
    )

    @Test
    fun append_writesTheEntryAndMakesItObservable() = runTest {
        val repository = FileDiagnosticsLogRepository(context)

        repository.append(entry("eins"))

        assertEquals(listOf("eins"), repository.observeAll().value.map { it.message })
        assertTrue(logFile.exists())
    }

    @Test
    fun append_persistsAcrossInstances() = runTest {
        FileDiagnosticsLogRepository(context).append(entry("eins"))

        val reopened = FileDiagnosticsLogRepository(context)

        assertEquals(listOf("eins"), reopened.observeAll().value.map { it.message })
    }

    @Test
    fun append_rotatesOldestEntriesOnceTheLimitIsExceeded() = runTest {
        val repository = FileDiagnosticsLogRepository(context)

        repeat(FileDiagnosticsLogRepository.MAX_ENTRIES + 5) { i -> repository.append(entry("entry-$i")) }

        val messages = repository.observeAll().value.map { it.message }
        assertEquals(FileDiagnosticsLogRepository.MAX_ENTRIES, messages.size)
        assertEquals("entry-5", messages.first())
        assertEquals("entry-${FileDiagnosticsLogRepository.MAX_ENTRIES + 4}", messages.last())
    }

    @Test
    fun clear_removesAllEntries() = runTest {
        val repository = FileDiagnosticsLogRepository(context)
        repository.append(entry("eins"))

        repository.clear()

        assertEquals(emptyList<DiagnosticsLogEntry>(), repository.observeAll().value)
        assertFalse(logFile.exists())
    }
}
