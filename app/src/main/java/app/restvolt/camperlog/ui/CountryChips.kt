package app.restvolt.camperlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.domain.countryDisplayName
import app.restvolt.camperlog.domain.countryFlagEmoji
import java.util.Locale

/** Schreibgeschützte Chip-Zeile der Länder aus [codes] mit Flaggen-Emoji und lokalisiertem Namen; leer, wenn [codes] leer ist. */
@Composable
fun CountryChipsRow(codes: Set<String>, locale: Locale, modifier: Modifier = Modifier) {
    if (codes.isEmpty()) return
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        codes.sortedBy { countryDisplayName(it, locale) }.forEach { code -> CountryChip(code, locale) }
    }
}

@Composable
internal fun CountryChip(code: String, locale: Locale) {
    val name = countryDisplayName(code, locale)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            "${countryFlagEmoji(code)} $name",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
