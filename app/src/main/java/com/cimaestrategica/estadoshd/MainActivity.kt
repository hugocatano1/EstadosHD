package com.cimaestrategica.estadoshd

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (savedInstanceState == null) handleShare(intent)
        setContent { EstadosTheme { AppScreen(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Permite "Compartir → Estados HD" desde la galería. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.load(it) }
        }
    }
}

/* ================= Pantalla principal ================= */

@Composable
fun AppScreen(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.load(uri)
    }
    val queueLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onQueueReturned()
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val view = LocalView.current
    DisposableEffect(s.processing) {
        view.keepScreenOn = s.processing
        onDispose { view.keepScreenOn = false }
    }

    // Cola guiada: abre la siguiente parte solo cuando regresas de la anterior.
    var countdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.queueActive, s.queueIndex) {
        if (!s.queueActive) { countdown = 0; return@LaunchedEffect }
        val i = s.queueIndex
        if (i >= s.segments.size || s.launchedIndex == i) return@LaunchedEffect
        if (i > 0) {
            for (c in 3 downTo 1) { countdown = c; delay(1000) }
        }
        countdown = 0
        val f = s.segments.getOrNull(i)?.file ?: return@LaunchedEffect
        vm.markLaunched(i)
        try {
            queueLauncher.launch(Sharer.single(context, f, Sharer.packageFor(context, s.platform)))
        } catch (e: Exception) {
            vm.pauseQueue()
            vm.emit("No se pudo abrir la app de destino")
        }
    }

    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1150), Ink, Ink)))
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 18.dp, end = 18.dp,
                    top = pad.calculateTopPadding() + 14.dp,
                    bottom = pad.calculateBottomPadding() + 40.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Header() }
                val info = s.info
                if (info == null) {
                    item { EmptyHero(s.loading) { pick() } }
                } else {
                    item { VideoCard(info, enabled = !s.processing) { pick() } }
                    item { SettingsCard(s, vm) }
                    item { ProcessCard(s, vm) }
                    if (s.allReady) {
                        item {
                            PublishCard(
                                s = s,
                                countdown = countdown,
                                onAll = {
                                    val files = s.segments.mapNotNull { it.file }
                                    val ok = Sharer.safeStart(
                                        context, Sharer.multiple(context, files, Sharer.packageFor(context, s.platform))
                                    )
                                    if (ok) vm.markAllShared() else vm.emit("No se encontró una app para publicar")
                                },
                                onQueue = { vm.startQueue() },
                                onPause = { vm.pauseQueue() },
                                onResume = { vm.resumeQueue() },
                                onSave = { vm.saveAll() }
                            )
                        }
                    }
                    item {
                        Text(
                            "Partes · ${s.segments.size}",
                            color = TextHi, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                        )
                    }
                    itemsIndexed(s.segments, key = { _, sg -> "${sg.index}-${sg.startMs}-${sg.endMs}" }) { _, sg ->
                        SegmentRow(
                            seg = sg,
                            total = s.segments.size,
                            progress = if (s.processing && s.currentIndex == sg.index) s.currentProgress else null,
                            isNextInQueue = s.queueActive && s.queueIndex == sg.index
                        ) {
                            sg.file?.let { f ->
                                val ok = Sharer.safeStart(context, Sharer.single(context, f, Sharer.packageFor(context, s.platform)))
                                if (ok) vm.markShared(sg.index)
                            }
                        }
                    }
                    item { TipsCard() }
                }
            }
        }
    }
}

/* ================= Componentes ================= */

@Composable
fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(HeroBrush),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Estados HD", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Corta · Optimiza · Publica en orden", color = TextLo, fontSize = 13.sp)
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(18.dp)
            .animateContentSize()
    ) { content() }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) BrandBrush else SolidColor(Panel2))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) Color.White else TextLo)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = if (enabled) Color.White else TextLo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun GhostButton(text: String, icon: ImageVector?, color: Color = TextHi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, null, tint = color); Spacer(Modifier.width(8.dp)) }
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun EmptyHero(loading: Boolean, onPick: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(HeroBrush),
                contentAlignment = Alignment.Center
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                else Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (loading) "Analizando tu video…" else "Sube tu video",
                color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "De cualquier tamaño o duración. Lo dividimos exacto para cada red social y lo dejamos en Full HD.",
                color = TextLo, fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            GradientButton("Elegir video", enabled = !loading, icon = Icons.Filled.PlayArrow, onClick = onPick)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Feature("✂️", "Cortes\nexactos")
                Feature("🎞️", "Full HD\nnítido")
                Feature("📤", "Publica\nen orden")
            }
        }
    }
}

