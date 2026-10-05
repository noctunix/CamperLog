package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDeleteResult
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomVehicleRepositoryTest.
 * [tourCount] liefert die Anzahl Touren eines Fahrzeugs für die HAS_TOURS-Prüfung bei [delete].
 */
class FakeVehicleRepository(
    initial: List<Vehicle> = listOf(defaultVehicle()),
    currentVehicleId: Long = initial.firstOrNull()?.id ?: 0,
    private val tourCount: (Long) -> Int = { 0 },
) : VehicleRepository {

    private val state = MutableStateFlow(initial)
    private val current = MutableStateFlow(currentVehicleId)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    private val repairs = MutableStateFlow<List<Repair>>(emptyList())
    private var nextRepairId = 1L

    val vehicles: List<Vehicle> get() = state.value
    val currentVehicleId: Long get() = current.value

    override fun observeVehicles(): Flow<List<Vehicle>> =
        state.map { list -> list.sortedWith(compareBy<Vehicle> { it.isSold }.thenBy { it.name }.thenBy { it.id }) }

    override fun observeVehicle(id: Long): Flow<Vehicle?> = state.map { list -> list.firstOrNull { it.id == id } }

    override fun observeCurrentVehicle(): Flow<Vehicle> =
        combine(state, current) { list, id -> list.firstOrNull { it.id == id } ?: list.first() }

    override suspend fun setCurrentVehicle(id: Long) {
        current.value = id
    }

    override suspend fun save(vehicle: Vehicle): Long {
        val now = Instant.EPOCH
        return if (vehicle.id == 0L) {
            val id = nextId++
            state.value += vehicle.copy(id = id, createdAt = now, updatedAt = now)
            id
        } else {
            state.value = state.value.map { if (it.id == vehicle.id) vehicle.copy(updatedAt = now) else it }
            vehicle.id
        }
    }

    override suspend fun delete(id: Long): VehicleDeleteResult {
        if (state.value.size <= 1) return VehicleDeleteResult.LAST_VEHICLE
        if (tourCount(id) > 0) return VehicleDeleteResult.HAS_TOURS
        state.value = state.value.filterNot { it.id == id }
        return VehicleDeleteResult.DELETED
    }

    override fun observeRepairs(vehicleId: Long): Flow<List<Repair>> =
        repairs.map { list -> list.filter { it.vehicleId == vehicleId }.sortedByDescending { it.date } }

    override suspend fun saveRepair(repair: Repair): Long = if (repair.id == 0L) {
        val id = nextRepairId++
        repairs.value += repair.copy(id = id)
        id
    } else {
        repairs.value = repairs.value.map { if (it.id == repair.id) repair else it }
        repair.id
    }

    override suspend fun deleteRepair(id: Long) {
        repairs.value = repairs.value.filterNot { it.id == id }
    }

    override suspend fun restoreRepair(repair: Repair) {
        repairs.value += repair
    }
}

/** Fahrzeug für Tests mit sinnvollen Zeitstempeln; [sold] setzt ein Verkaufsdatum. */
fun defaultVehicle(id: Long = 1, name: String = "", sold: Boolean = false) = Vehicle(
    id = id,
    uuid = "vehicle-$id",
    name = name,
    saleDate = if (sold) java.time.LocalDate.of(2025, 1, 1) else null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
