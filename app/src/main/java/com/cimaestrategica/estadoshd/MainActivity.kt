package com.cimaestrategica.estadoshd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.delay

const val APP_VERSION = "3.2"
const val AUTHOR = "Hugo Cataño"
const val AUTHOR_WEB = "https://hugocatano.com"

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val dlVm: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { EstadosTheme { AppScreen(vm, dlVm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (stream != null && intent.type?.startsWith("video") == true) vm.load(stream)
            else if (text != null) dlVm.openWithText(text)
        }
    }
}

/* ======================= Raíz ======================= */

@Composable
fun AppScreen(vm: MainViewModel, dlVm: DownloadViewModel) {
    val s by vm.state.collectAsState()
    val d by dlVm.state.collectAsState()
    var section by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(d.openRequest) { if (d.openRequest > 0) section = 1 }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showAbout by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.load(uri)
    }
    val queueLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onQueueReturned()
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { dlVm.messages.collect { snackbar.showSnackbar(it) } }

    val view = LocalView.current
    DisposableEffect(s.processing) {
        view.keepScreenOn = s.processing
        onDispose { view.keepScreenOn = false }
    }

    var countdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.queueActive, s.queueIndex, s.awaitingConfirm, s.launchedIndex) {
        if (!s.queueActive || s.awaitingConfirm) { countdown = 0; return@LaunchedEffect }
        val i = s.queueIndex
        if (i >= s.segments.size || s.launchedIndex == i) return@LaunchedEffect
        countdown = 1
        delay(700)
        countdown = 0
        val f = s.segments.getOrNull(i)?.file ?: return@LaunchedEffect
        vm.markLaunched(i)
        try {
            val qp = Sharer.packageFor(context, s.platform)
            queueLauncher.launch(
                Sharer.single(context, f, qp, if (s.platform.isWhatsApp && qp != null) Sharer.STATUS_JID else null)
            )
        } catch (e: Exception) {
            vm.pauseQueue()
            vm.emit("No se pudo abrir la app de destino")
        }
    }

    val info = s.info
    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (info == null) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        if (section == 0) HomeScreen(s.loading, onPick = { pick() }, onAbout = { showAbout = true })
                        else DownloaderScreen(d, dlVm, onAbout = { showAbout = true }, onCut = { f ->
                            vm.load(Uri.fromFile(f)); section = 0
                        })
                    }
                    BottomNav(section) { section = it }
                }
            }
            else EditorScreen(s, vm, info, onPick = { pick() }, onAbout = { showAbout = true }, countdown = countdown)
        }
    }

    if (showAbout) AboutDialog { showAbout = false }
    val login = d.loginAccount
    if (login != null) LoginOverlay(login) { dlVm.finishLogin(it) }
}

/* ======================= Inicio ======================= */

@Composable
fun HomeScreen(loading: Boolean, onPick: () -> Unit, onAbout: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.55f), cs.background, cs.background)))
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(onAbout, trailing = null)
        Spacer(Modifier.weight(1f))
        HeroArt(loading)
        Spacer(Modifier.height(28.dp))
        Text(
            if (loading) "Analizando tu video…" else "Tus estados en HD,\nnítidos y en orden",
            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = cs.onBackground,
            textAlign = TextAlign.Center, lineHeight = 36.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Corta tu video al cuadro exacto para cada red social y optimízalo para que nunca se vea borroso.",
            fontSize = 15.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 21.sp
        )
        Spacer(Modifier.height(26.dp))
        Button3D("Elegir video", R.drawable.ms_video_library, Modifier.fillMaxWidth(), enabled = !loading, onClick = onPick)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureTile(R.drawable.ms_hd, GBlue, "HD Status", "Anti-borroso", Modifier.weight(1f))
            FeatureTile(R.drawable.ms_content_cut, GRed, "Corte exacto", "Al cuadro", Modifier.weight(1f))
            FeatureTile(R.drawable.ms_playlist_play, GGreen, "En orden", "Parte por parte", Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Credits()
    }
}

