package com.cimaestrategica.estadoshd

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DlItem(
    val id: Long,
    val title: String,
    val site: String,
    val format: DlFormat,
    val files: List<File>,
    val thumb: Bitmap?,
    val saved: Boolean,
    val date: Long = System.currentTimeMillis(),
    val thumbPath: String? = null,
    val gallery: List<String> = emptyList(),
    val sentToStatus: Boolean = false
) {
    val isVideo get() = files.any { it.extension.lowercase() in listOf("mp4", "webm", "mkv", "mov") }
    val videoFile get() = files.firstOrNull { it.extension.lowercase() in listOf("mp4", "webm", "mkv", "mov") && it.exists() }
    val exists get() = files.any { it.exists() }
    val size get() = files.filter { it.exists() }.sumOf { it.length() }
}

/** Pedido de la línea de producción: descargado → partes HD → publicar. */
data class PipelineReq(val file: File, val platformId: String, val n: Int)

data class DlState(
    val url: String = "",
    val analyzing: Boolean = false,
    val info: LinkInfo? = null,
    val thumb: Bitmap? = null,
    val format: DlFormat = DlFormat.VIDEO_BEST,
    val downloading: Boolean = false,
    val converting: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val engineReady: Boolean = false,
    val engineMsg: String = "Preparando el motor de descargas…",
    val history: List<DlItem> = emptyList(),
    val openRequest: Int = 0,
    val errorTitle: String? = null,
    val errorDetail: String? = null,
    val errorAccount: String? = null,
    val connected: Set<String> = emptySet(),
    val loginAccount: String? = null,
    val autoStatus: Boolean = false,
    val autoPlatformId: String = "wahd",
    val pipeline: PipelineReq? = null,
    val selected: Set<Long> = emptySet()
)

/** Historial permanente (sobrevive al cerrar la app). */
object HistoryStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "historial.json")

    fun load(ctx: Context): List<DlItem> = try {
        val arr = JSONArray(file(ctx).takeIf { it.exists() }?.readText() ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val tp = o.optString("thumb").ifBlank { null }
            DlItem(
                id = o.getLong("id"), title = o.optString("title"), site = o.optString("site"),
                format = runCatching { DlFormat.valueOf(o.optString("format")) }.getOrDefault(DlFormat.VIDEO_BEST),
                files = o.optJSONArray("files")?.let { a -> (0 until a.length()).map { File(a.getString(it)) } } ?: emptyList(),
                thumb = tp?.let { BitmapFactory.decodeFile(it) },
                saved = o.optBoolean("saved"), date = o.optLong("date"), thumbPath = tp,
                gallery = o.optJSONArray("gallery")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                sentToStatus = o.optBoolean("status")
            )
        }
    } catch (e: Exception) { emptyList() }

    fun save(ctx: Context, items: List<DlItem>) {
        val arr = JSONArray()
        items.forEach { it2 ->
            arr.put(JSONObject().apply {
                put("id", it2.id); put("title", it2.title); put("site", it2.site); put("format", it2.format.name)
                put("files", JSONArray(it2.files.map { f -> f.absolutePath })); put("thumb", it2.thumbPath ?: "")
                put("saved", it2.saved); put("date", it2.date); put("gallery", JSONArray(it2.gallery)); put("status", it2.sentToStatus)
            })
        }
        file(ctx).writeText(arr.toString())
    }

    fun saveThumb(ctx: Context, id: Long, bmp: Bitmap?): String? = try {
        if (bmp == null) null else {
            val d = File(ctx.filesDir, "miniaturas").apply { mkdirs() }
            val f = File(d, "$id.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            f.absolutePath
        }
    } catch (e: Exception) { null }
}

class DownloadViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DlState())
    val state: StateFlow<DlState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val ctx: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("estadoshd", Context.MODE_PRIVATE)
    private var job: Job? = null
    private var processId = ""
    private var pipeCounter = 0

    init {
        _state.update {
            it.copy(
                connected = Accounts.connected(ctx),
                autoStatus = prefs.getBoolean("autoStatus", false),
                autoPlatformId = prefs.getString("autoPlatform", "wahd") ?: "wahd"
            )
        }
        viewModelScope.launch {
            val h = withContext(Dispatchers.IO) { HistoryStore.load(ctx) }
            _state.update { it.copy(history = h) }
        }
        viewModelScope.launch {
            try {
                DownloadEngine.ensureInit(ctx)
                _state.update { it.copy(engineReady = true, engineMsg = "Motor listo · ${DownloadEngine.version(ctx)}") }
                val last = prefs.getLong("lastUpdate", 0L)
                if (System.currentTimeMillis() - last > 24 * 3600 * 1000L) {
                    runCatching { DownloadEngine.update(ctx) }.onSuccess {
                        prefs.edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
                        _state.update { s -> s.copy(engineMsg = "Motor actualizado · ${DownloadEngine.version(ctx)}") }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(engineMsg = "No se pudo iniciar el motor: ${e.message}") }
            }
        }
    }

    fun emit(m: String) { _messages.tryEmit(m) }

    private fun persist() {
        val h = _state.value.history
        viewModelScope.launch(Dispatchers.IO) { HistoryStore.save(ctx, h) }
    }

    fun setUrl(v: String) = _state.update { it.copy(url = v.trim(), info = if (v.trim() == it.url) it.info else null) }
    fun setFormat(f: DlFormat) = _state.update { it.copy(format = f) }

    fun setAutoStatus(b: Boolean) {
        prefs.edit().putBoolean("autoStatus", b).apply()
        _state.update { it.copy(autoStatus = b, format = if (b && it.format !in listOf(DlFormat.VIDEO_BEST, DlFormat.VIDEO_720, DlFormat.SLIDESHOW)) DlFormat.VIDEO_BEST else it.format) }
    }

    fun setAutoPlatform(id: String) {
        prefs.edit().putString("autoPlatform", id).apply()
        _state.update { it.copy(autoPlatformId = id) }
    }

    fun consumePipeline() = _state.update { it.copy(pipeline = null) }

    /** Enviar un video del historial a la línea de estados. */
    fun sendToStatus(item: DlItem) {
        val f = item.videoFile ?: run { emit("Este archivo ya no existe"); return }
        pipeCounter++
        _state.update { s ->
            s.copy(
                pipeline = PipelineReq(f, s.autoPlatformId, pipeCounter),
                history = s.history.map { if (it.id == item.id) it.copy(sentToStatus = true) else it }
            )
        }
        persist()
    }

    fun clearError() = _state.update { it.copy(errorTitle = null, errorDetail = null, errorAccount = null) }
    fun startLogin(key: String) = _state.update { it.copy(loginAccount = key) }
    fun finishLogin(save: Boolean) {
        val key = _state.value.loginAccount ?: return
        val acc = Accounts.ALL.first { it.key == key }
        val ok = save && Accounts.save(ctx, acc)
        _state.update { it.copy(loginAccount = null, connected = Accounts.connected(ctx)) }
        if (save) emit(if (ok) "${acc.name} conectado" else "No se detectó la sesión")
    }

    fun openWithText(text: String) {
        val u = Sites.extractUrl(text) ?: return
        _state.update { it.copy(url = u, info = null, openRequest = it.openRequest + 1) }
        analyze()
    }

    private fun fail(e: Exception) {
        val raw = (e.message ?: e.toString()).trim()
        _state.update {
            it.copy(
                errorTitle = friendly(raw),
                errorDetail = raw.lines().filter { l -> l.isNotBlank() }.let { ls ->
                    (ls.filter { l -> l.startsWith("página") || l.startsWith("graphql") || l.startsWith("embed") || l.startsWith("Instagram") } + ls.takeLast(3)).distinct()
                }.joinToString("\n").take(1200),
                errorAccount = null
            )
        }
    }

    fun analyze() {
        val url = Sites.extractUrl(_state.value.url) ?: run { emit("Pega un enlace válido (https://…)"); return }
        if (_state.value.analyzing || _state.value.downloading) return
        _state.update { it.copy(url = url, analyzing = true, info = null, thumb = null, errorTitle = null, errorDetail = null) }
        viewModelScope.launch {
            try {
                val info = DownloadEngine.analyze(ctx, url)
                val bmp = withContext(Dispatchers.IO) { DownloadEngine.loadBitmap(info.thumbnailUrl) }
                _state.update {
                    it.copy(analyzing = false, info = info, thumb = bmp,
                        format = if (info.hasVideo) DlFormat.VIDEO_BEST else DlFormat.SLIDESHOW)
                }
            } catch (e: Exception) {
                _state.update { it.copy(analyzing = false) }
                fail(e)
            }
        }
    }

    fun download() {
        val s = _state.value
        val url = Sites.extractUrl(s.url) ?: run { emit("Pega un enlace válido"); return }
        if (s.downloading) return
        processId = "dl_${System.currentTimeMillis()}"
        val title = s.info?.title ?: url
        val thumb = s.thumb
        val format = s.format
        val auto = s.autoStatus
        val autoPlat = s.autoPlatformId
        _state.update { it.copy(downloading = true, progress = 0f, status = "Conectando…", errorTitle = null, errorDetail = null) }
        job = viewModelScope.launch {
            try {
                val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                val dir = File(base, "descargas/${System.currentTimeMillis()}")
                var files = DownloadEngine.download(ctx, url, format, dir, processId) { p, st ->
                    _state.update { it.copy(progress = p / 100f, status = st) }
                }
                if (format == DlFormat.SLIDESHOW) {
                    val audio = files.firstOrNull { it.extension.lowercase() in listOf("m4a", "mp3", "opus", "aac", "ogg") }
                    val images = files.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") }
                    val video = files.firstOrNull { it.extension.lowercase() in listOf("mp4", "webm") }
                    if (audio != null && images.isNotEmpty()) {
                        _state.update { it.copy(converting = true, status = "Creando video con sonido…", progress = 0f) }
                        val out = File(dir, "${audio.nameWithoutExtension}_video.mp4")
                        VideoProcessor(ctx).slideshow(images, audio, out) { p -> _state.update { it.copy(progress = p / 100f) } }
                        files = listOf(out)
                    } else if (video != null) files = listOf(video)
                }
                if (files.isEmpty()) throw IllegalStateException("No se encontró contenido descargable")
                val uris = GallerySaver.saveAny(ctx, files)
                val id = System.currentTimeMillis()
                val tp = withContext(Dispatchers.IO) { HistoryStore.saveThumb(ctx, id, thumb) }
                var item = DlItem(id, title, Sites.detect(url).name, format, files, thumb, uris.isNotEmpty(), id, tp, uris)
                val goStatus = auto && item.isVideo
                if (goStatus) item = item.copy(sentToStatus = true)
                _state.update { it.copy(downloading = false, converting = false, progress = 1f, status = "", history = listOf(item) + it.history) }
                persist()
                if (goStatus) {
                    pipeCounter++
                    _state.update { it.copy(pipeline = PipelineReq(item.videoFile!!, autoPlat, pipeCounter)) }
                    emit("Descargado. Creando las partes en HD…")
                } else {
                    if (auto && !item.isVideo) emit("Descargado. Solo los videos se envían a estados.")
                    else emit(if (uris.isNotEmpty()) "¡Descargado! Guardado en tu galería (EstadosHD)" else "¡Descargado!")
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(downloading = false, converting = false, status = "") }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(downloading = false, converting = false, status = "") }
                fail(e)
            }
        }
    }

    fun cancel() {
        DownloadEngine.cancel(processId)
        job?.cancel()
        emit("Descarga cancelada")
    }

    fun updateEngine() {
        _state.update { it.copy(engineMsg = "Actualizando motor…") }
        viewModelScope.launch {
            try {
                val r = DownloadEngine.update(ctx)
                prefs.edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
                _state.update { it.copy(engineMsg = "Motor al día · $r") }
            } catch (e: Exception) {
                _state.update { it.copy(engineMsg = "No se pudo actualizar: revisa tu conexión") }
            }
        }
    }

    /* ---------- Historial: selección y borrado ---------- */

    fun toggleSelect(id: Long) = _state.update { s -> s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id) }
    fun selectAll() = _state.update { s -> s.copy(selected = if (s.selected.size == s.history.size) emptySet() else s.history.map { it.id }.toSet()) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** permanent=false: solo quita del historial. permanent=true: borra archivos de la app y de la galería. */
    fun deleteSelected(permanent: Boolean) {
        val sel = _state.value.selected
        val targets = _state.value.history.filter { it.id in sel }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            var freed = 0L
            if (permanent) withContext(Dispatchers.IO) {
                for (it in targets) {
                    it.files.forEach { f -> if (f.exists()) { freed += f.length(); f.delete() } }
                    it.files.mapNotNull { f -> f.parentFile }.distinct().forEach { d -> if (d.listFiles()?.isEmpty() == true) d.delete() }
                    it.gallery.forEach { u -> runCatching { ctx.contentResolver.delete(Uri.parse(u), null, null) } }
                }
            }
            withContext(Dispatchers.IO) { targets.forEach { it.thumbPath?.let { p -> File(p).delete() } } }
            _state.update { s -> s.copy(history = s.history.filter { it.id !in sel }, selected = emptySet()) }
            persist()
            emit(
                if (permanent) "Eliminados ${targets.size} · liberaste ${fmtSize(freed)}"
                else "Quitados ${targets.size} del historial (los archivos siguen en tu galería)"
            )
        }
    }

    fun remove(item: DlItem) {
        _state.update { s -> s.copy(history = s.history.filter { it.id != item.id }) }
        persist()
    }

    private fun friendly(msg: String?): String {
        val m = (msg ?: "").lowercase()
        return when {
            "login" in m || "cookies" in m || "private" in m || "authentication" in m || "not available" in m || "empty media" in m || "rate-limit" in m ->
                "La red bloqueó la consulta pública o la publicación es privada. Espera un momento y reintenta, o toca «Actualizar»."
            "unsupported url" in m -> "Este enlace no es compatible."
            "requested format" in m || "no video formats" in m -> "No hay ese formato. Prueba «Imagen» o «Fotos con sonido»."
            "unable to resolve" in m || "network" in m || "timed out" in m -> "Sin conexión o la red no respondió. Intenta de nuevo."
            "python" in m -> "El motor necesita actualizarse. Toca «Actualizar»."
            "ssl" in m || "certificate" in m -> "Problema de conexión segura. Revisa la fecha/hora del celular o tu red."
            else -> "No se pudo procesar este enlace. Revisa el detalle técnico."
        }
    }
}
