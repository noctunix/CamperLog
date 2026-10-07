package app.restvolt.camperlog.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ExpiringVignette
import app.restvolt.camperlog.domain.countryDisplayName
import app.restvolt.camperlog.domain.countryFlagEmoji
import app.restvolt.camperlog.domain.formatDate
import java.util.Locale

/** Warnkarte für Vignetten der Tour, die vor dem Tourende ablaufen (siehe [ExpiringVignette]); leer, wenn [warnings] leer ist. */
@Composable
fun VignetteWarningsCard(warnings: List<ExpiringVignette>, locale: Locale, onOpenStation: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            warnings.forEach { warning -> VignetteWarningRow(warning, locale, onClick = { onOpenStation(warning.stationId) }) }
        }
    }
}

@Composable
private fun VignetteWarningRow(warning: ExpiringVignette, locale: Locale, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.tours_open_details)
    val date = formatDate(warning.validUntil, locale)
    val text = warning.countryCode?.let { code ->
        stringResource(R.string.vignette_warning_country, "${countryFlagEmoji(code)} ${countryDisplayName(code, locale)}", date)
    } ?: stringResource(R.string.vignette_warning, date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
