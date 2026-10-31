package com.cimaestrategica.estadoshd

import android.content.Intent
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.delay

const val APP_VERSION = "2.0"
const val AUTHOR = "Hugo Cataño"
const val AUTHOR_WEB = "https://hugocatano.com"

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

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.load(it) }
        }
    }
}

/* ======================= Raíz ======================= */

@Composable
fun AppScreen(vm: MainViewModel) {
    val s by vm.state.collectAsState()
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

    val view = LocalView.current
    DisposableEffect(s.processing) {
        view.keepScreenOn = s.processing
        onDispose { view.keepScreenOn = false }
    }

    var countdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.queueActive, s.queueIndex) {
        if (!s.queueActive) { countdown = 0; return@LaunchedEffect }
        val i = s.queueIndex
        if (i >= s.segments.size || s.launchedIndex == i) return@LaunchedEffect
        if (i > 0) { for (c in 3 downTo 1) { countdown = c; delay(1000) } }
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

    Box(Modifier.fillMaxSize().background(Ink)) {
        AnimatedBackdrop()
        val info = s.info
        if (info == null) {
            HomeScreen(loading = s.loading, onPick = { pick() }, onAbout = { showAbout = true })
        } else {
            EditorScreen(
                s = s, vm = vm, info = info,
                onPick = { pick() },
                onAbout = { showAbout = true },
                countdown = countdown
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
    }

    if (showAbout) AboutDialog { showAbout = false }
}

/* ======================= Fondo animado ======================= */

@Composable
fun AnimatedBackdrop() {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    Canvas(Modifier.fillMaxSize()) {
        fun orb(cx: Float, cy: Float, r: Float, c: Color, alpha: Float) {
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r, center = Offset(cx, cy)
            )
        }
        val w = size.width
        val h = size.height
        orb(w * (0.1f + 0.3f * a), h * 0.10f, w * 0.95f, Violet, 0.42f)
        orb(w * (0.95f - 0.3f * a), h * (0.42f + 0.12f * a), w * 0.80f, Cyan, 0.22f)
        orb(w * (0.25f + 0.35f * a), h * 0.95f, w * 0.90f, Pink, 0.26f)
    }
}

/* ======================= Inicio ======================= */

@Composable
fun HomeScreen(loading: Boolean, onPick: () -> Unit, onAbout: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "p"
    )
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(onAbout = onAbout, action = null)
        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(170.dp).scale(pulse).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Violet.copy(alpha = 0.45f), Color.Transparent)))
            )
            Box(
                Modifier.size(112.dp).clip(RoundedCornerShape(34.dp)).background(HeroBrush)
                    .clickable(enabled = !loading, onClick = onPick),
                contentAlignment = Alignment.Center
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                else Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            if (loading) "Analizando tu video…" else "Tus estados en HD,\nnítidos y en orden",
            style = TextStyle(brush = TitleBrush, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Sube un video de cualquier tamaño. Lo cortamos exacto para cada red y lo dejamos listo para que no se vea borroso.",
            color = TextLo, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp
        )
        Spacer(Modifier.height(26.dp))
        GradientButton("Elegir video", enabled = !loading, icon = Icons.Filled.PlayArrow, onClick = onPick)
        Spacer(Modifier.height(8.dp))
        Text("o compártelo desde tu galería → Estados HD", color = TextLo, fontSize = 12.sp)

        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureTile("✨", "HD Status", "Anti-borroso", Modifier.weight(1f))
            FeatureTile("✂️", "Corte exacto", "Al cuadro", Modifier.weight(1f))
            FeatureTile("📤", "En orden", "1 → 2 → 3", Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Credits()
    }
}

@Composable
fun FeatureTile(emoji: String, title: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Glass)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(sub, color = TextLo, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun TopBar(onAbout: () -> Unit, action: (@Composable () -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(HeroBrush),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Estados HD", color = TextHi, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text("by $AUTHOR", color = TextLo, fontSize = 12.sp)
        }
        if (action != null) action()
        IconButton(onClick = onAbout) { Icon(Icons.Filled.Info, "Acerca de", tint = TextLo) }
    }
}

