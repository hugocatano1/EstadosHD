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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        if (d.errorTitle != null) {
            Spacer(Modifier.height(12.dp))
            ErrorCard(d, vm)
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
            Spacer(Modifier.height(4.dp))
            PipelineCard(d, vm)
            Spacer(Modifier.height(12.dp))
            if (d.downloading) ProgressCard(d) { vm.cancel() }
            else Button3D(
                if (d.autoStatus) "Descargar y crear estados" else "Descargar ahora",
                if (d.autoStatus) R.drawable.ms_auto_awesome else R.drawable.ms_download,
                Modifier.fillMaxWidth(), face = if (d.autoStatus) Color(0xFFD62976) else GGreen
            ) { vm.download() }
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
            "Sin iniciar sesión: funciona con publicaciones públicas. Descarga solo contenido propio o con permiso de su autor.",
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
        Text("Instagram · TikTok · Facebook · YouTube · LinkedIn · X · Threads · Pinterest · +1.000 sitios", color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp)
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
        // (fila antigua sin uso)
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            Triple("Estados", R.drawable.ms_hd, GBlue),
            Triple("Descargar", R.drawable.ms_cloud_download, Color(0xFFD62976)),
            Triple("Historial", R.drawable.ms_folder, GYellow)
        ).forEachIndexed { i, (label, ic, col) ->
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
                if (sel) Icon3D(ic, col, 32.dp) else Icon(painterResource(ic), null, tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, color = if (sel) cs.onSurface else cs.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}



@Composable
fun ErrorCard(d: DlState, vm: DownloadViewModel) {
    var showDetail by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(lerp(cs.surface, GRed, 0.08f))
            .border(1.dp, GRed.copy(alpha = 0.35f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon3D(R.drawable.ms_error, GRed, 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(d.errorTitle ?: "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = cs.onSurface, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clearError() }) { Icon(painterResource(R.drawable.ms_close), "Cerrar", tint = cs.onSurfaceVariant) }
        }
        val accKey: String? = null
        if (accKey != null) {
            val acc = Accounts.ALL.first { it.key == accKey }
            Spacer(Modifier.height(10.dp))
            Button3D("Conectar ${acc.name}", acc.icon, Modifier.fillMaxWidth(), face = Color(acc.color), height = 52.dp) { vm.startLogin(acc.key) }
        }
        if (!d.errorDetail.isNullOrBlank()) {
            TextButton(onClick = { showDetail = !showDetail }) { Text(if (showDetail) "Ocultar detalle técnico" else "Ver detalle técnico") }
            if (showDetail) {
                Text(
                    d.errorDetail ?: "", fontSize = 10.5.sp, color = cs.onSurfaceVariant, lineHeight = 14.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun AccountsCard(d: DlState, vm: DownloadViewModel) {
    AppCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon3D(R.drawable.ms_verified, GBlue, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Cuentas conectadas", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
                Text("Instagram y otras redes exigen sesión. Se guarda solo en tu celular.", fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Accounts.ALL.forEach { acc ->
                val on = acc.key in d.connected
                val col = Color(acc.color)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) lerp(cs.surface, GGreen, 0.10f) else cs.surfaceContainerHigh)
                        .border(if (on) 1.5.dp else 0.dp, if (on) GGreen else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { if (on) vm.disconnect(acc.key) else vm.startLogin(acc.key) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon3D(acc.icon, col, 30.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(acc.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 1)
                    Text(if (on) "Conectada" else "Conectar", fontSize = 9.5.sp, color = if (on) GGreen else cs.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Pantalla de inicio de sesión dentro de la app (WebView). */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginOverlay(accKey: String, onDone: (Boolean) -> Unit) {
    val acc = Accounts.ALL.first { it.key == accKey }
    androidx.activity.compose.BackHandler { onDone(false) }
    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDone(false) }) { Icon(painterResource(R.drawable.ms_close), "Cerrar") }
            Icon3D(acc.icon, Color(acc.color), 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Conectar ${acc.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
                Text("Inicia sesión y luego toca «Listo»", fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
            Button3D("Listo", R.drawable.ms_check_circle, Modifier.width(120.dp), face = GGreen, height = 44.dp) { onDone(true) }
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { c ->
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = android.webkit.WebViewClient()
                    loadUrl(acc.loginUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}


/** Línea de producción: al terminar la descarga, crea las partes HD y abre la publicación. */
@Composable
fun PipelineCard(d: DlState, vm: DownloadViewModel) {
    val pink = Color(0xFFD62976)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (d.autoStatus) 8.dp else 1.dp, shape, spotColor = pink)
            .clip(shape)
            .background(if (d.autoStatus) lerp(cs.surface, pink, 0.07f) else cs.surface)
            .border(if (d.autoStatus) 1.5.dp else 0.dp, if (d.autoStatus) pink.copy(alpha = 0.5f) else Color.Transparent, shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon3D(R.drawable.ms_auto_awesome, pink, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Subir a estados al terminar", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = cs.onSurface)
                Text("Descarga → partes HD → publicar en orden, todo seguido", fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 14.sp)
            }
            androidx.compose.material3.Switch(checked = d.autoStatus, onCheckedChange = { vm.setAutoStatus(it) }, enabled = !d.downloading)
        }
        AnimatedVisibility(visible = d.autoStatus) {
            Column {
                Spacer(Modifier.height(12.dp))
                Text("¿Para qué red?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                PLATFORMS.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            val on = p.id == d.autoPlatformId
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (on) lerp(cs.surface, p.color, 0.14f) else cs.surfaceContainerHigh)
                                    .border(if (on) 1.5.dp else 0.dp, if (on) p.color else Color.Transparent, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !d.downloading) { vm.setAutoPlatform(p.id) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon3D(platformIcon(p), p.color, 28.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(p.short, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.maxSeconds} s", fontSize = 9.sp, color = cs.onSurfaceVariant)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/* ======================= Historial ======================= */

@Composable
fun HistoryScreen(d: DlState, vm: DownloadViewModel, onAbout: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val total = d.history.sumOf { it.size }
    val selMode = d.selected.isNotEmpty()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        TopBar(onAbout, trailing = null)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(R.drawable.ms_folder, GYellow, "${d.history.size}", "descargas", Modifier.weight(1f))
            StatTile(R.drawable.ms_layers, GBlue, fmtSize(total), "ocupado", Modifier.weight(1f))
            StatTile(R.drawable.ms_hd, Color(0xFFD62976), "${d.history.count { it.sentToStatus }}", "a estados", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        if (d.history.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon3D(R.drawable.ms_folder, GYellow, 84.dp)
                Spacer(Modifier.height(16.dp))
                Text("Aún no hay descargas", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = cs.onSurface)
                Text("Lo que descargues aparecerá aquí", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = d.selected.size == d.history.size && d.history.isNotEmpty(),
                    onCheckedChange = { vm.selectAll() }
                )
                Text(if (selMode) "${d.selected.size} seleccionadas" else "Seleccionar todo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.onSurface, modifier = Modifier.weight(1f))
                if (selMode) TextButton(onClick = { vm.clearSelection() }) { Text("Cancelar") }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 10.dp)
            ) {
                items(d.history.size, key = { d.history[it].id }) { i ->
                    val it2 = d.history[i]
                    HistoryItemRow(it2, it2.id in d.selected, selMode, ctx,
                        onToggle = { vm.toggleSelect(it2.id) },
                        onStatus = { vm.sendToStatus(it2) })
                }
            }
            AnimatedVisibility(visible = selMode) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tonal3D("Quitar", R.drawable.ms_close, Modifier.weight(1f)) { vm.deleteSelected(false) }
                    Button3D("Borrar archivos", R.drawable.ms_delete, Modifier.weight(1.3f), face = GRed, height = 52.dp) { confirm = true }
                }
            }
        }
    }
    if (confirm) {
        val bytes = d.history.filter { it.id in d.selected }.sumOf { it.size }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon3D(R.drawable.ms_delete, GRed, 52.dp) },
            title = { Text("¿Borrar ${d.selected.size} definitivamente?", fontWeight = FontWeight.Bold) },
            text = { Text("Se eliminarán los archivos de la app y de tu galería (carpeta EstadosHD). Liberarás ${fmtSize(bytes)}. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.deleteSelected(true) }) { Text("Borrar", color = GRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun HistoryItemRow(item: DlItem, selected: Boolean, selMode: Boolean, ctx: Context, onToggle: () -> Unit, onStatus: () -> Unit) {
    val (ic, col) = FORMAT_UI.getValue(item.format)
    val shape = RoundedCornerShape(20.dp)
    val date = remember(item.date) { java.text.SimpleDateFormat("d MMM · h:mm a", java.util.Locale("es", "CO")).format(java.util.Date(item.date)) }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(if (selected) 6.dp else 1.dp, shape, spotColor = GBlue)
            .clip(shape)
            .background(if (selected) lerp(cs.surface, GBlue, 0.08f) else cs.surface)
            .border(if (selected) 2.dp else 0.dp, if (selected) GBlue else Color.Transparent, shape)
            .clickable {
                if (selMode) onToggle()
                else item.uris(ctx).firstOrNull()?.let { (u, mime) -> Sharer.safeStart(ctx, Sharer.openUri(u, mime)) }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
            val img = remember(item.thumb) { item.thumb?.asImageBitmap() }
            if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon3D(ic, col, 34.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
            Text("${item.site} · ${item.format.title}", fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1)
            Text(
                if (item.exists) "$date · ${fmtSize(item.size)}" else "$date · archivo borrado",
                fontSize = 10.5.sp, color = if (item.exists) cs.onSurfaceVariant else GRed, maxLines = 1
            )
            if (item.sentToStatus) Text("Enviado a estados", fontSize = 10.sp, color = Color(0xFFD62976), fontWeight = FontWeight.SemiBold)
        }
        if (!selMode && item.exists) {
            Box(Modifier.clickable {
                val us = item.uris(ctx)
                if (us.isNotEmpty()) Sharer.safeStart(ctx, Sharer.shareUris(ctx, us.map { p -> p.first }, us.map { p -> p.second }))
            }) { Icon3D(R.drawable.ms_share, GBlue, 32.dp) }
            if (item.isVideo) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clickable(onClick = onStatus)) { Icon3D(R.drawable.ms_auto_awesome, Color(0xFFD62976), 32.dp) }
            }
        }
    }
}
