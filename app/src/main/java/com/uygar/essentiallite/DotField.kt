package com.uygar.essentiallite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Her hücresi ayrı renkli bir nokta olan ızgara. Renk 0 ise nokta çizilmez. */
class DotField(ctx: Context, private val cols: Int, private val rows: Int) : View(ctx) {

    var colors: IntArray = IntArray(cols * rows)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cw = width / cols.toFloat()
        val ch = height / rows.toFloat()
        val r = minOf(cw, ch) * 0.36f
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val c = colors.getOrElse(y * cols + x) { 0 }
                if (c == 0) continue
                paint.color = c
                canvas.drawCircle(cw * (x + 0.5f), ch * (y + 0.5f), r, paint)
            }
        }
    }
}
