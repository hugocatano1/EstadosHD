package com.cimaestrategica.estadoshd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF07091A)
val Panel = Color(0xFF121735)
val Panel2 = Color(0xFF1B2147)
val Glass = Color(0xB3121735)
val Line = Color(0x1FFFFFFF)
val Violet = Color(0xFF8B5CFF)
val Cyan = Color(0xFF22D3EE)
val Pink = Color(0xFFFF4FA3)
val Mint = Color(0xFF34D399)
val Amber = Color(0xFFFBBF24)
val Danger = Color(0xFFFF6B6B)
val TextHi = Color(0xFFF4F5FF)
val TextLo = Color(0xFF9BA3C9)

val BrandBrush = Brush.linearGradient(listOf(Violet, Cyan))
val HeroBrush = Brush.linearGradient(listOf(Violet, Pink, Cyan))
val TitleBrush = Brush.linearGradient(listOf(Color.White, Color(0xFFCBBEFF), Cyan))

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