@Composable
fun HeroArt(loading: Boolean) {
    val t = rememberInfiniteTransition(label = "hero")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "f")
    Box(Modifier.width(260.dp).height(200.dp), contentAlignment = Alignment.Center) {
        StatusCardArt(GGreen, -16f + 3f * f, (-66).dp, 0.86f, null)
        StatusCardArt(GRed, 16f - 3f * f, 66.dp, 0.86f, null)
        Box(Modifier.scale(0.98f + 0.04f * f)) {
            StatusCardArt(GBlue, 0f, 0.dp, 1f, if (loading) null else R.drawable.ms_play_arrow, loading)
        }
    }
}

@Composable
fun StatusCardArt(color: Color, rotation: Float, x: Dp, scale: Float, icon: Int?, loading: Boolean = false) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .offset(x = x)
            .rotate(rotation)
            .scale(scale)
            .width(112.dp)
            .height(176.dp)
            .shadow(22.dp, shape, ambientColor = color.copy(alpha = 0.5f), spotColor = color.copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.22f), lerp(color, Color.Black, 0.18f))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.05f))), shape)
    ) {
        Box(
            Modifier.fillMaxWidth().height(110.dp)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent)))
        )
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = if (i == 0) 0.95f else 0.45f))
                )
            }
        }
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(40.dp))
        } else if (icon != null) {
            Box(
                Modifier.align(Alignment.Center).size(58.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(36.dp)) }
        }
    }
}

