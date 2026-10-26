package app.restvolt.camperlog.domain

/**
 * Akustische Rückmeldung der Wasserwaage über kurze Beeps, siehe
 * [app.restvolt.camperlog.data.AndroidLevelAudioPlayer].
 */
interface LevelAudioFeedback {

    /** Spielt den Beep für [direction]; ein noch laufender Ton wird dabei gestoppt statt überlagert. */
    fun playBeep(direction: LevelAudioDirection)

    /** Gibt die Audioressourcen frei; danach darf [playBeep] nicht mehr aufgerufen werden. */
    fun release()
}
