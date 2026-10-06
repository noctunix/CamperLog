package app.restvolt.camperlog.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verhindert ein Zurückfallen auf die vor Phase F gültigen Datenschutzaussagen (9): Seit das
 * Wetter-&-Karte-Feature die INTERNET-Berechtigung einführt, sind "kein Netzwerkzugriff" und
 * "keine Berechtigungen" nicht mehr wahr, egal ob ein- oder ausgeschaltet.
 */
class PrivacyStringsTest {

    private val en = File("src/main/res/values/strings.xml").readText()
    private val de = File("src/main/res/values-de/strings.xml").readText()

    @Test
    fun noStaleNoNetworkOrNoPermissionsClaimsRemainInEnglish() {
        assertFalse("EN strings still claim no network access", en.contains("no network access", ignoreCase = true))
        assertFalse("EN strings still claim CamperLog needs no permissions", en.contains("needs no permissions", ignoreCase = true))
        assertFalse(en.contains("about_privacy_bullet_no_network"))
        assertFalse(en.contains("about_privacy_bullet_no_permissions"))
    }

    @Test
    fun noStaleNoNetworkOrNoPermissionsClaimsRemainInGerman() {
        assertFalse("DE strings still claim kein Netzwerkzugriff", de.contains("kein Netzwerkzugriff", ignoreCase = true))
        assertFalse("DE strings still claim CamperLog braucht keine Berechtigungen", de.contains("braucht CamperLog keine Berechtigungen", ignoreCase = true))
    }

    @Test
    fun aboutClaimNoLongerStatesPlainOfflineOnly() {
        assertTrue(en.contains("works offline"))
        assertTrue(de.contains("offline nutzbar"))
    }

    @Test
    fun privacyBulletsMentionTheInternetPermissionHonestly() {
        assertTrue(en.contains("about_privacy_bullet_weather_map"))
        assertTrue(en.contains("internet permission"))
        assertTrue(de.contains("Internet-Berechtigung"))
    }

    /** ROADMAP 1.8.0: die Kartenanteile wurden wiederhergestellt, nachdem die Karte fehlte (phase F). */
    @Test
    fun weatherSwitchAndPrivacyBulletMentionTheMap() {
        assertTrue(en.contains("Weather &amp; map (internet)"))
        assertTrue(de.contains("Wetter &amp; Karte (Internet)"))
        assertTrue(en.contains("The map loads the area you are looking at from OpenStreetMap."))
        assertTrue(de.contains("Die Karte lädt den angezeigten Ausschnitt von OpenStreetMap."))
    }

    @Test
    fun optInWordingCoversBothSwitches() {
        assertTrue(en.contains("Optional: location, weather, map"))
        assertTrue(de.contains("Optional: Standort, Wetter, Karte"))
    }

    @Test
    fun dataSourcesAttributionIsPresentInBothLanguages() {
        assertTrue(en.contains("Open-Meteo.com (CC BY 4.0)"))
        assertTrue(en.contains("OpenStreetMap contributors (ODbL)"))
        assertTrue(de.contains("Open-Meteo.com (CC BY 4.0)"))
        assertTrue(de.contains("OpenStreetMap-Mitwirkende (ODbL)"))
    }

    /** ROADMAP 1.9.0: Benachrichtigungen sind jetzt optional möglich - die alte Aussage wäre falsch. */
    @Test
    fun noStaleNoNotificationsClaimRemains() {
        assertFalse(en.contains("does not send notifications", ignoreCase = true))
        assertFalse(en.contains("No notifications.", ignoreCase = false))
        assertFalse(de.contains("versendet keine Benachrichtigungen", ignoreCase = true))
        assertFalse(de.contains("Keine Benachrichtigungen.", ignoreCase = false))
    }

    @Test
    fun readmeAndStoreTextsDescribeNotificationsAsOptional() {
        val readme = File("../README.md").readText()
        val fullDescriptionEn = File("../fastlane/metadata/android/en-US/full_description.txt").readText()
        val fullDescriptionDe = File("../fastlane/metadata/android/de-DE/full_description.txt").readText()

        assertTrue(readme.contains("Notifications are optional"))
        assertTrue(readme.contains("boot-completed permission"))
        assertTrue(fullDescriptionEn.contains("Notifications\" run a daily background check"))
        assertTrue(fullDescriptionDe.contains("Benachrichtigungen“, ein täglicher"))
    }
}
