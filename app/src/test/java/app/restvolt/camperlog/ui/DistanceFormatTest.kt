package app.restvolt.camperlog.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DistanceFormatTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(qualifiers = "de-rDE")
    fun germanGroupsThousandsWithDot() {
        assertEquals("38.500 km", context.getString(R.string.distance_km, 38_500))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun englishGroupsThousandsWithComma() {
        assertEquals("38,500 km", context.getString(R.string.distance_km, 38_500))
    }
}
