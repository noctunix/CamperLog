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

private val NavyBlue = Color(0xFF0F2D52)
private val GreyBlue = Color(0xFF5B6B82)

internal val LightColors = lightColorScheme(
    primary = NavyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3F5),
    onPrimaryContainer = NavyBlue,
    secondary = GreyBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE5F0),
    onSecondaryContainer = NavyBlue,
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF14202E),
    surface = Color(0xFFF4F6FA),
    onSurface = Color(0xFF14202E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDF1F7),
    surfaceContainerHigh = Color(0xFFE7ECF4),
    surfaceContainerHighest = Color(0xFFE1E7F0),
    onSurfaceVariant = GreyBlue,
    // Mindestens 3:1 zu Karten und Hintergrund, damit Feld- und Segmentrahmen erkennbar sind.
    outline = Color(0xFF6E7D94),
    outlineVariant = Color(0xFFCCD5E1),
    error = Color(0xFFB3261E),
)

/** Dunkles Gegenstück: helles Blau als Akzent, Karten heben sich leicht vom Hintergrund ab. */
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7F0),
    onPrimary = NavyBlue,
    primaryContainer = Color(0xFF24446E),
    onPrimaryContainer = Color(0xFFD6E3F5),
    secondary = Color(0xFFBCC7D9),
    onSecondary = Color(0xFF263142),
    secondaryContainer = Color(0xFF3C4759),
    onSecondaryContainer = Color(0xFFDDE5F0),
    background = Color(0xFF10161F),
    onBackground = Color(0xFFE1E6EE),
    surface = Color(0xFF10161F),
    onSurface = Color(0xFFE1E6EE),
    surfaceContainerLowest = Color(0xFF19212D),
    surfaceContainerLow = Color(0xFF19212D),
    surfaceContainer = Color(0xFF1E2734),
    surfaceContainerHigh = Color(0xFF242E3C),
    surfaceContainerHighest = Color(0xFF2C3746),
    onSurfaceVariant = Color(0xFFB4C0D0),
    outline = Color(0xFF8794A8),
    outlineVariant = Color(0xFF3C4759),
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

/** App-Theme mit dunkelblauer Primärfarbe; folgt standardmäßig dem hellen/dunklen Systemmodus. */
@Composable
fun CamperLogTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