@Composable
fun Feature(emoji: String, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(text, color = TextLo, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
    }
}

@Composable
fun VideoCard(info: VideoInfo, enabled: Boolean, onChange: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(86.dp)
                    .aspectRatio(if (info.height >= info.width) 9f / 16f else 16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Panel2),
                contentAlignment = Alignment.Center
            ) {
                val bmp = remember(info.thumb) { info.thumb?.asImageBitmap() }
                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.PlayArrow, null, tint = TextLo)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, color = TextHi, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(fmtTime(info.durationMs), Cyan)
                    Tag("${info.width}×${info.height}", Violet)
                }
                Spacer(Modifier.height(6.dp))
                Tag(fmtSize(info.sizeBytes), Pink)
            }
        }
        Spacer(Modifier.height(14.dp))
        GhostButton("Cambiar video", Icons.Filled.Refresh, if (enabled) TextHi else TextLo) { if (enabled) onChange() }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun Pill(text: String, selected: Boolean, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.18f) else Panel2)
            .border(1.5.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(7.dp))
        Text(
            text, color = if (selected) TextHi else TextLo, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun OptionBox(
    title: String, desc: String, selected: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Violet.copy(alpha = 0.16f) else Panel2)
            .border(1.5.dp, if (selected) Violet else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = if (selected) TextHi else TextLo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(desc, color = TextLo, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun RoundBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Panel2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (enabled) TextHi else TextLo, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SettingsCard(s: UiState, vm: MainViewModel) {
    val on = !s.processing
    Card {
        SectionTitle("Destino")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLATFORMS.forEach { p -> Pill(p.name, s.platform.id == p.id, p.color, on) { vm.selectPlatform(p) } }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Duración máxima por parte")
        Row(verticalAlignment = Alignment.CenterVertically) {
            val step = if (s.secondsPerPart > 60) 10 else 5
            RoundBtn("−", on) { vm.setSeconds(s.secondsPerPart - step) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.secondsPerPart} s", color = TextHi, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Text("límite de ${s.platform.name}", color = TextLo, fontSize = 12.sp)
            }
            RoundBtn("+", on) { vm.setSeconds(s.secondsPerPart + step) }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Partes iguales", color = TextHi, fontWeight = FontWeight.SemiBold)
                Text(
                    if (s.equalParts) "Reparte el video en partes del mismo largo (sin un final cortico)"
                    else "Partes al máximo y la última con lo que sobre",
                    color = TextLo, fontSize = 12.sp
                )
            }
            Switch(
                checked = s.equalParts, onCheckedChange = { vm.setEqual(it) }, enabled = on,
                colors = SwitchDefaults.colors(checkedTrackColor = Violet, checkedThumbColor = Color.White)
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Calidad")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quality.entries.forEach { q ->
                OptionBox(q.label, q.desc, s.quality == q, on, Modifier.weight(1f)) { vm.setQuality(q) }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Formato")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Frame.entries.forEach { f ->
                OptionBox(f.label, f.desc, s.frame == f, on, Modifier.weight(1f)) { vm.setFrame(f) }
            }
        }
    }
}

@Composable
fun ProcessCard(s: UiState, vm: MainViewModel) {
    val n = s.segments.size
    val avg = if (n > 0) s.segments.sumOf { it.durationMs } / n else 0L
    Card {
        when {
            s.processing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Procesando parte ${s.currentIndex + 1} de $n", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Mantén la app abierta · ${(s.overallProgress * 100).toInt()}%", color = TextLo, fontSize = 13.sp)
                    }
                    IconButton(onClick = { vm.cancel() }) { Icon(Icons.Filled.Close, "Cancelar", tint = Danger) }
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { s.overallProgress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                    color = Cyan, trackColor = Panel2
                )
            }
            s.allReady -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Mint, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("$n ${if (n == 1) "parte lista" else "partes listas"}", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${s.quality.desc} · H.264 · ${s.frame.label}", color = TextLo, fontSize = 13.sp)
                    }
                }
            }
            else -> {
                Text(
                    "Se crearán $n ${if (n == 1) "parte" else "partes"} de ~${fmtSecs(avg)}",
                    color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp
                )
                Text("Corte exacto al cuadro, sin perder segundos entre partes", color = TextLo, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                GradientButton(
                    "Crear $n ${if (n == 1) "parte" else "partes"} en HD",
                    enabled = n > 0, icon = Icons.Filled.PlayArrow
                ) { vm.process() }
            }
        }
    }
}

