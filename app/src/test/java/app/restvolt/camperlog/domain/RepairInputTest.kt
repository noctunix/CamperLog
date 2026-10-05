package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class RepairInputTest {

    private val de = Locale.GERMANY

    private val valid = RepairInput(
        date = LocalDate.of(2026, 3, 1),
        description = "Reifen gewechselt",
        odometerKm = "42000",
        cost = "250",
    )

    @Test
    fun validInputHasNoErrors() {
        assertTrue(valid.validate(de).isEmpty())
    }

    @Test
    fun dateIsRequired() {
        assertEquals(RepairError.REQUIRED, valid.copy(date = null).validate(de)[RepairField.DATE])
    }

    @Test
    fun descriptionIsRequired() {
        assertEquals(RepairError.REQUIRED, valid.copy(description = "").validate(de)[RepairField.DESCRIPTION])
        assertEquals(RepairError.REQUIRED, valid.copy(description = "   ").validate(de)[RepairField.DESCRIPTION])
    }

    @Test
    fun odometerFollowsExistingOdometerBounds() {
        assertEquals(RepairError.NEGATIVE_NUMBER, valid.copy(odometerKm = "-1").validate(de)[RepairField.ODOMETER_KM])
        assertEquals(RepairError.INVALID_NUMBER, valid.copy(odometerKm = "abc").validate(de)[RepairField.ODOMETER_KM])
        assertEquals(RepairError.TOO_LARGE, valid.copy(odometerKm = "10000001").validate(de)[RepairField.ODOMETER_KM])
        assertTrue(valid.copy(odometerKm = "").validate(de).isEmpty())
    }

    @Test
    fun costFollowsExistingAmountRules() {
        assertEquals(RepairError.INVALID_AMOUNT, valid.copy(cost = "12km").validate(de)[RepairField.COST])
        assertEquals(
            RepairError.AMOUNT_TOO_LARGE,
            valid.copy(cost = "100000000000.01").validate(de)[RepairField.COST],
        )
        assertTrue(valid.copy(cost = "").validate(de).isEmpty())
    }

    @Test
    fun roundTripThroughInputIsLossless() {
        val repair = valid.toRepair(null, vehicleId = 3, de)
        assertEquals(repair, repair.toInput(de).toRepair(repair, vehicleId = 3, de))
    }

    @Test
    fun toRepairKeepsIdUuidAndTimestampsOfOriginal() {
        val original = Repair(
            id = 9,
            uuid = "repair-9",
            vehicleId = 3,
            date = LocalDate.of(2025, 1, 1),
            description = "Alt",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val edited = valid.toRepair(original, vehicleId = 3, de)
        assertEquals(9L, edited.id)
        assertEquals("repair-9", edited.uuid)
        assertEquals(Instant.EPOCH, edited.createdAt)
        assertEquals(Instant.EPOCH, edited.updatedAt)
        assertEquals("Reifen gewechselt", edited.description)
    }

    @Test
    fun blankOptionalFieldsBecomeNull() {
        val repair = RepairInput(date = LocalDate.of(2026, 1, 1), description = "Service").toRepair(null, vehicleId = 1, de)
        assertNull(repair.odometerKm)
        assertNull(repair.cost)
    }

    @Test
    fun descriptionAndCostAreTrimmedAndParsedByLocale() {
        val input = RepairInput(date = LocalDate.of(2026, 1, 1), description = "  Ölwechsel  ", cost = "99,90")
        val repair = input.toRepair(null, vehicleId = 1, de)
        assertEquals("Ölwechsel", repair.description)
        assertEquals(9990L, repair.cost?.minor)
    }
}
