package mg.baiboly.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.R
import mg.baiboly.app.ThemeMode

// ---------------------------------------------------------------- type

/** Scripture and body text: Gentium Book Plus (SIL OFL), readable, with full accent and Greek coverage. */
val TextSerif = FontFamily(
    Font(R.font.gentium_regular, FontWeight.Normal),
    Font(R.font.gentium_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.gentium_bold, FontWeight.Bold),
)

/** Titles and labels: Cormorant SC (SIL OFL), engraved small capitals. */
val TitleSerif = FontFamily(
    Font(R.font.cormorant_sc_medium, FontWeight.Medium),
    Font(R.font.cormorant_sc_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_sc_bold, FontWeight.Bold),
)

private val typography = Typography().let { t ->
    fun TextStyle.body() = copy(fontFamily = TextSerif)
    fun TextStyle.title(weight: FontWeight = FontWeight.SemiBold, spacing: Float = 0.6f) =
        copy(fontFamily = TitleSerif, fontWeight = weight, letterSpacing = spacing.sp)
    t.copy(
        displayLarge = t.displayLarge.title(), displayMedium = t.displayMedium.title(), displaySmall = t.displaySmall.title(),
        headlineLarge = t.headlineLarge.title(), headlineMedium = t.headlineMedium.title(), headlineSmall = t.headlineSmall.title(),
        titleLarge = t.titleLarge.title().copy(fontSize = 24.sp),
        titleMedium = t.titleMedium.title(spacing = 0.8f).copy(fontSize = 19.sp),
        titleSmall = t.titleSmall.title(spacing = 0.8f).copy(fontSize = 16.sp),
        labelLarge = t.labelLarge.title(FontWeight.Bold, 1.0f).copy(fontSize = 16.sp),
        labelMedium = t.labelMedium.title(FontWeight.Bold, 1.0f).copy(fontSize = 14.sp),
        labelSmall = t.labelSmall.title(FontWeight.Bold, 1.0f).copy(fontSize = 12.sp),
        bodyLarge = t.bodyLarge.body(), bodyMedium = t.bodyMedium.body(), bodySmall = t.bodySmall.body(),
    )
}

// ---------------------------------------------------------------- colour

/** Extra colours the Material scheme has no slot for. */
@Immutable
data class Ornament(
    /** Gilding: hairlines, verse numbers, ornaments. */
    val gold: Color,
    /** Gold wash behind the passage being read. */
    val focusWash: Color,
    /** Rubric red for initials and the current item. */
    val rubric: Color,
)

val LocalOrnament = staticCompositionLocalOf { Ornament(Color(0xFFA8853A), Color(0x22A8853A), Color(0xFF7A1E2C)) }

// Light: vellum and ink, oxblood rubrics, old gold.
private val Light = lightColorScheme(
    primary = Color(0xFF6E1A28),            // oxblood
    onPrimary = Color(0xFFFFF8EC),
    primaryContainer = Color(0xFFF3DED7),
    onPrimaryContainer = Color(0xFF3A0A12),
    secondary = Color(0xFF8A6A2C),          // old gold, text-safe
    onSecondary = Color(0xFFFFFAF0),
    secondaryContainer = Color(0xFFEFE2C4),
    onSecondaryContainer = Color(0xFF2E2208),
    tertiary = Color(0xFF3D5A45),
    tertiaryContainer = Color(0xFFD8E6D6),
    background = Color(0xFFF7F1E5),         // vellum
    onBackground = Color(0xFF2A221C),       // iron-gall ink
    surface = Color(0xFFF7F1E5),
    onSurface = Color(0xFF2A221C),
    surfaceVariant = Color(0xFFEBE0CC),
    onSurfaceVariant = Color(0xFF5C5044),
    surfaceContainerLowest = Color(0xFFFCF8F0),
    surfaceContainerLow = Color(0xFFF3EBDC),
    surfaceContainer = Color(0xFFEFE6D4),
    surfaceContainerHigh = Color(0xFFE9DFCB),
    surfaceContainerHighest = Color(0xFFE2D6BF),
    outline = Color(0xFFB59B68),
    outlineVariant = Color(0xFFD9C8A4),
    error = Color(0xFF9C2A1F),
)

// Dark: candle-lit night, warm ink-black with gilt.
private val Dark = darkColorScheme(
    primary = Color(0xFFD9B872),            // gilt
    onPrimary = Color(0xFF2B1F06),
    primaryContainer = Color(0xFF4A2026),
    onPrimaryContainer = Color(0xFFF4DCD5),
    secondary = Color(0xFFD9B872),
    onSecondary = Color(0xFF2B1F06),
    secondaryContainer = Color(0xFF3A3020),
    onSecondaryContainer = Color(0xFFF1E3C2),
    tertiary = Color(0xFFA9C9AE),
    tertiaryContainer = Color(0xFF2A3D2F),
    background = Color(0xFF14110E),
    onBackground = Color(0xFFEDE3D1),
    surface = Color(0xFF14110E),
    onSurface = Color(0xFFEDE3D1),
    surfaceVariant = Color(0xFF2E2821),
    onSurfaceVariant = Color(0xFFCBBDA6),
    surfaceContainerLowest = Color(0xFF0F0D0B),
    surfaceContainerLow = Color(0xFF1A1612),
    surfaceContainer = Color(0xFF1F1A16),
    surfaceContainerHigh = Color(0xFF29231D),
    surfaceContainerHighest = Color(0xFF332C24),
    outline = Color(0xFF8C7650),
    outlineVariant = Color(0xFF4A3F30),
    error = Color(0xFFE59A8C),
)

private val LightOrnament = Ornament(gold = Color(0xFFA8853A), focusWash = Color(0x26C9A24E), rubric = Color(0xFF7A1E2C))
private val DarkOrnament = Ornament(gold = Color(0xFFCDAA62), focusWash = Color(0x22D9B872), rubric = Color(0xFFE08A86))

/** Highlight colours (ARGB), soft enough for vellum and for night. */
val HighlightColours = listOf(0xFFF2D675.toInt(), 0xFFB9D9A6.toInt(), 0xFFA9C8E8.toInt(), 0xFFE8B4C0.toInt())

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun BaibolyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    CompositionLocalProvider(LocalOrnament provides if (dark) DarkOrnament else LightOrnament) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typography, shapes = shapes, content = content)
    }
}

// ---------------------------------------------------------------- ornaments

/** A gilt hairline that fades out at both ends. */
@Composable
fun GoldRule(modifier: Modifier = Modifier) {
    val gold = LocalOrnament.current.gold
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, gold, gold, Color.Transparent)),
            Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
        )
    }
}

/** Two gilt hairlines around a small ornament: ── ✠ ── */
@Composable
fun Fleuron(modifier: Modifier = Modifier, symbol: String = "✠") {
    val gold = LocalOrnament.current.gold
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        GoldRule(Modifier.width(72.dp))
        Text(symbol, color = gold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp))
        GoldRule(Modifier.width(72.dp))
    }
}
