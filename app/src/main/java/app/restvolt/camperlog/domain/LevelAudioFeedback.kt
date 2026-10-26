package app.restvolt.camperlog.domain

/**
 * Akustische Rückmeldung der Wasserwaage über kurze Beeps und einen Dauerton bei „eben", siehe
 * [app.restvolt.camperlog.data.AndroidLevelAudioPlayer].
 */
interface LevelAudioFeedback {

    /** Spielt den Beep für [direction]; ein noch laufender Ton wird dabei gestoppt statt überlagert. */
    fun playBeep(direction: LevelAudioDirection)

    /** Startet den Dauerton für „eben"; ein wiederholter Aufruf, während er schon läuft, ist ein No-op. */
    fun startContinuousTone()

    /** Stoppt den Dauerton; der zugrunde liegende Audiotrack bleibt für einen erneuten Start reserviert. */
    fun stopContinuousTone()

    /** Gibt die Audioressourcen frei; danach dürfen [playBeep], [startContinuousTone] und [stopContinuousTone] nicht mehr aufgerufen werden. */
    fun release()
}
