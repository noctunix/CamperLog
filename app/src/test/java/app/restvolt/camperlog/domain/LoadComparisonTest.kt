package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LoadComparisonTest {

    private val now = Instant.EPOCH

    private fun vehicle(wheelbaseCm: Int? = 400, fuelTankDl: Int? = null) = Vehicle(
        id = 1,
        wheelbaseCm = wheelbaseCm,
        measuredFrontAxleKg = 1500,
        measuredRearAxleKg = 1800,
        allowedFrontAxleKg = 2000,
        allowedRearAxleKg = 2200,
        grossWeightKg = 4000,
        fuelTankDl = fuelTankDl,
        createdAt = now,
        updatedAt = now,
    )

    private fun item(id: Long, massGrams: Long = 10_000, positionCm: Int? = 100, inWeighing: Boolean = false) = LoadItem(
        id = id,
        vehicleId = 1,
        name = "Item $id",
        category = LoadCategory.EQUIPMENT,
        massGrams = massGrams,
        positionCm = positionCm,
        inWeighing = inWeighing,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun differentItemActivationChangesTotalDeltaAndListsTheItem() {
        val vehicle = vehicle()
        val bike = item(id = 1, massGrams = 15_000)
        val plan = LoadPlan(
            vehicleId = 1,
            items = listOf(bike),
            profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "A"), LoadProfile(id = 2, vehicleId = 1, name = "B")),
            itemStates = listOf(LoadProfileItemState(profileId = 2, itemId = 1, active = true)),
        )

        val comparison = compareProfiles(vehicle, plan, profileA = 1, profileB = 2)

        assertEquals(15.0, checkNotNull(comparison.totalDeltaKg), 0.001)
        val difference = comparison.itemDifferences.single()
        assertEquals(bike, difference.item)
        assertEquals(false, difference.activeInA)
        assertEquals(true, difference.activeInB)
    }

    @Test
    fun sameActivationInBothProfilesListsNoDifference() {
        val vehicle = vehicle()
        val bike = item(id = 1)
        val plan = LoadPlan(
            vehicleId = 1,
            items = listOf(bike),
            profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "A"), LoadProfile(id = 2, vehicleId = 1, name = "B")),
            itemStates = listOf(
                LoadProfileItemState(profileId = 1, itemId = 1, active = true),
                LoadProfileItemState(profileId = 2, itemId = 1, active = true),
            ),
        )

        val comparison = compareProfiles(vehicle, plan, profileA = 1, profileB = 2)

        assertTrue(comparison.itemDifferences.isEmpty())
        assertEquals(0.0, checkNotNull(comparison.totalDeltaKg), 0.001)
    }

    @Test
    fun differentTankLevelIsListedWithItsMassDifference() {
        val vehicle = vehicle(fuelTankDl = 800)
        val settings = LoadTankSettings(vehicleId = 1, kind = LoadTankKind.FUEL, positionCm = 200, densityGramsPerLitre = 1000, weighingLevelDl = 400)
        val plan = LoadPlan(
            vehicleId = 1,
            tankSettings = listOf(settings),
            profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "A"), LoadProfile(id = 2, vehicleId = 1, name = "B")),
            tankLevels = listOf(
                LoadProfileTankLevel(profileId = 1, kind = LoadTankKind.FUEL, levelDl = 400),
                LoadProfileTankLevel(profileId = 2, kind = LoadTankKind.FUEL, levelDl = 800),
            ),
        )

        val comparison = compareProfiles(vehicle, plan, profileA = 1, profileB = 2)

        val difference = comparison.tankDifferences.single()
        assertEquals(LoadTankKind.FUEL, difference.kind)
        assertEquals(400, difference.levelDlA)
        assertEquals(800, difference.levelDlB)
        assertEquals(40.0, difference.massDeltaKg, 0.001)
    }

    @Test
    fun missingAxleDataOnEitherSideLeavesTheDeltaNull() {
        val vehicle = vehicle(wheelbaseCm = null)
        val item = item(id = 1)
        val plan = LoadPlan(
            vehicleId = 1,
            items = listOf(item),
            profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "A"), LoadProfile(id = 2, vehicleId = 1, name = "B")),
            itemStates = listOf(LoadProfileItemState(profileId = 2, itemId = 1, active = true)),
        )

        val comparison = compareProfiles(vehicle, plan, profileA = 1, profileB = 2)

        assertNull(comparison.frontAxleDeltaKg)
        assertNull(comparison.rearAxleDeltaKg)
    }
}
