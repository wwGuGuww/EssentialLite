package com.uygar.essentiallite

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

object Shot {
    fun ready(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    /**
     * Ekran görüntüsünü alıp kaydeder ve kaydedilen dosyayı döndürür (hata: null).
     * [source]: ön plandaki uygulamanın paket adı. Arka planda çağrılmalı.
     */
    fun capture(ctx: Context, source: String? = null): File? {
        if (!ready()) return null
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("screencap", "-p"), null, null) as Process
        val bytes = p.inputStream.readBytes()
        val code = p.waitFor()
        if (code != 0 || bytes.isEmpty()) {
            AppLog.e(ctx, "EKRAN: screencap başarısız, kod=$code")
            return null
        }
        val file = Store.newFile(ctx, "png")
        file.writeBytes(bytes)
        Store.saveMeta(file, source)
        CaptureWidget.refreshAll(ctx)
        val app = ctx.applicationContext
        Thread { Ocr.afterSave(app, file) }.start()
        return file
    }

    /**
     * Ham (PNG'siz) ekran görüntüsü: kodlama beklemeden bellekte bitmap döndürür. Başlık 12 ya da 16 bayt
     * (genişlik, yükseklik, biçim[, renk uzayı]); biçim RGBA_8888 değilse null döner.
     */
    fun grab(): android.graphics.Bitmap? {
        if (!ready()) return null
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("screencap"), null, null) as Process
        val bytes = p.inputStream.readBytes()
        p.waitFor()
        if (bytes.size < 16) return null
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val w = buf.getInt(0)
        val h = buf.getInt(4)
        val fmt = buf.getInt(8)
        val header = bytes.size - w * h * 4
        if (fmt != 1 || w <= 0 || h <= 0 || (header != 12 && header != 16)) return null
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes, header, w * h * 4).slice())
        return bmp
    }

    /** Bitmap'i yeni bir PNG kaydı olarak yazar; yazıldıktan sonra widget'ı yeniler. Arka planda çağrılmalı. */
    fun write(ctx: Context, bmp: android.graphics.Bitmap, file: File, source: String? = null) {
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        Store.saveMeta(file, source)
        CaptureWidget.refreshAll(ctx)
    }

    /** Eski çağrı noktaları (widget, uygulama düğmesi) için mesaj döndüren sarmalayıcı. */
    fun take(ctx: Context, source: String? = null): String {
        if (!ready()) return "SHİZUKU HAZIR DEĞİL"
        return if (capture(ctx, source) != null) "EKRAN KAYDEDİLDİ" else "EKRAN ALINAMADI"
    }
}
