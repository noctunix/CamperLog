package app.restvolt.camperlog.domain.guide

/** Lebenszyklus-Phase einer geführten Tour. */
enum class GuidePhase {

    /** Keine Tour läuft. */
    IDLE,

    /** Der aktuelle Schritt ist bereit für "Weiter". */
    ACTIVE,

    /** Die Tour ist unterbrochen; die Barriere verschwindet, bis [ACTIVE]/[BLOCKED] wiederhergestellt wird. */
    PAUSED,

    /** Der aktuelle Schritt wartet auf eine Aktion oder Bedingung, bevor "Weiter" erlaubt ist. */
    BLOCKED,

    /** Die Tour ist beendet, regulär oder durch Abbruch. */
    ENDED,
}
