package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.LoadCategory
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadPlan
import app.restvolt.camperlog.domain.LoadProfile
import app.restvolt.camperlog.domain.LoadProfileItemState
import app.restvolt.camperlog.domain.LoadProfileTankLevel
import app.restvolt.camperlog.domain.LoadTankKind
import app.restvolt.camperlog.domain.LoadTankSettings
import java.time.Instant

internal fun LoadItemEntity.toDomain(): LoadItem = LoadItem(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    name = name,
    category = LoadCategory.valueOf(category),
    massGrams = massGrams,
    quantity = quantity,
    positionCm = positionCm,
    inWeighing = inWeighing,
    sortOrder = sortOrder,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

internal fun LoadItem.toEntity(): LoadItemEntity = LoadItemEntity(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    name = name,
    category = category.name,
    massGrams = massGrams,
    quantity = quantity,
    positionCm = positionCm,
    inWeighing = inWeighing,
    sortOrder = sortOrder,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

internal fun LoadProfileEntity.toDomain(): LoadProfile = LoadProfile(id = id, uuid = uuid, vehicleId = vehicleId, name = name, sortOrder = sortOrder)

internal fun LoadProfile.toEntity(): LoadProfileEntity = LoadProfileEntity(id = id, uuid = uuid, vehicleId = vehicleId, name = name, sortOrder = sortOrder)

internal fun LoadProfileItemStateEntity.toDomain(): LoadProfileItemState = LoadProfileItemState(profileId = profileId, itemId = itemId, active = active)

internal fun LoadProfileTankLevelEntity.toDomain(): LoadProfileTankLevel =
    LoadProfileTankLevel(profileId = profileId, kind = LoadTankKind.valueOf(kind), levelDl = levelDl)

internal fun LoadTankSettingsEntity.toDomain(): LoadTankSettings = LoadTankSettings(
    vehicleId = vehicleId,
    kind = LoadTankKind.valueOf(kind),
    positionCm = positionCm,
    densityGramsPerLitre = densityGramsPerLitre,
    weighingLevelDl = weighingLevelDl,
)

internal fun LoadTankSettings.toEntity(): LoadTankSettingsEntity = LoadTankSettingsEntity(
    vehicleId = vehicleId,
    kind = kind.name,
    positionCm = positionCm,
    densityGramsPerLitre = densityGramsPerLitre,
    weighingLevelDl = weighingLevelDl,
)

internal fun LoadPlanRow.toDomain(vehicleId: Long): LoadPlan = LoadPlan(
    vehicleId = vehicleId,
    items = items.map(LoadItemEntity::toDomain),
    tankSettings = tankSettings.map(LoadTankSettingsEntity::toDomain),
    profiles = profiles.map(LoadProfileEntity::toDomain),
    itemStates = itemStates.map(LoadProfileItemStateEntity::toDomain),
    tankLevels = tankLevels.map(LoadProfileTankLevelEntity::toDomain),
)
