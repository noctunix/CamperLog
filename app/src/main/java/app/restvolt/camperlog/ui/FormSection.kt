package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/** Merkt bewusste Klappentscheidungen je [key]; [hasError] öffnet nur vorübergehend und ändert die Präferenz nicht. */
@Composable
fun FormSection(
    key: String,
    title: String,
    summary: String?,
    hasError: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("form_sections", Context.MODE_PRIVATE) }
    var expanded by rememberSaveable(key) { mutableStateOf(preferences.getBoolean(key, false)) }
    CollapsibleSection(
        title = title,
        expanded = expanded || hasError,
        onToggle = {
            expanded = !expanded
            preferences.edit { putBoolean(key, expanded) }
        },
        summary = summary,
        content = content,
    )
}
