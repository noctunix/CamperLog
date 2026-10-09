package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class VehicleInputTest {

    private val de = Locale.GERMANY

    private val valid = VehicleInput(
        name = "Wohnmobil",
        licensePlate = "B-AB 1234",
        manufacturer = "Hymer",
        model = "Exsis",
        vin = "WDB1234567890",
        firstRegistration = LocalDate.of(2020, 5, 1),
        purchaseDate = LocalDate.of(2021, 1, 1),
        purchasePrice = "45000",
        purchaseOdometerKm = "12000",
        insurer = "ACME",
        insurancePolicyNumber = "POL-1",
        insurancePremiumPerYear = "600",
        vehicleTaxPerYear = "250",
        lengthCm = "636",
        widthCm = "230",
        heightCm = "280",
        grossWeightKg = "3500",
        measuredEmptyWeightKg = "3020",
        breakdownProvider = "ADAC",
        breakdownMembershipNumber = "123 456 789",
        breakdownPhone = "+49 89 22 22 22",
        travelProtectionProvider = "Beispiel AG",
        travelProtectionContractNumber = "987-654",
        travelProtectionPhone = "+49 30 123456",
        insurerClaimsPhone = "+49 69 5678",
        powerKw = "120",
        tireSize = "225/75 R16 C",
        tirePressureFrontBar = "2,80",
        tirePressureRearBar = "3,00",
        fuelTankL = "90",
        adBlueTankL = "15",
        freshWaterTankL = "120",
        greyWaterTankL = "90",
        boilerL = "10",
        cassetteL = "19",
        batteryCapacityAh = "200",
        solarPowerWp = "300",
        notes = "Testfahrzeug",
    )

    @Test
    fun validInputHasNoErrors() {
        assertTrue(valid.validate(de, isNew = true).isEmpty())
    }

    @Test
    fun nameRequiredOnlyForNewVehicle() {
        val blankName = valid.copy(name = "")
        assertEquals(VehicleError.REQUIRED, blankName.validate(de, isNew = true)[VehicleField.NAME])
        assertTrue(blankName.validate(de, isNew = false).isEmpty())
    }

    @Test
    fun firstRegistrationCannotBeInTheFuture() {
        val today = LocalDate.of(2026, 10, 5)
        val future = valid.copy(firstRegistration = today.plusDays(1))
        assertEquals(VehicleError.DATE_IN_FUTURE, future.validate(de, isNew = true, today)[VehicleField.FIRST_REGISTRATION])
        assertTrue(valid.copy(firstRegistration = today).validate(de, isNew = true, today).isEmpty())
    }

    @Test
    fun saleDateMustNotBeBeforePurchaseDate() {
        val input = valid.copy(purchaseDate = LocalDate.of(2022, 1, 1), saleDate = LocalDate.of(2021, 12, 31))
        assertEquals(VehicleError.SALE_BEFORE_PURCHASE, input.validate(de, isNew = true)[VehicleField.SALE_DATE])
        val sameDay = valid.copy(purchaseDate = LocalDate.of(2022, 1, 1), saleDate = LocalDate.of(2022, 1, 1))
        assertTrue(sameDay.validate(de, isNew = true).isEmpty())
    }

    @Test
    fun dimensionsMustBePositiveAndWithinBounds() {
        assertEquals(VehicleError.NOT_POSITIVE, valid.copy(lengthCm = "0").validate(de, isNew = true)[VehicleField.LENGTH])
        assertEquals(VehicleError.TOO_LARGE, valid.copy(lengthCm = "3001").validate(de, isNew = true)[VehicleField.LENGTH])
        assertTrue(valid.copy(lengthCm = "3000").validate(de, isNew = true).isEmpty())
    }

    @Test
    fun upperBoundsApplyToWeightPowerPressureTanksAndEnergy() {
        assertEquals(VehicleError.TOO_LARGE, valid.copy(grossWeightKg = "100001").validate(de, isNew = true)[VehicleField.GROSS_WEIGHT_KG])
        assertEquals(VehicleError.TOO_LARGE, valid.copy(powerKw = "2001").validate(de, isNew = true)[VehicleField.POWER_KW])
        assertEquals(
            VehicleError.TOO_LARGE,
            valid.copy(tirePressureFrontBar = "15,01").validate(de, isNew = true)[VehicleField.TIRE_PRESSURE_FRONT],
        )
        assertEquals(VehicleError.TOO_LARGE, valid.copy(fuelTankL = "10000,1").validate(de, isNew = true)[VehicleField.FUEL_TANK])
        assertEquals(
            VehicleError.TOO_LARGE,
            valid.copy(batteryCapacityAh = "100001").validate(de, isNew = true)[VehicleField.BATTERY_CAPACITY_AH],
        )
        assertEquals(VehicleError.TOO_LARGE, valid.copy(solarPowerWp = "100001").validate(de, isNew = true)[VehicleField.SOLAR_POWER_WP])
        assertEquals(
            VehicleError.TOO_LARGE,
            valid.copy(purchaseOdometerKm = "10000001").validate(de, isNew = true)[VehicleField.PURCHASE_ODOMETER_KM],
        )
    }

    @Test
    fun negativeAndMalformedNumbersAreInvalid() {
        assertEquals(VehicleError.NEGATIVE_NUMBER, valid.copy(grossWeightKg = "-1").validate(de, isNew = true)[VehicleField.GROSS_WEIGHT_KG])
        assertEquals(VehicleError.INVALID_NUMBER, valid.copy(grossWeightKg = "abc").validate(de, isNew = true)[VehicleField.GROSS_WEIGHT_KG])
    }

    @Test
    fun moneyFieldsFollowExistingAmountRules() {
        assertEquals(VehicleError.INVALID_AMOUNT, valid.copy(purchasePrice = "12km").validate(de, isNew = true)[VehicleField.PURCHASE_PRICE])
        assertEquals(
            VehicleError.AMOUNT_TOO_LARGE,
            valid.copy(purchasePrice = "100000000000.01").validate(de, isNew = true)[VehicleField.PURCHASE_PRICE],
        )
    }

    @Test
    fun lengthIsStoredAsEnteredInCentimetres() {
        assertEquals(636, valid.copy(lengthCm = "636").toVehicle(null, de).lengthCm)
    }

    @Test
    fun tirePressureScalesToMillibar() {
        val vehicle = valid.toVehicle(null, de)
        assertEquals(2800, vehicle.tirePressureFrontMbar)
        assertEquals(3000, vehicle.tirePressureRearMbar)
    }

    @Test
    fun tankVolumeScalesToDeciliters() {
        val vehicle = valid.toVehicle(null, de)
        assertEquals(900, vehicle.fuelTankDl)
        assertEquals(1200, vehicle.freshWaterTankDl)
    }

    @Test
    fun roundTripThroughInputIsLossless() {
        val vehicle = valid.toVehicle(null, de)
        assertEquals(vehicle, vehicle.toInput(de).toVehicle(vehicle, de))
    }

    @Test
    fun maintenanceFieldsRoundTripThroughInput() {
        val input = valid.copy(
            nextInspectionDate = LocalDate.of(2027, 1, 1),
            nextGasCheckDate = LocalDate.of(2027, 2, 1),
            nextLeakTestDate = LocalDate.of(2027, 3, 1),
            lastOilChangeDate = LocalDate.of(2026, 1, 1),
            lastOilChangeOdometerKm = "50000",
        )
        val vehicle = input.toVehicle(null, de).copy(id = 7)
        assertEquals(LocalDate.of(2027, 1, 1), vehicle.nextInspectionDate)
        assertEquals(LocalDate.of(2027, 2, 1), vehicle.nextGasCheckDate)
        assertEquals(LocalDate.of(2027, 3, 1), vehicle.nextLeakTestDate)
        assertEquals(LocalDate.of(2026, 1, 1), vehicle.lastOilChangeDate)
        assertEquals(50_000, vehicle.lastOilChangeOdometerKm)

        val edited = vehicle.toInput(de).copy(name = "Neuer Name").toVehicle(vehicle, de)
        assertEquals(7L, edited.id)
        assertEquals(LocalDate.of(2027, 1, 1), edited.nextInspectionDate)
        assertEquals(LocalDate.of(2027, 2, 1), edited.nextGasCheckDate)
        assertEquals(LocalDate.of(2027, 3, 1), edited.nextLeakTestDate)
        assertEquals(LocalDate.of(2026, 1, 1), edited.lastOilChangeDate)
        assertEquals(50_000, edited.lastOilChangeOdometerKm)
        assertEquals("Neuer Name", edited.name)
    }

    @Test
    fun newVehicleHasNoMaintenanceFieldsWhenNotEntered() {
        val vehicle = valid.toVehicle(null, de)
        assertNull(vehicle.nextInspectionDate)
        assertNull(vehicle.lastOilChangeOdometerKm)
    }

    @Test
    fun lastOilChangeCannotBeInTheFuture() {
        val today = LocalDate.of(2026, 10, 5)
        val future = valid.copy(lastOilChangeDate = today.plusDays(1))
        assertEquals(VehicleError.DATE_IN_FUTURE, future.validate(de, isNew = true, today)[VehicleField.LAST_OIL_CHANGE_DATE])
        assertTrue(valid.copy(lastOilChangeDate = today).validate(de, isNew = true, today).isEmpty())
    }

    @Test
    fun lastOilChangeOdometerFollowsExistingOdometerBounds() {
        assertEquals(
            VehicleError.NEGATIVE_NUMBER,
            valid.copy(lastOilChangeOdometerKm = "-1").validate(de, isNew = true)[VehicleField.LAST_OIL_CHANGE_ODOMETER_KM],
        )
        assertEquals(
            VehicleError.TOO_LARGE,
            valid.copy(lastOilChangeOdometerKm = "10000001").validate(de, isNew = true)[VehicleField.LAST_OIL_CHANGE_ODOMETER_KM],
        )
        assertTrue(valid.copy(lastOilChangeOdometerKm = "50000").validate(de, isNew = true).isEmpty())
    }

    @Test
    fun blankOptionalFieldsBecomeNull() {
        val vehicle = VehicleInput(name = "X").toVehicle(null, de)
        assertNull(vehicle.purchasePrice)
        assertNull(vehicle.lengthCm)
        assertNull(vehicle.powerKw)
        assertEquals("", vehicle.licensePlate)
        assertNull(vehicle.measuredEmptyWeightKg)
        assertEquals("", vehicle.breakdownProvider)
    }

    @Test
    fun measuredEmptyWeightFollowsGrossWeightBounds() {
        assertEquals(
            VehicleError.TOO_LARGE,
            valid.copy(measuredEmptyWeightKg = "100001").validate(de, isNew = true)[VehicleField.MEASURED_EMPTY_WEIGHT_KG],
        )
        assertEquals(
            VehicleError.NEGATIVE_NUMBER,
            valid.copy(measuredEmptyWeightKg = "-1").validate(de, isNew = true)[VehicleField.MEASURED_EMPTY_WEIGHT_KG],
        )
        assertTrue(valid.copy(measuredEmptyWeightKg = "100000").validate(de, isNew = true).isEmpty())
    }

    @Test
    fun remainingPayloadIsGrossMinusMeasuredEmptyWeight() {
        val vehicle = valid.copy(grossWeightKg = "3500", measuredEmptyWeightKg = "3020").toVehicle(null, de)
        assertEquals(480, vehicle.remainingPayloadKg)
    }

    @Test
    fun remainingPayloadIsNegativeWhenOverweight() {
        val vehicle = valid.copy(grossWeightKg = "3500", measuredEmptyWeightKg = "3600").toVehicle(null, de)
        assertEquals(-100, vehicle.remainingPayloadKg)
    }

    @Test
    fun remainingPayloadIsNullWhenEitherWeightIsMissing() {
        val vehicle = valid.copy(grossWeightKg = "3500", measuredEmptyWeightKg = "").toVehicle(null, de)
        assertNull(vehicle.remainingPayloadKg)
    }

    @Test
    fun phoneNumbersAcceptDigitsSpacesAndFormattingCharacters() {
        val input = valid.copy(breakdownPhone = "+49 89 22-22/22 (0)")
        assertTrue(input.validate(de, isNew = true).isEmpty())
    }

    @Test
    fun phoneNumbersRejectLetters() {
        assertEquals(
            VehicleError.INVALID_PHONE,
            valid.copy(breakdownPhone = "call ADAC").validate(de, isNew = true)[VehicleField.BREAKDOWN_PHONE],
        )
    }

    @Test
    fun phoneNumbersRejectExcessiveLength() {
        val tooLong = "1".repeat(MAX_PHONE_LENGTH + 1)
        assertEquals(
            VehicleError.INVALID_PHONE,
            valid.copy(travelProtectionPhone = tooLong).validate(de, isNew = true)[VehicleField.TRAVEL_PROTECTION_PHONE],
        )
    }

    @Test
    fun blankPhoneNumbersAreValid() {
        assertTrue(valid.copy(insurerClaimsPhone = "").validate(de, isNew = true).isEmpty())
    }

    @Test
    fun requiredEnergyTypesDefaultToEmptyAndRoundTripThroughInput() {
        assertEquals(emptySet<EnergyType>(), valid.toVehicle(null, de).requiredEnergyTypes)

        val input = valid.copy(requiredEnergyTypes = setOf(EnergyType.DIESEL, EnergyType.ELECTRICITY))
        val vehicle = input.toVehicle(null, de)
        assertEquals(setOf(EnergyType.DIESEL, EnergyType.ELECTRICITY), vehicle.requiredEnergyTypes)
        assertEquals(vehicle.requiredEnergyTypes, vehicle.toInput(de).requiredEnergyTypes)
    }

    @Test
    fun allowsAnyIsTrueWhenEmptyOrMatching() {
        assertTrue(emptySet<EnergyType>().allowsAny(EnergyType.DIESEL))
        assertTrue(setOf(EnergyType.DIESEL).allowsAny(EnergyType.PETROL, EnergyType.DIESEL))
        assertTrue(setOf(EnergyType.GAS).allowsAny(EnergyType.GAS) && !setOf(EnergyType.GAS).allowsAny(EnergyType.DIESEL))
    }
}
