package com.cimaestrategica.estadoshd

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lector público de LinkedIn (sin iniciar sesión): lee la página pública de la publicación
 * y su versión embebida para obtener videos (data-sources) e imágenes (media.licdn.com).
 */
object LiExtractor {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    private const val UA_M = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
    private val cache = HashMap<String, IgExtractor.Post>()

    fun isLinkedIn(url: String) = url.contains("linkedin.com", true) || url.contains("lnkd.in", true)
    fun cached(url: String) = cache[url]

    private fun get(u: String, ua: String): Pair<Int, String> {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 15000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "es-CO,es;q=0.9,en;q=0.8")
        }
        val code = c.responseCode
        val body = if (code in 200..299) c.inputStream.bufferedReader().readText() else ""
        return code to body
    }

    private fun resolve(url: String): String = try {
        if (!url.contains("lnkd.in", true)) url else {
            val (_, html) = get(url, UA_M)
            Regex("https://www\\.linkedin\\.com/[^\"'<> ]+").find(html)?.value ?: url
        }
    } catch (e: Exception) { url }

    private fun embedUrl(url: String): String? {
        val m = Regex("(activity|ugcPost|share)[-:](\\d{15,})").find(url) ?: return null
        return "https://www.linkedin.com/embed/feed/update/urn:li:${m.groupValues[1]}:${m.groupValues[2]}"
    }

    private fun unescape(s: String) = s.replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'")
        .replace("\\u002F", "/").replace("\\/", "/").replace("\\u0026", "&")

    fun fetch(url0: String): IgExtractor.Post {
        cache[url0]?.let { return it }
        val url = resolve(url0.substringBefore('#'))
        val pages = listOfNotNull(url, embedUrl(url)).distinct()
        val errors = mutableListOf<String>()
        for (p in pages) for (ua in listOf(UA, UA_M)) {
            try {
                val (code, html) = get(p, ua)
                if (code !in 200..299) { errors += "linkedin(${if (p.contains("/embed/")) "embed" else "página"}): HTTP $code"; continue }
                parse(html)?.let { cache[url0] = it; return it }
                errors += "linkedin(${if (p.contains("/embed/")) "embed" else "página"}): sin video ni imagen"
            } catch (e: Exception) { errors += "linkedin: ${e.message}" }
        }
        throw IllegalStateException("LinkedIn no entregó el contenido público.\n" + errors.distinct().joinToString("\n"))
    }

    private fun parse(raw: String): IgExtractor.Post? {
        val html = unescape(raw)
        val title = (Regex("<meta property=\"og:title\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: Regex("<title>([^<]+)</title>").find(html)?.groupValues?.get(1) ?: "Publicación de LinkedIn").trim().take(90)
        val user = Regex("\"author\":\\{[^}]*?\"name\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: title.substringBefore(" en LinkedIn").substringBefore(" on LinkedIn").take(40)
        val poster = Regex("data-poster-url=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        // Video: data-sources="[{src,type,data-bitrate}]" -> el de mayor calidad
        // data-sources viene con comillas escapadas (&quot;): se busca en el HTML original y luego se des-escapa
        val sources = Regex("data-sources=\"(\\[[^\"]*\\])\"").find(raw)?.groupValues?.get(1)?.let { unescape(it) }
        var video: String? = null
        if (sources != null) runCatching {
            val arr = JSONArray(sources)
            var best = -1L
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val src = o.optString("src"); val br = o.optLong("data-bitrate", i.toLong())
                if (src.startsWith("http") && o.optString("type").contains("mp4") && br >= best) { best = br; video = src }
            }
        }
        if (video == null) video = Regex("\"(https://dms\\.licdn\\.com/playlist/vid/[^\"]+)\"").findAll(html).map { it.groupValues[1] }.lastOrNull()
        if (video != null) return IgExtractor.Post(title, user, 0, listOf(IgExtractor.Media(true, video!!, poster)), null, "https://www.linkedin.com/")
        // Imágenes de la publicación (no avatares): feedshare / image-shrink
        val imgs = Regex("(?:data-delayed-url|src)=\"(https://media\\.licdn\\.com/dms/image/[^\"]+)\"").findAll(html)
            .map { it.groupValues[1] }
            .filter { it.contains("feedshare") || it.contains("image-shrink") || it.contains("article-cover") }
            .distinctBy { it.substringBefore('?').substringBeforeLast('/') }
            .toList()
        val og = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val list = imgs.ifEmpty { listOfNotNull(og?.takeIf { it.contains("media.licdn.com") && !it.contains("profile-displayphoto") }) }
        if (list.isEmpty()) return null
        return IgExtractor.Post(title, user, 0, list.map { IgExtractor.Media(false, it, it) }, null, "https://www.linkedin.com/")
    }
}
