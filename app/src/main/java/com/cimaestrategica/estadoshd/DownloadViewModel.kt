package com.cimaestrategica.estadoshd

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
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
import java.io.File

data class DlItem(
    val id: Long,
    val title: String,
    val site: String,
    val format: DlFormat,
    val files: List<File>,
    val thumb: Bitmap?,
    val saved: Boolean
) {
    val isVideo get() = files.any { it.extension.lowercase() in listOf("mp4", "webm", "mkv", "mov") }
    val size get() = files.sumOf { it.length() }
}

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
    val openRequest: Int = 0
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DlState())
    val state: StateFlow<DlState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val ctx: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("estadoshd", Context.MODE_PRIVATE)
    private var job: Job? = null
    private var processId = ""

    init {
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

    fun setUrl(v: String) = _state.update { it.copy(url = v.trim(), info = if (v.trim() == it.url) it.info else null) }

    fun setFormat(f: DlFormat) = _state.update { it.copy(format = f) }

    /** Llega un enlace compartido desde otra app. */
    fun openWithText(text: String) {
        val u = Sites.extractUrl(text) ?: return
        _state.update { it.copy(url = u, info = null, openRequest = it.openRequest + 1) }
        analyze()
    }

    fun analyze() {
        val url = Sites.extractUrl(_state.value.url) ?: run { emit("Pega un enlace válido (https://…)"); return }
        if (_state.value.analyzing || _state.value.downloading) return
        _state.update { it.copy(url = url, analyzing = true, info = null, thumb = null) }
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
                emit(friendly(e.message))
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
        _state.update { it.copy(downloading = true, progress = 0f, status = "Conectando…") }
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
                        VideoProcessor(ctx).slideshow(images, audio, out) { p ->
                            _state.update { it.copy(progress = p / 100f) }
                        }
                        files = listOf(out)
                    } else if (video != null) {
                        files = listOf(video)
                    }
                }
                if (files.isEmpty()) throw IllegalStateException("No se encontró contenido descargable")
                val n = GallerySaver.saveAny(ctx, files)
                val item = DlItem(System.currentTimeMillis(), title, Sites.detect(url).name, format, files, thumb, n > 0)
                _state.update { it.copy(downloading = false, converting = false, progress = 1f, status = "", history = listOf(item) + it.history) }
                emit(if (n > 0) "¡Descargado! Guardado en tu galería (EstadosHD)" else "¡Descargado!")
            } catch (e: CancellationException) {
                _state.update { it.copy(downloading = false, converting = false, status = "") }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(downloading = false, converting = false, status = "") }
                emit(friendly(e.message))
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

    fun remove(item: DlItem) = _state.update { s -> s.copy(history = s.history.filter { it.id != item.id }) }

    private fun friendly(msg: String?): String {
        val m = (msg ?: "").lowercase()
        return when {
            "login" in m || "cookies" in m || "private" in m || "authentication" in m ->
                "Este contenido es privado o la red pide iniciar sesión. Prueba con una publicación pública."
            "unsupported url" in m -> "Este enlace no es compatible."
            "requested format" in m || "no video formats" in m -> "No hay ese formato. Prueba «Imagen» o «Fotos con sonido»."
            "unable to resolve" in m || "network" in m || "timed out" in m -> "Sin conexión o la red no respondió. Intenta de nuevo."
            "rate" in m && "limit" in m -> "La red limitó las descargas por un momento. Espera unos minutos."
            else -> "No se pudo descargar. Toca «Actualizar motor» e intenta de nuevo."
        }
    }
}
