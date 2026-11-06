package com.cimaestrategica.estadoshd

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Extractor público de Instagram (sin iniciar sesión), igual que los descargadores web:
 * 1) consulta pública de la publicación (GraphQL anónimo)
 * 2) visor embebido público (/embed/captioned)
 * Funciona con publicaciones y reels públicos.
 */
object IgExtractor {
    data class Media(val isVideo: Boolean, val url: String, val thumb: String?)
    data class Post(val title: String, val user: String, val durationSec: Int, val items: List<Media>, val audioUrl: String?) {
        val hasVideo get() = items.any { it.isVideo }
        val cover get() = items.firstOrNull()?.thumb ?: items.firstOrNull()?.url
    }

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    private const val UA_M = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
    private const val APP_ID = "936619743392459"
    private val DOC_IDS = listOf("8845758582119845", "9510064595728286", "10015901848480474")

    private val cache = HashMap<String, Post>()
    private val cookies = java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)
    init { java.net.CookieHandler.setDefault(cookies) }
    private fun cookie(name: String): String? =
        cookies.cookieStore.cookies.firstOrNull { it.name == name && it.domain.contains("instagram") }?.value
    fun cached(url: String) = cache[shortcode(url) ?: url]

    fun isInstagram(url: String) = url.contains("instagram.com", true) || url.contains("instagr.am", true)

    fun shortcode(url: String): String? =
        Regex("/(?:p|reel|reels|tv)/([A-Za-z0-9_-]{5,})").find(url)?.groupValues?.get(1)

    private fun conn(u: String, ua: String = UA): HttpURLConnection =
        (URL(u).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 15000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept-Language", "es-CO,es;q=0.9,en;q=0.8")
        }

    /** Resuelve enlaces /share/… a la URL final. */
    fun resolve(url: String): String {
        if (shortcode(url) != null) return url
        return try {
            val c = conn(url, UA_M); c.instanceFollowRedirects = true; c.inputStream.close()
            c.url.toString()
        } catch (e: Exception) { url }
    }

    fun fetch(url0: String): Post {
        val url = resolve(url0)
        val sc = shortcode(url) ?: throw IllegalArgumentException("No reconozco este enlace de Instagram")
        val errors = mutableListOf<String>()
        var lsd = "AVqbxe3J_YA"
        try {
            val kind = if (url.contains("/reel")) "reel" else "p"
            val page = conn("https://www.instagram.com/$kind/$sc/").let { c ->
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                c.setRequestProperty("Sec-Fetch-Mode", "navigate")
                if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().readText()
            }
            Regex("\"LSD\",\\[\\],\\{\"token\":\"([^\"]+)\"").find(page)?.groupValues?.get(1)?.let { lsd = it }
            fromPage(page)?.let { cache[sc] = it; return it }
            errors += "página: sin video en el HTML público"
        } catch (e: Exception) { errors += "página: ${e.message}" }
        for (doc in DOC_IDS) {
            try { graphql(sc, doc, lsd)?.let { cache[sc] = it; return it } ?: run { errors += "graphql($doc): respuesta vacía" } }
            catch (e: Exception) { errors += "graphql($doc): ${e.message}" }
        }
        try { embed(sc)?.let { cache[sc] = it; return it } ?: run { errors += "embed: sin video ni imagen" } }
        catch (e: Exception) { errors += "embed: ${e.message}" }
        try { mediaRedirect(sc)?.let { cache[sc] = it; return it } ?: run { errors += "media: sin imagen" } }
        catch (e: Exception) { errors += "media: ${e.message}" }
        try { oembed(sc)?.let { cache[sc] = it; return it } ?: run { errors += "oembed: sin imagen" } }
        catch (e: Exception) { errors += "oembed: ${e.message}" }
        throw IllegalStateException("Instagram no entregó el contenido público.\n" + errors.joinToString("\n"))
    }

    private fun graphql(sc: String, docId: String, lsd: String): Post? {
        val vars = """{"shortcode":"$sc","fetch_tagged_user_count":null,"hoisted_comment_id":null,"hoisted_reply_id":null}"""
        val body = "lsd=$lsd&doc_id=$docId&variables=" + URLEncoder.encode(vars, "UTF-8")
        val c = conn("https://www.instagram.com/graphql/query")
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.setRequestProperty("X-IG-App-ID", APP_ID)
        c.setRequestProperty("X-FB-LSD", lsd)
        c.setRequestProperty("X-ASBD-ID", "129477")
        c.setRequestProperty("Sec-Fetch-Site", "same-origin")
        c.setRequestProperty("Origin", "https://www.instagram.com")
        c.setRequestProperty("Referer", "https://www.instagram.com/reel/$sc/")
        cookie("csrftoken")?.let { c.setRequestProperty("X-CSRFToken", it) }
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        c.setRequestProperty("X-IG-WWW-Claim", "0")
        c.setRequestProperty("Sec-Fetch-Mode", "cors")
        c.setRequestProperty("Sec-Fetch-Dest", "empty")
        c.outputStream.use { it.write(body.toByteArray()) }
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        val txt = c.inputStream.bufferedReader().readText()
        val media = JSONObject(txt).optJSONObject("data")?.optJSONObject("xdt_shortcode_media") ?: return null
        val items = mutableListOf<Media>()
        val side = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        if (side != null && side.length() > 0) {
            for (i in 0 until side.length()) side.getJSONObject(i).optJSONObject("node")?.let { items += node(it) }
        } else items += node(media)
        if (items.isEmpty() || items.all { it.url.isBlank() }) return null
        val user = media.optJSONObject("owner")?.optString("username").orEmpty()
        val cap = media.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
            ?.optJSONObject(0)?.optJSONObject("node")?.optString("text").orEmpty()
        val audio = media.optJSONObject("clips_music_attribution_info")?.optString("audio_url")?.takeIf { it.startsWith("http") }
        return Post(
            title = cap.lineSequence().firstOrNull()?.take(90)?.ifBlank { null } ?: "Publicación de @$user",
            user = user, durationSec = media.optDouble("video_duration", 0.0).toInt(), items = items, audioUrl = audio
        )
    }

    /** Lee del HTML público el video o las imágenes (fotos y carruseles). */
    private fun fromPage(raw: String): Post? {
        var html = raw
        repeat(3) { html = unescape(html) }
        val user = Regex("\"username\":\"([A-Za-z0-9._]+)\"").find(html)?.groupValues?.get(1).orEmpty()
        val title = Regex("<meta property=\"og:title\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.take(90)
            ?: "Publicación de @$user"
        val ogImage = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val video = Regex("\"video_versions\":\\[\\{[^\\]]*?\"url\":\"(https[^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: Regex("\"video_url\":\"(https[^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: Regex("<meta property=\"og:video(?::secure_url)?\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        if (video != null) return Post(title, user, 0, listOf(Media(true, video, ogImage)), null)
        // Imágenes: image_versions2 (la primera candidata es la de mayor tamaño)
        val count = Regex("\"carousel_media_count\":(\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val imgs = Regex("\"image_versions2\":\\{\"candidates\":\\[\\{[^\\]]*?\"url\":\"(https[^\"]+)\"")
            .findAll(html).map { it.groupValues[1] }.distinctBy { it.substringBefore('?') }.toList()
        val chosen = when {
            imgs.isNotEmpty() -> imgs.take(maxOf(1, if (count > 1) count + 1 else 1)).let { l -> if (count > 1 && l.size > count) l.drop(1).take(count) else l.take(maxOf(1, count)) }
            ogImage != null -> listOf(ogImage)
            else -> return null
        }
        return Post(title, user, 0, chosen.map { Media(false, it, it) }, null)
    }

    /** Camino público clásico: /p/{código}/media/?size=l redirige a la imagen. */
    private fun mediaRedirect(sc: String): Post? {
        val c = conn("https://www.instagram.com/p/$sc/media/?size=l", UA_M)
        c.instanceFollowRedirects = true
        val ok = c.responseCode in 200..299 && (c.contentType ?: "").startsWith("image")
        val finalUrl = c.url.toString()
        c.inputStream.close()
        return if (ok) Post("Imagen de Instagram", "", 0, listOf(Media(false, finalUrl, finalUrl)), null) else null
    }

    /** oEmbed público: devuelve la imagen de portada de la publicación. */
    private fun oembed(sc: String): Post? {
        val c = conn("https://www.instagram.com/api/v1/oembed/?url=" + URLEncoder.encode("https://www.instagram.com/p/$sc/", "UTF-8"))
        c.setRequestProperty("X-IG-App-ID", APP_ID)
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        val o = JSONObject(c.inputStream.bufferedReader().readText())
        val img = o.optString("thumbnail_url").takeIf { it.startsWith("http") } ?: return null
        val user = o.optString("author_name")
        return Post(o.optString("title").take(90).ifBlank { "Publicación de @$user" }, user, 0, listOf(Media(false, img, img)), null)
    }

    private fun node(n: JSONObject): Media {
        val isVideo = n.optBoolean("is_video")
        val disp = n.optString("display_url").ifBlank { null }
        return Media(isVideo, if (isVideo) n.optString("video_url") else (disp ?: ""), disp)
    }

    private fun unescape(s: String) = s.replace("\\\\/", "/").replace("\\/", "/")
        .replace("\\\\u0026", "&").replace("\\u0026", "&").replace("&amp;", "&").replace("\\\"", "\"")

    private fun embed(sc: String): Post? {
        var html = conn("https://www.instagram.com/p/$sc/embed/captioned/", UA_M).inputStream.bufferedReader().readText()
        repeat(3) { html = unescape(html) }
        val video = Regex("\"video_url\":\"(https[^\"]+)\"").find(html)?.groupValues?.get(1)
        val images = Regex("\"display_url\":\"(https[^\"]+)\"").findAll(html).map { it.groupValues[1] }.distinct().toList()
            .ifEmpty {
                Regex("<img[^>]*EmbeddedMediaImage[^>]*>").findAll(html).mapNotNull { m ->
                    val tag = m.value
                    Regex("srcset=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)?.split(",")?.lastOrNull()?.trim()?.substringBefore(' ')
                        ?: Regex("src=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)
                }.toList()
            }
        val user = Regex("\"username\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: Regex("class=\"UsernameText\">([^<]+)<").find(html)?.groupValues?.get(1).orEmpty()
        val items = when {
            video != null -> listOf(Media(true, video, images.firstOrNull()))
            images.isNotEmpty() -> images.map { Media(false, it, it) }
            else -> return null
        }
        return Post("Publicación de @$user", user, 0, items, null)
    }

    /** Descarga directa con progreso. */
    fun download(url: String, out: File, onProgress: (Float) -> Unit) {
        val c = conn(url, UA)
        c.setRequestProperty("Referer", "https://www.instagram.com/")
        val total = c.contentLengthLong
        var done = 0L
        c.inputStream.use { inp ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val n = inp.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
    }
}
