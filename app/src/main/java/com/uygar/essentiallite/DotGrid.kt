package com.uygar.essentiallite

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Not alanının arka planı: [step] piksel aralıklı nokta ızgarası. */
class DotGrid(private val step: Float, color: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color }

    override fun draw(canvas: Canvas) {
        val r = step * 0.07f
        var y = bounds.top + step / 2f
        while (y < bounds.bottom) {
            var x = bounds.left + step / 2f
            while (x < bounds.right) {
                canvas.drawCircle(x, y, r, paint)
                x += step
            }
            y += step
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
