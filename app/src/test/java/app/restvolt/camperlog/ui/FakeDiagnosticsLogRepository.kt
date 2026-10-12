package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.DiagnosticsLogEntry
import app.restvolt.camperlog.domain.DiagnosticsLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Synchrones In-Memory-Repository für UI-Tests; die Dateianbindung testet FileDiagnosticsLogRepositoryTest. */
class FakeDiagnosticsLogRepository(initial: List<DiagnosticsLogEntry> = emptyList()) : DiagnosticsLogRepository {
    private val state = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<DiagnosticsLogEntry>> = state

    override suspend fun append(entry: DiagnosticsLogEntry) {
        state.value += entry
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}
