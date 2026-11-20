package com.uygar.essentiallite

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager

/**
 * Nothing Phone (1) Glyph ışıkları (Nothing Glyph Developer Kit). Yalnızca ön plandaki uygulama kullanabilir;
 * bu yüzden alarm çalarken [AlarmRingActivity] ön plandayken oynatılır. Geliştirici modunda (test anahtarıyla)
 * `adb shell settings put global nt_glyph_interface_debug_enable 1` gerekir; bu ayar 48 saatte kapanır.
 */
object NothingGlyph {
    /** (kayıt adı, ekranda görünen ad) */
    val patterns = listOf("OFF" to "OFF", "BREATHE" to "BREATHE", "SERIT" to "STRIP", "YANIP" to "BLINK")

    private var gm: GlyphManager? = null
    private var worker: Thread? = null
    private var appCtx: Context? = null
    @Volatile private var gen = 0
    /** Başlat/durdur işleri sırayla çalışır; böylece yeni desen, eskinin kapanışıyla çakışmaz. */
    private val ops = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 33 && runCatching { Common.is20111() }.getOrDefault(false)

    private val strip = intArrayOf(
        Glyph.Code_20111.D1_1, Glyph.Code_20111.D1_2, Glyph.Code_20111.D1_3, Glyph.Code_20111.D1_4,
        Glyph.Code_20111.D1_5, Glyph.Code_20111.D1_6, Glyph.Code_20111.D1_7, Glyph.Code_20111.D1_8
    )
    private val all = intArrayOf(
        Glyph.Code_20111.A1, Glyph.Code_20111.B1, Glyph.Code_20111.C1, Glyph.Code_20111.C2, Glyph.Code_20111.C3,
        Glyph.Code_20111.C4, Glyph.Code_20111.E1
    ) + strip

    private fun frame(m: GlyphManager, vararg ch: Int): GlyphFrame {
        val b = m.getGlyphFrameBuilder()
        ch.forEach { b.buildChannel(it) }
        return b.build()
    }

    fun start(ctx: Context, pattern: String) {
        stop()
        if (!supported() || pattern == "OFF") return
        val my = ++gen
        val app = ctx.applicationContext
        appCtx = app
        ops.execute {
            if (my != gen) return@execute
            val m = GlyphManager.getInstance(app)
            gm = m
            m.init(object : GlyphManager.Callback {
                override fun onServiceConnected(c: ComponentName) {
                    if (my != gen) return
                    try { m.register(Glyph.DEVICE_20111); m.openSession() } catch (e: Throwable) { return }
                    worker = Thread { play(m, pattern, my) }.also { it.start() }
                }

                override fun onServiceDisconnected(c: ComponentName) {}
            })
        }
    }

    private fun play(m: GlyphManager, pattern: String, my: Int) {
        try {
            when (pattern) {
                "BREATHE" -> {
                    val b = m.getGlyphFrameBuilder()
                    all.forEach { b.buildChannel(it) }
                    m.animate(b.buildPeriod(2000).buildCycles(60).buildInterval(0).build())
                }
                "SERIT" -> while (gen == my) {
                    for (i in 0..7) { if (gen != my) return; m.toggle(frame(m, strip[i])); Thread.sleep(110) }
                    for (i in 6 downTo 1) { if (gen != my) return; m.toggle(frame(m, strip[i])); Thread.sleep(110) }
                }
                "YANIP" -> while (gen == my) {
                    m.toggle(frame(m, *all)); Thread.sleep(350)
                    if (gen != my) return
                    m.turnOff(); Thread.sleep(350)
                }
            }
        } catch (e: Throwable) {
            // Ayar kapalıysa ya da başka bir uygulama Glyph'i kullanıyorsa sessizce vazgeçilir.
        }
    }

    /** Işıkları söndürür. Çalışan desen bitene kadar beklenir (geç kalan bir toggle ışığı yeniden yakmasın) ve iki kez kapatılır. */
    fun stop() {
        gen++
        ops.execute {
            val m = gm
            val w = worker
            gm = null
            worker = null
            if (m != null) {
                runCatching { w?.join(800) }
                runCatching { m.turnOff() }
                runCatching { Thread.sleep(150) }
                runCatching { m.turnOff() }
                runCatching { m.closeSession() }
                runCatching { m.unInit() }
            }
            forceOff()
        }
    }

    /**
     * Bağımsız son kapatma: yeni bir bağlantı açıp tüm ışıkları söndürür. Önceki oturum yarım kaldıysa (durdurma bağlantıdan önce
     * geldiyse, uygulama kapanıp açıldıysa) ışıkların açık kalmasını önler. Başka bir uygulama Glyph'i kullanıyorsa dokunmaz.
     */
    fun forceOff(ctx: Context? = null) {
        val app = ctx?.applicationContext ?: appCtx ?: return
        if (!supported()) return
        val latch = java.util.concurrent.CountDownLatch(1)
        try {
            val m = GlyphManager.getInstance(app)
            m.init(object : GlyphManager.Callback {
                override fun onServiceConnected(c: ComponentName) {
                    try {
                        m.register(Glyph.DEVICE_20111)
                        m.openSession()
                        m.turnOff()
                        Thread.sleep(120)
                        m.turnOff()
                        m.closeSession()
                    } catch (e: Throwable) {
                    } finally {
                        runCatching { m.unInit() }
                        latch.countDown()
                    }
                }

                override fun onServiceDisconnected(c: ComponentName) {}
            })
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Throwable) {
        }
    }
}

