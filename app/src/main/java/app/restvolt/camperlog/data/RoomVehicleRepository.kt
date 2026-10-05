package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDeleteResult
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * [VehicleRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und Änderung,
 * [newUuid] die Kennung neuer Fahrzeuge und Reparaturen ohne eigene UUID.
 */
class RoomVehicleRepository(
    private val dao: VehicleDao,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : VehicleRepository {

    override fun observeVehicles(): Flow<List<Vehicle>> =
        dao.observeAll().map { rows -> rows.map(VehicleEntity::toDomain) }

    override fun observeVehicle(id: Long): Flow<Vehicle?> =
        dao.observeById(id).distinctUntilChanged().map { it?.toDomain() }

    override fun observeCurrentVehicle(): Flow<Vehicle> = flow {
        // Stellt sicher, dass mindestens ein Fahrzeug existiert, bevor die Auswahl live mitläuft.
        dao.resolveCurrentVehicleId(clock(), newUuid)
        emitAll(
            combine(dao.observeCurrentVehicleId(), dao.observeAllByIdAsc()) { currentId, vehicles ->
                (vehicles.firstOrNull { it.id == currentId } ?: vehicles.first()).toDomain()
            },
        )
    }.distinctUntilChanged()

    override suspend fun setCurrentVehicle(id: Long) = dao.setCurrentVehicleId(id)

    override suspend fun save(vehicle: Vehicle): Long {
        val now = clock()
        return if (vehicle.id == 0L) {
            val uuid = vehicle.uuid.ifEmpty { newUuid() }
            dao.insert(vehicle.copy(uuid = uuid, createdAt = now, updatedAt = now).toEntity())
        } else {
            dao.update(vehicle.copy(updatedAt = now).toEntity())
            vehicle.id
        }
    }

    override suspend fun delete(id: Long): VehicleDeleteResult {
        if (dao.countVehicles() <= 1) return VehicleDeleteResult.LAST_VEHICLE
        if (dao.countToursForVehicle(id) > 0) return VehicleDeleteResult.HAS_TOURS
        dao.deleteById(id)
        return VehicleDeleteResult.DELETED
    }

    override fun observeRepairs(vehicleId: Long): Flow<List<Repair>> =
        dao.observeRepairs(vehicleId).map { rows -> rows.map(RepairEntity::toDomain) }

    override suspend fun saveRepair(repair: Repair): Long {
        require(repair.description.isNotBlank()) { "Beschreibung darf nicht leer sein" }
        val now = clock()
        return if (repair.id == 0L) {
            val uuid = repair.uuid.ifEmpty { newUuid() }
            dao.insertRepair(repair.copy(uuid = uuid, createdAt = now, updatedAt = now).toEntity())
        } else {
            dao.updateRepair(repair.copy(updatedAt = now).toEntity())
            repair.id
        }
    }

    override suspend fun deleteRepair(id: Long) = dao.deleteRepairById(id)

    override suspend fun restoreRepair(repair: Repair) {
        dao.insertRepair(repair.toEntity())
    }

    override suspend fun lastUsedRepairCurrency(): Currency? = dao.lastUsedRepairCurrency()?.let(Currency::getInstance)
}
