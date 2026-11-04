package com.uygar.essentiallite

import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/** Shizuku üzerinden kabuk komutları. Shizuku hazır değilse null döner. Arka planda çağrılmalı. */
object Shell {

    fun run(vararg cmd: String): String? {
        if (!Shot.ready()) return null
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf(*cmd), null, null) as Process
            val out = p.inputStream.bufferedReader().readText()
            // Çıktı bitince süreç de biter. Shizuku'nun süreç nesnesinde waitFor(zaman aşımı) hata fırlattığı için kullanılmaz.
            runCatching { p.waitFor() }
            out
        } catch (e: Throwable) {
            android.util.Log.e("Shell", "komut başarısız", e)
            null
        }
    }

    /** Şu an ön planda olan uygulamanın paket adı (ör. com.instagram.android). */
    fun foregroundPackage(): String? {
        val out = run("sh", "-c", "dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
        if (out == null) return null
        return Regex("""\s([a-zA-Z0-9_.]+)/""").find(out)?.groupValues?.get(1)
    }
}
