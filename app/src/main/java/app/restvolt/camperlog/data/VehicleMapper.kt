package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.Axle
import app.restvolt.camperlog.domain.EnergyType
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.TransmissionType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.withMinimumAxles
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency

internal fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id,
    uuid = uuid,
    name = name,
    licensePlate = licensePlate,
    manufacturer = manufacturer,
    model = model,
    vin = vin,
    firstRegistration = firstRegistration?.let(LocalDate::parse),
    notes = notes,
    purchaseDate = purchaseDate?.let(LocalDate::parse),
    purchasePrice = moneyOf(purchasePriceCurrency, purchasePriceMinor),
    purchaseOdometerKm = purchaseOdometerKm,
    sellerName = sellerName,
    saleDate = saleDate?.let(LocalDate::parse),
    salePrice = moneyOf(salePriceCurrency, salePriceMinor),
    saleOdometerKm = saleOdometerKm,
    buyerName = buyerName,
    insurer = insurer,
    insurancePolicyNumber = insurancePolicyNumber,
    insurancePremiumPerYear = moneyOf(insurancePremiumPerYearCurrency, insurancePremiumPerYearMinor),
    vehicleTaxPerYear = moneyOf(vehicleTaxPerYearCurrency, vehicleTaxPerYearMinor),
    lengthCm = lengthCm,
    widthCm = widthCm,
    heightCm = heightCm,
    grossWeightKg = grossWeightKg,
    measuredEmptyWeightKg = measuredEmptyWeightKg,
    breakdownProvider = breakdownProvider,
    breakdownMembershipNumber = breakdownMembershipNumber,
    breakdownPhone = breakdownPhone,
    travelProtectionProvider = travelProtectionProvider,
    travelProtectionContractNumber = travelProtectionContractNumber,
    travelProtectionPhone = travelProtectionPhone,
    insurerClaimsPhone = insurerClaimsPhone,
    powerKw = powerKw,
    displacementCc = displacementCc,
    transmission = transmission?.let(TransmissionType::valueOf),
    tirePressureFrontMbar = tirePressureFrontMbar,
    tirePressureRearMbar = tirePressureRearMbar,
    requiredEnergyTypes = decodeEnergyTypes(requiredEnergyTypes),
    fuelTankDl = fuelTankDl,
    adBlueTankDl = adBlueTankDl,
    freshWaterTankDl = freshWaterTankDl,
    greyWaterTankDl = greyWaterTankDl,
    boilerDl = boilerDl,
    cassetteDl = cassetteDl,
    batteryCapacityAh = batteryCapacityAh,
    solarPowerWp = solarPowerWp,
    nextInspectionDate = nextInspectionDate?.let(LocalDate::parse),
    nextGasCheckDate = nextGasCheckDate?.let(LocalDate::parse),
    nextLeakTestDate = nextLeakTestDate?.let(LocalDate::parse),
    lastOilChangeDate = lastOilChangeDate?.let(LocalDate::parse),
    lastOilChangeOdometerKm = lastOilChangeOdometerKm,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
    isDemo = isDemo,
)