@Composable
fun Credits() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("DESARROLLADA POR", color = TextLo, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(
            AUTHOR,
            style = TextStyle(brush = BrandBrush, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Glass)
                .border(1.dp, Line, RoundedCornerShape(50))
                .clickable { Sharer.openWeb(ctx, AUTHOR_WEB) }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌐", fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("hugocatano.com", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AboutDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("Estados HD $APP_VERSION", color = TextHi, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "Divide tus videos al cuadro exacto para WhatsApp, Instagram, Facebook, TikTok y YouTube, " +
                        "los optimiza en Full HD con el peso justo para que no se vean borrosos y te deja publicarlos en orden.",
                    color = TextLo, fontSize = 14.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(14.dp))
                Text("Desarrollada por", color = TextLo, fontSize = 12.sp)
                Text(AUTHOR, style = TextStyle(brush = BrandBrush, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
                Text("Consultor en gestión empresarial y estrategia digital", color = TextLo, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { Sharer.openWeb(ctx, AUTHOR_WEB) }) { Text("hugocatano.com", color = Cyan) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cerrar", color = TextLo) } }
    )
}

/* ======================= Editor ======================= */

@Composable
fun EditorScreen(
    s: UiState,
    vm: MainViewModel,
    info: VideoInfo,
    onPick: () -> Unit,
    onAbout: () -> Unit,
    countdown: Int
) {
    val context = LocalContext.current
    val pkg = Sharer.packageFor(context, s.platform)

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopBar(onAbout = onAbout, action = {
                TextButton(onClick = { vm.clearVideo() }, enabled = !s.processing) {
                    Text("Nuevo", color = if (s.processing) TextLo else Cyan, fontWeight = FontWeight.Bold)
                }
            })
        }
        item { StepsBar(if (s.allReady) 3 else if (s.processing) 2 else 1) }
        item { VideoCard(info, s, enabled = !s.processing, onChange = onPick) }
        item { DestinationCard(s, vm) }
        item { TrimCard(s, vm, info) }
        item { AdjustCard(s, vm) }
        item { ProcessCard(s, vm) }
        if (s.allReady) {
            item {
                PublishCard(
                    s = s,
                    countdown = countdown,
                    onMyChat = {
                        val files = s.segments.mapNotNull { it.file }
                        if (pkg == null) vm.emit("No encontré WhatsApp instalado")
                        else if (Sharer.safeStart(context, Sharer.multiple(context, files, pkg, s.myNumber))) vm.markAllShared()
                    },
                    onAll = {
                        val files = s.segments.mapNotNull { it.file }
                        if (Sharer.safeStart(context, Sharer.multiple(context, files, pkg))) vm.markAllShared()
                        else vm.emit("No se encontró una app para publicar")
                    },
                    onQueue = { vm.startQueue() },
                    onPause = { vm.pauseQueue() },
                    onResume = { vm.resumeQueue() },
                    onSave = { vm.saveAll() },
                    onNumber = { vm.setMyNumber(it) }
                )
            }
        }
        item {
            Row(Modifier.padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Partes", color = TextHi, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Spacer(Modifier.width(8.dp))
                Tag("${s.segments.size}", Violet)
                Spacer(Modifier.weight(1f))
                if (s.totalOutBytes > 0) Text("Total ${fmtSize(s.totalOutBytes)}", color = TextLo, fontSize = 12.sp)
            }
        }
        itemsIndexed(s.segments, key = { _, sg -> "${sg.index}-${sg.startMs}-${sg.endMs}" }) { _, sg ->
            SegmentRow(
                seg = sg,
                total = s.segments.size,
                progress = if (s.processing && s.currentIndex == sg.index) s.currentProgress else null,
                isNextInQueue = s.queueActive && s.queueIndex == sg.index,
                onPreview = { sg.file?.let { Sharer.safeStart(context, Sharer.preview(context, it)) } }
            ) {
                sg.file?.let { f ->
                    if (Sharer.safeStart(context, Sharer.single(context, f, pkg))) vm.markShared(sg.index)
                }
            }
        }
        item { TipsCard() }
        item { Credits() }
    }
}

