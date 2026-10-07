package app.restvolt.camperlog.ui.attachments

import android.content.Context
import androidx.core.content.edit

/** Einmaliger Hinweis, dass Galeriefotos ohne Standort kommen (siehe [PhotoAttachmentsSection]). */
class AttachmentSettings(context: Context) {
    private val preferences = context.getSharedPreferences("attachments", Context.MODE_PRIVATE)

    var galleryLocationHintShown: Boolean
        get() = preferences.getBoolean(KEY_GALLERY_LOCATION_HINT_SHOWN, false)
        set(value) {
            preferences.edit { putBoolean(KEY_GALLERY_LOCATION_HINT_SHOWN, value) }
        }

    private companion object {
        const val KEY_GALLERY_LOCATION_HINT_SHOWN = "gallery_location_hint_shown"
    }
}
