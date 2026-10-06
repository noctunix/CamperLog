package app.restvolt.camperlog.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.CompassDirection
import app.restvolt.camperlog.domain.WeatherCaptureController
import app.restvolt.camperlog.domain.WeatherCaptureState
import app.restvolt.camperlog.domain.WeatherSnapshot
import app.restvolt.camperlog.domain.compassDirection
import app.restvolt.camperlog.domain.formatObservedTime
import app.restvolt.camperlog.domain.weatherCondition
import app.restvolt.camperlog.share.tryStart

/**
 * Nicht erfolgreiche Zustände der Wetterabfrage (6.8), ohne [WeatherCaptureState.Success]: der
 * Aufrufer übernimmt einen Treffer sofort ins Formular und zeigt ihn selbst mit [WeatherSummary]
 * an, genau wie [LocationCaptureSection] es mit einem GPS-Fix macht.
 */
@Composable
fun WeatherFetchRow(controller: WeatherCaptureController, label: String, onFetch: () -> Unit, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    when (state) {
        WeatherCaptureState.Ready -> {
            FilledTonalButton(onClick = onFetch, modifier = modifier) {
                Icon(painterResource(R.drawable.ic_partly_cloudy_day), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        WeatherCaptureState.Loading -> WeatherLoadingRow(modifier)
        WeatherCaptureState.Offline -> WeatherErrorRow(stringResource(R.string.weather_offline_body), onFetch, modifier)
        WeatherCaptureState.ServiceUnavailable -> WeatherErrorRow(stringResource(R.string.weather_service_unavailable_body), onFetch, modifier)
        is WeatherCaptureState.Success -> Unit
    }
}

/** Zeile mit Refresh/Entfernen (6.8 Erfolgszustand) oder, während eine erneute Abfrage läuft, deren Zustand. */
@Composable
fun WeatherRefreshRow(controller: WeatherCaptureController, onRefresh: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    when (state) {
        WeatherCaptureState.Ready -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.weather_action_refresh)) }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.weather_action_remove)) }
        }
        WeatherCaptureState.Loading -> WeatherLoadingRow(modifier)
        WeatherCaptureState.Offline -> WeatherErrorRow(stringResource(R.string.weather_offline_body), onRefresh, modifier)
        WeatherCaptureState.ServiceUnavailable -> WeatherErrorRow(stringResource(R.string.weather_service_unavailable_body), onRefresh, modifier)
        is WeatherCaptureState.Success -> Unit
    }
}

@Composable
private fun WeatherLoadingRow(modifier: Modifier = Modifier) {
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(stringResource(R.string.weather_loading_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WeatherErrorRow(body: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(body, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.weather_action_retry)) }
    }
}

private val CompassDirection.labelRes: Int
    get() = when (this) {
        CompassDirection.N -> R.string.wind_direction_n
        CompassDirection.NE -> R.string.wind_direction_ne
        CompassDirection.E -> R.string.wind_direction_e
        CompassDirection.SE -> R.string.wind_direction_se
        CompassDirection.S -> R.string.wind_direction_s
        CompassDirection.SW -> R.string.wind_direction_sw
        CompassDirection.W -> R.string.wind_direction_w
        CompassDirection.NW -> R.string.wind_direction_nw
    }

/** Temperatur, Wetterlage, Wind und Zeitpunkt eines Schnappschusses (6.6, 6.8), mit Open-Meteo-Attribution. */
@Composable
fun WeatherSummary(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val condition = weatherCondition(snapshot.weatherCode)
    val openMeteoUri = stringResource(R.string.about_open_meteo_uri)
    val temperature = Math.round(snapshot.temperatureDeciC / 10.0).toInt()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(condition.iconRes), contentDescription = null)
            Text(
                "${stringResource(R.string.weather_temperature_c, temperature)} · ${stringResource(condition.labelRes)}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(weatherWindText(snapshot), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatObservedTime(snapshot.observedAt), style = MaterialTheme.typography.bodySmall)
            Text("·", style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = { context.tryStart(Intent(Intent.ACTION_VIEW, openMeteoUri.toUri())) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(R.string.weather_attribution), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun weatherWindText(snapshot: WeatherSnapshot): String {
    val direction = snapshot.windDirectionDeg?.let { stringResource(compassDirection(it).labelRes) }
    val gust = snapshot.gustKmh
    return when {
        direction != null && gust != null -> stringResource(R.string.weather_wind_full, snapshot.windKmh, direction, gust)
        direction != null -> stringResource(R.string.weather_wind_no_gust, snapshot.windKmh, direction)
        gust != null -> stringResource(R.string.weather_wind_no_direction_full, snapshot.windKmh, gust)
        else -> stringResource(R.string.weather_wind_plain, snapshot.windKmh)
    }
}