@Composable
fun StepsBar(step: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .border(1.dp, Line, RoundedCornerShape(50))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Ajustar", "Crear HD", "Publicar").forEachIndexed { i, label ->
            val n = i + 1
            val active = n == step
            val done = n < step
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) BrandBrush else SolidColor(Color.Transparent))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (done) "✓" else "$n",
                    color = if (active) Color.White else if (done) Mint else TextLo,
                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp
                )
                Spacer(Modifier.width(5.dp))
                Text(label, color = if (active) Color.White else TextLo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/* ======================= Piezas base ======================= */

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Glass)
            .border(1.dp, Line, RoundedCornerShape(26.dp))
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
    brush: Brush = BrandBrush,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (enabled) brush else SolidColor(Panel2))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) Color.White else TextLo)
                Spacer(Modifier.width(9.dp))
            }
            Text(text, color = if (enabled) Color.White else TextLo, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun GhostButton(text: String, icon: ImageVector?, color: Color = TextHi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.5.dp, Line, RoundedCornerShape(17.dp))
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
    Text(text.uppercase(), color = TextLo, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun RoundBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Panel2)
            .border(1.dp, Line, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (enabled) TextHi else TextLo, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun ToggleRow(title: String, desc: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = TextLo, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Violet, checkedThumbColor = Color.White,
                uncheckedTrackColor = Panel2, uncheckedBorderColor = Line
            )
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
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) Violet.copy(alpha = 0.18f) else Panel2.copy(alpha = 0.7f))
            .border(1.5.dp, if (selected) Violet else Line, RoundedCornerShape(17.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = if (selected) TextHi else TextLo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(desc, color = TextLo, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/* ======================= Tarjetas ======================= */

@Composable
fun VideoCard(info: VideoInfo, s: UiState, enabled: Boolean, onChange: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Glass)
            .border(1.dp, Line, RoundedCornerShape(26.dp))
    ) {
        Box(
            Modifier.fillMaxWidth().height(190.dp).background(Panel2),
            contentAlignment = Alignment.Center
        ) {
            val bmp = remember(info.thumb) { info.thumb?.asImageBitmap() }
            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Ink.copy(alpha = 0.92f)))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(info.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(fmtTime(info.durationMs), Cyan)
                    Tag("${info.width}×${info.height}", Violet)
                    Tag(fmtSize(info.sizeBytes), Pink)
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Ink.copy(alpha = 0.65f))
                    .clickable(enabled = enabled, onClick = onChange)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Cambiar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Seleccionado: ${fmtTime(s.selectedMs)}", color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                "${s.segments.size} ${if (s.segments.size == 1) "parte" else "partes"}",
                color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DestinationCard(s: UiState, vm: MainViewModel) {
    val on = !s.processing
    Card {
        SectionTitle("¿Dónde lo vas a publicar?")
        PLATFORMS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    PlatformTile(p, s.platform.id == p.id, on, Modifier.weight(1f)) { vm.selectPlatform(p) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(s.platform.note, color = TextLo, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
fun PlatformTile(p: Platform, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) p.color.copy(alpha = 0.17f) else Panel2.copy(alpha = 0.65f))
            .border(1.5.dp, if (selected) p.color else Line, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(p.color))
            Spacer(Modifier.width(7.dp))
            Text(
                p.short, color = if (selected) TextHi else TextLo, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(3.dp))
        Text("${p.maxSeconds} s por parte", color = TextLo, fontSize = 11.sp)
        if (p.badge != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                "★ ${p.badge}", color = p.color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimCard(s: UiState, vm: MainViewModel, info: VideoInfo) {
    val dur = info.durationMs.toFloat().coerceAtLeast(1000f)
    var range by remember(info.uri, s.trimStartMs, s.trimEndMs) {
        mutableStateOf(s.trimStartMs.toFloat()..(if (s.trimEndMs > 0) s.trimEndMs.toFloat() else dur))
    }
    Card {
        SectionTitle("Recortar (opcional)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(fmtTime(range.start.toLong()), Cyan)
            Spacer(Modifier.weight(1f))
            Text("${fmtTime((range.endInclusive - range.start).toLong())} seleccionados", color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Tag(fmtTime(range.endInclusive.toLong()), Pink)
        }
        Spacer(Modifier.height(6.dp))
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..dur,
            enabled = !s.processing,
            onValueChangeFinished = { vm.setTrim(range.start.toLong(), range.endInclusive.toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Violet,
                inactiveTrackColor = Panel2
            )
        )
        Text("Arrastra los extremos para quitar el inicio o el final.", color = TextLo, fontSize = 12.sp)
    }
}

@Composable
fun AdjustCard(s: UiState, vm: MainViewModel) {
    val on = !s.processing
    Card {
        SectionTitle("Duración máxima por parte")
        Row(verticalAlignment = Alignment.CenterVertically) {
            val step = if (s.secondsPerPart > 60) 10 else 5
            RoundBtn("−", on) { vm.setSeconds(s.secondsPerPart - step) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${s.secondsPerPart} s",
                    style = TextStyle(brush = TitleBrush, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                )
                Text("límite de ${s.platform.short}", color = TextLo, fontSize = 12.sp)
            }
            RoundBtn("+", on) { vm.setSeconds(s.secondsPerPart + step) }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Calidad")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quality.entries.forEach { q ->
                OptionBox(q.label, q.desc, s.quality == q, on, Modifier.weight(1f)) { vm.setQuality(q) }
            }
        }
        if (s.quality == Quality.HD_STATUS) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Cada parte queda en ~${"%.1f".format(s.estimatedMbPerPart)} MB: el peso que WhatsApp acepta sin aplastar la imagen.",
                color = Mint, fontSize = 12.sp, lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Formato")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Frame.entries.forEach { f ->
                OptionBox(f.label, f.desc, s.frame == f, on, Modifier.weight(1f)) { vm.setFrame(f) }
            }
        }

        Spacer(Modifier.height(14.dp))
        ToggleRow(
            "Partes iguales",
            if (s.equalParts) "Todas del mismo largo, sin un final cortico" else "Partes al máximo; la última con lo que sobre",
            s.equalParts, on
        ) { vm.setEqual(it) }
        ToggleRow("Fluidez 30 fps", "Más nitidez por cuadro en videos de 60 fps", s.cap30fps, on) { vm.setCap30(it) }
        ToggleRow("Quitar audio", "Video sin sonido (para poner música en la red social)", s.muteAudio, on) { vm.setMute(it) }
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
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { s.overallProgress },
                            modifier = Modifier.size(58.dp),
                            color = Cyan, trackColor = Panel2, strokeWidth = 6.dp
                        )
                        Text("${(s.overallProgress * 100).toInt()}%", color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Creando parte ${s.currentIndex + 1} de $n", color = TextHi, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("Mantén la app abierta", color = TextLo, fontSize = 13.sp)
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
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Mint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.CheckCircle, null, tint = Mint, modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("$n ${if (n == 1) "parte lista" else "partes listas"} en HD", color = TextHi, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("${s.quality.label} · ${s.frame.label} · ${fmtSize(s.totalOutBytes)}", color = TextLo, fontSize = 13.sp)
                    }
                }
            }
            else -> {
                Text(
                    "Se crearán $n ${if (n == 1) "parte" else "partes"} de ~${fmtSecs(avg)}",
                    color = TextHi, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp
                )
                Text("Corte exacto al cuadro · sin perder ni repetir segundos", color = TextLo, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                GradientButton(
                    "Crear $n ${if (n == 1) "parte" else "partes"} en HD",
                    enabled = n > 0, icon = Icons.Filled.Star, brush = HeroBrush
                ) { vm.process() }
            }
        }
    }
}

@Composable
fun PublishCard(
    s: UiState,
    countdown: Int,
    onMyChat: () -> Unit,
    onAll: () -> Unit,
    onQueue: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onNumber: (String) -> Unit
) {
    val n = s.segments.size
    Card {
        SectionTitle("Publicar en ${s.platform.short}")

        AnimatedVisibility(visible = s.queueActive) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Violet.copy(alpha = 0.15f))
                    .padding(14.dp)
            ) {
                Text(
                    if (countdown > 0) "Parte ${s.queueIndex + 1} de $n en $countdown…"
                    else "Publicando parte ${minOf(s.queueIndex + 1, n)} de $n",
                    color = TextHi, fontWeight = FontWeight.Bold
                )
                Text("Publica la parte y vuelve aquí: la siguiente se abre sola, siempre en orden.", color = TextLo, fontSize = 12.sp)
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
            if (s.platform.isWhatsApp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Mint.copy(alpha = 0.10f))
                        .border(1.dp, Mint.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Text("✨ Máxima nitidez (truco HD)", color = Mint, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1. Envía las partes a tu propio chat.\n2. En el chat, mantén presionado → Reenviar ↪ → Mi estado.\n" +
                            "3. Si aparece el botón HD, actívalo.",
                        color = TextLo, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = s.myNumber,
                        onValueChange = onNumber,
                        singleLine = true,
                        label = { Text("Tu número con indicativo (ej. 573001234567)") },
                        leadingIcon = { Icon(Icons.Filled.Person, null, tint = TextLo) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Mint, unfocusedBorderColor = Line,
                            focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                            focusedLabelColor = Mint, unfocusedLabelColor = TextLo, cursorColor = Mint
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    GradientButton(
                        "Enviar a mi chat",
                        icon = Icons.Filled.Send,
                        enabled = s.myNumber.length >= 8,
                        brush = Brush.linearGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E)))
                    ) { onMyChat() }
                }
                Spacer(Modifier.height(12.dp))
                GradientButton("Subir todas a Mi estado en orden", icon = Icons.Filled.Share, onClick = onAll)
            } else {
                Text(s.platform.note, color = TextLo, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                GradientButton("Enviar todas en orden", icon = Icons.Filled.Send, onClick = onAll)
            }
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
fun SegmentRow(
    seg: Segment,
    total: Int,
    progress: Int?,
    isNextInQueue: Boolean,
    onPreview: () -> Unit,
    onShare: () -> Unit
) {
    val (label, color) = when (seg.status) {
        SegStatus.PENDING -> "En espera" to TextLo
        SegStatus.PROCESSING -> "Creando ${progress ?: 0}%" to Cyan
        SegStatus.READY -> "Lista" to Mint
        SegStatus.SHARED -> "Enviada" to Violet
        SegStatus.ERROR -> "Error" to Danger
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Glass)
            .border(if (isNextInQueue) 2.dp else 1.dp, if (isNextInQueue) Violet else Line, RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(60.dp)
                .heightIn(min = 80.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Panel2)
                .clickable(enabled = seg.file != null, onClick = onPreview),
            contentAlignment = Alignment.Center
        ) {
            val img = remember(seg.thumb) { seg.thumb?.asImageBitmap() }
            if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (seg.file != null) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Ink.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.PlayArrow, "Ver", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HeroBrush)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) { Text("${seg.index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Parte ${seg.index + 1} de $total", color = TextHi, fontWeight = FontWeight.Bold)
            Text(
                "${fmtTime(seg.startMs)} – ${fmtTime(seg.endMs)} · ${fmtSecs(seg.durationMs)}" +
                    if (seg.sizeBytes > 0) " · ${fmtSize(seg.sizeBytes)}" else "",
                color = TextLo, fontSize = 12.sp
            )
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
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Cyan.copy(alpha = 0.14f)).clickable(onClick = onShare),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Share, "Compartir", tint = Cyan, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun TipsCard() {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💡", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text("Para que se vea perfecto", color = TextHi, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            "Usa «WhatsApp HD»: partes de 30 s con el peso justo para que WhatsApp casi no recomprima.",
            "Reenviar desde tu chat al estado conserva mejor la calidad que subir desde la galería.",
            "Parte de un video original de buena calidad: un video que ya pasó por WhatsApp no se puede recuperar.",
            "No edites ni recortes las partes dentro de WhatsApp: eso las vuelve a comprimir.",
            "Publica con Wi-Fi y activa el botón HD si aparece."
        ).forEach {
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("•", color = Violet, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(it, color = TextLo, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
