package com.cimaestrategica.estadoshd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0A0D18)
val Panel = Color(0xFF131A2E)
val Panel2 = Color(0xFF1C2442)
val Line = Color(0xFF2A3358)
val Violet = Color(0xFF7C5CFF)
val Cyan = Color(0xFF22D3EE)
val Pink = Color(0xFFFF4FA3)
val Mint = Color(0xFF34D399)
val Amber = Color(0xFFFBBF24)
val Danger = Color(0xFFFF6B6B)
val TextHi = Color(0xFFF2F4FF)
val TextLo = Color(0xFF98A2C3)

val BrandBrush = Brush.linearGradient(listOf(Violet, Cyan))
val HeroBrush = Brush.linearGradient(listOf(Violet, Pink, Cyan))

@Composable
fun EstadosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            onPrimary = Color.White,
            secondary = Cyan,
            background = Ink,
            onBackground = TextHi,
            surface = Panel,
            onSurface = TextHi,
            surfaceVariant = Panel2,
            onSurfaceVariant = TextLo,
            error = Danger
        ),
        content = content
    )
}
