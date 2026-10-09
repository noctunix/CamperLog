package app.restvolt.camperlog.ui.guide

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.domain.guide.GuideCheckpoint
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** Persistiert pro Tour, ob sie bereits abgeschlossen wurde; ein Versionswechsel zeigt sie erneut. */
class GuideProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("guide_progress", Context.MODE_PRIVATE)

    /** `true`, wenn [tourId] genau in [version] schon abgeschlossen wurde. */
    fun isCompleted(tourId: String, version: Int): Boolean {
        val checkpoint = load(tourId) ?: return false
        return checkpoint.version == version && checkpoint.completed
    }

    /** Markiert [tourId] in [version] als abgeschlossen. */
    fun markCompleted(tourId: String, version: Int) {
        val checkpoint = GuideCheckpoint(tourId = tourId, version = version, completed = true)
        preferences.edit { putString(tourId, json.encodeToString(GuideCheckpoint.serializer(), checkpoint)) }
    }

    private fun load(tourId: String): GuideCheckpoint? {
        val raw = preferences.getString(tourId, null) ?: return null
        return runCatching { json.decodeFromString(GuideCheckpoint.serializer(), raw) }.getOrNull()
    }
}
