package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LoadCalculationTest {

    private val now = Instant.EPOCH

    private fun vehicle(
        wheelbaseCm: Int? = 415,
        measuredFrontAxleKg: Int? = 1530,
        measuredRearAxleKg: Int? = 1900,
        measuredEmptyWeightKg: Int? = null,
        allowedFrontAxleKg: Int? = 2000,
        allowedRearAxleKg: Int? = 2240,
        grossWeightKg: Int? = 4000,
        fuelTankDl: Int? = null,
        requiredEnergyTypes: Set<EnergyType> = emptySet(),
    ) = Vehicle(
        id = 1,
        wheelbaseCm = wheelbaseCm,
        measuredFrontAxleKg = measuredFrontAxleKg,
        measuredRearAxleKg = measuredRearAxleKg,
        measuredEmptyWeightKg = measuredEmptyWeightKg,
        allowedFrontAxleKg = allowedFrontAxleKg,
        allowedRearAxleKg = allowedRearAxleKg,
        grossWeightKg = grossWeightKg,
        fuelTankDl = fuelTankDl,
        requiredEnergyTypes = requiredEnergyTypes,
        createdAt = now,
        updatedAt = now,
    )

    private fun item(
        id: Long,
        massGrams: Long,
        quantity: Int = 1,
        positionCm: Int? = null,
        inWeighing: Boolean = false,
        category: LoadCategory = LoadCategory.EQUIPMENT,
    ) = LoadItem(
        id = id,
        vehicleId = 1,
        name = "Item $id",
        category = category,
        massGrams = massGrams,
        quantity = quantity,
        positionCm = positionCm,
        inWeighing = inWeighing,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun axleDeltaInsideWheelbaseSplitsBetweenAxles() {
        val delta = axleDelta(massKg = 35.0, positionCm = 350, wheelbaseCm = 415)
        assertEquals(29.518, delta.rearKg, 0.001)
        assertEquals(5.481, delta.frontKg, 0.001)
    }

    @Test
    fun axleDeltaBehindRearAxleCanMakeFrontNegative() {
        val delta = axleDelta(massKg = 35.0, positionCm = 640, wheelbaseCm = 415)
        assertTrue(delta.rearKg > 35.0)
        assertTrue(delta.frontKg < 0.0)
        assertEquals(35.0, delta.frontKg + delta.rearKg, 0.0001)
    }

    @Test
    fun totalOkButRearAxleOver() {
        val vehicle = vehicle(wheelbaseCm = 400)
        val heavyItem = item(id = 1, massGrams = 344_000, positionCm = 400, inWeighing = false)
        val plan = LoadPlan(
            vehicleId = 1,
            items = listOf(heavyItem),
            profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "Reise")),
            itemStates = listOf(LoadProfileItemState(profileId = 1, itemId = 1, active = true)),
        )

        val forecast = forecastLoad(vehicle, plan, profileId = 1)

        val rearAxleCheck = checkNotNull(forecast.rearAxleCheck)

        assertEquals(3774.0, checkNotNull(forecast.totalKg), 0.001)
        assertEquals(1530.0, forecast.frontAxleKg!!, 0.001)
        assertEquals(2244.0, forecast.rearAxleKg!!, 0.001)
        assertTrue(forecast.totalCheck!!.ok)
        assertTrue(forecast.frontAxleCheck!!.ok)
        assertEquals(false, rearAxleCheck.ok)
        assertEquals(-4.0, rearAxleCheck.reserveKg, 0.001)
    }

    @Test
    fun togglingItemInactiveRemovesMassExactlyOnce() {
        val vehicle = vehicle()
        val bikeRack = item(id = 1, massGrams = 15_000, positionCm = 100, inWeighing = false)
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Reise")
        val activePlan = LoadPlan(
            vehicleId = 1,
            items = listOf(bikeRack),
            profiles = listOf(profile),
            itemStates = listOf(LoadProfileItemState(profileId = 1, itemId = 1, active = true)),
        )
        val inactivePlan = activePlan.copy(
            itemStates = listOf(LoadProfileItemState(profileId = 1, itemId = 1, active = false)),
        )

        val activeForecast = forecastLoad(vehicle, activePlan, profileId = 1)
        val inactiveForecast = forecastLoad(vehicle, inactivePlan, profileId = 1)

        assertEquals(15.0, activeForecast.deltaKg - inactiveForecast.deltaKg, 0.001)
    }

    @Test
    fun itemInWeighingButInactiveInProfileReducesMass() {
        val vehicle = vehicle()
        val awning = item(id = 1, massGrams = 20_000, positionCm = 50, inWeighing = true, category = LoadCategory.FIXTURE)
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Winter")
        val plan = LoadPlan(
            vehicleId = 1,
            items = listOf(awning),
            profiles = listOf(profile),
            itemStates = listOf(LoadProfileItemState(profileId = 1, itemId = 1, active = false)),
        )
        val baseline = vehicle.measuredFrontAxleKg!! + vehicle.measuredRearAxleKg!!

        val forecast = forecastLoad(vehicle, plan, profileId = 1)

        assertEquals(baseline - 20.0, checkNotNull(forecast.totalKg), 0.001)
    }

    @Test
    fun changingTankLevelChangesMassRelativeToWeighingLevel() {
        val vehicle = vehicle(fuelTankDl = 800)
        val settings = LoadTankSettings(vehicleId = 1, kind = LoadTankKind.FUEL, positionCm = 200, densityGramsPerLitre = 835, weighingLevelDl = 400)
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Reise")
        val plan = LoadPlan(
            vehicleId = 1,
            tankSettings = listOf(settings),
            profiles = listOf(profile),
            tankLevels = listOf(LoadProfileTankLevel(profileId = 1, kind = LoadTankKind.FUEL, levelDl = 800)),
        )
        val baseline = vehicle.measuredFrontAxleKg!! + vehicle.measuredRearAxleKg!!

        val forecast = forecastLoad(vehicle, plan, profileId = 1)

        val expectedDeltaKg = (800 - 400) * 835 / 10_000.0
        assertEquals(baseline + expectedDeltaKg, checkNotNull(forecast.totalKg), 0.001)
    }

    @Test
    fun unknownPositionKeepsTotalButClearsAxlesAndWarns() {
        val vehicle = vehicle()
        val unknown = item(id = 1, massGrams = 10_000, positionCm = null, inWeighing = false)
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Reise")
        val plan = LoadPlan(
            vehicleId = 1,
            items = listOf(unknown),
            profiles = listOf(profile),
            itemStates = listOf(LoadProfileItemState(profileId = 1, itemId = 1, active = true)),
        )
        val baseline = vehicle.measuredFrontAxleKg!! + vehicle.measuredRearAxleKg!!

        val forecast = forecastLoad(vehicle, plan, profileId = 1)

        assertEquals(baseline + 10.0, checkNotNull(forecast.totalKg), 0.001)
        assertNull(forecast.frontAxleKg)
        assertNull(forecast.rearAxleKg)
        assertTrue(forecast.warnings.any { it is LoadWarning.ItemsWithoutPosition })
    }

    @Test
    fun missingWheelbaseClearsAxlesAndWarns() {
        val vehicle = vehicle(wheelbaseCm = null)
        val item = item(id = 1, massGrams = 10_000, positionCm = 100, inWeighing = false)
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Reise")
        val plan = LoadPlan(
            vehicleId = 1,
            items = listOf(item),
            profiles = listOf(profile),
            itemStates = listOf(LoadProfileItemState(profileId = 1, itemId = 1, active = true)),
        )

        val forecast = forecastLoad(vehicle, plan, profileId = 1)

        assertNull(forecast.frontAxleKg)
        assertNull(forecast.rearAxleKg)
        assertTrue(forecast.warnings.any { it is LoadWarning.NoWheelbase })
    }

    @Test
    fun freshProfileWithoutRowsEqualsBaseline() {
        val vehicle = vehicle()
        val item = item(id = 1, massGrams = 10_000, positionCm = 100, inWeighing = true)
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Frisch")
        val plan = LoadPlan(vehicleId = 1, items = listOf(item), profiles = listOf(profile))

        val forecast = forecastLoad(vehicle, plan, profileId = 1)

        val measuredFrontAxleKg = checkNotNull(vehicle.measuredFrontAxleKg)
        val measuredRearAxleKg = checkNotNull(vehicle.measuredRearAxleKg)
        assertEquals(measuredFrontAxleKg.toDouble(), forecast.frontAxleKg!!, 0.0001)
        assertEquals(measuredRearAxleKg.toDouble(), forecast.rearAxleKg!!, 0.0001)
        assertEquals((measuredFrontAxleKg + measuredRearAxleKg).toDouble(), checkNotNull(forecast.totalKg), 0.0001)
        assertTrue(forecast.warnings.isEmpty())
    }

    @Test
    fun noWeighingKeepsOnlyDeltaTotalAndWarns() {
        val vehicle = vehicle(measuredFrontAxleKg = null, measuredRearAxleKg = null, measuredEmptyWeightKg = null)
        val item = item(id = 1, massGrams = 10_000, positionCm = 100, inWeighing = false)
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Reise")
        val plan = LoadPlan(
            vehicleId = 1,
            items = listOf(item),
            profiles = listOf(profile),
            itemStates = listOf(LoadProfileItemState(profileId = 1, itemId = 1, active = true)),
        )

        val forecast = forecastLoad(vehicle, plan, profileId = 1)

        assertEquals(10.0, forecast.deltaKg, 0.0001)
        assertNull(forecast.totalKg)
        assertNull(forecast.totalCheck)
        assertNull(forecast.frontAxleKg)
        assertNull(forecast.rearAxleKg)
        assertTrue(forecast.warnings.any { it is LoadWarning.NoWeighing })
    }

    @Test
    fun defaultFuelDensityIsDieselUnlessPetrolOnly() {
        val dieselOrUnknown = vehicle()
        assertEquals(DEFAULT_DIESEL_DENSITY_GRAMS_PER_LITRE, LoadTankKind.FUEL.defaultDensityGramsPerLitre(dieselOrUnknown))

        val petrolOnly = vehicle(requiredEnergyTypes = setOf(EnergyType.PETROL))
        assertEquals(DEFAULT_PETROL_DENSITY_GRAMS_PER_LITRE, LoadTankKind.FUEL.defaultDensityGramsPerLitre(petrolOnly))

        val petrolAndDiesel = vehicle(requiredEnergyTypes = setOf(EnergyType.PETROL, EnergyType.DIESEL))
        assertEquals(DEFAULT_DIESEL_DENSITY_GRAMS_PER_LITRE, LoadTankKind.FUEL.defaultDensityGramsPerLitre(petrolAndDiesel))
    }
}
