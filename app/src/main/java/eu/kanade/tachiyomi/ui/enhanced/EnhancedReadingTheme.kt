package eu.kanade.tachiyomi.ui.enhanced

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val healthColors = darkColorScheme(
    primary = Color(0xFFF21C82),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF080F20),
    onBackground = Color(0xFFF3F6FF),
    surface = Color(0xFF121C30),
    onSurface = Color(0xFFF3F6FF),
    surfaceVariant = Color(0xFF18253C),
    onSurfaceVariant = Color(0xFFAAB9CB),
    secondary = Color(0xFF35CEDB),
    onSecondary = Color(0xFF08111D),
    outline = Color(0xFF36516F),
    error = Color(0xFFFF6477),
)

@Composable
internal fun EnhancedReadingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = healthColors, content = content)
}
