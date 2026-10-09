package app.restvolt.camperlog.ui.guide

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuideProgressStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clear() {
        context.getSharedPreferences("guide_progress", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun isCompleted_withoutAnyCheckpoint_isFalse() {
        clear()
        try {
            assertFalse(GuideProgressStore(context).isCompleted("tour-a", version = 1))
        } finally {
            clear()
        }
    }

    @Test
    fun markCompleted_persistsAcrossInstances() {
        clear()
        try {
            GuideProgressStore(context).markCompleted("tour-a", version = 1)

            assertTrue(GuideProgressStore(context).isCompleted("tour-a", version = 1))
        } finally {
            clear()
        }
    }

    @Test
    fun isCompleted_withANewerVersion_isFalseAgain() {
        clear()
        try {
            GuideProgressStore(context).markCompleted("tour-a", version = 1)

            assertFalse(GuideProgressStore(context).isCompleted("tour-a", version = 2))
        } finally {
            clear()
        }
    }

    @Test
    fun markCompleted_doesNotAffectOtherTours() {
        clear()
        try {
            GuideProgressStore(context).markCompleted("tour-a", version = 1)

            assertFalse(GuideProgressStore(context).isCompleted("tour-b", version = 1))
        } finally {
            clear()
        }
    }
}