@Composable
fun FeatureTile(icon: Int, color: Color, title: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surface)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon3D(icon, color, 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onSurface, textAlign = TextAlign.Center)
        Text(sub, fontSize = 11.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun TopBar(onAbout: () -> Unit, trailing: (@Composable () -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon3D(R.drawable.ms_play_arrow, GBlue, 40.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Estados HD", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
            Text("by $AUTHOR", fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        if (trailing != null) trailing()
        IconButton(onClick = onAbout) {
            Icon(painterResource(R.drawable.ms_info), "Acerca de", tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun Credits() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("DESARROLLADA POR", fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(AUTHOR, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .shadow(2.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(cs.surface)
                .clickable { Sharer.openWeb(ctx, AUTHOR_WEB) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ms_language), null, tint = cs.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("hugocatano.com", color = cs.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AboutDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon3D(R.drawable.ms_play_arrow, GBlue, 56.dp) },
        title = { Text("Estados HD $APP_VERSION", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Divide tus videos al cuadro exacto para WhatsApp, Instagram, Facebook, TikTok y YouTube, " +
                        "los optimiza en Full HD con el peso justo para que no se vean borrosos y te deja publicarlos en orden.",
                    fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text("Desarrollada por", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Text(AUTHOR, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                Text("Consultor en gestión empresarial y estrategia digital", fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = { Sharer.openWeb(ctx, AUTHOR_WEB) }) { Text("hugocatano.com") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cerrar") } }
    )
}

/* ======================= Editor ======================= */

@Composable
fun EditorScreen(s: UiState, vm: MainViewModel, info: VideoInfo, onPick: () -> Unit, onAbout: () -> Unit, countdown: Int) {
    val context = LocalContext.current
    val pkg = Sharer.packageFor(context, s.platform)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showPublish by remember { mutableStateOf(false) }

    LaunchedEffect(s.allReady) { if (s.allReady) tab = 3 }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            TopBar(onAbout) {
                TextButton(onClick = { vm.clearVideo() }, enabled = !s.processing) {
                    Icon(painterResource(R.drawable.ms_add), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo", fontWeight = FontWeight.Bold)
                }
            }
            VideoHeader(info, enabled = !s.processing, onChange = onPick)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val n = s.segments.size
                val avg = if (n > 0) s.segments.sumOf { it.durationMs } / n else 0L
                StatTile(R.drawable.ms_view_agenda, GBlue, "$n", if (n == 1) "parte" else "partes", Modifier.weight(1f))
                StatTile(R.drawable.ms_timer, GYellow, fmtSecs(avg), "por parte", Modifier.weight(1f))
                StatTile(
                    R.drawable.ms_hd, GGreen,
                    if (s.totalOutBytes > 0) fmtSize(s.totalOutBytes / maxOf(1, n)) else "~${"%.0f".format(s.estimatedMbPerPart)} MB",
                    "peso c/u", Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            PillTabs(
                listOf(
                    "Destino" to R.drawable.ms_public,
                    "Calidad" to R.drawable.ms_high_quality,
                    "Recorte" to R.drawable.ms_content_cut,
                    "Partes" to R.drawable.ms_view_agenda
                ),
                tab
            ) { tab = it }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> DestinationTab(s, vm)
                    1 -> QualityTab(s, vm)
                    2 -> TrimTab(s, vm, info)
                    else -> PartsTab(s, context, pkg, vm)
                }
            }
        }
        ActionBar(s, vm, countdown, onPublish = { showPublish = true })
    }

    if (showPublish) {
        PublishSheet(
            s = s,
            onDismiss = { showPublish = false },
            onMyChat = {
                val files = s.segments.mapNotNull { it.file }
                if (pkg == null) vm.emit("No encontré WhatsApp instalado")
                else if (Sharer.safeStart(context, Sharer.multiple(context, files, pkg, Sharer.jidOf(s.myNumber)))) vm.markAllShared()
                showPublish = false
            },
            onAll = {
                val files = s.segments.mapNotNull { it.file }
                if (Sharer.safeStart(context, Sharer.multiple(context, files, pkg))) vm.markAllShared()
                else vm.emit("No se encontró una app para publicar")
                showPublish = false
            },
            onDirectStatus = {
                val files = s.segments.mapNotNull { it.file }
                if (pkg == null) vm.emit("No encontré WhatsApp instalado")
                else if (Sharer.safeStart(context, Sharer.multiple(context, files, pkg, Sharer.STATUS_JID))) vm.markAllShared()
                showPublish = false
            },
            onQueue = {
                showPublish = false
                if (s.queueIndex in 1 until s.segments.size) vm.resumeQueue() else vm.startQueue()
            },
            onSave = { vm.saveAll(); showPublish = false },
            onNumber = { vm.setMyNumber(it) }
        )
    }
}

@Composable
fun VideoHeader(info: VideoInfo, enabled: Boolean, onChange: () -> Unit) {
    AppCard(padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(54.dp).height(72.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val bmp = remember(info.thumb) { info.thumb?.asImageBitmap() }
                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${fmtTime(info.durationMs)} · ${info.width}×${info.height} · ${fmtSize(info.sizeBytes)}",
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )
            }
            Box(Modifier.clickable(enabled = enabled, onClick = onChange)) {
                Icon3D(R.drawable.ms_refresh, if (enabled) GPurple else Color.Gray, 38.dp)
            }
        }
    }
}

@Composable
fun TabScroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) { content() }
}

@Composable
fun DestinationTab(s: UiState, vm: MainViewModel) {
    val on = !s.processing
    TabScroll {
        PLATFORMS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p -> PlatformTile(p, s.platform.id == p.id, on, Modifier.weight(1f)) { vm.selectPlatform(p) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(painterResource(R.drawable.ms_info), null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.platform.note, fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 17.sp)
        }
    }
}

@Composable
fun PlatformTile(p: Platform, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .shadow(if (selected) 6.dp else 1.dp, shape, spotColor = p.color)
            .clip(shape)
            .background(if (selected) lerp(cs.surface, p.color, 0.10f) else cs.surface)
            .border(if (selected) 2.dp else 0.dp, if (selected) p.color else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon3D(platformIcon(p), p.color, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.short, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (p.badge != null) "${p.maxSeconds} s · Recomendado" else "${p.maxSeconds} s por parte",
                fontSize = 11.sp, color = if (p.badge != null) p.color else cs.onSurfaceVariant, maxLines = 1
            )
        }
    }
}

@Composable
fun QualityTab(s: UiState, vm: MainViewModel) {
    val on = !s.processing
    TabScroll {
        AppCard(padding = 12.dp) {
            SettingRow(R.drawable.ms_timer, GYellow, "Duración por parte", "Límite de ${s.platform.short}") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val step = if (s.secondsPerPart > 60) 10 else 5
                    StepperBtn("−", on) { vm.setSeconds(s.secondsPerPart - step) }
                    Text(
                        "${s.secondsPerPart}s", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                        color = cs.onSurface, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center
                    )
                    StepperBtn("+", on) { vm.setSeconds(s.secondsPerPart + step) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Calidad", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        SegChoice(Quality.entries.map { it.label to it.desc }, Quality.entries.indexOf(s.quality), on) { vm.setQuality(Quality.entries[it]) }
        if (s.quality == Quality.HD_STATUS) {
            Text(
                "Cada parte queda en ~${"%.1f".format(s.estimatedMbPerPart)} MB, el peso que WhatsApp acepta sin aplastar la imagen.",
                fontSize = 12.sp, color = GGreen, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Formato", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        SegChoice(Frame.entries.map { it.label to it.desc }, Frame.entries.indexOf(s.frame), on) { vm.setFrame(Frame.entries[it]) }
        Spacer(Modifier.height(10.dp))
        AppCard(padding = 12.dp) {
            ToggleSetting(R.drawable.ms_splitscreen, GBlue, "Partes iguales", "Todas del mismo largo", s.equalParts, on) { vm.setEqual(it) }
            ToggleSetting(R.drawable.ms_speed, GTeal, "Fluidez 30 fps", "Más nitidez por cuadro", s.cap30fps, on) { vm.setCap30(it) }
            ToggleSetting(R.drawable.ms_volume_off, GRed, "Quitar audio", "Para poner música en la red", s.muteAudio, on) { vm.setMute(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimTab(s: UiState, vm: MainViewModel, info: VideoInfo) {
    val dur = info.durationMs.toFloat().coerceAtLeast(1000f)
    var range by remember(info.uri, s.trimStartMs, s.trimEndMs) {
        mutableStateOf(s.trimStartMs.toFloat()..(if (s.trimEndMs > 0) s.trimEndMs.toFloat() else dur))
    }
    TabScroll {
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(R.drawable.ms_play_arrow, GGreen, fmtTime(range.start.toLong()), "inicio", Modifier.weight(1f))
                StatTile(R.drawable.ms_timer, GBlue, fmtTime((range.endInclusive - range.start).toLong()), "seleccionado", Modifier.weight(1f))
                StatTile(R.drawable.ms_pause, GRed, fmtTime(range.endInclusive.toLong()), "final", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            RangeSlider(
                value = range,
                onValueChange = { range = it },
                valueRange = 0f..dur,
                enabled = !s.processing,
                onValueChangeFinished = { vm.setTrim(range.start.toLong(), range.endInclusive.toLong()) }
            )
            Text("Arrastra los extremos para quitar el inicio o el final del video.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Tonal3D("Usar video completo", R.drawable.ms_refresh, Modifier.fillMaxWidth(), enabled = !s.processing) {
                vm.setTrim(0, info.durationMs)
            }
        }
    }
}

@Composable
fun PartsTab(s: UiState, context: android.content.Context, pkg: String?, vm: MainViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(s.segments, key = { _, sg -> "${sg.index}-${sg.startMs}-${sg.endMs}" }) { _, sg ->
            SegmentRow(
                seg = sg,
                total = s.segments.size,
                progress = if (s.processing && s.currentIndex == sg.index) s.currentProgress else null,
                isNext = s.queueActive && s.queueIndex == sg.index,
                onPreview = { sg.file?.let { Sharer.safeStart(context, Sharer.preview(context, it)) } },
                onShare = { sg.file?.let { f -> if (Sharer.safeStart(context, Sharer.single(context, f, pkg))) vm.markShared(sg.index) } }
            )
        }
    }
}

@Composable
fun SegmentRow(seg: Segment, total: Int, progress: Int?, isNext: Boolean, onPreview: () -> Unit, onShare: () -> Unit) {
    val (label, color) = when (seg.status) {
        SegStatus.PENDING -> "En espera" to cs.onSurfaceVariant
        SegStatus.PROCESSING -> "Creando ${progress ?: 0}%" to GBlue
        SegStatus.READY -> "Lista" to GGreen
        SegStatus.SHARED -> "Enviada" to GPurple
        SegStatus.ERROR -> "Error" to GRed
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(if (isNext) 6.dp else 1.dp, shape, spotColor = GBlue)
            .clip(shape)
            .background(cs.surface)
            .border(if (isNext) 2.dp else 0.dp, if (isNext) GBlue else Color.Transparent, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(48.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant)
                .clickable(enabled = seg.file != null, onClick = onPreview),
            contentAlignment = Alignment.Center
        ) {
            val img = remember(seg.thumb) { seg.thumb?.asImageBitmap() }
            if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopStart).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(GBlue)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) { Text("${seg.index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Parte ${seg.index + 1} de $total", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
            Text(
                "${fmtTime(seg.startMs)}–${fmtTime(seg.endMs)} · ${fmtSecs(seg.durationMs)}" +
                    if (seg.sizeBytes > 0) " · ${fmtSize(seg.sizeBytes)}" else "",
                fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1
            )
            Spacer(Modifier.height(5.dp))
            StatusChip(label, color)
            if (progress != null) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50))
                )
            }
        }
        if (seg.file != null) {
            Box(Modifier.clickable(onClick = onPreview)) { Icon3D(R.drawable.ms_play_arrow, GTeal, 36.dp) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clickable(onClick = onShare)) { Icon3D(R.drawable.ms_share, GBlue, 36.dp) }
        }
    }
}

/* ======================= Barra de acción inferior ======================= */

@Composable
fun ActionBar(s: UiState, vm: MainViewModel, countdown: Int, onPublish: () -> Unit) {
    val n = s.segments.size
    Surface(
        color = cs.surface,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            when {
                s.processing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { s.overallProgress }, modifier = Modifier.size(52.dp),
                                strokeWidth = 5.dp, trackColor = cs.surfaceVariant
                            )
                            Text("${(s.overallProgress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Creando parte ${s.currentIndex + 1} de $n", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
                            Text("Mantén la app abierta", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        Box(Modifier.clickable { vm.cancel() }) { Icon3D(R.drawable.ms_close, GRed, 40.dp) }
                    }
                }
                s.queueActive && s.awaitingConfirm -> {
                    val cur = s.queueIndex + 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon3D(R.drawable.ms_hourglass_top, GYellow, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("¿La parte $cur de $n ya terminó de subir?", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = cs.onSurface)
                            Text(
                                "En WhatsApp › Novedades › Mi estado, debe verse sin el relojito. Hasta que no confirmes, la parte ${cur + 1} no se envía.",
                                fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button3D(
                            if (cur >= n) "Sí, terminar" else "Sí, enviar parte ${cur + 1}",
                            R.drawable.ms_check_circle, Modifier.weight(1f), face = GGreen
                        ) { vm.confirmUploaded() }
                        Spacer(Modifier.width(8.dp))
                        Button3D(null, R.drawable.ms_refresh, Modifier.width(60.dp), face = cs.primaryContainer, content = cs.onPrimaryContainer) {
                            vm.retryCurrent()
                        }
                        Spacer(Modifier.width(8.dp))
                        Button3D(null, R.drawable.ms_pause, Modifier.width(60.dp), face = cs.surfaceVariant, content = cs.onSurfaceVariant) {
                            vm.pauseQueue()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.queueIndex.toFloat() / maxOf(1, n) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                    )
                }
                s.queueActive -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon3D(R.drawable.ms_playlist_play, GBlue, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Abriendo parte ${minOf(s.queueIndex + 1, n)} de $n…", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = cs.onSurface)
                            Text("Publícala en Mi estado y vuelve a Estados HD", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        Box(Modifier.clickable { vm.pauseQueue() }) { Icon3D(R.drawable.ms_pause, GYellow, 40.dp) }
                    }
                }
                s.allReady -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button3D(
                            "Publicar $n ${if (n == 1) "parte" else "partes"}", R.drawable.ms_send,
                            Modifier.weight(1f),
                            face = if (s.platform.isWhatsApp) GGreen else GBlue,
                            onClick = onPublish
                        )
                        Spacer(Modifier.width(10.dp))
                        Button3D(null, R.drawable.ms_download, Modifier.width(64.dp), face = cs.primaryContainer, content = cs.onPrimaryContainer) {
                            vm.saveAll()
                        }
                    }
                }
                else -> {
                    Button3D(
                        "Crear $n ${if (n == 1) "parte" else "partes"} en HD", R.drawable.ms_auto_awesome,
                        Modifier.fillMaxWidth(), enabled = n > 0
                    ) { vm.process() }
                }
            }
        }
    }
}

/* ======================= Hoja de publicación ======================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishSheet(
    s: UiState,
    onDismiss: () -> Unit,
    onMyChat: () -> Unit,
    onAll: () -> Unit,
    onDirectStatus: () -> Unit,
    onQueue: () -> Unit,
    onSave: () -> Unit,
    onNumber: (String) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val n = s.segments.size
    var showForward by remember { mutableStateOf(false) }
    val queueLabel = if (s.queueIndex in 1 until n) "Continuar (parte ${s.queueIndex + 1})" else "Una por una, en orden"
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = cs.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon3D(platformIcon(s.platform), s.platform.color, 50.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Publicar en ${s.platform.short}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text("$n ${if (n == 1) "parte lista" else "partes listas"} · ${fmtSize(s.totalOutBytes)}", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(18.dp))

            if (s.platform.isWhatsApp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(lerp(cs.surface, GGreen, 0.07f))
                        .border(1.dp, GGreen.copy(alpha = 0.30f), RoundedCornerShape(22.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon3D(R.drawable.ms_verified, GGreen, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Orden garantizado", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
                            Text("Recomendado · directo a Mi estado", fontSize = 12.sp, color = GGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    NumberedStep(1, "La app abre Mi estado solo con la parte 1. Publícala.", GGreen)
                    NumberedStep(2, "Espera a que termine de subir (sin relojito) y vuelve.", GGreen)
                    NumberedStep(3, "Confirma y recién ahí se abre la parte 2. Y así hasta el final.", GGreen)
                    Spacer(Modifier.height(12.dp))
                    Button3D(
                        if (s.queueIndex in 1 until n) "Continuar con la parte ${s.queueIndex + 1}" else "Publicar en orden garantizado",
                        R.drawable.ms_playlist_play, Modifier.fillMaxWidth(), face = WaGreen, onClick = onQueue
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon3D(R.drawable.ms_upload, GYellow, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Todas de una vez (rápido)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = cs.onSurface)
                        Text("WhatsApp las sube al tiempo y pueden quedar en desorden", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    TextButton(onClick = onDirectStatus) { Text("Subir") }
                }
                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showForward = !showForward }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon3D(R.drawable.ms_forward, GPurple, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Método alterno: reenviar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = cs.onSurface)
                        Text("Si tu WhatsApp comprime más al subir directo", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    Text(if (showForward) "Ocultar" else "Ver", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (showForward) {
                    Spacer(Modifier.height(8.dp))
                    NumberedStep(1, "Envía las partes a tu propio chat", GPurple)
                    NumberedStep(2, "Mantén presionado → Reenviar → Mi estado", GPurple)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = s.myNumber,
                        onValueChange = onNumber,
                        singleLine = true,
                        label = { Text("Tu número con indicativo (ej. 573001234567)") },
                        leadingIcon = { Icon(painterResource(R.drawable.ms_call), null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button3D("Enviar a mi chat", R.drawable.ms_send, Modifier.fillMaxWidth(), face = GPurple, enabled = s.myNumber.length >= 8, onClick = onMyChat)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onAll, modifier = Modifier.fillMaxWidth()) { Text("Elegir chat o destino manualmente") }
                }
            } else {
                Text(s.platform.note, fontSize = 13.sp, color = cs.onSurfaceVariant, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Button3D("Publicar en orden garantizado", R.drawable.ms_playlist_play, Modifier.fillMaxWidth(), face = s.platform.color, onClick = onQueue)
                Spacer(Modifier.height(10.dp))
                Tonal3D("Todas de una vez (puede desordenarse)", R.drawable.ms_send, Modifier.fillMaxWidth(), onClick = onAll)
            }
            Spacer(Modifier.height(10.dp))
            Tonal3D("Guardar todas en la galería", R.drawable.ms_download, Modifier.fillMaxWidth(), onClick = onSave)
        }
    }
}
