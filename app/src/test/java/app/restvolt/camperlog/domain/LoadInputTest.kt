package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

class LoadInputTest {

    private val de = Locale.GERMANY

    private val validItem = LoadItemInput(name = "Fahrradträger", category = LoadCategory.FIXTURE, massKg = "15", quantity = "1", positionCm = "640")

    @Test
    fun validItemInputHasNoErrors() {
        assertTrue(validItem.validate(de).isEmpty())
    }

    @Test
    fun itemNameIsRequiredAndLimited() {
        assertEquals(LoadItemError.REQUIRED, validItem.copy(name = "").validate(de)[LoadItemField.NAME])
        assertEquals(LoadItemError.TOO_LONG, validItem.copy(name = "x".repeat(MAX_LOAD_ITEM_NAME_LENGTH + 1)).validate(de)[LoadItemField.NAME])
    }

    @Test
    fun itemMassMustBePositiveAndWithinBounds() {
        assertEquals(LoadItemError.NOT_POSITIVE, validItem.copy(massKg = "0").validate(de)[LoadItemField.MASS])
        assertEquals(LoadItemError.TOO_LARGE, validItem.copy(massKg = "10001").validate(de)[LoadItemField.MASS])
        assertEquals(LoadItemError.INVALID_NUMBER, validItem.copy(massKg = "abc").validate(de)[LoadItemField.MASS])
        assertTrue(validItem.copy(massKg = "10000").validate(de).isEmpty())
    }

    @Test
    fun itemQuantityMustBeInRange() {
        assertEquals(LoadItemError.NOT_POSITIVE, validItem.copy(quantity = "0").validate(de)[LoadItemField.QUANTITY])
        assertEquals(LoadItemError.TOO_LARGE, validItem.copy(quantity = "1000").validate(de)[LoadItemField.QUANTITY])
        assertTrue(validItem.copy(quantity = "999").validate(de).isEmpty())
    }

    @Test
    fun itemPositionIsOptionalButBounded() {
        assertTrue(validItem.copy(positionCm = "").validate(de).isEmpty())
        assertEquals(LoadItemError.OUT_OF_RANGE, validItem.copy(positionCm = "-501").validate(de)[LoadItemField.POSITION])
        assertEquals(LoadItemError.OUT_OF_RANGE, validItem.copy(positionCm = "2501").validate(de)[LoadItemField.POSITION])
    }

    @Test
    fun massRoundTripsThroughInputInGrams() {
        val item = validItem.copy(massKg = "15,5").toItem(null, vehicleId = 1, locale = de)
        assertEquals(15_500L, item.massGrams)
        assertEquals("15,5", item.toInput(de).massKg)
    }

    @Test
    fun defaultInWeighingIsTrueOnlyForFixtures() {
        assertEquals(true, defaultInWeighing(LoadCategory.FIXTURE))
        assertEquals(false, defaultInWeighing(LoadCategory.EQUIPMENT))
        assertEquals(false, defaultInWeighing(LoadCategory.SUPPLIES))
        assertEquals(false, defaultInWeighing(LoadCategory.PERSON))
        assertEquals(false, defaultInWeighing(LoadCategory.OTHER))
    }

    @Test
    fun itemRoundTripsThroughInput() {
        val item = validItem.toItem(null, vehicleId = 1, locale = de).copy(id = 3, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val edited = item.toInput(de).toItem(item, vehicleId = 1, locale = de)
        assertEquals(item, edited)
    }

    private val validProfile = LoadProfileInput(name = "Langreise")

    @Test
    fun profileNameIsRequiredAndLimited() {
        assertEquals(LoadProfileError.REQUIRED, validProfile.copy(name = "").validate()[LoadProfileField.NAME])
        assertEquals(
            LoadProfileError.TOO_LONG,
            validProfile.copy(name = "x".repeat(MAX_LOAD_PROFILE_NAME_LENGTH + 1)).validate()[LoadProfileField.NAME],
        )
        assertTrue(validProfile.validate().isEmpty())
    }

    @Test
    fun profileRoundTripsThroughInput() {
        val profile = validProfile.toProfile(null, vehicleId = 1)
        assertEquals(profile, profile.toInput().toProfile(profile, vehicleId = 1))
    }

    private val validTankSettings = LoadTankSettingsInput(positionCm = "200", densityGramsPerLitre = "1000", weighingLevelL = "40")

    @Test
    fun tankSettingsDensityMustBeInRange() {
        assertEquals(
            LoadTankSettingsError.OUT_OF_RANGE,
            validTankSettings.copy(densityGramsPerLitre = "499").validate(capacityDl = 1000, locale = de)[LoadTankSettingsField.DENSITY],
        )
        assertEquals(
            LoadTankSettingsError.OUT_OF_RANGE,
            validTankSettings.copy(densityGramsPerLitre = "1501").validate(capacityDl = 1000, locale = de)[LoadTankSettingsField.DENSITY],
        )
    }

    @Test
    fun tankSettingsWeighingLevelMustNotExceedCapacity() {
        assertEquals(
            LoadTankSettingsError.TOO_LARGE,
            validTankSettings.copy(weighingLevelL = "101").validate(capacityDl = 1000, locale = de)[LoadTankSettingsField.WEIGHING_LEVEL],
        )
        assertTrue(validTankSettings.validate(capacityDl = 1000, locale = de).isEmpty())
    }

    @Test
    fun tankSettingsRoundTripThroughInput() {
        val settings = validTankSettings.toSettings(vehicleId = 1, kind = LoadTankKind.FRESH_WATER, locale = de)
        assertEquals(1, settings.vehicleId)
        assertEquals(200, settings.positionCm)
        assertEquals(1000, settings.densityGramsPerLitre)
        assertEquals(400, settings.weighingLevelDl)
        assertEquals(settings, settings.toInput(de).toSettings(vehicleId = 1, kind = LoadTankKind.FRESH_WATER, locale = de))
    }
}
