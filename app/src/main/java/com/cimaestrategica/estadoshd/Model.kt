package com.cimaestrategica.estadoshd

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------- Plataformas ---------- */

data class Platform(
    val id: String,
    val name: String,
    val maxSeconds: Int,
    val packages: List<String>,
    val color: Color,
    val note: String
)

val PLATFORMS = listOf(
    Platform(
        "wa", "WhatsApp Estados", 60, listOf("com.whatsapp", "com.whatsapp.w4b"), Color(0xFF25D366),
        "Toca «Enviar todas en orden», elige «Mi estado» una sola vez y WhatsApp las sube en fila, una tras otra."
    ),
    Platform(
        "wa90", "WhatsApp 90 s", 90, listOf("com.whatsapp", "com.whatsapp.w4b"), Color(0xFF128C7E),
        "Solo si tu WhatsApp ya permite estados de 1:30. Si te pide recortar, usa el de 60 s."
    ),
    Platform(
        "ig", "Instagram Historias", 60, listOf("com.instagram.android"), Color(0xFFE1306C),
        "Usa la cola guiada: en cada parte elige «Historia» y vuelve a Estados HD para la siguiente."
    ),
    Platform(
        "fb", "Facebook Historias", 60, listOf("com.facebook.katana"), Color(0xFF1877F2),
        "Usa la cola guiada: en cada parte elige «Historia» y vuelve para la siguiente."
    ),
    Platform(
        "yt", "YouTube Shorts", 180, listOf("com.google.android.youtube"), Color(0xFFFF3B3B),
        "Cada parte se abre en el editor de Shorts. Usa la cola guiada."
    ),
    Platform(
        "tt", "TikTok", 600, listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), Color(0xFF00E5E0),
        "Cada parte se abre en el editor de TikTok. Usa la cola guiada."
    ),
    Platform(
        "custom", "Personalizado", 30, emptyList(), Color(0xFFB9A8FF),
        "Ajusta los segundos y elige la app al publicar."
    )
)

enum class Quality(val label: String, val desc: String, val shortSide: Int, val bitrate: Int) {
    MAX("Máxima", "1080p · 16 Mbps", 1080, 16_000_000),
    HIGH("Alta", "1080p · 10 Mbps", 1080, 10_000_000),
    LIGHT("Ligera", "720p · 5 Mbps", 720, 5_000_000)
}

enum class Frame(val label: String, val desc: String) {
    ORIGINAL("Original", "Mantiene la forma"),
    VERTICAL("Vertical 9:16", "1080×1920 con bordes")
}

enum class SegStatus { PENDING, PROCESSING, READY, SHARED, ERROR }

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumb: Bitmap?
)

data class Segment(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val status: SegStatus = SegStatus.PENDING,
    val file: File? = null,
    val thumb: Bitmap? = null
) {
    val durationMs get() = endMs - startMs
}

data class UiState(
    val info: VideoInfo? = null,
    val loading: Boolean = false,
    val platform: Platform = PLATFORMS[0],
    val secondsPerPart: Int = PLATFORMS[0].maxSeconds,
    val equalParts: Boolean = true,
    val quality: Quality = Quality.MAX,
    val frame: Frame = Frame.ORIGINAL,
    val segments: List<Segment> = emptyList(),
    val processing: Boolean = false,
    val currentIndex: Int = 0,
    val currentProgress: Int = 0,
    val queueActive: Boolean = false,
    val queueIndex: Int = 0,
    val launchedIndex: Int = -1
) {
    val allReady get() = segments.isNotEmpty() && segments.all { it.file != null }
    val readyCount get() = segments.count { it.file != null }
    val overallProgress: Float
        get() {
            if (segments.isEmpty()) return 0f
            val done = segments.count { it.status == SegStatus.READY || it.status == SegStatus.ERROR || it.status == SegStatus.SHARED }
            val cur = if (processing) currentProgress / 100f else 0f
            return ((done + cur) / segments.size).coerceIn(0f, 1f)
        }
}

/* ---------- División exacta ---------- */

object Splitter {
    /** Margen de 200 ms para que ninguna red recorte el último cuadro. */
    fun plan(durationMs: Long, seconds: Int, equal: Boolean): List<Pair<Long, Long>> {
        if (durationMs <= 0) return emptyList()
        val maxMs = (seconds * 1000L - 200L).coerceAtLeast(1000L)
        val n = ((durationMs + maxMs - 1) / maxMs).toInt().coerceAtLeast(1)
        return if (equal) {
            (0 until n).map { i ->
                val a = durationMs * i / n
                val b = if (i == n - 1) durationMs else durationMs * (i + 1) / n
                a to b
            }
        } else {
            (0 until n).map { i -> (i * maxMs) to minOf(durationMs, (i + 1) * maxMs) }
        }
    }
}

fun fmtTime(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun fmtSecs(ms: Long): String {
    val s = ms / 1000.0
    return if (s >= 10) "${s.toInt()} s" else "%.1f s".format(s)
}

fun fmtSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    else -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
}

/* ---------- Lectura del video ---------- */

object VideoMeta {
    suspend fun read(ctx: Context, uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        var name = "video.mp4"
        var size = 0L
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) c.getString(ni)?.let { name = it }
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            if (w <= 0 || h <= 0) { w = 1080; h = 1920 }
            val thumb = runCatching {
                r.getScaledFrameAtTime(minOf(1_000_000L, dur * 500L), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
            }.getOrNull()
            VideoInfo(uri, name, size, dur, w, h, thumb)
        } finally {
            runCatching { r.release() }
        }
    }

    suspend fun thumbnails(ctx: Context, uri: Uri, timesMs: List<Long>, onThumb: (Int, Bitmap) -> Unit) =
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                timesMs.forEachIndexed { i, t ->
                    ensureActive()
                    runCatching {
                        r.getScaledFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 300, 300)
                    }.getOrNull()?.let { onThumb(i, it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            } finally {
                runCatching { r.release() }
            }
        }
}

/* ---------- Guardar en galería ---------- */

object GallerySaver {
    suspend fun save(ctx: Context, files: List<File>): Int = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val resolver = ctx.contentResolver
        var ok = 0
        for (f in files) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "EstadoHD_${stamp}_${f.name}")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EstadosHD")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: continue
            try {
                resolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                ok++
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
            }
        }
        ok
    }
}

/* ---------- Compartir ---------- */

object Sharer {
    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun packageFor(ctx: Context, p: Platform): String? = p.packages.firstOrNull { isInstalled(ctx, it) }

    private fun uri(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    private fun clipOf(ctx: Context, uris: List<Uri>): ClipData {
        val clip = ClipData.newUri(ctx.contentResolver, "video", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return clip
    }

    fun single(ctx: Context, file: File, pkg: String?): Intent {
        val u = uri(ctx, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, u)
            clipData = clipOf(ctx, listOf(u))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg != null) setPackage(pkg)
        }
        return if (pkg != null) send else Intent.createChooser(send, "Publicar parte")
    }

    fun multiple(ctx: Context, files: List<File>, pkg: String?): Intent {
        val uris = ArrayList(files.map { uri(ctx, it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/mp4"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = clipOf(ctx, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg != null) setPackage(pkg)
        }
        return if (pkg != null) send else Intent.createChooser(send, "Publicar partes")
    }

    /** Lanza un intent y, si la app no lo acepta, abre el selector general. */
    fun safeStart(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        try {
            ctx.startActivity(Intent.createChooser(intent.setPackage(null), "Publicar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e2: Exception) { false }
    }
}
