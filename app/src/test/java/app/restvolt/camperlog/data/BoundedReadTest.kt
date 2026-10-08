package app.restvolt.camperlog.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedReadTest {

    @Test
    fun readBytesAtMost_exactlyAtLimit_returnsAllBytes() {
        val bytes = ByteArray(20_000) { it.toByte() }

        val read = ByteArrayInputStream(bytes).readBytesAtMost(bytes.size)

        assertEquals(bytes.toList(), read.toList())
    }

    @Test(expected = ResponseTooLargeException::class)
    fun readBytesAtMost_oneByteOverLimit_throws() {
        ByteArrayInputStream(ByteArray(20_001)).readBytesAtMost(20_000)
    }

    @Test
    fun readTextAtMost_decodesUtf8() {
        assertEquals("Ålesund", ByteArrayInputStream("Ålesund".toByteArray()).readTextAtMost(100))
    }
}