/** Alarm çalarken sesi (seçilen ya da varsayılan alarm sesi), titreşimi ve tam ekran bildirimi yönetir. */
class AlarmRingService : Service() {
    companion object {
        const val CH = "alarm_ring"
        const val NOTE = 7010
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, AlarmRingService::class.java)) }
        }
    }

    private var ringtone: Ringtone? = null
    private val h = Handler(Looper.getMainLooper())
    private val auto = Runnable { stopSelf() }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra("id", 0) ?: 0
        val m = intent?.getIntExtra("m", 0) ?: 0
        val label = intent?.getStringExtra("label") ?: ""
        val tone = intent?.getStringExtra("tone") ?: ""
        val glyph = intent?.getStringExtra("glyph") ?: "BREATHE"

        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "Alarm ringing", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            })
        }
        val ring = Intent(this, AlarmRingActivity::class.java)
            .putExtra("id", id).putExtra("m", m).putExtra("label", label).putExtra("glyph", glyph)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val full = PendingIntent.getActivity(this, 7011, ring, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getBroadcast(
            this, 7004, Intent(this, AlarmDismissReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val time = ToolUi.p2((m / 60).toLong()) + ":" + ToolUi.p2((m % 60).toLong())
        val n = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle("ALARM").setContentText("$time · $label")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
            .setContentIntent(full).setFullScreenIntent(full, true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notif), "CLOSE", dismiss).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTE, n)

        // Ses: seçilen alarm sesi, yoksa telefonun varsayılan alarm sesi.
        ringtone?.stop()
        val uri: Uri = if (tone.isNotEmpty()) Uri.parse(tone)
        else RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = runCatching {
            RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }.getOrNull()
        runCatching {
            val v = getSystemService(Vibrator::class.java)
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 300, 500), 0),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        h.removeCallbacks(auto)
        h.postDelayed(auto, 55_000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(auto)
        // Çalma ekranı açıksa o da kapanır (otomatik susunca ışıklar yanık kalmasın).
        sendBroadcast(Intent(AlarmRingActivity.DONE).setPackage(packageName))
        NothingGlyph.stop()
        runCatching { ringtone?.stop() }
        runCatching { getSystemService(Vibrator::class.java).cancel() }
        super.onDestroy()
    }
}

/** Alarm çalarken açılan tam ekran: saat, ad ve KAPAT. Uygulama kilidi sormaz; Glyph ışıkları burada oynar. */
class AlarmRingActivity : Activity() {
    companion object { const val DONE = "com.uygar.essentiallite.ALARM_DONE" }

    private var glyph = "BREATHE"
    private val done = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { NothingGlyph.stop(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.apply(this)
        setTheme(if (Ui.light) R.style.Theme_EssentialLite_Light else R.style.Theme_EssentialLite_Dark)
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        val id = intent.getLongExtra("id", 0)
        val m = intent.getIntExtra("m", 0)
        val label = intent.getStringExtra("label") ?: "ALARM"
        glyph = intent.getStringExtra("glyph") ?: "BREATHE"
        fun dp(v: Int) = ToolUi.dp(this, v)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(24), dp(120), dp(24), dp(48))
            addView(Ui.text(this@AlarmRingActivity, "", 11f, Ui.WHITE, bold = true).apply {
                text = ToolUi.dotText("ALARM"); letterSpacing = 0.2f
            })
            addView(Ui.display(this@AlarmRingActivity, ToolUi.p2((m / 60).toLong()) + ":" + ToolUi.p2((m % 60).toLong()), 96f),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(28), 0, 0) })
            addView(Ui.text(this@AlarmRingActivity, label, 12f, Ui.GREY).apply { letterSpacing = 0.18f },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(12), 0, 0) })
            addView(android.view.View(this@AlarmRingActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(outlineBtn(this@AlarmRingActivity, "CLOSE", true, Ui.WHITE, Ui.WHITE) {
                sendBroadcast(Intent(this@AlarmRingActivity, AlarmDismissReceiver::class.java).putExtra("id", id))
                finish()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, android.content.IntentFilter(DONE), Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(done, android.content.IntentFilter(DONE))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(done) }
        NothingGlyph.stop()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        NothingGlyph.start(this, glyph)
    }

    override fun onPause() {
        super.onPause()
        NothingGlyph.stop()
    }

    override fun onStop() {
        super.onStop()
        NothingGlyph.stop()
    }
}
