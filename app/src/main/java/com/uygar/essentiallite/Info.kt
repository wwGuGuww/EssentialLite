package com.uygar.essentiallite

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Bir kaydın ek bilgisi: sabitleme, etiketler, başlık ve hatırlatıcı. Kaydın yanında <damga>.info olarak durur.
 * Ekran görüntüsündeki okunmuş metin (OCR) ise <damga>.ocr dosyasındadır.
 */
object Info {
    data class Data(
        val pinned: Boolean = false,
        val tags: List<String> = emptyList(),
        val title: String = "",
        val remind: Long = 0L
    )

    private val cache = ConcurrentHashMap<String, Pair<Long, Data>>()
    private val ocrCache = ConcurrentHashMap<String, Pair<Long, String>>()

    private fun infoFile(f: File) = File(f.parentFile, "${f.nameWithoutExtension}.info")
    private fun ocrFile(f: File) = File(f.parentFile, "${f.nameWithoutExtension}.ocr")

    fun read(f: File): Data {
        val file = infoFile(f)
        if (!file.exists()) return Data()
        val mod = file.lastModified()
        cache[file.path]?.let { if (it.first == mod) return it.second }
        val d = try {
            val j = JSONObject(file.readText())
            val tags = j.optJSONArray("tags")
            Data(
                pinned = j.optBoolean("pinned", false),
                tags = (0 until (tags?.length() ?: 0)).map { tags!!.getString(it) },
                title = j.optString("title", ""),
                remind = j.optLong("remind", 0L)
            )
        } catch (e: Throwable) {
            Data()
        }
        cache[file.path] = mod to d
        return d
    }

    fun write(f: File, d: Data) {
        val file = infoFile(f)
        if (!d.pinned && d.tags.isEmpty() && d.title.isBlank() && d.remind == 0L) {
            file.delete()
            cache.remove(file.path)
            return
        }
        val j = JSONObject()
            .put("pinned", d.pinned)
            .put("tags", JSONArray(d.tags))
            .put("title", d.title)
            .put("remind", d.remind)
        file.writeText(j.toString())
        cache.remove(file.path)
    }

    fun update(f: File, block: (Data) -> Data) = write(f, block(read(f)))

    fun readOcr(f: File): String {
        val file = ocrFile(f)
        if (!file.exists()) return ""
        val mod = file.lastModified()
        ocrCache[file.path]?.let { if (it.first == mod) return it.second }
        val t = try { file.readText() } catch (e: Throwable) { "" }
        ocrCache[file.path] = mod to t
        return t
    }

    fun hasOcr(f: File) = ocrFile(f).exists()

    fun writeOcr(f: File, text: String) {
        val file = ocrFile(f)
        file.writeText(text)
        ocrCache.remove(file.path)
    }

    fun deleteAll(f: File) {
        infoFile(f).delete()
        ocrFile(f).delete()
        cache.remove(infoFile(f).path)
        ocrCache.remove(ocrFile(f).path)
    }

    /** "iş, fatura ,  #acil" → [iş, fatura, acil]. Küçük harf, tekrarsız, en fazla 8 etiket. */
    fun parseTags(raw: String): List<String> =
        raw.split(',', ';', '\n').map { it.trim().trimStart('#').lowercase(java.util.Locale.ENGLISH) }
            .filter { it.isNotEmpty() }.distinct().take(8)
}
