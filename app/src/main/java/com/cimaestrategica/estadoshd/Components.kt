package com.cimaestrategica.estadoshd

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ícono 3D estilo 2026 (soft glass): baldosa translúcida tintada, borde de vidrio, brillo difuso y glifo con degradado. */
@Composable
fun Icon3D(@DrawableRes res: Int, color: Color, side: Dp = 44.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(side * 0.32f)
    val surface = cs.surface
    val dark = surface.luminance() < 0.5f
    val glyphTop = lerp(color, Color.White, if (dark) 0.40f else 0.12f)
    val glyphBottom = lerp(color, Color.Black, if (dark) 0.05f else 0.28f)
    val glyph = Brush.linearGradient(listOf(glyphTop, glyphBottom))
    Box(
        modifier
            .size(side)
            .shadow(side * 0.16f, shape, ambientColor = color.copy(alpha = 0.45f), spotColor = color.copy(alpha = 0.55f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(surface, color, if (dark) 0.20f else 0.08f),
                        lerp(surface, color, if (dark) 0.34f else 0.22f)
                    )
                )
            )
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.16f else 0.70f), Color.Transparent),
                        center = Offset(this.size.width * 0.28f, this.size.height * 0.05f),
                        radius = this.size.width * 0.85f
                    )
                )
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.95f), color.copy(alpha = 0.22f))
                ),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        val p = painterResource(res)
        Icon(
            p, null, tint = color.copy(alpha = 0.35f),
            modifier = Modifier.size(side * 0.52f).offset(y = side * 0.05f).blur(side * 0.05f)
        )
        Icon(
            p, null, tint = color,
            modifier = Modifier
                .size(side * 0.52f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(glyph, blendMode = BlendMode.SrcAtop)
                }
        )
    }
}

/** Botón 3D suave: degradado, borde de vidrio, sombra difusa de color y leve hundimiento al tocar. */
@Composable
fun Button3D(
    text: String?,
    @DrawableRes icon: Int?,
    modifier: Modifier = Modifier,
    face: Color = GBlue,
    content: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "sc")
    val c = if (enabled) face else cs.surfaceVariant
    val fg = if (enabled) content else cs.onSurfaceVariant.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(height)
            .scale(sc)
            .shadow(if (pressed || !enabled) 3.dp else 12.dp, shape, ambientColor = c.copy(alpha = 0.45f), spotColor = c.copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(c, Color.White, 0.16f), c, lerp(c, Color.Black, 0.06f))))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        endY = this.size.height * 0.55f
                    )
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))), shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(22.dp))
            if (icon != null && text != null) Spacer(Modifier.width(9.dp))
            if (text != null) Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Botón 3D secundario (tonal). */
@Composable
fun Tonal3D(text: String?, @DrawableRes icon: Int?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button3D(text, icon, modifier, face = cs.primaryContainer, content = cs.onPrimaryContainer, enabled = enabled, height = 52.dp, onClick = onClick)

@Composable
fun AppCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(shape)
            .background(cs.surface)
            .padding(padding)
    ) { content() }
}

@Composable
fun PillTabs(tabs: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            val sel = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .then(if (sel) Modifier.shadow(3.dp, RoundedCornerShape(50)) else Modifier)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) cs.surface else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(icon), null, tint = if (sel) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    label, fontSize = 12.sp, maxLines = 1,
                    color = if (sel) cs.primary else cs.onSurfaceVariant,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SegChoice(options: List<Pair<String, String>>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        options.forEachIndexed { i, (t, d) ->
            val sel = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .background(if (sel) cs.primaryContainer else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(t, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (sel) cs.onPrimaryContainer else cs.onSurface)
                Text(d, fontSize = 11.sp, color = if (sel) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
            }
            if (i < options.size - 1) Box(Modifier.width(1.dp).height(52.dp).background(cs.outlineVariant))
        }
    }
}

@Composable
fun SettingRow(
    @DrawableRes icon: Int,
    color: Color,
    title: String,
    desc: String,
    trailing: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon3D(icon, color, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = cs.onSurface)
            Text(desc, fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
fun ToggleSetting(@DrawableRes icon: Int, color: Color, title: String, desc: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(icon, color, title, desc) { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) }

@Composable
fun StepperBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(cs.surface)
            .border(1.dp, cs.outlineVariant, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (enabled) cs.primary else cs.onSurfaceVariant, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun StatTile(@DrawableRes icon: Int, color: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(1.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon3D(icon, color, 30.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = cs.onSurface, maxLines = 1)
            Text(label, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Text(
        text,
        color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
fun NumberedStep(n: Int, text: String, color: Color) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).shadow(3.dp, CircleShape, spotColor = color).clip(CircleShape)
                .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.18f), lerp(color, Color.Black, 0.12f)))),
            contentAlignment = Alignment.Center
        ) { Text("$n", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = cs.onSurface, lineHeight = 18.sp)
    }
}

fun platformIcon(p: Platform): Int = when (p.id) {
    "wahd" -> R.drawable.ms_hd
    "wa60", "wa90" -> R.drawable.ms_chat
    "ig" -> R.drawable.ms_photo_camera
    "fb" -> R.drawable.ms_public
    "yt" -> R.drawable.ms_bolt
    "tt" -> R.drawable.ms_equalizer
    else -> R.drawable.ms_tune
}
