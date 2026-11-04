package com.uygar.essentiallite

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini API (ücretsiz katman, kullanıcının kendi anahtarı): not özeti ve ekran görüntüsü başlık/etiketi.
 * Model adı doğrulanması gereken tek yer burası; sorun çıkarsa yalnızca MODEL değişir.
 * Ağ çağrısı yaptığı için arka planda çağrılmalı.
 */
object Summarizer {
    private const val MODEL = "gemini-3.5-flash-lite"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    const val DAILY_LIMIT = 30
    private const val USAGE_FILE = "essentiallite_usage"

    /** Bugün için kalan hak var mı? Gün değişince sayaç sıfırlanır. */
    fun withinDailyLimit(ctx: Context): Boolean = todayCount(ctx) < DAILY_LIMIT

    /** Bir istek gönderildiğinde çağrılır. */
    fun recordUse(ctx: Context) {
        val p = ctx.getSharedPreferences(USAGE_FILE, Context.MODE_PRIVATE)
        val today = Store.dayKey(System.currentTimeMillis())
        val count = if (p.getString("day", null) == today) p.getInt("count", 0) else 0
        p.edit().putString("day", today).putInt("count", count + 1).apply()
    }

    private fun todayCount(ctx: Context): Int {
        val p = ctx.getSharedPreferences(USAGE_FILE, Context.MODE_PRIVATE)
        return if (p.getString("day", null) == Store.dayKey(System.currentTimeMillis())) p.getInt("count", 0) else 0
    }

    fun summarize(apiKey: String, text: String): String {
        val prompt = "Aşağıdaki notu Türkçe, en fazla 5 kısa madde ile özetle. Notta olmayan bilgi ekleme.\n\n$text"
        return generate(apiKey, prompt, 400, false)
    }

    /** Ekran görüntüsünden okunan metne kısa bir başlık ve küçük harfli etiketler üretir. */
    fun tag(apiKey: String, ocrText: String): Pair<String, List<String>> {
        val prompt = "Aşağıdaki metin bir ekran görüntüsünden okundu. Türkçe, en fazla 6 kelimelik bir başlık ve " +
            "en fazla 4 adet tek kelimelik küçük harfli etiket üret. Metinde olmayan bilgi ekleme. " +
            "Yalnızca şu JSON'u ver: {\"title\":\"...\",\"tags\":[\"...\"]}\n\n" + ocrText.take(3000)
        val raw = generate(apiKey, prompt, 200, true)
        val j = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val tags = j.optJSONArray("tags")
        return j.optString("title", "").trim() to
            Info.parseTags((0 until (tags?.length() ?: 0)).joinToString(",") { tags!!.getString(it) })
    }

    private fun generate(apiKey: String, prompt: String, maxTokens: Int, json: Boolean): String {
        val config = JSONObject().put("maxOutputTokens", maxTokens)
        if (json) config.put("responseMimeType", "application/json")
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            )
            .put("generationConfig", config)

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code: ${raw.replace('\n', ' ').take(160)}")
            val parts = JSONObject(raw)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text"))
            }
            return sb.toString().trim().ifEmpty { throw IllegalStateException("Boş yanıt") }
        } finally {
            conn.disconnect()
        }
    }
}
