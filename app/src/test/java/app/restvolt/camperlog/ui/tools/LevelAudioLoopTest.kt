package app.restvolt.camperlog.ui.tools

import app.restvolt.camperlog.domain.FakeLevelAudioFeedback
import app.restvolt.camperlog.domain.LevelAudioCue
import app.restvolt.camperlog.domain.LevelAudioDirection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LevelAudioLoopTest {

    @Test
    fun startsTheContinuousToneForLevelAndDoesNotRestartItOnSensorJitter() = runTest(UnconfinedTestDispatcher()) {
        var cue: LevelAudioCue? = LevelAudioCue.Level
        val audioPlayer = FakeLevelAudioFeedback()
        backgroundScope.launch { runLevelAudioLoop({ cue }, audioPlayer) }

        assertEquals(1, audioPlayer.toneStartCount)
        assertEquals(0, audioPlayer.toneStopCount)

        // Minimales Sensor-Zittern knapp um die Toleranzgrenze bleibt weiterhin "eben".
        repeat(5) {
            advanceTimeBy(LEVEL_AUDIO_POLL_INTERVAL_MILLIS)
            cue = LevelAudioCue.Level
        }

        assertEquals(1, audioPlayer.toneStartCount)
        assertEquals(0, audioPlayer.toneStopCount)
    }

    @Test
    fun switchesFromBeepToToneAndBackAgain() = runTest(UnconfinedTestDispatcher()) {
        var cue: LevelAudioCue? = LevelAudioCue.Beep(LevelAudioDirection.BACK, 50L)
        val audioPlayer = FakeLevelAudioFeedback()
        backgroundScope.launch { runLevelAudioLoop({ cue }, audioPlayer) }

        assertEquals(listOf(LevelAudioDirection.BACK), audioPlayer.beepCalls)
        assertEquals(0, audioPlayer.toneStartCount)

        cue = LevelAudioCue.Level
        advanceTimeBy(60L)

        assertEquals(1, audioPlayer.toneStartCount)
        assertEquals(0, audioPlayer.toneStopCount)

        cue = LevelAudioCue.Beep(LevelAudioDirection.LEFT, 50L)
        advanceTimeBy(LEVEL_AUDIO_POLL_INTERVAL_MILLIS)

        assertEquals(1, audioPlayer.toneStopCount)
        assertEquals(LevelAudioDirection.LEFT, audioPlayer.beepCalls.last())
    }

    @Test
    fun stopsTheContinuousToneWhenTheCoroutineIsCancelled() = runTest(UnconfinedTestDispatcher()) {
        val cue: LevelAudioCue? = LevelAudioCue.Level
        val audioPlayer = FakeLevelAudioFeedback()
        val job = backgroundScope.launch { runLevelAudioLoop({ cue }, audioPlayer) }

        assertEquals(1, audioPlayer.toneStartCount)

        job.cancelAndJoin()

        assertEquals(1, audioPlayer.toneStopCount)
    }
}
