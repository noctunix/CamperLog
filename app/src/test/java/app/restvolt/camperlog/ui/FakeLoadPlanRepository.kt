package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadPlan
import app.restvolt.camperlog.domain.LoadPlanRepository
import app.restvolt.camperlog.domain.LoadProfile
import app.restvolt.camperlog.domain.LoadProfileItemState
import app.restvolt.camperlog.domain.LoadProfileTankLevel
import app.restvolt.camperlog.domain.LoadTankKind
import app.restvolt.camperlog.domain.LoadTankSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomLoadPlanRepositoryTest. */
class FakeLoadPlanRepository(
    items: List<LoadItem> = emptyList(),
    profiles: List<LoadProfile> = emptyList(),
    tankSettings: List<LoadTankSettings> = emptyList(),
    itemStates: List<LoadProfileItemState> = emptyList(),
    tankLevels: List<LoadProfileTankLevel> = emptyList(),
) : LoadPlanRepository {

    private val plans = MutableStateFlow(
        buildMap {
            items.groupBy(LoadItem::vehicleId).keys.plus(profiles.map(LoadProfile::vehicleId)).plus(tankSettings.map(LoadTankSettings::vehicleId))
                .toSet()
                .forEach { vehicleId ->
                    put(
                        vehicleId,
                        LoadPlan(
                            vehicleId = vehicleId,
                            items = items.filter { it.vehicleId == vehicleId },
                            tankSettings = tankSettings.filter { it.vehicleId == vehicleId },
                            profiles = profiles.filter { it.vehicleId == vehicleId },
                            itemStates = itemStates,
                            tankLevels = tankLevels,
                        ),
                    )
                }
        },
    )

    private var nextItemId = (items.maxOfOrNull { it.id } ?: 0) + 1
    private var nextProfileId = (profiles.maxOfOrNull { it.id } ?: 0) + 1

    private fun planOf(vehicleId: Long): LoadPlan = plans.value[vehicleId] ?: LoadPlan(vehicleId = vehicleId)

    private fun update(vehicleId: Long, transform: (LoadPlan) -> LoadPlan) {
        plans.value = plans.value + (vehicleId to transform(planOf(vehicleId)))
    }

    private fun planOfItem(itemId: Long): LoadPlan = plans.value.values.first { plan -> plan.items.any { it.id == itemId } }

    private fun planOfProfile(profileId: Long): LoadPlan = plans.value.values.first { plan -> plan.profiles.any { it.id == profileId } }

    override fun observePlan(vehicleId: Long): Flow<LoadPlan> = plans.map { it[vehicleId] ?: LoadPlan(vehicleId = vehicleId) }

    override suspend fun saveItem(item: LoadItem): Long = if (item.id == 0L) {
        val id = nextItemId++
        update(item.vehicleId) { plan -> plan.copy(items = plan.items + item.copy(id = id)) }
        id
    } else {
        update(item.vehicleId) { plan -> plan.copy(items = plan.items.map { if (it.id == item.id) item else it }) }
        item.id
    }

    override suspend fun deleteItem(id: Long) {
        val vehicleId = planOfItem(id).vehicleId
        update(vehicleId) { plan ->
            plan.copy(items = plan.items.filterNot { it.id == id }, itemStates = plan.itemStates.filterNot { it.itemId == id })
        }
    }

    override suspend fun restoreItem(item: LoadItem) {
        update(item.vehicleId) { plan -> plan.copy(items = plan.items + item) }
    }

    override suspend fun saveProfile(profile: LoadProfile): Long = if (profile.id == 0L) {
        val id = nextProfileId++
        update(profile.vehicleId) { plan -> plan.copy(profiles = plan.profiles + profile.copy(id = id)) }
        id
    } else {
        update(profile.vehicleId) { plan -> plan.copy(profiles = plan.profiles.map { if (it.id == profile.id) profile else it }) }
        profile.id
    }

    override suspend fun duplicateProfile(profileId: Long, newName: String): Long {
        val plan = planOfProfile(profileId)
        val original = plan.profiles.first { it.id == profileId }
        val newId = nextProfileId++
        val copy = original.copy(id = newId, uuid = "", name = newName)
        update(plan.vehicleId) { current ->
            current.copy(
                profiles = current.profiles + copy,
                itemStates = current.itemStates + current.itemStates.filter { it.profileId == profileId }.map { it.copy(profileId = newId) },
                tankLevels = current.tankLevels + current.tankLevels.filter { it.profileId == profileId }.map { it.copy(profileId = newId) },
            )
        }
        return newId
    }

    override suspend fun deleteProfile(id: Long) {
        val vehicleId = planOfProfile(id).vehicleId
        update(vehicleId) { plan ->
            plan.copy(
                profiles = plan.profiles.filterNot { it.id == id },
                itemStates = plan.itemStates.filterNot { it.profileId == id },
                tankLevels = plan.tankLevels.filterNot { it.profileId == id },
            )
        }
    }

    override suspend fun setItemActive(profileId: Long, itemId: Long, active: Boolean) {
        val vehicleId = planOfProfile(profileId).vehicleId
        update(vehicleId) { plan ->
            val withoutState = plan.itemStates.filterNot { it.profileId == profileId && it.itemId == itemId }
            plan.copy(itemStates = withoutState + LoadProfileItemState(profileId, itemId, active))
        }
    }

    override suspend fun setTankLevel(profileId: Long, kind: LoadTankKind, levelDl: Int) {
        val vehicleId = planOfProfile(profileId).vehicleId
        update(vehicleId) { plan ->
            val withoutLevel = plan.tankLevels.filterNot { it.profileId == profileId && it.kind == kind }
            plan.copy(tankLevels = withoutLevel + LoadProfileTankLevel(profileId, kind, levelDl))
        }
    }

    override suspend fun saveTankSettings(settings: LoadTankSettings) {
        update(settings.vehicleId) { plan ->
            plan.copy(tankSettings = plan.tankSettings.filterNot { it.kind == settings.kind } + settings)
        }
    }

    override suspend fun ensureProfile(vehicleId: Long, defaultName: String): Long {
        planOf(vehicleId).profiles.firstOrNull()?.let { return it.id }
        return saveProfile(LoadProfile(vehicleId = vehicleId, name = defaultName))
    }
}
