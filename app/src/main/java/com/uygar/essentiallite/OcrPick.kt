package com.uygar.essentiallite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * Asistan açılırken ekranın üstüne çizilen yazı seçici. Okunan her satır, ekranda gerçekte durduğu yerdedir:
 * satıra dokunmak onu seçer / seçimini kaldırır; basılı tutup sürüklemek sürüklenen satırları seçime ekler.
 * Yazı olmayan boş yere dokunmak [onEmpty] çağırır (asistan kapanır). Seçimi kopyalamayı panel yapar.
 */
class OcrPick(ctx: Context) : View(ctx) {
    class Line(val text: String, val r: RectF)

    var onEmpty: () -> Unit = {}
    var onSelection: (text: String, count: Int) -> Unit = { _, _ -> }

    private var lines: List<Line> = emptyList()
    private val sel = LinkedHashSet<Int>()
    private val d = ctx.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * d; color = Ui.RED }
    private val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * d; color = 0x44FFFFFF }

    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var dragging = false

    private val longPress = Runnable {
        val i = hit(downX, downY)
        if (i >= 0) {
            dragging = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            if (sel.add(i)) changed()
        }
    }

    fun setLines(l: List<Line>) {
        lines = l
        sel.clear()
        changed()
    }

    fun clear() {
        handler.removeCallbacks(longPress)
        lines = emptyList()
        sel.clear()
        invalidate()
    }

    fun clearSelection() {
        sel.clear()
        changed()
    }

    val lineCount get() = lines.size

    /** Seçili satırlar, ekrandaki okuma sırasıyla (üstten alta, soldan sağa). */
    fun selectedText(): String = sel.map { lines[it] }
        .sortedWith(compareBy({ (it.r.top / (12 * d)).toInt() }, { it.r.left }))
        .joinToString("\n") { it.text }

    private fun changed() {
        invalidate()
        onSelection(selectedText(), sel.size)
    }

    private fun hit(x: Float, y: Float): Int {
        val pad = 8 * d
        var best = -1
        var bestArea = Float.MAX_VALUE
        lines.forEachIndexed { i, l ->
            if (x >= l.r.left - pad && x <= l.r.right + pad && y >= l.r.top - pad && y <= l.r.bottom + pad) {
                val a = l.r.width() * l.r.height()
                if (a < bestArea) { best = i; bestArea = a }
            }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; moved = false; dragging = false
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(e.x - downX, e.y - downY) > slop) {
                    moved = true
                    if (!dragging) handler.removeCallbacks(longPress)
                }
                if (dragging) {
                    val i = hit(e.x, e.y)
                    if (i >= 0 && sel.add(i)) changed()
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!dragging && !moved) {
                    val i = hit(e.x, e.y)
                    if (i < 0) onEmpty()
                    else { if (!sel.add(i)) sel.remove(i); changed() }
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longPress); dragging = false }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        lines.forEachIndexed { i, l ->
            val r = RectF(l.r.left - 3 * d, l.r.top - 2 * d, l.r.right + 3 * d, l.r.bottom + 2 * d)
            if (i in sel) {
                c.drawRoundRect(r, 6 * d, 6 * d, fill)
                c.drawRoundRect(r, 6 * d, 6 * d, edge)
            }
        }
    }
}
