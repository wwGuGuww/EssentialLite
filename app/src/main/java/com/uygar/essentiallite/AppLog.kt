package com.uygar.essentiallite

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sessiz yutulan hataları kayda geçirir (files/errors.log, en fazla ~200 satır). Ayarlar'daki
 * HATA GÜNLÜĞÜ ekranı bunu gösterir. Günlük yazımı asla uygulamayı çökertmez.
 */
object AppLog {
    private const val FILE = "errors.log"
    private const val MAX_LINES = 200
    private val lock = Any()

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun e(ctx: Context, where: String, t: Throwable? = null) {
        try {
            val line = buildString {
                append(SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date()))
                append("  ").append(where)
                if (t != null) append(": ").append(t.javaClass.simpleName).append(" ").append((t.message ?: "").replace("\n", " "))
            }
            synchronized(lock) {
                val f = file(ctx)
                val old = if (f.exists()) f.readLines() else emptyList()
                val kept = (old + line).takeLast(MAX_LINES)
                f.writeText(kept.joinToString("\n") + "\n")
            }
        } catch (_: Throwable) {
        }
    }

    fun read(ctx: Context): String = try {
        file(ctx).takeIf { it.exists() }?.readText().orEmpty()
    } catch (_: Throwable) {
        ""
    }

    fun clear(ctx: Context) {
        try { file(ctx).delete() } catch (_: Throwable) {}
    }
}
