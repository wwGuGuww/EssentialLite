package com.uygar.essentiallite

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Kopyalanan ya da paylaşılan linkin sayfa başlığını bulur ve kaydın başlığı olarak saklar. */
object LinkTitle {
    private val URL_RE = Regex("""https?://[^\s<>"']+""")

    fun urlIn(text: String): String? = URL_RE.find(text)?.value

    /** Sayfanın <title> değerini döndürür (ağ çağrısı; arka planda çağrılmalı). */
    fun fetch(url: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 6_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) EssentialLite")
                setRequestProperty("Accept-Language", "tr,en;q=0.8")
            }
            try {
                val bytes = conn.inputStream.use { it.readNBytes(96 * 1024) }
                val html = String(bytes, Charsets.UTF_8)
                Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.get(1)
                    ?.replace(Regex("""\s+"""), " ")?.trim()
                    ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")
                    ?.replace("&lt;", "<")?.replace("&gt;", ">")
                    ?.takeIf { it.isNotEmpty() }?.take(140)
            } finally {
                conn.disconnect()
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** Metinde link varsa başlığı arka planda bulup kayda yazar. */
    fun attach(ctx: Context, file: File, text: String) {
        val url = urlIn(text) ?: return
        val app = ctx.applicationContext
        Thread {
            val title = fetch(url) ?: return@Thread
            Info.update(file) { if (it.title.isBlank()) it.copy(title = title) else it }
            CaptureWidget.refreshAll(app)
        }.start()
    }
}
