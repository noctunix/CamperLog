package app.restvolt.camperlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Markenfarbe der App-Familie (auch Logo). Auf hellem Grund zu kontrastarm für Text, daher dort [AzureOnLight]. */
private val Azure = Color(0xFF0099FF)
private val AzureOnLight = Color(0xFF0A63C0)
private val DeepNavy = Color(0xFF0D1B2A)
private val GreyBlue = Color(0xFF55657A)

internal val LightColors = lightColorScheme(
    primary = AzureOnLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E6FF),
    onPrimaryContainer = Color(0xFF00305E),
    secondary = GreyBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F2),
    onSecondaryContainer = DeepNavy,
    // Bernstein für „bald fällig“, damit es sich von Fehlern (rot) und der Markenfarbe abhebt.
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6B8),
    onTertiaryContainer = Color(0xFF3D2A00),
    background = Color(0xFFF4F7FB),
    onBackground = DeepNavy,
    surface = Color(0xFFF4F7FB),
    onSurface = DeepNavy,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFECF2F9),
    surfaceContainerHigh = Color(0xFFE5EDF6),
    surfaceContainerHighest = Color(0xFFDEE7F1),
    onSurfaceVariant = GreyBlue,
    // Mindestens 3:1 zu Karten und Hintergrund, damit Feld- und Segmentrahmen erkennbar sind.
    outline = Color(0xFF6A7B90),
    outlineVariant = Color(0xFFC9D5E3),
    error = Color(0xFFB3261E),
)

/** Dunkles Gegenstück mit der Markenfarbe selbst als Akzent; Karten heben sich leicht vom Hintergrund ab. */
internal val DarkColors = darkColorScheme(
    primary = Azure,
    onPrimary = Color(0xFF001E3C),
    primaryContainer = Color(0xFF004A86),
    onPrimaryContainer = Color(0xFFD3E6FF),
    secondary = Color(0xFFB8C6D8),
    onSecondary = Color(0xFF22303F),
    secondaryContainer = Color(0xFF384757),
    onSecondaryContainer = Color(0xFFDCE6F2),
    tertiary = Color(0xFFF2C46B),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFE6B8),
    background = Color(0xFF0E141B),
    onBackground = Color(0xFFE0E6EE),
    surface = Color(0xFF0E141B),
    onSurface = Color(0xFFE0E6EE),
    surfaceContainerLowest = Color(0xFF172029),
    surfaceContainerLow = Color(0xFF172029),
    surfaceContainer = Color(0xFF1C2631),
    surfaceContainerHigh = Color(0xFF222D39),
    surfaceContainerHighest = Color(0xFF2A3644),
    onSurfaceVariant = Color(0xFFB3C0D0),
    outline = Color(0xFF8593A7),
    outlineVariant = Color(0xFF384757),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

private val baseTypography = Typography()

private val typography = baseTypography.copy(
    displaySmall = baseTypography.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

/** App-Theme in der Markenfarbe Azurblau; folgt standardmäßig dem hellen/dunklen Systemmodus. */
@Composable
fun CamperLogTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
