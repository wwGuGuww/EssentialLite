package com.uygar.essentiallite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Asistan açılırken ekranın üstüne çizilen yazı seçici; telefonun kendi metin seçimi gibi çalışır.
 * Her kelime ekranda gerçekte durduğu yerdedir. Kelimeye dokunmak onu seçer; seçimin iki ucundaki tutamaçları
 * sürükleyerek seçim kelime kelime, satır satır genişler ya da daralır. Bir kelimeden başlayıp sürüklemek de
 * aralık seçer. Seçimin üstünde KOPYALA / TÜMÜ çubuğu çıkar. Yazı olmayan boş yere dokunmak asistanı kapatır.
 */
class OcrPick(ctx: Context) : View(ctx) {
    class Word(val text: String, val r: RectF, val line: Int)

    var onEmpty: () -> Unit = {}
    var onCopy: (String) -> Unit = {}
    var onSelection: (text: String, count: Int) -> Unit = { _, _ -> }

    private var words: List<Word> = emptyList()
    private var s = -1
    private var e = -1

    private val d = ctx.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.RED }
    private val handleEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = 0xFFFFFFFF.toInt() }
    private val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2 * d; color = Ui.RED }
    private val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2161616.toInt() }
    private val barEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d; color = 0xFF3A3A3A.toInt() }
    private val barText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = 13 * d; typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true; letterSpacing = 0.1f; textAlign = Paint.Align.CENTER
    }

    private var bar: RectF? = null
    private var barSplitX = 0f

    private var downX = 0f
    private var downY = 0f
    private var downWord = -1
    private var moved = false
    private var mode = 0 // 0 dokunma/aralık, 1 başlangıç tutamacı, 2 bitiş tutamacı, 3 çubuk

    fun setWords(w: List<Word>) {
        words = w
        s = -1; e = -1
        changed()
    }

    fun clear() {
        words = emptyList()
        s = -1; e = -1
        bar = null
        invalidate()
    }

    fun clearSelection() {
        s = -1; e = -1
        changed()
    }

    val lineCount get() = words.size

    /** Seçili kelimeler okuma sırasıyla; satır değişince alt satıra geçilir. */
    fun selectedText(): String {
        if (s < 0) return ""
        val sb = StringBuilder()
        for (i in s..e) {
            if (i > s) sb.append(if (words[i].line != words[i - 1].line) "\n" else " ")
            sb.append(words[i].text)
        }
        return sb.toString()
    }

    private fun changed() {
        invalidate()
        onSelection(selectedText(), if (s < 0) 0 else e - s + 1)
    }

    private fun hit(x: Float, y: Float): Int {
        val pad = 5 * d
        var best = -1
        var bestArea = Float.MAX_VALUE
        words.forEachIndexed { i, w ->
            if (x >= w.r.left - pad && x <= w.r.right + pad && y >= w.r.top - pad && y <= w.r.bottom + pad) {
                val a = w.r.width() * w.r.height()
                if (a < bestArea) { best = i; bestArea = a }
            }
        }
        return best
    }

    /** Parmağa en yakın kelime (tutamaç sürüklerken her zaman bir kelime döner). */
    private fun nearest(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        words.forEachIndexed { i, w ->
            val dx = max(max(w.r.left - x, x - w.r.right), 0f)
            val dy = max(max(w.r.top - y, y - w.r.bottom), 0f)
            val dist = dx * dx + dy * dy * 3f
            if (dist < bestD) { bestD = dist; best = i }
        }
        return best
    }

    private fun startHandle() = if (s >= 0) floatArrayOf(words[s].r.left, words[s].r.bottom + 12 * d) else null
    private fun endHandle() = if (e >= 0) floatArrayOf(words[e].r.right, words[e].r.bottom + 12 * d) else null

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.rawX
        val y = ev.rawY
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; moved = false
                downWord = hit(x, y)
                mode = 0
                val b = bar
                val sh = startHandle()
                val eh = endHandle()
                val r = 30 * d
                if (b != null && b.contains(x, y)) mode = 3
                else if (sh != null && hypot(x - sh[0], y - sh[1]) < r) mode = 1
                else if (eh != null && hypot(x - eh[0], y - eh[1]) < r) mode = 2
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(x - downX, y - downY) > slop) moved = true
                if (!moved) return true
                val w = nearest(x, y)
                if (w < 0) return true
                when (mode) {
                    1 -> { if (w > e) { s = e; e = w; mode = 2 } else s = w; changed() }
                    2 -> { if (w < s) { e = s; s = w; mode = 1 } else e = w; changed() }
                    0 -> if (downWord >= 0) { s = min(downWord, w); e = max(downWord, w); changed() }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mode == 3) {
                    val b = bar
                    if (b != null && b.contains(x, y)) {
                        if (x < barSplitX) onCopy(selectedText())
                        else { s = 0; e = words.size - 1; changed() }
                    }
                } else if (!moved && mode == 0) {
                    if (downWord >= 0) {
                        if (s == downWord && e == downWord) { s = -1; e = -1 } else { s = downWord; e = downWord }
                        changed()
                    } else onEmpty()
                }
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        bar = null
        if (s < 0) return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        c.save()
        c.translate(-loc[0].toFloat(), -loc[1].toFloat())

        // Aynı satırdaki seçili kelimeler tek şerit olur.
        var i = s
        var topAll = Float.MAX_VALUE
        var leftAll = Float.MAX_VALUE
        var botAll = 0f
        while (i <= e) {
            var j = i
            while (j + 1 <= e && words[j + 1].line == words[i].line) j++
            var t = Float.MAX_VALUE
            var b = 0f
            for (k in i..j) { t = min(t, words[k].r.top); b = max(b, words[k].r.bottom) }
            val rect = RectF(words[i].r.left - 3 * d, t - 2 * d, words[j].r.right + 3 * d, b + 2 * d)
            c.drawRoundRect(rect, 3 * d, 3 * d, fill)
            topAll = min(topAll, rect.top); botAll = max(botAll, rect.bottom); leftAll = min(leftAll, rect.left)
            i = j + 1
        }
        // Tutamaçlar
        val sh = startHandle()!!
        val eh = endHandle()!!
        for (h in listOf(sh, eh)) {
            c.drawLine(h[0], h[1] - 12 * d, h[0], h[1] - 4 * d, stem)
            c.drawCircle(h[0], h[1], 8 * d, handleFill)
            c.drawCircle(h[0], h[1], 8 * d, handleEdge)
        }

        // KOPYALA / TÜMÜ çubuğu: ekran koordinatında hesaplanır, seçimin yeterince üstünde durur (tutamaçlara binmez).
        val w1 = barText.measureText("COPY") + 28 * d
        val w2 = barText.measureText("ALL") + 28 * d
        val bw = w1 + w2
        val bh = 44 * d
        var by = topAll - bh - 20 * d
        if (by < 90 * d) by = botAll + 48 * d
        val bx = leftAll.coerceIn(8 * d, width - bw - 8 * d)
        val rr = RectF(bx, by, bx + bw, by + bh)
        bar = RectF(rr)
        barSplitX = rr.left + w1
        c.drawRoundRect(rr, bh / 2, bh / 2, barFill)
        c.drawRoundRect(rr, bh / 2, bh / 2, barEdge)
        val base = rr.centerY() - (barText.descent() + barText.ascent()) / 2
        c.drawText("COPY", rr.left + w1 / 2, base, barText)
        c.drawText("ALL", rr.left + w1 + w2 / 2, base, barText)
        c.restore()
    }
}
