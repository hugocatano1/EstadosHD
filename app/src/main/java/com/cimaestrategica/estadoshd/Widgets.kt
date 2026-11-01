package com.cimaestrategica.estadoshd

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.json.JSONArray
import java.io.File

/** Acciones que abren la app desde widgets, accesos rápidos y el botón de notificaciones. */
object Launch {
    const val PASTE = "com.cimaestrategica.estadoshd.PASTE_STATUS"
    const val PICK = "com.cimaestrategica.estadoshd.PICK_VIDEO"
    const val DOWNLOAD = "com.cimaestrategica.estadoshd.OPEN_DOWNLOAD"
    const val HISTORY = "com.cimaestrategica.estadoshd.OPEN_HISTORY"
    fun intent(ctx: Context, action: String) = Intent(ctx, MainActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

private val WHITE = ColorProvider(Color.White)
private val WHITE_SOFT = ColorProvider(Color(0xE6FFFFFF))
private val PINK = Color(0xFFD62976)

data class WidgetInfo(val count: Int, val lastTitle: String?, val lastSite: String?)

private fun readInfo(ctx: Context): WidgetInfo = try {
    val f = File(ctx.filesDir, "historial.json")
    val arr = JSONArray(if (f.exists()) f.readText() else "[]")
    val first = if (arr.length() > 0) arr.getJSONObject(0) else null
    WidgetInfo(arr.length(), first?.optString("title"), first?.optString("site"))
} catch (e: Exception) { WidgetInfo(0, null, null) }

suspend fun refreshWidgets(ctx: Context) {
    runCatching { EstadosWidget().updateAll(ctx) }
}

@Composable
private fun Tile(res: Int, bg: Int, tint: Color, box: Int, icon: Int) {
    Box(
        GlanceModifier.size(box.dp).background(ImageProvider(bg)),
        contentAlignment = Alignment.Center
    ) {
        Image(ImageProvider(res), null, GlanceModifier.size(icon.dp), colorFilter = ColorFilter.tint(ColorProvider(tint)))
    }
}

@Composable
private fun ActionChip(label: String, res: Int, bg: Int, tint: Color, action: String, modifier: GlanceModifier) {
    val ctx = LocalContext.current
    Column(
        modifier.background(ImageProvider(R.drawable.widget_chip)).padding(vertical = 8.dp)
            .clickable(actionStartActivity(Launch.intent(ctx, action))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tile(res, bg, tint, 34, 20)
        Spacer(GlanceModifier.height(4.dp))
        Text(label, style = TextStyle(color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun PasteButton(compact: Boolean) {
    val ctx = LocalContext.current
    Row(
        GlanceModifier.fillMaxWidth().height(if (compact) 48.dp else 54.dp)
            .background(ImageProvider(R.drawable.widget_button)).padding(horizontal = 10.dp)
            .clickable(actionStartActivity(Launch.intent(ctx, Launch.PASTE))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Tile(R.drawable.ms_content_paste, R.drawable.tile_pink, PINK, 32, 19)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            if (compact) "Pegar y crear" else "Pegar enlace → Estados HD",
            style = TextStyle(color = ColorProvider(PINK), fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

class EstadosWidget : GlanceAppWidget() {
    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 190.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val info = readInfo(context)
        provideContent { Content(info) }
    }

    @Composable
    private fun Content(info: WidgetInfo) {
        val size = LocalSize.current
        val ctx = LocalContext.current
        Column(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                size.width < 200.dp -> {
                    // Pequeño: logo + botón pegar
                    Column(
                        GlanceModifier.fillMaxSize().clickable(actionStartActivity(Launch.intent(ctx, Launch.PASTE))),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tile(R.drawable.ms_content_paste, R.drawable.tile_pink, PINK, 48, 28)
                        Spacer(GlanceModifier.height(6.dp))
                        Text("Pegar y crear", style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text("estados HD", style = TextStyle(color = WHITE_SOFT, fontSize = 11.sp), maxLines = 1)
                    }
                }
                size.height < 180.dp -> {
                    // Ancho: encabezado + 3 acciones
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Tile(R.drawable.ms_play_arrow, R.drawable.tile_blue, Color(0xFF1A73E8), 34, 22)
                        Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text("Estados HD", style = TextStyle(color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text("Pega un link y súbelo a estados", style = TextStyle(color = WHITE_SOFT, fontSize = 11.sp), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        ActionChip("Pegar", R.drawable.ms_content_paste, R.drawable.tile_pink, PINK, Launch.PASTE, GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        ActionChip("Video", R.drawable.ms_video_library, R.drawable.tile_blue, Color(0xFF1A73E8), Launch.PICK, GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        ActionChip("Historial", R.drawable.ms_folder, R.drawable.tile_yellow, Color(0xFFE09600), Launch.HISTORY, GlanceModifier.defaultWeight())
                    }
                }
                else -> {
                    // Grande: encabezado, botón principal, acciones y última descarga
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Tile(R.drawable.ms_play_arrow, R.drawable.tile_blue, Color(0xFF1A73E8), 40, 26)
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text("Estados HD", style = TextStyle(color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text("by Hugo Cataño", style = TextStyle(color = WHITE_SOFT, fontSize = 11.sp), maxLines = 1)
                        }
                        Text("${info.count}", style = TextStyle(color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                        Spacer(GlanceModifier.width(4.dp))
                        Text("descargas", style = TextStyle(color = WHITE_SOFT, fontSize = 10.sp))
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    PasteButton(compact = false)
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        ActionChip("Video", R.drawable.ms_video_library, R.drawable.tile_blue, Color(0xFF1A73E8), Launch.PICK, GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        ActionChip("Descargar", R.drawable.ms_cloud_download, R.drawable.tile_purple, Color(0xFF7B2FF7), Launch.DOWNLOAD, GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        ActionChip("Historial", R.drawable.ms_folder, R.drawable.tile_yellow, Color(0xFFE09600), Launch.HISTORY, GlanceModifier.defaultWeight())
                    }
                    if (info.lastTitle != null && size.height > 230.dp) {
                        Spacer(GlanceModifier.height(8.dp))
                        Text(
                            "Último: ${info.lastTitle} · ${info.lastSite}",
                            style = TextStyle(color = WHITE_SOFT, fontSize = 11.sp), maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

class EstadosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EstadosWidget()
}

/** Widget 1×1: un toque para pegar el enlace y crear estados. */
class QuickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            Column(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(6.dp)
                    .clickable(actionStartActivity(Launch.intent(ctx, Launch.PASTE))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tile(R.drawable.ms_content_paste, R.drawable.tile_pink, PINK, 36, 22)
                Spacer(GlanceModifier.height(3.dp))
                Text("Pegar", style = TextStyle(color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()
}
