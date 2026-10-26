package app.restvolt.camperlog.domain

/** [LevelAudioFeedback]-Fake für Tests ohne echte Audioausgabe; zeichnet alle Aufrufe auf. */
class FakeLevelAudioFeedback : LevelAudioFeedback {

    val beepCalls = mutableListOf<LevelAudioDirection>()
    var toneStartCount = 0
        private set
    var toneStopCount = 0
        private set
    var released = false
        private set

    override fun playBeep(direction: LevelAudioDirection) {
        beepCalls.add(direction)
    }

    override fun startContinuousTone() {
        toneStartCount++
    }

    override fun stopContinuousTone() {
        toneStopCount++
    }

    override fun release() {
        released = true
    }
}
