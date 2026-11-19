package mg.baiboly.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
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
import mg.baiboly.app.LineSpacing
import mg.baiboly.app.Palette
import mg.baiboly.app.Style
import mg.baiboly.app.TextFont
import mg.baiboly.app.ThemeMode
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp

// ---------------------------------------------------------------- type

/** Scripture and body text: Gentium Book Plus (SIL OFL), readable, with full accent and Greek coverage. */
val GentiumFamily = FontFamily(
    Font(R.font.gentium_regular, FontWeight.Normal),
    Font(R.font.gentium_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.gentium_bold, FontWeight.Bold),
)

/** Titles and labels: Cormorant SC (SIL OFL), engraved small capitals. */
val CormorantFamily = FontFamily(
    Font(R.font.cormorant_sc_medium, FontWeight.Medium),
    Font(R.font.cormorant_sc_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_sc_bold, FontWeight.Bold),
)

/** The reader's choices of type, provided to the whole app. */
@Immutable
data class Reading(val text: FontFamily, val title: FontFamily, val lineSpacing: Float)

val LocalReading = staticCompositionLocalOf { Reading(GentiumFamily, CormorantFamily, 1.55f) }

/** Scripture and body text in the reader's chosen typeface. */
val TextSerif: FontFamily
    @Composable @ReadOnlyComposable get() = LocalReading.current.text

/** Titles in the reader's chosen style. */
val TitleSerif: FontFamily
    @Composable @ReadOnlyComposable get() = LocalReading.current.title

fun TextFont.family(): FontFamily = when (this) {
    TextFont.Classic -> GentiumFamily
    TextFont.Modern -> FontFamily.Serif
    TextFont.Sans -> FontFamily.SansSerif
}

fun LineSpacing.factor(): Float = when (this) {
    LineSpacing.Compact -> 1.35f
    LineSpacing.Normal -> 1.55f
    LineSpacing.Airy -> 1.8f
}

private fun typography(text: FontFamily, engraved: Boolean) = Typography().let { t ->
    // Engraved: Cormorant small capitals, spaced. Plain: the text face, ordinary case.
    val titleFamily = if (engraved) CormorantFamily else text
    fun TextStyle.body() = copy(fontFamily = text)
    fun TextStyle.title(weight: FontWeight = FontWeight.SemiBold, spacing: Float = 0.6f) =
        if (engraved) copy(fontFamily = titleFamily, fontWeight = weight, letterSpacing = spacing.sp)
        else copy(fontFamily = titleFamily, fontWeight = if (weight == FontWeight.Bold) FontWeight.Bold else FontWeight.Medium, letterSpacing = 0.2.sp)
    t.copy(
        displayLarge = t.displayLarge.title(), displayMedium = t.displayMedium.title(), displaySmall = t.displaySmall.title(),
        headlineLarge = t.headlineLarge.title(), headlineMedium = t.headlineMedium.title(), headlineSmall = t.headlineSmall.title(),
        titleLarge = t.titleLarge.title().copy(fontSize = 24.sp),
        titleMedium = t.titleMedium.title(spacing = 0.8f).copy(fontSize = 19.sp),
        titleSmall = t.titleSmall.title(spacing = 0.8f).copy(fontSize = 16.sp),
        labelLarge = t.labelLarge.title(FontWeight.Bold, 1.0f).copy(fontSize = if (engraved) 16.sp else 14.sp),
        labelMedium = t.labelMedium.title(FontWeight.Bold, 1.0f).copy(fontSize = if (engraved) 14.sp else 12.sp),
        labelSmall = t.labelSmall.title(FontWeight.Bold, 1.0f).copy(fontSize = if (engraved) 12.sp else 11.sp),
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

private fun shapes(style: Style) = when (style) {
    Style.Manuscript -> manuscriptShapes
    Style.Modern -> Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(14.dp), RoundedCornerShape(20.dp), RoundedCornerShape(28.dp), RoundedCornerShape(32.dp))
    Style.Page -> Shapes(RoundedCornerShape(0.dp), RoundedCornerShape(2.dp), RoundedCornerShape(2.dp), RoundedCornerShape(4.dp), RoundedCornerShape(4.dp))
    Style.Glass -> Shapes(RoundedCornerShape(2.dp), RoundedCornerShape(4.dp), RoundedCornerShape(6.dp), RoundedCornerShape(8.dp), RoundedCornerShape(10.dp))
}

/** The theme in use: every component asks it how to look and where to sit. */
val LocalStyle = staticCompositionLocalOf { Style.Manuscript }

private val manuscriptShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Colour themes. Each has a day and a night version; Light / Dark / Device
 * picks between them. Vellum is hand-tuned; the others are derived from a
 * few colours by [derive], so every theme fills every slot consistently.
 */
private class PaletteColours(val light: ColorScheme, val dark: ColorScheme, val lightOrnament: Ornament, val darkOrnament: Ornament)

private fun derive(
    dark: Boolean, bg: Color, ink: Color, primary: Color, onPrimary: Color,
    gold: Color, rubric: Color, tertiary: Color, error: Color,
): Pair<ColorScheme, Ornament> {
    fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val text = if (dark) gold else mix(gold, ink, 0.25f) // gold that is still legible as text
    val scheme = base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = mix(bg, primary, if (dark) 0.28f else 0.14f),
        onPrimaryContainer = if (dark) mix(primary, ink, 0.5f) else mix(primary, ink, 0.45f),
        secondary = text, onSecondary = bg,
        secondaryContainer = mix(bg, gold, if (dark) 0.22f else 0.2f),
        onSecondaryContainer = ink,
        tertiary = tertiary, onTertiary = bg,
        tertiaryContainer = mix(bg, tertiary, 0.2f), onTertiaryContainer = ink,
        background = bg, onBackground = ink, surface = bg, onSurface = ink,
        surfaceVariant = mix(bg, ink, 0.08f), onSurfaceVariant = mix(ink, bg, 0.3f),
        surfaceContainerLowest = if (dark) mix(bg, Color.Black, 0.3f) else mix(bg, Color.White, 0.5f),
        surfaceContainerLow = mix(bg, ink, 0.03f),
        surfaceContainer = mix(bg, ink, 0.045f),
        surfaceContainerHigh = mix(bg, ink, 0.07f),
        surfaceContainerHighest = mix(bg, ink, 0.1f),
        surfaceTint = primary,
        inverseSurface = ink, inverseOnSurface = bg, inversePrimary = mix(primary, bg, 0.4f),
        outline = mix(gold, bg, 0.15f), outlineVariant = mix(gold, bg, 0.55f),
        error = error, onError = bg,
    )
    return scheme to Ornament(gold = gold, focusWash = gold.copy(alpha = if (dark) 0.14f else 0.16f), rubric = rubric)
}

