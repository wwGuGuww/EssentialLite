package com.uygar.essentiallite

import android.content.Context
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Ekran görüntüsündeki metni cihazda okur (ML Kit, ağ gerekmez) ve <damga>.ocr olarak saklar. */
object Ocr {
    private val running = AtomicBoolean(false)

    /** Yüklenme ilerlemesi: kaç görüntü okunacak, kaçı okundu. Ana ekranda gösterilir. */
    @Volatile var pending = 0
    @Volatile var done = 0

    /** Tek dosyayı okur; arka planda çağrılmalı. Okunan metni döndürür (hata: null). */
    fun process(ctx0: Context, f: File): String? {
        if (!f.exists()) return null
        return try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            var sample = 1
            while (maxOf(b.outWidth, b.outHeight) / sample > 2600) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = try {
                Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0)), 30, TimeUnit.SECONDS).text
            } finally {
                rec.close()
                bmp.recycle()
            }
            Info.writeOcr(f, text)
            text
        } catch (e: Throwable) {
            AppLog.e(ctx0, "OCR", e)
            null
        }
    }

    /** Yeni kayıttan sonra: önce OCR, sonra (ayarda açıksa) AI başlık/etiket. Arka planda çağrılmalı. */
    fun afterSave(ctx: Context, f: File) {
        val text = process(ctx, f) ?: return
        AiTag.maybe(ctx, f, text)
        CaptureWidget.refreshAll(ctx)
    }

    /** OCR'ı olmayan eski görselleri sırayla okur (uygulama açılınca). Aynı anda tek iş çalışır. */
    fun backlog(ctx: Context) {
        if (!running.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        Thread {
            try {
                val todo = Store.dir(app).listFiles()
                    ?.filter { Store.isImage(it) && !Info.hasOcr(it) }
                    ?.sortedByDescending { it.lastModified() }
                    .orEmpty()
                pending = todo.size
                done = 0
                todo.forEach { process(app, it); done++ }
            } finally {
                pending = 0
                done = 0
                running.set(false)
            }
        }.start()
    }
}

/** Ayardan açıksa ekran görüntüsüne Gemini ile başlık ve etiket ekler. */
object AiTag {
    fun maybe(ctx: Context, f: File, text: String) {
        if (!Prefs.get(ctx, "ai_tag", false)) return
        if (text.trim().length < 20) return
        val key = Settings.apiKey(ctx) ?: return
        if (!Summarizer.withinDailyLimit(ctx)) return
        try {
            Summarizer.recordUse(ctx)
            val (title, tags) = Summarizer.tag(key, text)
            Info.update(f) { d ->
                d.copy(
                    title = if (d.title.isBlank()) title else d.title,
                    tags = (d.tags + tags).distinct().take(8)
                )
            }
        } catch (e: Throwable) {
            AppLog.e(ctx, "AI etiket", e)
        }
    }
}
