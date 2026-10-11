package app.restvolt.camperlog.ui

import android.database.sqlite.SQLiteException
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomVehicleDocumentRepositoryTest. */
class FakeVehicleDocumentRepository(initial: List<VehicleDocument> = emptyList()) : VehicleDocumentRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val documents: List<VehicleDocument> get() = state.value

    /** Lässt [save] wie bei einem Datenbankfehler scheitern. */
    var failWrites = false

    /** Zählt die Aufrufe von [save]. */
    var saveCount = 0

    override fun observeForVehicle(vehicleId: Long): Flow<List<VehicleDocument>> =
        state.map { list -> list.filter { it.vehicleId == vehicleId } }

    override suspend fun allDocuments(): List<VehicleDocument> = state.value

    override fun observeAllDocuments(): Flow<List<VehicleDocument>> = state

    override suspend fun save(document: VehicleDocument): Long {
        if (failWrites) throw SQLiteException("simulierter Schreibfehler")
        saveCount++
        return if (document.id == 0L) {
            val id = nextId++
            state.value += document.copy(id = id)
            id
        } else {
            state.value = state.value.map { if (it.id == document.id) document else it }
            document.id
        }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(document: VehicleDocument) {
        state.value += document
    }
}