private fun palette(
    light: Array<Color>, dark: Array<Color>,
): PaletteColours {
    // bg, ink, primary, onPrimary, gold, rubric, tertiary, error
    val (l, lo) = derive(false, light[0], light[1], light[2], light[3], light[4], light[5], light[6], light[7])
    val (d, dor) = derive(true, dark[0], dark[1], dark[2], dark[3], dark[4], dark[5], dark[6], dark[7])
    return PaletteColours(l, d, lo, dor)
}

private fun c(v: Long) = Color(v)

private val palettes: Map<Palette, PaletteColours> by lazy {
    mapOf(
        Palette.Vellum to PaletteColours(Light, Dark, LightOrnament, DarkOrnament),
        // Lapis: ultramarine of illuminated manuscripts, with gold
        Palette.Lapis to palette(
            arrayOf(c(0xFFF3F2EC), c(0xFF1C2333), c(0xFF213F78), c(0xFFF5F7FC), c(0xFFA8853A), c(0xFF8B2331), c(0xFF3D5A45), c(0xFF9C2A1F)),
            arrayOf(c(0xFF0D1220), c(0xFFE4E7EF), c(0xFFAFC6F2), c(0xFF0E1E3F), c(0xFFD2B36C), c(0xFFF0A3A3), c(0xFFA9C9AE), c(0xFFE59A8C)),
        ),
        // Olive: garden green and bronze
        Palette.Olive to palette(
            arrayOf(c(0xFFF2F1E6), c(0xFF22261B), c(0xFF3D5A28), c(0xFFF7FAEF), c(0xFF9A7B35), c(0xFF7D3A1D), c(0xFF5A4A7A), c(0xFF9C2A1F)),
            arrayOf(c(0xFF11140E), c(0xFFE6E9DA), c(0xFFB9D198), c(0xFF1C2A0E), c(0xFFCDB06A), c(0xFFE6A582), c(0xFFC4B6E6), c(0xFFE59A8C)),
        ),
        // Purple: the colour of Advent and Lent, with gold
        Palette.Purple to palette(
            arrayOf(c(0xFFF6F2F1), c(0xFF29202B), c(0xFF5A2B6E), c(0xFFFBF5FC), c(0xFFA8853A), c(0xFF8A2240), c(0xFF3D5A45), c(0xFF9C2A1F)),
            arrayOf(c(0xFF141017), c(0xFFEDE5EE), c(0xFFD4B4E4), c(0xFF2E1240), c(0xFFD9B872), c(0xFFEBA3B6), c(0xFFA9C9AE), c(0xFFE59A8C)),
        ),
        // Sepia: soft contrast for long reading
        Palette.Sepia to palette(
            arrayOf(c(0xFFEFE2C8), c(0xFF3A2918), c(0xFF6A4322), c(0xFFFFF6E6), c(0xFF9C7A3C), c(0xFF8C3B22), c(0xFF4E5E3A), c(0xFF9C2A1F)),
            arrayOf(c(0xFF1F1911), c(0xFFDFCFB0), c(0xFFD9BA8B), c(0xFF33220E), c(0xFFBFA06A), c(0xFFD99A82), c(0xFFB5C49A), c(0xFFE59A8C)),
        ),
        // Slate: cool white and blue, for the Modern theme
        Palette.Slate to palette(
            arrayOf(c(0xFFF7F8FA), c(0xFF191C21), c(0xFF2C5BCF), c(0xFFFFFFFF), c(0xFF98A2B0), c(0xFF2C5BCF), c(0xFF0F7C6E), c(0xFFB3261E)),
            arrayOf(c(0xFF0F1114), c(0xFFE5E8EC), c(0xFFA8C0FF), c(0xFF0B2367), c(0xFF5D6673), c(0xFFA8C0FF), c(0xFF7FD6C6), c(0xFFFF8A80)),
        ),
        // Paper: plain black on white; at night true black (saves power on OLED screens)
        Palette.Paper to palette(
            arrayOf(c(0xFFFFFFFF), c(0xFF161616), c(0xFF262626), c(0xFFFFFFFF), c(0xFF8C8C8C), c(0xFFB3261E), c(0xFF35618A), c(0xFFB3261E)),
            arrayOf(c(0xFF000000), c(0xFFE6E6E6), c(0xFFEDEDED), c(0xFF111111), c(0xFF7D7D7D), c(0xFFFF8A80), c(0xFF9CC3E6), c(0xFFFF8A80)),
        ),
    )
}

