package app.restvolt.camperlog.share

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CsvVocabularyTest {

    @Test
    fun germanDeviceLanguageSelectsGermanVocabulary() {
        assertEquals(CsvVocabulary.GERMAN, CsvVocabulary.fromLocale(Locale.GERMANY))
    }

    @Test
    fun englishDeviceLanguageSelectsEnglishVocabulary() {
        assertEquals(CsvVocabulary.ENGLISH, CsvVocabulary.fromLocale(Locale.US))
    }

    @Test
    fun unsupportedDeviceLanguageFallsBackToEnglishVocabulary() {
        assertEquals(CsvVocabulary.ENGLISH, CsvVocabulary.fromLocale(Locale.FRENCH))
    }
}
