package com.cimaestrategica.estadoshd

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val processor = VideoProcessor(app)
    private var processJob: Job? = null
    private var thumbJob: Job? = null
    private val ctx: Context get() = getApplication()

    fun emit(msg: String) { _messages.tryEmit(msg) }

    private fun busy() = _state.value.processing

    /* ----- Cargar video ----- */

    fun load(uri: Uri) {
        if (busy()) { emit("Espera a que termine el proceso actual"); return }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, info = null, segments = emptyList(), queueActive = false) }
            try {
                val info = VideoMeta.read(ctx, uri)
                if (info.durationMs <= 0) throw IllegalStateException("no se pudo leer la duración")
                _state.update { it.copy(info = info, loading = false) }
                replan()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false) }
                emit("No se pudo abrir el video: ${e.message ?: "formato no compatible"}")
            }
        }
    }

    /* ----- Ajustes ----- */

    fun selectPlatform(p: Platform) {
        if (busy()) return
        _state.update { it.copy(platform = p, secondsPerPart = p.maxSeconds) }
        replan()
    }

    fun setSeconds(v: Int) {
        if (busy()) return
        val c = v.coerceIn(3, 900)
        if (c == _state.value.secondsPerPart) return
        _state.update { it.copy(secondsPerPart = c) }
        replan()
    }

    fun setEqual(b: Boolean) {
        if (busy()) return
        _state.update { it.copy(equalParts = b) }
        replan()
    }

    fun setQuality(q: Quality) {
        if (busy()) return
        _state.update { it.copy(quality = q) }
        resetOutputs()
    }

    fun setFrame(f: Frame) {
        if (busy()) return
        _state.update { it.copy(frame = f) }
        resetOutputs()
    }

    private fun resetOutputs() {
        _state.update { st ->
            st.copy(
                segments = st.segments.map { it.copy(file = null, status = SegStatus.PENDING) },
                queueActive = false, queueIndex = 0, launchedIndex = -1
            )
        }
    }

    private fun replan() {
        val s = _state.value
        val info = s.info ?: return
        val ranges = Splitter.plan(info.durationMs, s.secondsPerPart, s.equalParts)
        _state.update { st ->
            st.copy(
                segments = ranges.mapIndexed { i, r -> Segment(i, r.first, r.second) },
                queueActive = false, queueIndex = 0, launchedIndex = -1
            )
        }
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch {
            val times = ranges.take(120).map { it.first + (it.second - it.first) / 3 }
            VideoMeta.thumbnails(ctx, info.uri, times) { i, bmp ->
                val start = ranges[i].first
                _state.update { st ->
                    if (i < st.segments.size && st.segments[i].startMs == start) {
                        st.copy(segments = st.segments.mapIndexed { idx, sg -> if (idx == i) sg.copy(thumb = bmp) else sg })
                    } else st
                }
            }
        }
    }

    private fun setSeg(i: Int, f: (Segment) -> Segment) {
        _state.update { st -> st.copy(segments = st.segments.mapIndexed { idx, sg -> if (idx == i) f(sg) else sg }) }
    }

    /* ----- Procesar ----- */

    fun process() {
        val s0 = _state.value
        val info = s0.info ?: return
        if (s0.processing || s0.segments.isEmpty()) return
        resetOutputs()
        _state.update { it.copy(processing = true, currentIndex = 0, currentProgress = 0) }

        val job = viewModelScope.launch {
            val dir = File(ctx.cacheDir, "segments").apply { deleteRecursively(); mkdirs() }
            var failed = 0
            val segs = _state.value.segments
            for (seg in segs) {
                setSeg(seg.index) { it.copy(status = SegStatus.PROCESSING) }
                _state.update { it.copy(currentIndex = seg.index, currentProgress = 0) }
                val out = File(dir, "parte_%02d.mp4".format(seg.index + 1))
                try {
                    processor.export(info, seg.startMs, seg.endMs, out, s0.quality, s0.frame) { p ->
                        _state.update { it.copy(currentProgress = p) }
                    }
                    setSeg(seg.index) { it.copy(status = SegStatus.READY, file = out) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    setSeg(seg.index) { it.copy(status = SegStatus.ERROR) }
                }
            }
            _state.update { it.copy(processing = false) }
            val n = segs.size
            if (failed == 0) emit("¡Listo! $n ${if (n == 1) "parte" else "partes"} en alta calidad")
            else emit("${n - failed} listas y $failed con error. Prueba calidad «Alta» o «Ligera».")
        }
        processJob = job
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                _state.update { st ->
                    st.copy(
                        processing = false,
                        segments = st.segments.map { if (it.status == SegStatus.PROCESSING) it.copy(status = SegStatus.PENDING) else it }
                    )
                }
            }
        }
    }

    fun cancel() {
        processJob?.cancel()
        emit("Proceso cancelado")
    }

    /* ----- Publicación ----- */

    fun markShared(i: Int) = setSeg(i) { if (it.file != null) it.copy(status = SegStatus.SHARED) else it }

    fun markAllShared() {
        _state.update { st -> st.copy(segments = st.segments.map { if (it.file != null) it.copy(status = SegStatus.SHARED) else it }) }
    }

    fun startQueue() {
        if (!_state.value.allReady) return
        _state.update { it.copy(queueActive = true, queueIndex = 0, launchedIndex = -1) }
    }

    fun pauseQueue() = _state.update { it.copy(queueActive = false) }

    fun resumeQueue() {
        val s = _state.value
        if (s.queueIndex >= s.segments.size) return
        _state.update { it.copy(queueActive = true, launchedIndex = -1) }
    }

    fun markLaunched(i: Int) = _state.update { it.copy(launchedIndex = i) }

    /** Se llama cuando el usuario regresa de la red social. */
    fun onQueueReturned() {
        val s = _state.value
        if (!s.queueActive) return
        val i = s.queueIndex
        markShared(i)
        val next = i + 1
        if (next >= s.segments.size) {
            _state.update { it.copy(queueActive = false, queueIndex = next) }
            emit("¡Completo! Las ${s.segments.size} partes se enviaron en orden")
        } else {
            _state.update { it.copy(queueIndex = next) }
        }
    }

    fun saveAll() {
        val files = _state.value.segments.mapNotNull { it.file }
        if (files.isEmpty()) return
        viewModelScope.launch {
            val n = GallerySaver.save(ctx, files)
            emit(if (n == files.size) "Guardadas $n partes en Películas › EstadosHD" else "Se guardaron $n de ${files.size}")
        }
    }
}
