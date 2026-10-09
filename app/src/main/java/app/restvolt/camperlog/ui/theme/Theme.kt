package app.restvolt.camperlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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

/** Herzrot für den „würde wiederkommen“-Favoriten; fest statt themenabhängig, gut sichtbar auf hellem und dunklem Grund. */
val HeartRed = Color(0xFFE03C4D)

/** Primär-Rollen (inkl. Container) einer [AccentColor] für einen Hell- oder Dunkelmodus. */
private data class PrimaryRoles(val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color)

// Bernstein für „bald fällig“, bewusst unabhängig von der Akzentfarbe (siehe KDoc AccentColor).
private val TertiaryLight = PrimaryRoles(Color(0xFF7A5900), Color.White, Color(0xFFFFE6B8), Color(0xFF3D2A00))
private val TertiaryDark = PrimaryRoles(Color(0xFFF2C46B), Color(0xFF402D00), Color(0xFF5C4300), Color(0xFFFFE6B8))

private fun buildLightColors(primary: PrimaryRoles) = lightColorScheme(
    primary = primary.primary,
    onPrimary = primary.onPrimary,
    primaryContainer = primary.primaryContainer,
    onPrimaryContainer = primary.onPrimaryContainer,
    secondary = GreyBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F2),
    onSecondaryContainer = DeepNavy,
    tertiary = TertiaryLight.primary,
    onTertiary = TertiaryLight.onPrimary,
    tertiaryContainer = TertiaryLight.primaryContainer,
    onTertiaryContainer = TertiaryLight.onPrimaryContainer,
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

private fun buildDarkColors(primary: PrimaryRoles) = darkColorScheme(
    primary = primary.primary,
    onPrimary = primary.onPrimary,
    primaryContainer = primary.primaryContainer,
    onPrimaryContainer = primary.onPrimaryContainer,
    secondary = Color(0xFFB8C6D8),
    onSecondary = Color(0xFF22303F),
    secondaryContainer = Color(0xFF384757),
    onSecondaryContainer = Color(0xFFDCE6F2),
    tertiary = TertiaryDark.primary,
    onTertiary = TertiaryDark.onPrimary,
    tertiaryContainer = TertiaryDark.primaryContainer,
    onTertiaryContainer = TertiaryDark.onPrimaryContainer,
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

/**
 * Feste Auswahl an Akzentfarben für die Primär-Rolle des Themes (keine freie Farbwahl); jede
 * Option ist in Hell- und Dunkelmodus auf WCAG-AA-Kontrast geprüft. Die Tertiär-Rolle bleibt bei
 * jeder Akzentfarbe das Bernstein aus [TertiaryLight]/[TertiaryDark], da sie den Status „bald
 * fällig“ trägt und sich von Fehlern (Rot) und jeder Akzentfarbe abheben muss.
 */
enum class AccentColor(lightPrimary: PrimaryRoles, darkPrimary: PrimaryRoles) {
    AZURE(
        PrimaryRoles(AzureOnLight, Color.White, Color(0xFFD3E6FF), Color(0xFF00305E)),
        PrimaryRoles(Azure, Color(0xFF001E3C), Color(0xFF004A86), Color(0xFFD3E6FF)),
    ),
    FOREST(
        PrimaryRoles(Color(0xFF0B8734), Color.White, Color(0xFFD7F4E1), Color(0xFF0D4A21)),
        PrimaryRoles(Color(0xFF0DA640), Color(0xFF0A2E16), Color(0xFF1E6736), Color(0xFFD7F4E1)),
    ),
    TEAL(
        PrimaryRoles(Color(0xFF0C8092), Color.White, Color(0xFFD7F0F4), Color(0xFF0D424A)),
        PrimaryRoles(Color(0xFF0E9BB0), Color(0xFF0A292E), Color(0xFF1E5D67), Color(0xFFD7F0F4)),
    ),
    PLUM(
        PrimaryRoles(Color(0xFF9311D4), Color.White, Color(0xFFEAD7F4), Color(0xFF350D4A)),
        PrimaryRoles(Color(0xFFB94AF0), Color(0xFF220A2E), Color(0xFF4E1E67), Color(0xFFEAD7F4)),
    ),
    BERRY(
        PrimaryRoles(Color(0xFFD4116C), Color.White, Color(0xFFF4D7E5), Color(0xFF4A0D29)),
        PrimaryRoles(Color(0xFFEE3088), Color(0xFF2E0A1B), Color(0xFF671E40), Color(0xFFF4D7E5)),
    ),
    GRAPHITE(
        PrimaryRoles(Color(0xFF5A6A8C), Color.White, Color(0xFFE1E4EA), Color(0xFF212836)),
        PrimaryRoles(Color(0xFF7084AA), Color(0xFF161A22), Color(0xFF353E50), Color(0xFFE1E4EA)),
    ),
    ;

    internal val light: ColorScheme = buildLightColors(lightPrimary)
    internal val dark: ColorScheme = buildDarkColors(darkPrimary)

    /** Repräsentative Farbe für die Auswahl-Kreise in den Einstellungen, unabhängig vom Theme-Modus. */
    internal val swatch: Color = darkPrimary.primary
}

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

/** App-Theme mit wählbarer Akzentfarbe; folgt standardmäßig dem hellen/dunklen Systemmodus. */
@Composable
fun CamperLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.AZURE,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) accentColor.dark else accentColor.light,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
