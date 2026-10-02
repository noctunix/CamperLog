package de.hannes.camperlog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val NavyBlue = Color(0xFF0F2D52)
private val GreyBlue = Color(0xFF5B6B82)

private val colors = lightColorScheme(
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
    outline = Color(0xFF8A99AD),
    outlineVariant = Color(0xFFCCD5E1),
    error = Color(0xFFB3261E),
)

private val baseTypography = Typography()

private val typography = baseTypography.copy(
    displaySmall = baseTypography.displaySmall.copy(fontWeight = FontWeight.Bold, color = NavyBlue),
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

/** Helles Theme der App mit dunkelblauer Primärfarbe. */
@Composable
fun CamperLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
