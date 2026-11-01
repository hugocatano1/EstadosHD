package com.cimaestrategica.estadoshd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Formatos de descarga ofrecidos al usuario. */
enum class DlFormat(val title: String, val desc: String) {
    VIDEO_BEST("Video máxima calidad", "MP4 · hasta 1080p"),
    VIDEO_720("Video liviano", "MP4 · 720p"),
    AUDIO_MP3("Solo audio", "MP3 · alta calidad"),
    IMAGE("Imagen", "JPG · tamaño original"),
    SLIDESHOW("Fotos con sonido", "Convierte a video MP4")
}

data class LinkInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val durationSec: Int,
    val thumbnailUrl: String?,
    val hasVideo: Boolean,
    val site: String
)

data class SiteInfo(val name: String, val color: Long, val icon: Int)

object Sites {
    fun detect(url: String): SiteInfo {
        val u = url.lowercase()
        return when {
            "instagram." in u || "instagr.am" in u -> SiteInfo("Instagram", 0xFFE1306C, R.drawable.ms_photo_camera)
            "tiktok." in u -> SiteInfo("TikTok", 0xFFFE2C55, R.drawable.ms_equalizer)
            "facebook." in u || "fb.watch" in u || "fb.com" in u -> SiteInfo("Facebook", 0xFF1877F2, R.drawable.ms_public)
            "youtube." in u || "youtu.be" in u -> SiteInfo("YouTube", 0xFFEA4335, R.drawable.ms_smart_display)
            "twitter." in u || "x.com" in u -> SiteInfo("X", 0xFF202124, R.drawable.ms_close)
            "threads." in u -> SiteInfo("Threads", 0xFF202124, R.drawable.ms_chat)
            "pinterest." in u || "pin.it" in u -> SiteInfo("Pinterest", 0xFFE60023, R.drawable.ms_image)
            "snapchat." in u -> SiteInfo("Snapchat", 0xFFF7C600, R.drawable.ms_chat)
            "reddit." in u || "redd.it" in u -> SiteInfo("Reddit", 0xFFFF4500, R.drawable.ms_public)
            "vimeo." in u -> SiteInfo("Vimeo", 0xFF1AB7EA, R.drawable.ms_movie)
            "soundcloud." in u -> SiteInfo("SoundCloud", 0xFFFF5500, R.drawable.ms_music_note)
            "twitch." in u -> SiteInfo("Twitch", 0xFF9146FF, R.drawable.ms_smart_display)
            "linkedin." in u -> SiteInfo("LinkedIn", 0xFF0A66C2, R.drawable.ms_public)
            "dailymotion." in u -> SiteInfo("Dailymotion", 0xFF0066DC, R.drawable.ms_movie)
            "kwai." in u -> SiteInfo("Kwai", 0xFFFF7A00, R.drawable.ms_bolt)
            "likee." in u -> SiteInfo("Likee", 0xFFFF2D55, R.drawable.ms_bolt)
            "suno." in u -> SiteInfo("Suno", 0xFF111111, R.drawable.ms_music_note)
            else -> SiteInfo("Enlace web", 0xFF1A73E8, R.drawable.ms_language)
        }
    }

    fun extractUrl(text: String): String? =
        Regex("https?://[^\\s]+").find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')?.let { clean(it) }

    /** Quita parámetros de rastreo (?igsh=, ?psln=, ?utm_…) en redes donde no hacen falta. */
    fun clean(url: String): String {
        val u = url.lowercase()
        val strip = listOf("instagram.", "instagr.am", "tiktok.", "threads.", "x.com", "twitter.", "pinterest.", "snapchat.")
        return if (strip.any { it in u }) url.substringBefore('?').substringBefore('#') else url
    }
}

object DownloadEngine {
    private val mutex = Mutex()
    @Volatile var ready = false
        private set

