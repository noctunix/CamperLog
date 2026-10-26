package app.restvolt.camperlog.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.restvolt.camperlog.domain.LEVEL_AUDIO_FREQUENCY_BACK_HZ
import app.restvolt.camperlog.domain.LEVEL_AUDIO_FREQUENCY_FRONT_HZ
import app.restvolt.camperlog.domain.LEVEL_AUDIO_FREQUENCY_LEFT_HZ
import app.restvolt.camperlog.domain.LEVEL_AUDIO_FREQUENCY_RIGHT_HZ
import app.restvolt.camperlog.domain.LevelAudioDirection
import app.restvolt.camperlog.domain.LevelAudioFeedback
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val SAMPLE_RATE_HZ = 44_100
private const val BEEP_DURATION_MILLIS = 100
private const val FADE_MILLIS = 12
private const val AMPLITUDE = Short.MAX_VALUE * 0.6

/** Fallback, falls [AudioTrack.getMinBufferSize] einen Fehlercode statt einer gültigen Größe liefert. */
private const val FALLBACK_MIN_BUFFER_SIZE_BYTES = SAMPLE_RATE_HZ * 2

/**
 * Bestimmt die Puffergröße für [AudioTrack]: [AudioTrack.getMinBufferSize] darf nicht unterschritten
 * werden, sonst bleibt der Track unbemerkt uninitialisiert und spielt keinen Ton. [minBufferSizeResult]
 * kann laut Dokumentation auch einen negativen Fehlercode liefern, in dem Fall greift [FALLBACK_MIN_BUFFER_SIZE_BYTES].
 */
internal fun resolveBufferSizeBytes(minBufferSizeResult: Int, desiredBufferSizeBytes: Int): Int {
    val minBufferSizeBytes = if (minBufferSizeResult > 0) minBufferSizeResult else FALLBACK_MIN_BUFFER_SIZE_BYTES
    return maxOf(minBufferSizeBytes, desiredBufferSizeBytes)
}

/**
 * [LevelAudioFeedback] über [AudioTrack] mit synthetisch erzeugten Sinustönen je Richtung, damit
 * kein Audio-Asset benötigt wird. Nutzt `USAGE_ASSISTANCE_SONIFICATION`, damit sich der Ton
 * korrekt zur Systemlautstärke und zu Nicht-stören verhält.
 */
class AndroidLevelAudioPlayer : LevelAudioFeedback {

    private val buffers = LevelAudioDirection.entries.associateWith { direction -> buildBeep(frequencyHzFor(direction)) }
    private val audioTrack = buildAudioTrack(buffers.values.first().size)

    override fun playBeep(direction: LevelAudioDirection) {
        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) return
        val buffer = buffers.getValue(direction)
        audioTrack.pause()
        audioTrack.flush()
        val written = audioTrack.write(buffer, 0, buffer.size)
        if (written < 0) return
        audioTrack.play()
    }

    override fun release() {
        if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack.stop()
        }
        audioTrack.release()
    }

    private fun buildAudioTrack(desiredBufferSizeBytes: Int): AudioTrack {
        val minBufferSizeBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSizeBytes = resolveBufferSizeBytes(minBufferSizeBytes, desiredBufferSizeBytes)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun frequencyHzFor(direction: LevelAudioDirection): Int = when (direction) {
        LevelAudioDirection.FRONT -> LEVEL_AUDIO_FREQUENCY_FRONT_HZ
        LevelAudioDirection.BACK -> LEVEL_AUDIO_FREQUENCY_BACK_HZ
        LevelAudioDirection.LEFT -> LEVEL_AUDIO_FREQUENCY_LEFT_HZ
        LevelAudioDirection.RIGHT -> LEVEL_AUDIO_FREQUENCY_RIGHT_HZ
    }

    /** Erzeugt einen 16-Bit-PCM-Mono-Sinuston mit kurzem Fade-in/-out gegen Klick-Artefakte. */
    private fun buildBeep(frequencyHz: Int): ByteArray {
        val sampleCount = SAMPLE_RATE_HZ * BEEP_DURATION_MILLIS / 1000
        val fadeSamples = SAMPLE_RATE_HZ * FADE_MILLIS / 1000
        val bytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val timeSeconds = i.toDouble() / SAMPLE_RATE_HZ
            val fade = min(1.0, min(i.toDouble() / fadeSamples, (sampleCount - i).toDouble() / fadeSamples))
            val sample = (AMPLITUDE * fade * sin(2.0 * PI * frequencyHz * timeSeconds)).toInt().toShort()
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
