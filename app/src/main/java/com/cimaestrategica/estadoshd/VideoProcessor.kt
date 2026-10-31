package com.cimaestrategica.estadoshd

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
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

    private fun presentationFor(info: VideoInfo, q: Quality, f: Frame): Presentation {
        val w = info.width
        val h = info.height
        val short = minOf(q.shortSide, minOf(w, h))
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
        quality: Quality,
        frame: Frame,
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

            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), listOf(presentationFor(info, quality, frame))))
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
                            VideoEncoderSettings.Builder().setBitrate(quality.bitrate).build()
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
}