    suspend fun ensureInit(ctx: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!ready) {
                YoutubeDL.init(ctx.applicationContext)
                FFmpeg.init(ctx.applicationContext)
                ready = true
            }
        }
    }

    /** Actualiza el motor yt-dlp (las redes cambian seguido). */
    suspend fun update(ctx: Context): String = withContext(Dispatchers.IO) {
        ensureInit(ctx)
        val st = YoutubeDL.updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel.STABLE)
        "${st?.name ?: "OK"} · ${YoutubeDL.version(ctx.applicationContext) ?: ""}"
    }

    fun version(ctx: Context): String = try { YoutubeDL.version(ctx.applicationContext) ?: "" } catch (e: Exception) { "" }

    suspend fun analyze(ctx: Context, url: String): LinkInfo = withContext(Dispatchers.IO) {
        ensureInit(ctx)
        val req = YoutubeDLRequest(url)
        addCookies(ctx, req, url)
        req.addOption("--playlist-items", "1")
        req.addOption("--no-warnings")
        val v = YoutubeDL.getInfo(req)
        val hasVideo = (v.formats?.any { (it.vcodec ?: "none") != "none" } ?: false) ||
            (v.width > 0 && v.height > 0)
        LinkInfo(
            url = url,
            title = (v.title ?: v.fulltitle ?: "Contenido").take(120),
            uploader = v.uploader ?: v.uploaderId ?: "",
            durationSec = v.duration,
            thumbnailUrl = v.thumbnail ?: v.thumbnails?.lastOrNull()?.url,
            hasVideo = hasVideo,
            site = Sites.detect(url).name
        )
    }

    private fun addCookies(ctx: Context, req: YoutubeDLRequest, url: String) {
        val acc = Accounts.forUrl(url) ?: return
        val f = Accounts.file(ctx, acc.key)
        if (f.exists()) req.addOption("--cookies", f.absolutePath)
    }

    fun loadBitmap(url: String?): Bitmap? = try {
        if (url == null) null else {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            c.inputStream.use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) { null }

    /**
     * Descarga en [dir]. Devuelve los archivos nuevos.
     * onProgress(porcentaje 0..100, texto de estado)
     */
    suspend fun download(
        ctx: Context,
        url: String,
        format: DlFormat,
        dir: File,
        processId: String,
        onProgress: (Float, String) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        ensureInit(ctx)
        dir.mkdirs()
        val before = dir.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()
        val req = YoutubeDLRequest(url)
        addCookies(ctx, req, url)
        req.addOption("-o", dir.absolutePath + "/%(title).60B [%(id)s].%(ext)s")
        req.addOption("--no-mtime")
        req.addOption("--restrict-filenames")
        when (format) {
            DlFormat.VIDEO_BEST -> {
                req.addOption("-S", "res:1080,vcodec:h264,acodec:m4a")
                req.addOption("-f", "bv*+ba/b")
                req.addOption("--merge-output-format", "mp4")
            }
            DlFormat.VIDEO_720 -> {
                req.addOption("-S", "res:720,vcodec:h264,acodec:m4a")
                req.addOption("-f", "bv*+ba/b")
                req.addOption("--merge-output-format", "mp4")
            }
            DlFormat.AUDIO_MP3 -> {
                req.addOption("-f", "ba/b")
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
                req.addOption("--audio-quality", "0")
            }
            DlFormat.IMAGE -> {
                req.addOption("--skip-download")
                req.addOption("--write-thumbnail")
                req.addOption("--convert-thumbnails", "jpg")
            }
            DlFormat.SLIDESHOW -> {
                req.addOption("-f", "ba/b")
                req.addOption("-x")
                req.addOption("--audio-format", "m4a")
                req.addOption("--write-thumbnail")
                req.addOption("--convert-thumbnails", "jpg")
            }
        }
        YoutubeDL.execute(req, processId) { progress, eta, _ ->
            val p = progress.coerceIn(0f, 100f)
            onProgress(p, if (eta > 0) "Faltan ${eta}s" else "Descargando…")
        }
        val after = dir.listFiles()?.filter { it.absolutePath !in before && it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
        after?.sortedBy { it.name } ?: emptyList()
    }

    fun cancel(processId: String) {
        try { YoutubeDL.destroyProcessById(processId) } catch (e: Exception) { }
    }
}
