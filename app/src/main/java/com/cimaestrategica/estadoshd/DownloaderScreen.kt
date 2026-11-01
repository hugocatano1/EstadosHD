package com.cimaestrategica.estadoshd

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FORMAT_UI = mapOf(
    DlFormat.VIDEO_BEST to (R.drawable.ms_movie to GBlue),
    DlFormat.VIDEO_720 to (R.drawable.ms_smart_display to GTeal),
    DlFormat.AUDIO_MP3 to (R.drawable.ms_music_note to GPurple),
    DlFormat.IMAGE to (R.drawable.ms_image to GYellow),
    DlFormat.SLIDESHOW to (R.drawable.ms_slideshow to GRed)
)

@Composable
fun DownloaderScreen(d: DlState, vm: DownloadViewModel, onAbout: () -> Unit, onCut: (java.io.File) -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(lerp(cs.background, Color(0xFFE1306C), 0.10f), cs.background, cs.background)))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        TopBar(onAbout, trailing = null)
        Spacer(Modifier.height(6.dp))
        HeroDownloader()
        Spacer(Modifier.height(14.dp))
        LinkCard(d, vm, ctx)
        AnimatedVisibility(visible = d.analyzing) {
            AppCard(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Analizando enlace…", fontWeight = FontWeight.Bold, color = cs.onSurface)
                        Text("Buscando video, audio e imágenes", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        val info = d.info
        if (info != null) {
            Spacer(Modifier.height(12.dp))
            PreviewCard(info, d)
            Spacer(Modifier.height(12.dp))
            Text("Elige el formato", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            DlFormat.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { f -> FormatTile(f, d.format == f, !d.downloading, Modifier.weight(1f)) { vm.setFormat(f) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            if (d.downloading) ProgressCard(d) { vm.cancel() }
            else Button3D("Descargar ahora", R.drawable.ms_download, Modifier.fillMaxWidth(), face = GGreen) { vm.download() }
        }
        if (d.history.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Tus descargas", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = cs.onBackground, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            d.history.forEach { item -> HistoryRow(item, ctx, onCut) { vm.remove(item) } }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon3D(R.drawable.ms_bolt, if (d.engineReady) GGreen else GYellow, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Motor de descargas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.onSurface)
                Text(d.engineMsg, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { vm.updateEngine() }) { Text("Actualizar") }
        }
        Text(
            "Descarga solo contenido propio o con permiso de su autor. Las publicaciones privadas no se pueden descargar.",
            fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
        )
        Credits()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun HeroDownloader() {
    val t = rememberInfiniteTransition(label = "hero")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = Color(0xFFE1306C), spotColor = Color(0xFFE1306C))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(Color(0xFF7B2FF7), Color(0xFF4F5BD5), shift),
                        Color(0xFFD62976),
                        lerp(Color(0xFFFA7E1E), Color(0xFFFEDA75), shift)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Text("Descarga de cualquier red", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Videos, audios, imágenes y fotos con sonido", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(
                R.drawable.ms_photo_camera to Color(0xFFE1306C), R.drawable.ms_equalizer to Color(0xFFFE2C55),
                R.drawable.ms_public to Color(0xFF1877F2), R.drawable.ms_smart_display to GRed,
                R.drawable.ms_chat to Color(0xFF202124), R.drawable.ms_add to GGreen
            ).forEach { (ic, col) -> Icon3D(ic, col, 42.dp) }
        }
        Spacer(Modifier.height(10.dp))
        Text("Instagram · TikTok · Facebook · YouTube · X · Threads · Pinterest · +1.000 sitios", color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp)
    }
}

@Composable
fun LinkCard(d: DlState, vm: DownloadViewModel, ctx: Context) {
    AppCard {
        OutlinedTextField(
            value = d.url,
            onValueChange = { vm.setUrl(it) },
            singleLine = true,
            placeholder = { Text("Pega aquí el enlace del video o foto") },
            leadingIcon = { Icon(painterResource(R.drawable.ms_link), null, tint = cs.primary) },
            trailingIcon = {
                if (d.url.isNotEmpty()) IconButton(onClick = { vm.setUrl("") }) { Icon(painterResource(R.drawable.ms_close), "Borrar") }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.analyze() }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (d.url.isNotBlank()) {
            val site = Sites.detect(d.url)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon3D(site.icon, Color(site.color), 28.dp)
                Spacer(Modifier.width(8.dp))
                Text("Detectado: ${site.name}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tonal3D("Pegar", R.drawable.ms_content_paste, Modifier.weight(1f), enabled = !d.downloading) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                val u = Sites.extractUrl(txt)
                if (u != null) { vm.setUrl(u); vm.analyze() } else vm.emit("No hay un enlace copiado")
            }
            Button3D("Analizar", R.drawable.ms_travel_explore, Modifier.weight(1f), height = 52.dp,
                enabled = d.url.isNotBlank() && !d.analyzing && !d.downloading) { vm.analyze() }
        }
    }
}

@Composable
fun PreviewCard(info: LinkInfo, d: DlState) {
    AppCard(padding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(180.dp).background(cs.surfaceVariant)) {
            val img = remember(d.thumb) { d.thumb?.asImageBitmap() }
            if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(painterResource(R.drawable.ms_image), null, tint = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(48.dp))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
            val site = Sites.detect(info.url)
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon3D(site.icon, Color(site.color), 34.dp)
                Spacer(Modifier.width(8.dp))
                Text(site.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(info.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    info.uploader.takeIf { it.isNotBlank() }?.let { "@$it" },
                    if (info.durationSec > 0) fmtTime(info.durationSec * 1000L) else null,
                    if (info.hasVideo) "Video" else "Foto / audio"
                ).joinToString(" · ")
                Text(meta, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun FormatTile(f: DlFormat, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (ic, col) = FORMAT_UI.getValue(f)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .shadow(if (selected) 6.dp else 1.dp, shape, spotColor = col)
            .clip(shape)
            .background(if (selected) lerp(cs.surface, col, 0.10f) else cs.surface)
            .border(if (selected) 2.dp else 0.dp, if (selected) col else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon3D(ic, col, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(f.desc, fontSize = 10.5.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun ProgressCard(d: DlState, onCancel: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { d.progress }, modifier = Modifier.size(58.dp), strokeWidth = 6.dp, trackColor = cs.surfaceVariant, color = if (d.converting) GRed else GGreen)
                Text("${(d.progress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (d.converting) "Creando video con sonido" else "Descargando", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
                Text(d.status, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Box(Modifier.clickable(onClick = onCancel)) { Icon3D(R.drawable.ms_close, GRed, 40.dp) }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)), color = if (d.converting) GRed else GGreen)
    }
}

@Composable
fun HistoryRow(item: DlItem, ctx: Context, onCut: (java.io.File) -> Unit, onRemove: () -> Unit) {
    val (ic, col) = FORMAT_UI.getValue(item.format)
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(cs.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
            val img = remember(item.thumb) { item.thumb?.asImageBitmap() }
            if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon3D(ic, col, 34.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
            Text("${item.site} · ${item.format.title} · ${fmtSize(item.size)}", fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1)
            if (item.saved) Text("En tu galería", fontSize = 10.5.sp, color = GGreen, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.clickable { Sharer.safeStart(ctx, Sharer.openAny(ctx, item.files.first())) }) { Icon3D(R.drawable.ms_play_arrow, GTeal, 34.dp) }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clickable { Sharer.safeStart(ctx, Sharer.shareAny(ctx, item.files)) }) { Icon3D(R.drawable.ms_share, GBlue, 34.dp) }
        if (item.isVideo) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.clickable { onCut(item.files.first { it.extension.lowercase() in listOf("mp4", "webm", "mkv", "mov") }) }) { Icon3D(R.drawable.ms_content_cut, GRed, 34.dp) }
        }
    }
}

/** Barra de navegación inferior con íconos 3D. */
@Composable
fun BottomNav(section: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape)
            .clip(shape)
            .background(cs.surface)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(Triple("Estados HD", R.drawable.ms_hd, GBlue), Triple("Descargar", R.drawable.ms_cloud_download, Color(0xFFD62976))).forEachIndexed { i, (label, ic, col) ->
            val sel = i == section
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (sel) lerp(cs.surface, col, 0.12f) else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sel) Icon3D(ic, col, 36.dp) else Icon(painterResource(ic), null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, color = if (sel) cs.onSurface else cs.onSurfaceVariant)
            }
        }
    }
}

