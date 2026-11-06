package com.cimaestrategica.estadoshd

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.fillMaxSize
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

/** Ícono 3D vibrante (clay): degradado de dos tonos, brillo superior, relieve inferior, halo de color y glifo con sombra. */
@Composable
fun Icon3D(@DrawableRes res: Int, color: Color, side: Dp = 44.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(side * 0.30f)
    val c1 = hueShift(color, -16f, 0.85f, 1.18f)
    val c2 = hueShift(color, 24f, 1.05f, 0.92f)
    Box(
        modifier
            .size(side)
            .shadow(side * 0.24f, shape, ambientColor = c2.copy(alpha = 0.55f), spotColor = c2.copy(alpha = 0.8f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(c1, color, c2)))
            .drawBehind {
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), startY = 0f, endY = this.size.height * 0.55f),
                    topLeft = Offset(this.size.width * 0.08f, this.size.height * 0.03f),
                    size = androidx.compose.ui.geometry.Size(this.size.width * 0.84f, this.size.height * 0.5f)
                )
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)), startY = this.size.height * 0.6f, endY = this.size.height))
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.05f))), shape),
        contentAlignment = Alignment.Center
    ) {
        val p = painterResource(res)
        Icon(p, null, tint = Color.Black.copy(alpha = 0.28f), modifier = Modifier.size(side * 0.54f).offset(y = side * 0.045f).blur(side * 0.04f))
        Icon(p, null, tint = Color.White, modifier = Modifier.size(side * 0.54f))
    }
}

/** Botón 3D vibrante: degradado brillante, reflejo, halo de color y leve hundimiento al tocar. */
@Composable
fun Button3D(
    text: String?,
    @DrawableRes icon: Int?,
    modifier: Modifier = Modifier,
    face: Color = Vio,
    content: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, label = "sc")
    val c = if (enabled) face else cs.surfaceVariant
    val fg = if (enabled) content else cs.onSurfaceVariant.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(20.dp)
    val brush = when {
        !enabled -> Brush.linearGradient(listOf(c, c))
        face == Vio -> BrandBrushH
        else -> Brush.horizontalGradient(listOf(hueShift(c, -12f, 0.95f, 1.08f), c, hueShift(c, 20f, 1.05f, 0.95f)))
    }
    val glow = if (face == Vio) Pnk else hueShift(c, 20f)
    Box(
        modifier
            .height(height)
            .scale(sc)
            .shadow(if (pressed || !enabled) 3.dp else 14.dp, shape, ambientColor = glow.copy(alpha = 0.5f), spotColor = glow.copy(alpha = 0.7f))
            .clip(shape)
            .background(brush)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), endY = this.size.height * 0.55f)
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), shape)
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

/** Texto con degradado de marca. */
@Composable
fun GradientText(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.ExtraBold, modifier: Modifier = Modifier, brush: Brush = BrandBrushH) {
    Text(text, modifier = modifier, style = androidx.compose.ui.text.TextStyle(brush = brush, fontSize = size, fontWeight = weight, fontFamily = Poppins))
}

/** Fondo vivo: manchas de color que se mueven lentamente. */
@Composable
fun VibrantBackdrop() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(14000), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    val dark = cs.background.luminance() < 0.5f
    val k = if (dark) 0.30f else 0.26f
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = this.size.width; val h = this.size.height
        fun blob(cx: Float, cy: Float, r: Float, col: Color) =
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = k), col.copy(alpha = 0f)), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        blob(w * (0.05f + 0.25f * a), h * 0.08f, w * 0.9f, Vio)
        blob(w * (0.95f - 0.25f * a), h * (0.30f + 0.12f * a), w * 0.75f, Pnk)
        blob(w * (0.20f + 0.30f * a), h * 0.80f, w * 0.85f, GTeal)
        blob(w * (0.85f - 0.2f * a), h * 0.98f, w * 0.6f, Org)
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
            .shadow(6.dp, shape, ambientColor = Vio.copy(alpha = 0.18f), spotColor = Vio.copy(alpha = 0.22f))
            .clip(shape)
            .background(cs.surface.copy(alpha = 0.86f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (cs.background.luminance() < 0.5f) 0.12f else 0.9f), Color.White.copy(alpha = 0.05f))), shape)
            .padding(padding)
    ) { content() }
}

@Composable
fun PillTabs(tabs: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceContainerHigh.copy(alpha = 0.85f))
            .padding(4.dp)
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            val sel = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .then(if (sel) Modifier.shadow(8.dp, RoundedCornerShape(50), ambientColor = Pnk, spotColor = Pnk) else Modifier)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) BrandBrushH else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(icon), null, tint = if (sel) Color.White else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    label, fontSize = 12.sp, maxLines = 1,
                    color = if (sel) Color.White else cs.onSurfaceVariant,
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
                    .background(if (sel) Brush.linearGradient(listOf(Vio.copy(alpha = 0.18f), Pnk.copy(alpha = 0.18f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
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
            .background(cs.surface.copy(alpha = 0.86f))
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
