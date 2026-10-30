package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadPlan
import app.restvolt.camperlog.domain.LoadPlanRepository
import app.restvolt.camperlog.domain.LoadProfile
import app.restvolt.camperlog.domain.LoadTankKind
import app.restvolt.camperlog.domain.LoadTankSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * [LoadPlanRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und Änderung,
 * [newUuid] die Kennung neuer Gegenstände und Profile ohne eigene UUID.
 */
class RoomLoadPlanRepository(
    private val database: CamperLogDatabase,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : LoadPlanRepository {

    private val dao get() = database.loadDao()

    override fun observePlan(vehicleId: Long): Flow<LoadPlan> = dao.observePlan(vehicleId).map { it.toDomain(vehicleId) }

    override suspend fun saveItem(item: LoadItem): Long {
        val now = clock()
        return if (item.id == 0L) {
            val uuid = item.uuid.ifEmpty { newUuid() }
            dao.insertItem(item.copy(uuid = uuid, createdAt = now, updatedAt = now).toEntity())
        } else {
            dao.updateItem(item.copy(updatedAt = now).toEntity())
            item.id
        }
    }

    override suspend fun deleteItem(id: Long) = dao.deleteItem(id)

    override suspend fun restoreItem(item: LoadItem) {
        dao.insertItem(item.toEntity())
    }

    override suspend fun saveProfile(profile: LoadProfile): Long {
        return if (profile.id == 0L) {
            val uuid = profile.uuid.ifEmpty { newUuid() }
            dao.insertProfile(profile.copy(uuid = uuid).toEntity())
        } else {
            dao.updateProfile(profile.toEntity())
            profile.id
        }
    }

    override suspend fun duplicateProfile(profileId: Long, newName: String): Long = database.withTransaction {
        val original = checkNotNull(dao.profileById(profileId)) { "Profil $profileId existiert nicht" }
        val sortOrder = dao.nextProfileSortOrder(original.vehicleId)
        val newProfileId = dao.insertProfile(
            LoadProfileEntity(uuid = newUuid(), vehicleId = original.vehicleId, name = newName, sortOrder = sortOrder),
        )
        val states = dao.itemStatesOf(profileId).map { it.copy(profileId = newProfileId) }
        if (states.isNotEmpty()) dao.insertItemStates(states)
        val levels = dao.tankLevelsOf(profileId).map { it.copy(profileId = newProfileId) }
        if (levels.isNotEmpty()) dao.insertTankLevels(levels)
        newProfileId
    }

    override suspend fun deleteProfile(id: Long) = dao.deleteProfile(id)

    override suspend fun setItemActive(profileId: Long, itemId: Long, active: Boolean) =
        dao.setItemActive(LoadProfileItemStateEntity(profileId = profileId, itemId = itemId, active = active))

    override suspend fun setTankLevel(profileId: Long, kind: LoadTankKind, levelDl: Int) =
        dao.setTankLevel(LoadProfileTankLevelEntity(profileId = profileId, kind = kind.name, levelDl = levelDl))

    override suspend fun saveTankSettings(settings: LoadTankSettings) = dao.saveTankSettings(settings.toEntity())

    override suspend fun ensureProfile(vehicleId: Long, defaultName: String): Long {
        dao.firstProfile(vehicleId)?.let { return it.id }
        return dao.insertProfile(LoadProfileEntity(vehicleId = vehicleId, name = defaultName, sortOrder = 0))
    }
}
