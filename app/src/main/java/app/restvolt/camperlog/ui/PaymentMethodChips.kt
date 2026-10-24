package app.restvolt.camperlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Vorschläge aus der Zahlungsarten-Liste der Einstellungen; ein erneuter Tipp auf die gewählte Zahlungsart leert das Feld. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentMethodChips(suggestions: List<String>, selected: String, onSelect: (String) -> Unit) {
    if (suggestions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { name ->
            val isSelected = selected.trim() == name
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) "" else name) },
                label = { Text(name) },
            )
        }
    }
}
