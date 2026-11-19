package mg.baiboly.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import mg.baiboly.app.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF7A3E1D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF331100),
    secondary = Color(0xFF6B5B4E),
    secondaryContainer = Color(0xFFF4DED0),
    onSecondaryContainer = Color(0xFF251A10),
    tertiary = Color(0xFF3F6A3A),
    tertiaryContainer = Color(0xFFC1F0B4),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFBF7),
    surfaceVariant = Color(0xFFF2E6DD),
    surfaceContainer = Color(0xFFF7EEE7),
    surfaceContainerLow = Color(0xFFFBF3EE),
    surfaceContainerHigh = Color(0xFFF1E7E0),
    onSurface = Color(0xFF201A17),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85736A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68E),
    onPrimary = Color(0xFF532200),
    primaryContainer = Color(0xFF6E3412),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFD7C2B5),
    secondaryContainer = Color(0xFF52443A),
    onSecondaryContainer = Color(0xFFF4DED0),
    tertiary = Color(0xFFA6D49A),
    tertiaryContainer = Color(0xFF285124),
    background = Color(0xFF151311),
    surface = Color(0xFF151311),
    surfaceVariant = Color(0xFF3A322C),
    surfaceContainer = Color(0xFF221D1A),
    surfaceContainerLow = Color(0xFF1C1815),
    surfaceContainerHigh = Color(0xFF2C2622),
    onSurface = Color(0xFFEDE0D8),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFFA08D83),
)

/** Highlight colours (ARGB). Index stored in the database is the colour value itself. */
val HighlightColours = listOf(0xFFFFE066.toInt(), 0xFF9BE59B.toInt(), 0xFF8EC9FF.toInt(), 0xFFFFA8C8.toInt())

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun BaibolyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(mode)) Dark else Light, content = content)
}
