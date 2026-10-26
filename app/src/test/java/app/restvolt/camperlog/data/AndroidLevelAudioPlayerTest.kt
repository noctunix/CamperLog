package app.restvolt.camperlog.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidLevelAudioPlayerTest {

    @Test
    fun resolveBufferSizeBytes_usesDesiredSizeWhenAboveMinimum() {
        assertEquals(8820, resolveBufferSizeBytes(minBufferSizeResult = 4096, desiredBufferSizeBytes = 8820))
    }

    @Test
    fun resolveBufferSizeBytes_usesMinimumWhenDesiredSizeIsTooSmall() {
        assertEquals(16_384, resolveBufferSizeBytes(minBufferSizeResult = 16_384, desiredBufferSizeBytes = 8820))
    }

    @Test
    fun resolveBufferSizeBytes_fallsBackToASaneMinimumOnErrorCode() {
        val result = resolveBufferSizeBytes(minBufferSizeResult = -2, desiredBufferSizeBytes = 8820)

        assertEquals(88_200, result)
    }
}
