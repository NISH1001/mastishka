package nishparadox.mastishka.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF5E8C7F)
private val TealLight = Color(0xFF8FD3C2)
private val DarkBg = Color(0xFF12211D)
private val DarkSurface = Color(0xFF1B2D28)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF06281F),
    secondary = Teal,
    background = DarkBg,
    onBackground = Color(0xFFE3EFEA),
    surface = DarkSurface,
    onSurface = Color(0xFFE3EFEA),
    surfaceVariant = Color(0xFF263A34),
)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = TealLight,
    background = Color(0xFFF3F8F6),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun MastishkaTheme(
    // Mastishka is always dark — a calm, low-glare palette for meditation.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
