package com.uygar.essentiallite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Nokta matrisi ikonu. Her satır bir dizgi, '1' bir nokta demek. */
class DotIcon(ctx: Context, private val rows: List<String>, color: Int = Ui.WHITE, private val sizeDp: Int = 44) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val side = (sizeDp * resources.displayMetrics.density).toInt()
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        val cols = rows.maxOf { it.length }
        val n = rows.size
        val cell = minOf(width / cols.toFloat(), height / n.toFloat())
        val ox = (width - cell * cols) / 2f
        val oy = (height - cell * n) / 2f
        val r = cell * 0.36f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == '1') canvas.drawCircle(ox + cell * (x + 0.5f), oy + cell * (y + 0.5f), r, paint)
            }
        }
    }
}
