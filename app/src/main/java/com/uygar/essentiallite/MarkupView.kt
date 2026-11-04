package com.uygar.essentiallite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Görselin üstüne çizim katmanı. Noktalar görselin kendi oranlarında (0..1) tutulur,
 * böylece ekran boyutu değişse de kaydederken orijinal çözünürlükte aynı yerde çizilir.
 * Araçlar: kalem, vurgu, bulanıklaştırma, ok, metin ve kırpma.
 */
class MarkupView(ctx: Context, private val src: Bitmap) : View(ctx) {

    enum class Tool { PEN, MARK, MOSAIC, ARROW, TEXT, CROP }

    /** [width] görsel genişliğine oranlıdır. Metin için [text] dolu, [width] yazı boyutudur. */
    class Stroke(val tool: Tool, val color: Int, val width: Float, val pts: MutableList<PointF>) {
        /** Örnek işlem: ekranda görünür ama kaydedilen görüntüye girmez. */
        var demo = false
        var text: String? = null
    }

    val strokes = mutableListOf<Stroke>()
    var tool = Tool.PEN
    /** Kalem ve vurgu rengi (beyaz, kırmızı, sarı). */
    var color = Color.WHITE
    /** Her değişiklikte çağrılır (sayaç ve düğmeleri yenilemek için). */
    var onChange: (() -> Unit)? = null
    /** Metin aracıyla bir noktaya dokunulunca çağrılır (görselin oranlı koordinatı). */
    var onTextTap: ((Float, Float) -> Unit)? = null
    /** Kırpma alanı, görselin oranlı koordinatlarında. Yoksa tüm görsel kullanılır. */
    var crop: RectF? = null
    /** Kalınlık: 0 ince, 1 orta, 2 kalın. */
    var size = 1

    private var current: Stroke? = null
    private val imageRect = RectF()

    private fun computeRect() {
        if (width == 0 || height == 0) return
        val s = minOf(width / src.width.toFloat(), height / src.height.toFloat())
        val w = src.width * s
        val h = src.height * s
        imageRect.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        computeRect()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(src, null, imageRect, null)
        for (s in strokes) drawOnView(canvas, s)
        current?.let { drawOnView(canvas, it) }
        drawCropOverlay(canvas)
    }

