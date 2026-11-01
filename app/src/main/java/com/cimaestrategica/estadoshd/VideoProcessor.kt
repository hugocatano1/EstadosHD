package com.cimaestrategica.estadoshd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Effect
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Corta con precisión de cuadro (re-codifica, no depende de keyframes),
 * escala a Full HD, convierte HDR a SDR (evita colores lavados en WhatsApp)
 * y codifica H.264 + AAC con alto bitrate.
 */
@OptIn(UnstableApi::class)
class VideoProcessor(private val context: Context) {

    private fun even(v: Int) = ((v / 2) * 2).coerceAtLeast(2)

    private fun presentationFor(info: VideoInfo, shortSide: Int, f: Frame): Presentation {
        val w = info.width
        val h = info.height
        val short = minOf(shortSide, minOf(w, h))
        return when (f) {
            Frame.VERTICAL -> Presentation.createForWidthAndHeight(
                even(short), even(short * 16 / 9), Presentation.LAYOUT_SCALE_TO_FIT
            )
            Frame.ORIGINAL -> if (h >= w) {
                Presentation.createForWidthAndHeight(
                    even(short), even((short.toLong() * h / w).toInt()), Presentation.LAYOUT_SCALE_TO_FIT
                )
            } else {
                Presentation.createForWidthAndHeight(
                    even((short.toLong() * w / h).toInt()), even(short), Presentation.LAYOUT_SCALE_TO_FIT
                )
            }
        }
    }

    suspend fun export(
        info: VideoInfo,
        startMs: Long,
        endMs: Long,
        out: File,
        shortSide: Int,
        bitrate: Int,
        frame: Frame,
        removeAudio: Boolean,
        cap30fps: Boolean,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val handler = Handler(Looper.getMainLooper())
            out.delete()

            val mediaItem = MediaItem.Builder()
                .setUri(info.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

            val videoEffects = mutableListOf<Effect>()
            if (cap30fps) videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(30f))
            videoEffects.add(presentationFor(info, shortSide, frame))

            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(removeAudio)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

            val composition = Composition.Builder(listOf(EditedMediaItemSequence(listOf(edited))))
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()

            lateinit var poll: Runnable

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder().setBitrate(bitrate).build()
                        )
                        .setEnableFallback(true)
                        .build()
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll)
                        onProgress(100)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            val holder = ProgressHolder()
            poll = Runnable {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
                handler.postDelayed(poll, 300)
            }

            transformer.start(composition, out.absolutePath)
            handler.postDelayed(poll, 300)

            cont.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(poll)
                    transformer.cancel()
                    out.delete()
                }
            }
        }
    }

    /** Une una o varias imágenes con un audio y produce un MP4 (fotos con sonido -> video). */
    suspend fun slideshow(images: List<File>, audio: File, out: File, onProgress: (Int) -> Unit) {
        val prepared = withContext(Dispatchers.IO) {
            images.mapIndexedNotNull { i, f ->
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return@mapIndexedNotNull null
                val sc = minOf(1f, minOf(1080f / minOf(bmp.width, bmp.height), 1920f / maxOf(bmp.width, bmp.height)))
                val w = ((bmp.width * sc).toInt() / 2) * 2
                val h = ((bmp.height * sc).toInt() / 2) * 2
                val scaled = Bitmap.createScaledBitmap(bmp, maxOf(2, w), maxOf(2, h), true)
                val dst = File(out.parentFile, "slide_$i.jpg")
                dst.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                Triple(dst, scaled.width, scaled.height)
            }
        }
        if (prepared.isEmpty()) throw IllegalStateException("No se pudieron leer las imágenes")
        val durMs = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(audio.absolutePath)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15000L
            } finally { runCatching { r.release() } }
        }.coerceIn(2000L, 600000L)
        val (_, ow, oh) = prepared.first()
        val perUs = durMs * 1000L / prepared.size
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val handler = Handler(Looper.getMainLooper())
                val pres = Presentation.createForWidthAndHeight(ow, oh, Presentation.LAYOUT_SCALE_TO_FIT)
                @Suppress("DEPRECATION")
                val imgs = prepared.map { (f, _, _) ->
                    EditedMediaItem.Builder(
                        MediaItem.Builder().setUri(Uri.fromFile(f)).setMimeType(MimeTypes.IMAGE_JPEG).build()
                    )
                        .setDurationUs(perUs)
                        .setFrameRate(30)
                        .setEffects(Effects(emptyList(), listOf(pres)))
                        .build()
                }
                val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audio))).build()
                val composition = Composition.Builder(
                    listOf(EditedMediaItemSequence(imgs), EditedMediaItemSequence(listOf(audioItem)))
                ).build()
                lateinit var poll: Runnable
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(6_000_000).build())
                            .setEnableFallback(true).build()
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(poll); onProgress(100)
                            if (cont.isActive) cont.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                val holder = ProgressHolder()
                poll = Runnable {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                    handler.postDelayed(poll, 300)
                }
                out.delete()
                transformer.start(composition, out.absolutePath)
                handler.postDelayed(poll, 300)
                cont.invokeOnCancellation { handler.post { handler.removeCallbacks(poll); transformer.cancel() } }
            }
        }
        prepared.forEach { it.first.delete() }
    }

    /** Extrae solo el audio (AAC en .m4a). */
    suspend fun extractAudio(input: File, out: File, onProgress: (Int) -> Unit) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val handler = Handler(Looper.getMainLooper())
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input))).setRemoveVideo(true).build()
            lateinit var poll: Runnable
            val t = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll); onProgress(100); if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacks(poll); if (cont.isActive) cont.resumeWithException(exportException)
                    }
                }).build()
            val holder = ProgressHolder()
            poll = Runnable {
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                handler.postDelayed(poll, 300)
            }
            out.delete()
            t.start(item, out.absolutePath)
            handler.postDelayed(poll, 300)
            cont.invokeOnCancellation { handler.post { handler.removeCallbacks(poll); t.cancel() } }
        }
    }
}