/** Background, accent and ornament of a theme, for the swatches in Settings. */
fun paletteSwatch(p: Palette, dark: Boolean): Triple<Color, Color, Color> {
    val pc = palettes.getValue(p)
    val scheme = if (dark) pc.dark else pc.light
    val orn = if (dark) pc.darkOrnament else pc.lightOrnament
    return Triple(scheme.background, scheme.primary, orn.gold)
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun BaibolyTheme(
    mode: ThemeMode,
    style: Style = Style.Manuscript,
    palette: Palette = Palette.Vellum,
    textFont: TextFont = TextFont.Classic,
    engravedTitles: Boolean = true,
    lineSpacing: LineSpacing = LineSpacing.Normal,
    content: @Composable () -> Unit,
) {
    val dark = isDark(mode)
    val pc = palettes.getValue(palette)
    val text = textFont.family()
    val reading = remember(text, engravedTitles, lineSpacing) {
        Reading(text, if (engravedTitles) CormorantFamily else text, lineSpacing.factor())
    }
    val type = remember(text, engravedTitles) { typography(text, engravedTitles) }
    CompositionLocalProvider(
        LocalOrnament provides if (dark) pc.darkOrnament else pc.lightOrnament,
        LocalReading provides reading,
        LocalStyle provides style,
    ) {
        MaterialTheme(colorScheme = if (dark) pc.dark else pc.light, typography = type, shapes = shapes(style), content = content)
    }
}

// ---------------------------------------------------------------- ornaments

/** A gilt hairline that fades out at both ends. */
@Composable
fun GoldRule(modifier: Modifier = Modifier) {
    when (LocalStyle.current) {
        Style.Manuscript -> Unit
        // the other themes have no gilding: Modern no rule at all, Page and Glass a plain hairline
        Style.Modern -> { Spacer(modifier.height(0.dp)); return }
        else -> {
            androidx.compose.material3.HorizontalDivider(modifier, thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            return
        }
    }
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
