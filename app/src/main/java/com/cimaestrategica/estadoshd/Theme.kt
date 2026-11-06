package com.cimaestrategica.estadoshd

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/* Paleta vibrante (nombres conservados para compatibilidad) */
val GBlue = Color(0xFF3B82F6)
val GRed = Color(0xFFF43F5E)
val GYellow = Color(0xFFF59E0B)
val GGreen = Color(0xFF10B981)
val GPurple = Color(0xFF8B5CF6)
val GTeal = Color(0xFF06B6D4)
val WaGreen = Color(0xFF22C55E)
val Vio = Color(0xFF7C3AED)
val Pnk = Color(0xFFEC4899)
val Org = Color(0xFFF97316)

val BrandBrush = Brush.linearGradient(listOf(Vio, Pnk, Org))
val BrandBrushH = Brush.horizontalGradient(listOf(Vio, Pnk, Org))

/** Desplaza el tono (y opcionalmente saturación/brillo) de un color: base de los degradados vivos. */
fun hueShift(c: Color, deg: Float, sat: Float = 1f, value: Float = 1f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[0] = (hsv[0] + deg + 360f) % 360f
    hsv[1] = (hsv[1] * sat).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * value).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor((c.alpha * 255).toInt(), hsv))
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold)
)

private fun typography(): Typography {
    val b = Typography()
    fun TextStyle.p() = copy(fontFamily = Poppins)
    return Typography(
        displayLarge = b.displayLarge.p(), displayMedium = b.displayMedium.p(), displaySmall = b.displaySmall.p(),
        headlineLarge = b.headlineLarge.p(), headlineMedium = b.headlineMedium.p(), headlineSmall = b.headlineSmall.p(),
        titleLarge = b.titleLarge.p(), titleMedium = b.titleMedium.p(), titleSmall = b.titleSmall.p(),
        bodyLarge = b.bodyLarge.p(), bodyMedium = b.bodyMedium.p(), bodySmall = b.bodySmall.p(),
        labelLarge = b.labelLarge.p(), labelMedium = b.labelMedium.p(), labelSmall = b.labelSmall.p()
    )
}

private val LightColors = lightColorScheme(
    primary = Vio,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE5FF),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Pnk,
    background = Color(0xFFF8F5FF),
    onBackground = Color(0xFF1A1033),
    surface = Color.White,
    onSurface = Color(0xFF1A1033),
    surfaceVariant = Color(0xFFEFE9FB),
    onSurfaceVariant = Color(0xFF5B5470),
    outline = Color(0xFFCFC6E6),
    outlineVariant = Color(0xFFE6DFF5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF9FF),
    surfaceContainer = Color(0xFFF4EFFD),
    surfaceContainerHigh = Color(0xFFEDE7FA),
    surfaceContainerHighest = Color(0xFFE3DBF5),
    error = Color(0xFFE11D48)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB79CFF),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF3B2A7A),
    onPrimaryContainer = Color(0xFFEDE5FF),
    secondary = Color(0xFFF9A8D4),
    background = Color(0xFF0D0A1A),
    onBackground = Color(0xFFF1EDFF),
    surface = Color(0xFF17132B),
    onSurface = Color(0xFFF1EDFF),
    surfaceVariant = Color(0xFF241E3D),
    onSurfaceVariant = Color(0xFFC4BCDD),
    outline = Color(0xFF6D6489),
    outlineVariant = Color(0xFF332B50),
    surfaceContainerLowest = Color(0xFF09071A),
    surfaceContainerLow = Color(0xFF141026),
    surfaceContainer = Color(0xFF1B1631),
    surfaceContainerHigh = Color(0xFF241E3D),
    surfaceContainerHighest = Color(0xFF2E2749),
    error = Color(0xFFFB7185)
)

val cs: ColorScheme
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

@Composable
fun EstadosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = typography(),
        content = content
    )
}