@Composable
fun PublishCard(
    s: UiState,
    countdown: Int,
    onAll: () -> Unit,
    onQueue: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSave: () -> Unit
) {
    val n = s.segments.size
    Card {
        SectionTitle("Publicar en ${s.platform.name}")
        Text(s.platform.note, color = TextLo, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        AnimatedVisibility(visible = s.queueActive) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Violet.copy(alpha = 0.14f))
                    .padding(14.dp)
            ) {
                Text(
                    if (countdown > 0) "Parte ${s.queueIndex + 1} de $n en $countdown…"
                    else "Publicando parte ${minOf(s.queueIndex + 1, n)} de $n",
                    color = TextHi, fontWeight = FontWeight.Bold
                )
                Text(
                    "Publica la parte y vuelve aquí: la siguiente se abre sola, siempre en orden.",
                    color = TextLo, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { s.queueIndex.toFloat() / n },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = Violet, trackColor = Panel2
                )
                Spacer(Modifier.height(10.dp))
                GhostButton("Pausar cola", Icons.Filled.Close, Amber, onPause)
            }
        }

        if (!s.queueActive) {
            GradientButton("Enviar todas en orden", icon = Icons.Filled.Send, onClick = onAll)
            Spacer(Modifier.height(10.dp))
            if (s.queueIndex in 1 until n) {
                GhostButton("Continuar cola (parte ${s.queueIndex + 1})", Icons.Filled.PlayArrow, Cyan, onResume)
            } else {
                GhostButton("Cola guiada: una por una", Icons.Filled.PlayArrow, Cyan, onQueue)
            }
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Guardar todas en la galería", Icons.Filled.KeyboardArrowDown, TextHi, onSave)
    }
}

@Composable
fun SegmentRow(seg: Segment, total: Int, progress: Int?, isNextInQueue: Boolean, onShare: () -> Unit) {
    val (label, color) = when (seg.status) {
        SegStatus.PENDING -> "En espera" to TextLo
        SegStatus.PROCESSING -> "Procesando ${progress ?: 0}%" to Cyan
        SegStatus.READY -> "Lista" to Mint
        SegStatus.SHARED -> "Enviada" to Violet
        SegStatus.ERROR -> "Error" to Danger
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .border(
                if (isNextInQueue) 2.dp else 1.dp,
                if (isNextInQueue) Violet else Line,
                RoundedCornerShape(20.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(58.dp).height(78.dp).clip(RoundedCornerShape(12.dp)).background(Panel2),
            contentAlignment = Alignment.Center
        ) {
            val bmp: Bitmap? = seg.thumb
            val img = remember(bmp) { bmp?.asImageBitmap() }
            if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) { Text("${seg.index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Parte ${seg.index + 1} de $total", color = TextHi, fontWeight = FontWeight.Bold)
            Text("${fmtTime(seg.startMs)} – ${fmtTime(seg.endMs)} · ${fmtSecs(seg.durationMs)}", color = TextLo, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (seg.status) {
                    SegStatus.READY, SegStatus.SHARED -> Icon(Icons.Filled.Done, null, tint = color, modifier = Modifier.size(14.dp))
                    SegStatus.ERROR -> Icon(Icons.Filled.Warning, null, tint = color, modifier = Modifier.size(14.dp))
                    else -> {}
                }
                Spacer(Modifier.width(4.dp))
                Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    color = Cyan, trackColor = Panel2
                )
            }
        }
        if (seg.file != null) {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Compartir", tint = Cyan) }
        }
    }
}

@Composable
fun TipsCard() {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = Amber)
            Spacer(Modifier.width(8.dp))
            Text("Para que se vea lo más nítido", color = TextHi, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            "WhatsApp siempre vuelve a comprimir los estados. Esta app le entrega el mejor archivo posible (Full HD, alto bitrate, sin HDR) para que esa compresión dañe lo mínimo.",
            "Si en el editor de WhatsApp aparece el botón «HD», actívalo antes de publicar.",
            "Publica con Wi-Fi: con datos móviles algunas apps bajan más la calidad.",
            "WhatsApp sube los estados en el mismo orden en que los envías, así que la parte 2 nunca sale antes que la 1."
        ).forEach {
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("•", color = Violet, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(it, color = TextLo, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
