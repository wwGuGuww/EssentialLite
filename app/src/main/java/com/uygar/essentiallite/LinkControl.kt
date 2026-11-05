package com.uygar.essentiallite

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** PC'den uzaktan kontrol oturumu: ekran akışı (MediaProjection) ve dokunma/tuş/yazı (erişilebilirlik hizmeti). */
object LinkControl {
    @Volatile var active = false
    @Volatile var startedAt = 0L
    private val main = Handler(Looper.getMainLooper())
    private var cut: Runnable? = null
    private var dimmed = false

    /** Oturumu başlatır (ekran yakalama izni verilmiş olmalı). */
    @Volatile var watchOnly = false

    fun begin(ctx: Context, resultCode: Int, data: Intent, watchOnly: Boolean = false) {
        val i = Intent(ctx, LinkCaptureService::class.java).putExtra("rc", resultCode).putExtra("data", data)
        ctx.startForegroundService(i)
        this.watchOnly = watchOnly
        active = true
        startedAt = System.currentTimeMillis()
        Link.send("t" to "control", "on" to true)
        cut?.let { main.removeCallbacks(it) }
        if (Link.feature(ctx, "c_auto", true)) {
            cut = Runnable { stopAll() }.also { main.postDelayed(it, 5 * 60_000L) }
        }
        if (Link.feature(ctx, "c_dim", false)) dim(ctx, true)
        Link.changed()
    }

    fun stopAll() {
        val was = active
        active = false
        cut?.let { main.removeCallbacks(it) }
        cut = null
        LinkCaptureService.inst?.let { it.stopSelf() }
        Link.app()?.let { if (dimmed) dim(it, false) }
        if (was) {
            if (Link.live) Link.send("t" to "control", "on" to false)
            Link.changed()
        }
    }

    /** Ekranı karartma: parlaklık en aza iner (Shizuku gerekir); bitince eski değere döner. */
    private fun dim(ctx: Context, on: Boolean) {
        val p = Link.prefs(ctx)
        Thread {
            if (on) {
                val cur = Shell.run("settings", "get", "system", "screen_brightness")?.trim()?.toIntOrNull()
                if (cur != null) {
                    p.edit().putInt("bright", cur).apply()
                    Shell.run("settings", "put", "system", "screen_brightness", "1")
                    dimmed = true
                }
            } else {
                val v = p.getInt("bright", -1)
                if (v >= 0) Shell.run("settings", "put", "system", "screen_brightness", v.toString())
                dimmed = false
            }
        }.start()
    }

    /** PC'den gelen dokunma, tuş ve yazı iletileri. */
    fun input(ctx: Context, m: JSONObject) {
        val why = when {
            !active -> "TELEFONDA KONTROL OTURUMU KAPALI"
            watchOnly -> "SADECE İZLEME AÇIK · TELEFONDA \"KONTROLE İZİN VER\"E BAS"
            !Link.feature(ctx, "c_touch", true) -> "TELEFONDA \"DOKUNMA VE YAZMA\" KAPALI"
            KeyComboService.instance == null -> "TELEFONDA ERİŞİLEBİLİRLİK HİZMETİ KAPALI"
            else -> null
        }
        if (why != null) { Link.send("t" to "deny", "why" to why); return }
        val svc = KeyComboService.instance ?: return
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        main.post {
            when (m.optString("t")) {
                "touch" -> {
                    val x = (m.optDouble("x") * w).toFloat().coerceIn(0f, w - 1)
                    val y = (m.optDouble("y") * h).toFloat().coerceIn(0f, h - 1)
                    when (m.optString("k")) {
                        "tap" -> svc.remoteStroke(x, y, x, y, 40)
                        "long" -> svc.remoteStroke(x, y, x, y, 700)
                        "swipe" -> svc.remoteStroke(x, y,
                            (m.optDouble("x2") * w).toFloat().coerceIn(0f, w - 1), (m.optDouble("y2") * h).toFloat().coerceIn(0f, h - 1),
                            m.optLong("ms", 250).coerceIn(80, 900))
                    }
                }
                "key" -> svc.remoteKey(m.optString("k"))
                "text" -> svc.remoteText(m.optString("s"))
            }
        }
    }
}

/** MediaProjection ile ekranı küçük JPEG kareler halinde PC'ye akıtır. */
class LinkCaptureService : Service() {
    companion object { @Volatile var inst: LinkCaptureService? = null }

    private var mp: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var last = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LinkService.channel(this)
        val n = Notification.Builder(this, "link").setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("EKRAN PC'YE AKIYOR").setContentText("Oturumu bitirmek için uygulamayı aç").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7302, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(7302, n)
        inst = this
        val rc = intent?.getIntExtra("rc", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mgr.getMediaProjection(rc, data)
        if (proj == null) { stopSelf(); return START_NOT_STICKY }
        mp = proj
        val dm = resources.displayMetrics
        val sw = 360
        val sh = (dm.heightPixels * sw / dm.widthPixels.toFloat()).toInt() and 1.inv()
        val t = HandlerThread("link-cap").also { it.start() }
        thread = t
        val hnd = Handler(t.looper)
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { LinkControl.stopAll() }
        }, hnd)
        val r = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        reader = r
        vd = proj.createVirtualDisplay("essential-link", sw, sh, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, hnd)
        r.setOnImageAvailableListener({ rd ->
            val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - last < 180 || !Link.live) return@setOnImageAvailableListener
                last = now
                val p = img.planes[0]
                val rowPad = (p.rowStride - p.pixelStride * sw) / p.pixelStride
                val bmp = Bitmap.createBitmap(sw + rowPad, sh, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(p.buffer)
                val bo = ByteArrayOutputStream()
                Bitmap.createBitmap(bmp, 0, 0, sw, sh).compress(Bitmap.CompressFormat.JPEG, 55, bo)
                bmp.recycle()
                Link.send("t" to "frame", "d" to Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP), "w" to sw, "h" to sh)
            } catch (e: Exception) {
            } finally { img.close() }
        }, hnd)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (inst === this) inst = null
        runCatching { vd?.release() }
        runCatching { reader?.close() }
        runCatching { mp?.stop() }
        thread?.quitSafely()
        super.onDestroy()
    }
}
