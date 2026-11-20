package com.uygar.essentiallite

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews

class CaptureWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action != ACTION_SHOT) return
        val pending = goAsync()
        Thread {
            val msg = try { Shot.take(ctx) } catch (e: Throwable) { "ERROR" }
            Store.toast(ctx, msg)
            pending.finish()
        }.start()
    }

    companion object {
        const val ACTION_SHOT = "com.uygar.essentiallite.SHOT"

        /** Tüm widget örneklerini yeniden çizer. Kayıt yazıldıktan sonra arka planda çağrılmalı. */
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, CaptureWidget::class.java))
                .forEach { render(ctx, mgr, it) }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget)
            val brand = SpannableString("● ESSENTIAL")
            brand.setSpan(ForegroundColorSpan(0xFFF0303A.toInt()), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            views.setTextViewText(R.id.txt_brand, brand)
            views.setTextViewText(R.id.txt_count, Store.all(ctx).size.toString().padStart(3, '0'))
            views.setImageViewBitmap(R.id.ic_shot, dotBitmap(ctx, Ui.ICON_SHOT))
            views.setImageViewBitmap(R.id.ic_note, dotBitmap(ctx, Ui.ICON_NOTE))
            views.setImageViewBitmap(R.id.ic_voice, dotBitmap(ctx, Ui.ICON_WAVE))
            views.setTextViewText(R.id.txt_last, lastLine(ctx))
            views.setOnClickPendingIntent(R.id.btn_shot, broadcast(ctx, 1))
            views.setOnClickPendingIntent(R.id.btn_note, activity(ctx, NoteActivity::class.java, 2))
            views.setOnClickPendingIntent(R.id.btn_rec, activity(ctx, RecordActivity::class.java, 3))
            views.setOnClickPendingIntent(R.id.root, activity(ctx, MainActivity::class.java, 4))
            views.setOnClickPendingIntent(R.id.txt_last, activity(ctx, MainActivity::class.java, 5))
            mgr.updateAppWidget(id, views)
        }

        private fun lastLine(ctx: Context): String {
            val e = Store.latest(ctx) ?: return "LAST: —"
            val p = e.preview
            return "LAST ${e.kind} · ${Store.stamp(e.time)}" + if (p.isNotEmpty()) " · $p" else ""
        }

        /** 7×7 nokta ikonu: nokta 3dp, aralık 2dp; kapalı noktalar #262626. */
        private fun dotBitmap(ctx: Context, rows: List<String>): Bitmap {
            val d = ctx.resources.displayMetrics.density
            val dot = 3 * d
            val gap = 2 * d
            val cell = dot + gap
            val size = (cell * 7 - gap).toInt() + 1
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    p.color = if (ch == '1') 0xFFFFFFFF.toInt() else 0xFF262626.toInt()
                    c.drawCircle(x * cell + dot / 2, y * cell + dot / 2, dot / 2, p)
                }
            }
            return bmp
        }

        private fun broadcast(ctx: Context, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, code, Intent(ctx, CaptureWidget::class.java).setAction(ACTION_SHOT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun activity(ctx: Context, cls: Class<*>, code: Int): PendingIntent =
            PendingIntent.getActivity(
                ctx, code, Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
