package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.domain.AttachmentOwnerType
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
 *
 * Löschen eines Fahrzeugs oder einer Reparatur nimmt ihre Anhänge in derselben Transaktion mit: Die
 * `attachments`-Tabelle hat keinen Fremdschlüssel auf Reparaturen, Bordbuch-Einträge oder
 * Fahrzeugdokumente (ein Anhang kann je [AttachmentOwnerType] in eine andere Tabelle zeigen, siehe
 * [app.restvolt.camperlog.domain.AttachmentRepository]), daher löscht Room diese Zeilen beim
 * kaskadierenden Löschen von Reparaturen, Bordbuch-Einträgen und Fahrzeugdokumenten über den
 * Fremdschlüssel von `vehicles` nicht automatisch mit.
 */
class RoomVehicleRepository(
    private val database: CamperLogDatabase,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : VehicleRepository {

    private val dao get() = database.vehicleDao()
    private val logDao get() = database.logDao()
    private val documentDao get() = database.vehicleDocumentDao()
    private val attachmentDao get() = database.attachmentDao()

    override fun observeVehicles(): Flow<List<Vehicle>> =
        dao.observeAll().map { rows -> rows.map(VehicleEntity::toDomain) }

    override suspend fun allVehicles(): List<Vehicle> = dao.getAll().map(VehicleEntity::toDomain)

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
        if (dao.countToursForVehicle(id) > 0 || dao.countStationsForVehicle(id) > 0) return VehicleDeleteResult.HAS_TOURS_OR_STATIONS
        database.withTransaction {
            dao.repairIdsForVehicle(id).takeIf { it.isNotEmpty() }
                ?.let { attachmentDao.deleteForOwners(AttachmentOwnerType.REPAIR.name, it) }
            documentDao.idsForVehicle(id).takeIf { it.isNotEmpty() }
                ?.let { attachmentDao.deleteForOwners(AttachmentOwnerType.VEHICLE_DOCUMENT.name, it) }
            logDao.idsForVehicle(id).takeIf { it.isNotEmpty() }
                ?.let { attachmentDao.deleteForOwners(AttachmentOwnerType.LOG_ENTRY.name, it) }
            dao.deleteById(id)
        }
        return VehicleDeleteResult.DELETED
    }

    override fun observeRepairs(vehicleId: Long): Flow<List<Repair>> =
        dao.observeRepairs(vehicleId).map { rows -> rows.map(RepairEntity::toDomain) }

    override suspend fun allRepairs(): List<Repair> = dao.getAllRepairs().map(RepairEntity::toDomain)

    override fun observeAllRepairs(): Flow<List<Repair>> = dao.observeAllRepairs().map { rows -> rows.map(RepairEntity::toDomain) }

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

    override suspend fun deleteRepair(id: Long) = database.withTransaction {
        attachmentDao.deleteForOwner(AttachmentOwnerType.REPAIR.name, id)
        dao.deleteRepairById(id)
    }

    override suspend fun restoreRepair(repair: Repair) {
        dao.insertRepair(repair.toEntity())
    }

    override suspend fun lastUsedRepairCurrency(): Currency? = dao.lastUsedRepairCurrency()?.let(Currency::getInstance)
}
