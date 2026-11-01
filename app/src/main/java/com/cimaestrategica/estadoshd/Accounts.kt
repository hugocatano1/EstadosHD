package com.cimaestrategica.estadoshd

import android.content.Context
import android.webkit.CookieManager
import java.io.File

/** Sesiones de redes sociales guardadas SOLO en el celular (formato cookies de Netscape para yt-dlp). */
object Accounts {
    data class Acc(val key: String, val name: String, val loginUrl: String, val domains: List<String>, val color: Long, val icon: Int)

    val ALL = listOf(
        Acc("instagram", "Instagram", "https://www.instagram.com/accounts/login/", listOf("instagram.com"), 0xFFE1306C, R.drawable.ms_photo_camera),
        Acc("facebook", "Facebook", "https://m.facebook.com/login/", listOf("facebook.com"), 0xFF1877F2, R.drawable.ms_public),
        Acc("tiktok", "TikTok", "https://www.tiktok.com/login", listOf("tiktok.com"), 0xFFFE2C55, R.drawable.ms_equalizer),
        Acc("x", "X", "https://x.com/i/flow/login", listOf("x.com", "twitter.com"), 0xFF202124, R.drawable.ms_close)
    )

    fun forUrl(url: String): Acc? {
        val u = url.lowercase()
        return when {
            "instagram." in u || "instagr.am" in u || "threads." in u -> ALL[0]
            "facebook." in u || "fb.watch" in u || "fb.com" in u -> ALL[1]
            "tiktok." in u -> ALL[2]
            "x.com" in u || "twitter." in u -> ALL[3]
            else -> null
        }
    }

    fun file(ctx: Context, key: String) = File(ctx.filesDir, "cookies_$key.txt")
    fun isConnected(ctx: Context, key: String) = file(ctx, key).let { it.exists() && it.length() > 60 }
    fun connected(ctx: Context): Set<String> = ALL.filter { isConnected(ctx, it.key) }.map { it.key }.toSet()

    /** Lee las cookies del WebView tras iniciar sesión y las guarda para yt-dlp. */
    fun save(ctx: Context, acc: Acc): Boolean {
        val cm = CookieManager.getInstance(); cm.flush()
        val exp = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        var n = 0
        for (d in acc.domains) {
            val raw = cm.getCookie("https://www.$d") ?: cm.getCookie("https://$d") ?: continue
            raw.split(";").map { it.trim() }.filter { "=" in it }.forEach { kv ->
                val name = kv.substringBefore("="); val value = kv.substringAfter("=")
                sb.append(".$d\tTRUE\t/\tTRUE\t$exp\t$name\t$value\n"); n++
            }
        }
        if (n < 2) return false
        file(ctx, acc.key).writeText(sb.toString())
        return true
    }

    fun disconnect(ctx: Context, acc: Acc) {
        file(ctx, acc.key).delete()
        val cm = CookieManager.getInstance()
        for (d in acc.domains) {
            cm.getCookie("https://www.$d")?.split(";")?.map { it.trim().substringBefore("=") }?.forEach { name ->
                cm.setCookie("https://www.$d", "$name=; Max-Age=0; Domain=.$d; Path=/")
            }
        }
        cm.flush()
    }
}