internal fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    uuid = uuid,
    name = name,
    licensePlate = licensePlate,
    manufacturer = manufacturer,
    model = model,
    vin = vin,
    firstRegistration = firstRegistration?.toString(),
    notes = notes,
    purchaseDate = purchaseDate?.toString(),
    purchasePriceCurrency = purchasePrice?.currency?.currencyCode,
    purchasePriceMinor = purchasePrice?.minor,
    purchaseOdometerKm = purchaseOdometerKm,
    sellerName = sellerName,
    saleDate = saleDate?.toString(),
    salePriceCurrency = salePrice?.currency?.currencyCode,
    salePriceMinor = salePrice?.minor,
    saleOdometerKm = saleOdometerKm,
    buyerName = buyerName,
    insurer = insurer,
    insurancePolicyNumber = insurancePolicyNumber,
    insurancePremiumPerYearCurrency = insurancePremiumPerYear?.currency?.currencyCode,
    insurancePremiumPerYearMinor = insurancePremiumPerYear?.minor,
    vehicleTaxPerYearCurrency = vehicleTaxPerYear?.currency?.currencyCode,
    vehicleTaxPerYearMinor = vehicleTaxPerYear?.minor,
    lengthCm = lengthCm,
    widthCm = widthCm,
    heightCm = heightCm,
    grossWeightKg = grossWeightKg,
    measuredEmptyWeightKg = measuredEmptyWeightKg,
    breakdownProvider = breakdownProvider,
    breakdownMembershipNumber = breakdownMembershipNumber,
    breakdownPhone = breakdownPhone,
    travelProtectionProvider = travelProtectionProvider,
    travelProtectionContractNumber = travelProtectionContractNumber,
    travelProtectionPhone = travelProtectionPhone,
    insurerClaimsPhone = insurerClaimsPhone,
    powerKw = powerKw,
    displacementCc = displacementCc,
    transmission = transmission?.name,
    tirePressureFrontMbar = tirePressureFrontMbar,
    tirePressureRearMbar = tirePressureRearMbar,
    requiredEnergyTypes = encodeEnergyTypes(requiredEnergyTypes),
    fuelTankDl = fuelTankDl,
    adBlueTankDl = adBlueTankDl,
    freshWaterTankDl = freshWaterTankDl,
    greyWaterTankDl = greyWaterTankDl,
    boilerDl = boilerDl,
    cassetteDl = cassetteDl,
    batteryCapacityAh = batteryCapacityAh,
    solarPowerWp = solarPowerWp,
    nextInspectionDate = nextInspectionDate?.toString(),
    nextGasCheckDate = nextGasCheckDate?.toString(),
    nextLeakTestDate = nextLeakTestDate?.toString(),
    lastOilChangeDate = lastOilChangeDate?.toString(),
    lastOilChangeOdometerKm = lastOilChangeOdometerKm,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
    isDemo = isDemo,
)

internal fun VehicleWithAxles.toDomain(): Vehicle =
    vehicle.toDomain().copy(axles = axles.sortedBy(VehicleAxleEntity::position).map(VehicleAxleEntity::toDomain).withMinimumAxles())

internal fun Vehicle.toAxleEntities(): List<VehicleAxleEntity> = axles.mapIndexed { index, axle ->
    VehicleAxleEntity(vehicleId = id, position = index, tireSize = axle.tireSize, dualTires = axle.dualTires, secondTireSize = axle.secondTireSize)
}

private fun VehicleAxleEntity.toDomain(): Axle = Axle(tireSize = tireSize, dualTires = dualTires, secondTireSize = secondTireSize)

internal fun RepairEntity.toDomain(): Repair = Repair(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    date = LocalDate.parse(date),
    time = time?.let(LocalTime::parse),
    description = description,
    odometerKm = odometerKm,
    cost = moneyOf(costCurrency, costMinor),
    workshopName = workshopName,
    latitude = latitude,
    longitude = longitude,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

internal fun Repair.toEntity(): RepairEntity = RepairEntity(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    date = date.toString(),
    time = time?.toString(),
    description = description,
    odometerKm = odometerKm,
    costCurrency = cost?.currency?.currencyCode,
    costMinor = cost?.minor,
    workshopName = workshopName,
    latitude = latitude,
    longitude = longitude,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

private fun moneyOf(currency: String?, minor: Long?): Money? =
    if (currency != null && minor != null) Money(minor, Currency.getInstance(currency)) else null

private fun encodeEnergyTypes(types: Set<EnergyType>): String = types.joinToString(",") { it.name }

private fun decodeEnergyTypes(text: String): Set<EnergyType> =
    text.split(",").mapNotNullTo(LinkedHashSet()) { name -> name.takeIf(String::isNotBlank)?.let(EnergyType::valueOf) }
