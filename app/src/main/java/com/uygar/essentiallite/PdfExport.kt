package com.uygar.essentiallite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

/**
 * Seçili kayıtları PDF'e çevirir: her kayıt bir sayfa. Önce başlık ve tarih, sonra görsel (varsa), sonra metin.
 * Çok uzun metin tek sayfaya sığdırılır (kesilir); tam metin için notu metin olarak paylaşın.
 */
object PdfExport {
    private const val W = 595 // A4 nokta (72 dpi)
    private const val H = 842
    private const val M = 48

    fun write(ctx: Context, items: List<Entry>): File {
        val out = File(File(ctx.cacheDir, "share").apply { mkdirs() }, "records.pdf")
        val doc = PdfDocument()
        items.forEachIndexed { i, e ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create())
            drawEntry(ctx, page.canvas, e)
            doc.finishPage(page)
        }
        if (items.isEmpty()) doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create()))
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun drawEntry(ctx: Context, c: Canvas, e: Entry) {
        c.drawColor(Color.WHITE)
        val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(140, 140, 140); textSize = 10f; letterSpacing = 0.1f }
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 40); textSize = 12f }
        var y = M.toFloat()

        c.drawText("${e.kind}  ·  ${Store.stamp(e.time)}${e.sourceLabel?.let { "  ·  $it" } ?: ""}", M.toFloat(), y, meta)
        y += 22f

        if (e.title.isNotBlank()) {
            y = drawText(c, e.title, title, M.toFloat(), y, (W - 2 * M).toFloat()) + 10f
        }

        if (e.kind == "SCREEN" || e.kind == "IMAGE") {
            val bmp = decode(e.file, 1400)
            if (bmp != null) {
                val maxW = (W - 2 * M).toFloat()
                val maxH = H * 0.6f
                val scale = minOf(maxW / bmp.width, maxH / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val dst = android.graphics.RectF(M.toFloat(), y, M + w, y + h)
                c.drawBitmap(bmp, null, dst, null)
                bmp.recycle()
                y = dst.bottom + 16f
            }
        }

        if (e.text.isNotBlank()) {
            drawText(c, e.text.trim(), body, M.toFloat(), y, (W - 2 * M).toFloat(), maxBottom = H - M.toFloat())
        }
    }

    /** Metni verilen genişliğe sarar, çizer ve alt y'yi döndürür. [maxBottom] aşılırsa kesilir. */
    private fun drawText(c: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Float, maxBottom: Float = H - M.toFloat()): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.25f)
            .build()
        val avail = maxBottom - y
        c.save()
        c.translate(x, y)
        if (layout.height > avail) {
            // Kesilen kısma gelindiğinde ufak bir işaret koy.
            c.clipRect(0f, 0f, width, avail)
        }
        layout.draw(c)
        c.restore()
        return y + minOf(layout.height.toFloat(), avail)
    }

    private fun decode(f: File, maxSide: Int): Bitmap? = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (t: Throwable) {
        null
    }
}