    private fun drawCropOverlay(c: Canvas) {
        val k = crop ?: return
        val l = imageRect.left + k.left * imageRect.width()
        val t = imageRect.top + k.top * imageRect.height()
        val r = imageRect.left + k.right * imageRect.width()
        val b = imageRect.top + k.bottom * imageRect.height()
        val dim = Paint().apply { color = 0x99000000.toInt() }
        c.drawRect(imageRect.left, imageRect.top, imageRect.right, t, dim)
        c.drawRect(imageRect.left, b, imageRect.right, imageRect.bottom, dim)
        c.drawRect(imageRect.left, t, l, b, dim)
        c.drawRect(r, t, imageRect.right, b, dim)
        c.drawRect(l, t, r, b, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        })
    }

    private fun drawOnView(c: Canvas, s: Stroke) {
        val rw = imageRect.width()
        val rh = imageRect.height()
        if (s.tool == Tool.TEXT) {
            val t = s.text ?: return
            val p = s.pts.firstOrNull() ?: return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.color
                textSize = s.width * rw
                isFakeBoldText = true
            }
            c.drawText(t, imageRect.left + p.x * rw, imageRect.top + p.y * rh, paint)
            return
        }
        if (s.tool == Tool.MOSAIC) {
            if (s.pts.size < 2) return
            val a = s.pts.first()
            val b = s.pts.last()
            val r = RectF(
                imageRect.left + minOf(a.x, b.x) * rw,
                imageRect.top + minOf(a.y, b.y) * rh,
                imageRect.left + maxOf(a.x, b.x) * rw,
                imageRect.top + maxOf(a.y, b.y) * rh
            )
            // Bulanıklaştırma önizlemesi: koyu cam ve üstünde nokta dokusu.
            c.drawRect(r, Paint().apply { color = 0xAA0B0B0B.toInt() })
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
            var y = r.top + 4f
            while (y < r.bottom) {
                var x = r.left + 4f
                while (x < r.right) {
                    c.drawCircle(x, y, 1.4f, dot)
                    x += 8f
                }
                y += 8f
            }
            c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f
                alpha = 160
            })
            return
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = s.width * rw
        }
        if (s.tool == Tool.ARROW && s.pts.size >= 2) {
            val a = s.pts.first()
            val b = s.pts.last()
            drawArrow(c, imageRect.left + a.x * rw, imageRect.top + a.y * rh, imageRect.left + b.x * rw, imageRect.top + b.y * rh, p)
            return
        }
        if (s.pts.size == 1) {
            val pt = s.pts[0]
            c.drawCircle(imageRect.left + pt.x * rw, imageRect.top + pt.y * rh, p.strokeWidth / 2f,
                Paint(p).apply { style = Paint.Style.FILL })
        } else {
            val path = Path()
            s.pts.forEachIndexed { i, pt ->
                val x = imageRect.left + pt.x * rw
                val y = imageRect.top + pt.y * rh
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, p)
        }
    }

    /** Çizgi ve uç: uç uzunluğu kalınlığa göre. */
    private fun drawArrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) {
        c.drawLine(x0, y0, x1, y1, p)
        val ang = atan2(y1 - y0, x1 - x0).toDouble()
        val len = p.strokeWidth * 4f + 10f
        for (d in listOf(0.45, -0.45)) {
            val hx = x1 - len * cos(ang + d).toFloat()
            val hy = y1 - len * sin(ang + d).toFloat()
            c.drawLine(x1, y1, hx, hy, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (imageRect.width() <= 0f) return false
        val nx = ((e.x - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
        val ny = ((e.y - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> when (tool) {
                Tool.CROP -> crop = RectF(nx, ny, nx, ny)
                Tool.TEXT -> current = null
                else -> current = Stroke(tool, colorOf(tool), widthOf(tool), mutableListOf(PointF(nx, ny)))
            }
            MotionEvent.ACTION_MOVE -> when (tool) {
                Tool.CROP -> crop?.let { it.right = nx; it.bottom = ny }
                Tool.TEXT -> Unit
                else -> current?.let {
                    when (it.tool) {
                        Tool.MOSAIC, Tool.ARROW -> if (it.pts.size == 1) it.pts.add(PointF(nx, ny)) else it.pts[1] = PointF(nx, ny)
                        else -> it.pts.add(PointF(nx, ny))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (tool) {
                    Tool.CROP -> normalizeCrop()
                    Tool.TEXT -> if (e.actionMasked == MotionEvent.ACTION_UP) onTextTap?.invoke(nx, ny)
                    else -> current?.let { if ((it.tool != Tool.MOSAIC && it.tool != Tool.ARROW) || it.pts.size >= 2) strokes.add(it) }
                }
                current = null
                onChange?.invoke()
            }
        }
        invalidate()
        return true
    }

    /** Kırpma kutusu ters çizilmişse (sağdan sola) köşeleri düzeltir; çok küçükse siler. */
    private fun normalizeCrop() {
        val k = crop ?: return
        val r = RectF(minOf(k.left, k.right), minOf(k.top, k.bottom), maxOf(k.left, k.right), maxOf(k.top, k.bottom))
        crop = if (r.width() < 0.05f || r.height() < 0.05f) null else r
    }

    /** Metin aracı: dokunulan yere yazı ekler. */
    fun addText(x: Float, y: Float, text: String) {
        strokes.add(Stroke(Tool.TEXT, color, 0.05f, mutableListOf(PointF(x, y))).apply { this.text = text })
        invalidate()
        onChange?.invoke()
    }

    fun clearCrop() {
        crop = null
        invalidate()
        onChange?.invoke()
    }

    /**
     * Tasarımdaki üç örnek işlem: kırmızı kalem halkası, sarı vurgu çizgisi ve bulanık kutu.
     * Yalnızca ekranda gösterilir; render() bunları atlar.
     */
    fun seedDemo() {
        val circle = mutableListOf<PointF>()
        for (i in 0..28) {
            val a = (i / 28.0) * Math.PI * 2 - 2.2
            circle.add(PointF((0.43 + 0.095 * Math.cos(a)).toFloat(), (0.22 + 0.095 * Math.sin(a)).toFloat()))
        }
        val pen = Stroke(Tool.PEN, 0xFFF0303A.toInt(), 0.016f, circle).apply { demo = true }
        val mark = Stroke(Tool.MARK, 0x6BFFD60A, 0.07f,
            mutableListOf(PointF(0.07f, 0.08f), PointF(0.56f, 0.08f))).apply { demo = true }
        val blur = Stroke(Tool.MOSAIC, Color.TRANSPARENT, 0f,
            mutableListOf(PointF(0.04f, 0.46f), PointF(0.96f, 0.53f))).apply { demo = true }
        strokes.addAll(listOf(pen, mark, blur))
        invalidate()
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
        invalidate()
        onChange?.invoke()
    }

    /** Orijinal çözünürlükte, çizimler işlenmiş bir kopya üretir. Kırpma varsa kırpılmış olarak döner. */
    fun render(): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        for (s in strokes) {
            if (s.demo) continue
            when (s.tool) {
                Tool.TEXT -> {
                    val t = s.text ?: continue
                    val pt = s.pts.firstOrNull() ?: continue
                    c.drawText(t, pt.x * w, pt.y * h, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = s.color
                        textSize = s.width * w
                        isFakeBoldText = true
                    })
                }
                Tool.MOSAIC -> {
                    if (s.pts.size < 2) continue
                    val a = s.pts.first()
                    val b = s.pts.last()
                    val l = (minOf(a.x, b.x) * w).toInt().coerceIn(0, out.width - 1)
                    val t = (minOf(a.y, b.y) * h).toInt().coerceIn(0, out.height - 1)
                    val r = (maxOf(a.x, b.x) * w).toInt().coerceIn(l + 1, out.width)
                    val bt = (maxOf(a.y, b.y) * h).toInt().coerceIn(t + 1, out.height)
                    val region = Bitmap.createBitmap(out, l, t, r - l, bt - t)
                    val small = Bitmap.createScaledBitmap(region, maxOf(1, (r - l) / 14), maxOf(1, (bt - t) / 14), true)
                    val blocky = Bitmap.createScaledBitmap(small, r - l, bt - t, false)
                    c.drawBitmap(blocky, l.toFloat(), t.toFloat(), null)
                }
                else -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = s.color
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = s.width * w
                    }
                    if (s.tool == Tool.ARROW && s.pts.size >= 2) {
                        val a = s.pts.first()
                        val b = s.pts.last()
                        drawArrow(c, a.x * w, a.y * h, b.x * w, b.y * h, p)
                    } else if (s.pts.size == 1) {
                        val pt = s.pts[0]
                        c.drawCircle(pt.x * w, pt.y * h, p.strokeWidth / 2f, Paint(p).apply { style = Paint.Style.FILL })
                    } else {
                        val path = Path()
                        s.pts.forEachIndexed { i, pt ->
                            if (i == 0) path.moveTo(pt.x * w, pt.y * h) else path.lineTo(pt.x * w, pt.y * h)
                        }
                        c.drawPath(path, p)
                    }
                }
            }
        }
        val k = crop ?: return out
        val l = (k.left * w).toInt().coerceIn(0, out.width - 1)
        val t = (k.top * h).toInt().coerceIn(0, out.height - 1)
        val cw = ((k.right - k.left) * w).toInt().coerceIn(1, out.width - l)
        val ch = ((k.bottom - k.top) * h).toInt().coerceIn(1, out.height - t)
        return Bitmap.createBitmap(out, l, t, cw, ch)
    }

    /** Vurgu, seçili rengin %42 saydam hâlidir (tasarımdaki gibi). */
    private fun colorOf(t: Tool): Int = when (t) {
        Tool.PEN -> color
        Tool.MARK -> (color and 0x00FFFFFF) or (0x6B shl 24)
        Tool.MOSAIC -> Color.TRANSPARENT
        Tool.ARROW -> color
        else -> color
    }

    private fun widthOf(t: Tool) = when (t) {
        Tool.PEN -> floatArrayOf(0.004f, 0.007f, 0.014f)[size]
        Tool.MARK -> floatArrayOf(0.022f, 0.035f, 0.06f)[size]
        Tool.MOSAIC -> 0f
        Tool.ARROW -> floatArrayOf(0.005f, 0.009f, 0.016f)[size]
        else -> 0.008f
    }
}
