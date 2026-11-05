package com.uygar.essentiallite

import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Çerçeveli düğme (tasarım: 56 dp, 28 dp köşe, 0,2 em harf aralığı). [dot] ise başta kırmızı nokta. */
private fun outlineBtn(ctx: Context, label: String, dot: Boolean, border: Int, fg: Int, onClick: () -> Unit): TextView {
    val s = SpannableStringBuilder(if (dot) "● $label" else label)
    if (dot) s.setSpan(ForegroundColorSpan(Ui.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return Ui.text(ctx, "", 12f, fg, bold = true).apply {
        text = s
        gravity = Gravity.CENTER
        letterSpacing = 0.2f
        background = Ui.rounded(ctx, Color.TRANSPARENT, 28f, border)
        Ui.pressAnim(this)
        setOnClickListener { onClick() }
    }
}

/** Tasarımdaki 52×26 anahtar: kırmızı iz ve düğme açık, gri kapalı. */
private class DesignSwitch(ctx: Context, var on: Boolean, private val onChange: (Boolean) -> Unit) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    init {
        setOnClickListener {
            on = !on
            invalidate()
            onChange(on)
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((52 * d).toInt(), (44 * d).toInt())

    override fun onDraw(c: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d
        paint.color = if (on) Ui.RED else Ui.LINE
        c.drawRoundRect(RectF(d, 10 * d, 51 * d, 36 * d), 13 * d, 13 * d, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (on) Ui.RED else Ui.GREY
        c.drawCircle((if (on) 38 else 14) * d, 23 * d, 8 * d, paint)
    }
}

/** Izgara halinde yuvarlak noktalar. [colors] 0 olan hücre çizilmez. */
private class DotsView(ctx: Context, private val cols: Int, private val rows: Int, private val dotDp: Float, private val pitchYDp: Float) : View(ctx) {
    var colors = IntArray(cols * rows)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (rows * pitchYDp * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val cw = width / cols.toFloat()
        for (y in 0 until rows) for (x in 0 until cols) {
            val col = colors.getOrElse(y * cols + x) { 0 }
            if (col == 0) continue
            paint.color = col
            c.drawCircle((x + 0.5f) * cw, (y + 0.5f) * pitchYDp * d, dotDp / 2f * d, paint)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Su terazisi (tasarım 19)
// ---------------------------------------------------------------------------------------------

/** 21×21 nokta alanı: dış halka, iç halka, çapraz çizgiler ve baloncuk (tasarım 19). */
private class LevelField(ctx: Context) : View(ctx) {
    var bx = 0f
    var by = 0f
    var level = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    private val cell = 14 * d

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((21 * cell).toInt(), (21 * cell).toInt())

    override fun onDraw(canvas: Canvas) {
        for (r in -10..10) for (c in -10..10) {
            val dist = hypot(c.toFloat(), r.toFloat())
            val bd = hypot(c - bx, r - by)
            var col = 0
            var sz = 4f
            if (dist > 9.2f && dist <= 10.4f) { col = Ui.BORDER2; sz = 3f }
            else if (dist > 4.6f && dist < 5.4f) { col = Ui.OFF; sz = 3f }
            else if ((c == 0 || r == 0) && dist < 9f) { col = Ui.OFF; sz = 2f }
            if (dist > 1.1f && dist < 1.9f && col == 0) { col = Ui.LINE; sz = 3f }
            if (bd < 1.5f) { col = if (level) Ui.RED else Ui.WHITE; sz = if (bd < 0.8f) 10f else 7f }
            if (dist > 10.4f) col = 0
            if (col == 0) continue
            paint.color = col
            canvas.drawCircle((c + 10 + 0.5f) * cell, (r + 10 + 0.5f) * cell, sz / 2f * d, paint)
        }
    }
}

/** 41 çubuklu eğim göstergesi (tasarım 19): ortadan sağa/sola dolar, uçta baş çubuk. */
private class BarsView(ctx: Context) : View(ctx) {
    var v = 0f
    var level = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (8 * d).toInt())

    override fun onDraw(c: Canvas) {
        val slot = width / 41f
        val pos = (v * 20f / 10f).roundToInt().coerceIn(-20, 20)
        for (i in -20..20) {
            paint.color = when {
                i == pos -> if (level) Ui.RED else Ui.WHITE
                i == 0 -> Ui.PROG
                (i in 1 until pos) || (i < 0 && i > pos) -> Ui.GREY2
                else -> Ui.OFF
            }
            val cx = (i + 20 + 0.5f) * slot
            c.drawRoundRect(RectF(cx - 1.5f * d, 0f, cx + 1.5f * d, 8 * d), 2 * d, 2 * d, paint)
        }
    }
}

class LevelActivity : BaseActivity(), SensorEventListener {
    private lateinit var sm: SensorManager
    private var rx = 0f
    private var ry = 0f
    private var cx = 0f
    private var cy = 0f
    private lateinit var totalView: TextView
    private lateinit var axView: TextView
    private lateinit var ayView: TextView
    private lateinit var stateView: TextView
    private lateinit var field: LevelField
    private lateinit var barX: BarsView
    private lateinit var barY: BarsView
    private var first = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        totalView = Ui.display(this, "0,0", 72f)
        axView = Ui.text(this, "", 11f, Ui.WHITE).apply { letterSpacing = 0.14f; gravity = Gravity.END }
        ayView = Ui.text(this, "", 11f, Ui.WHITE).apply { letterSpacing = 0.14f; gravity = Gravity.END }
        field = LevelField(this)
        barX = BarsView(this)
        barY = BarsView(this)

        fun axisRow(label: String, value: TextView) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.text(this@LevelActivity, label, 11f, Ui.GREY).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(dp(24), WRAP))
            addView(value, LinearLayout.LayoutParams(dp(64), WRAP))
        }

        val angle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(LinearLayout(this@LevelActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(totalView)
                addView(Ui.display(this@LevelActivity, "°", 28f, Ui.GREY))
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(LinearLayout(this@LevelActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(axisRow("X", axView))
                addView(axisRow("Y", ayView), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
            })
        }

        val circle = FrameLayout(this).apply {
            background = Ui.rounded(this@LevelActivity, Ui.SURFACE, 165f, Ui.BORDER)
            addView(field, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }

        fun barRow(label: String, bars: BarsView) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@LevelActivity, label, 10f, Ui.GREY).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(dp(26), WRAP))
            addView(bars, LinearLayout.LayoutParams(0, dp(8), 1f))
        }

        val calibrate = outlineBtn(this, "KALİBRE ET", true, Ui.WHITE, Ui.WHITE) { cx = rx; cy = ry; update() }
        val reset = outlineBtn(this, "SIFIRLA", false, Ui.LINE, Ui.GREY) { cx = 0f; cy = 0f; update() }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(angle, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(circle, LinearLayout.LayoutParams(dp(330), dp(330)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(18), 0, 0) })
            addView(barRow("X", barX), LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(barRow("Y", barY), LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(10), 0, 0) })
            addView(View(this@LevelActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(LinearLayout(this@LevelActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(calibrate, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(0, 0, dp(10), 0) })
                addView(reset, LinearLayout.LayoutParams(dp(120), dp(56)))
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(16), 0, 0) })
        }
        stateView = ToolUi.screen(this, "SU TERAZİSİ", "EĞİK", ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(body)
        })
        if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
            stateView.text = "SENSÖR YOK"
        }
        update()
    }

    private fun fmt(v: Float) = (if (v >= 0f) "+" else "−") + String.format(java.util.Locale.US, "%.1f", abs(v)).replace('.', ',')

    private fun update() {
        val x = rx - cx
        val y = ry - cy
        val level = hypot(x, y) < 0.6f
        totalView.text = String.format(java.util.Locale.US, "%.1f", hypot(x, y)).replace('.', ',')
        totalView.setTextColor(if (level) Ui.RED else Ui.WHITE)
        axView.text = fmt(x) + "°"
        ayView.text = fmt(y) + "°"
        if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            stateView.text = if (level) "● DÜZ" else "EĞİK"
            stateView.setTextColor(if (level) Ui.WHITE else Ui.GREY)
        }
        field.bx = x * 0.55f
        field.by = y * 0.55f
        field.level = level
        field.invalidate()
        barX.v = x; barX.level = level; barX.invalidate()
        barY.v = y; barY.level = level; barY.invalidate()
    }

    override fun onResume() {
        super.onResume()
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val ax = e.values[0]
        val ay = e.values[1]
        val az = e.values[2]
        // Yüksek kenara doğru: sağ kenar yüksekse X artı, üst kenar yüksekse ekranda yukarı (Y eksi).
        val tx = Math.toDegrees(atan2(ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))).toFloat()
        val ty = -Math.toDegrees(atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))).toFloat()
        if (first) { rx = tx; ry = ty; first = false } else { rx += (tx - rx) * 0.2f; ry += (ty - ry) * 0.2f }
        update()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

// ---------------------------------------------------------------------------------------------
// Pil (tasarım 16)
// ---------------------------------------------------------------------------------------------

/** Pil verisi: anlık durum ve şarj geçmişi örnekleri. Kaydedilen örnekler: zaman,yüzde,şarjda. */
object BatteryWatch {
    const val ACTION = "com.uygar.essentiallite.BATT_SAMPLE"
    private const val CHANNEL = "battery"
    private const val NOTE_ID = 7001
    private const val LIMIT = 80

    class Snap(
        val level: Int, val charging: Boolean, val tempC: Double, val volt: Double,
        val cycles: Int, val remainMin: Long?, val fullMah: Double?, val designMah: Double?
    )

    fun snapshot(ctx: Context): Snap {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val raw = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (raw >= 0 && scale > 0) raw * 100 / scale else 0
        val charging = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val volt = (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
        val cycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // µAh
        var current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (current != 0L && abs(current) < 20000L) current *= 1000L // bazı cihazlar mA verir
        var remain: Long? = null
        if (charging) {
            val ms = if (Build.VERSION.SDK_INT >= 28) bm.computeChargeTimeRemaining() else -1L
            if (ms > 0) remain = ms / 60000L
        } else if (counter > 0 && current != 0L) {
            remain = (counter.toDouble() / abs(current) * 60.0).toLong()
        }
        val full = if (counter > 0 && level in 1..100) counter / (level / 100.0) / 1000.0 else null
        val design = runCatching {
            val pp = Class.forName("com.android.internal.os.PowerProfile").getConstructor(Context::class.java).newInstance(ctx)
            (pp.javaClass.getMethod("getBatteryCapacity").invoke(pp) as Number).toDouble()
        }.getOrNull()?.takeIf { it > 0 }
        return Snap(level, charging, temp, volt, cycles, remain, full, design)
    }

    private fun file(ctx: Context) = File(ctx.filesDir, "battery.log")

    fun record(ctx: Context) {
        val s = snapshot(ctx)
        val now = System.currentTimeMillis()
        val keep = now - 7L * 24 * 3600 * 1000
        val lines = samples(ctx).filter { it.first >= keep }.map { "${it.first},${it.second},${if (it.third) 1 else 0}" }.toMutableList()
        lines.add("$now,${s.level},${if (s.charging) 1 else 0}")
        runCatching { file(ctx).writeText(lines.takeLast(800).joinToString("\n")) }
    }

    fun samples(ctx: Context): List<Triple<Long, Int, Boolean>> = runCatching {
        file(ctx).readLines().mapNotNull { l ->
            val p = l.split(',')
            if (p.size < 3) null else Triple(p[0].toLong(), p[1].toInt(), p[2] == "1")
        }
    }.getOrDefault(emptyList())

    fun limitOn(ctx: Context) = Prefs.get(ctx, "batt_limit", true)

    /** Her 15 dakikada bir örnek alır ve şarj sınırını kontrol eder (Android bu aralığı tam tutmayabilir). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 7001, Intent(ctx, BatteryReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi
        )
        Prefs.set(ctx, "batt_on", true)
    }

    fun check(ctx: Context) {
        val s = snapshot(ctx)
        if (!s.charging) { Prefs.set(ctx, "batt_notified", false); return }
        if (!limitOn(ctx) || s.level < LIMIT || Prefs.get(ctx, "batt_notified", false)) return
        Prefs.set(ctx, "batt_notified", true)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pil", NotificationManager.IMPORTANCE_DEFAULT))
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        runCatching {
            nm.notify(NOTE_ID, n.setSmallIcon(R.drawable.ic_notif).setContentTitle("ŞARJ %$LIMIT").setContentText("Pil %${s.level}. Şarjı kesme zamanı.").setAutoCancel(true).build())
        }
    }
}

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        BatteryWatch.record(app)
        BatteryWatch.check(app)
    }
}

/** 40 çubuklu şarj ölçeği; açılışta çubuklar sırayla büyür (tasarım: cellIn). */
private class GaugeView(ctx: Context, private val level: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    private var t = 0f // ms

    init {
        ValueAnimator.ofFloat(0f, 40f * 12f + 320f).apply {
            duration = 40L * 12L + 320L
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (14 * d).toInt())

    override fun onDraw(c: Canvas) {
        val slot = width / 40f
        val on = (level / 100f * 40f).roundToInt()
        val over = OvershootInterpolator(1.3f)
        for (i in 0 until 40) {
            val p = ((t - i * 12f) / 320f).coerceIn(0f, 1f)
            if (p <= 0f) continue
            val s = 0.2f + 0.8f * over.getInterpolation(p)
            paint.color = if (i < on) (if (level <= 20) Ui.RED else Ui.WHITE) else Ui.OFF
            val cx = (i + 0.5f) * slot
            val hw = 2.5f * d * s
            val hh = 7f * d * s
            c.drawRoundRect(RectF(cx - hw, 7 * d - hh, cx + hw, 7 * d + hh), 3 * d, 3 * d, paint)
        }
    }
}

class BatteryActivity : BaseActivity() {
    private var range = 0
    private var limit = true
    private lateinit var snap: BatteryWatch.Snap
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        limit = BatteryWatch.limitOn(this)
        BatteryWatch.record(this)
        BatteryWatch.schedule(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        snap = BatteryWatch.snapshot(this)
        val right = ToolUi.screen(this, "PİL", "", ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        })
        right.text = if (snap.charging) "⚡ ŞARJ OLUYOR" else "ŞARJ OLMUYOR"
        right.setTextColor(if (snap.charging) Ui.WHITE else Ui.GREY)
        right.textSize = 11f
        build()
    }

    private fun spaced(n: Long): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()

    private fun remainText(m: Long?): String =
        if (m == null || m <= 0) "—" else if (m >= 60) "~${m / 60} SA ${m % 60} DK" else "~$m DK"

    private fun build() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        content.removeAllViews()
        val s = snap
        val health = if (s.fullMah != null && s.designMah != null) (s.fullMah / s.designMah * 100).coerceAtMost(100.0).roundToInt() else null

        // Şarj
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(Ui.display(this@BatteryActivity, s.level.toString(), 88f))
                addView(Ui.display(this@BatteryActivity, "%", 30f, Ui.GREY))
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(Ui.text(this@BatteryActivity, if (s.charging) "DOLMAYA" else "KALAN", 11f, Ui.GREY).apply { letterSpacing = 0.12f })
                addView(Ui.text(this@BatteryActivity, remainText(s.remainMin), 11f, Ui.WHITE).apply { letterSpacing = 0.12f })
            })
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(GaugeView(this, s.level), LinearLayout.LayoutParams(MATCH, dp(14)).apply { setMargins(0, dp(22), 0, 0) })

        // Sağlık kartı
        val healthDots = DotsView(this, 50, 1, 4f, 4f).apply {
            val lit = if (health == null) 0 else (health / 100f * 50f).roundToInt()
            colors = IntArray(50) { if (it < lit) Ui.WHITE else Ui.BORDER2 }
        }
        val healthLabel = when {
            health == null -> "—"
            health >= 80 -> "İYİ"
            health >= 60 -> "ORTA"
            else -> "ZAYIF"
        }
        val stats = listOf(
            "KAPASİTE" to if (s.fullMah != null && s.designMah != null) "${spaced(s.fullMah.roundToInt().toLong())} / ${spaced(s.designMah.roundToInt().toLong())}" else "—",
            "ŞARJ DÖNGÜSÜ" to if (s.cycles >= 0) s.cycles.toString() else "—",
            "SICAKLIK" to String.format(java.util.Locale.US, "%.1f", s.tempC).replace('.', ',') + "°C",
            "VOLTAJ" to String.format(java.util.Locale.US, "%.2f", s.volt).replace('.', ',') + " V"
        )
        val statGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            for (r in 0 until 2) {
                addView(LinearLayout(this@BatteryActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (c in 0 until 2) {
                        val (l, v) = stats[r * 2 + c]
                        addView(LinearLayout(this@BatteryActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(Ui.text(this@BatteryActivity, l, 10f, Ui.GREY).apply { letterSpacing = 0.16f })
                            addView(Ui.display(this@BatteryActivity, v, 18f), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(5), 0, 0) })
                        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, if (r == 0) 0 else dp(12), if (c == 0) dp(9) else 0, 0) })
                    }
                })
            }
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@BatteryActivity, Ui.SURFACE, 28f, Ui.BORDER)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(LinearLayout(this@BatteryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@BatteryActivity, "PİL SAĞLIĞI", 10f, Ui.GREY).apply { letterSpacing = 0.18f })
                    addView(LinearLayout(this@BatteryActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.BOTTOM
                        addView(Ui.display(this@BatteryActivity, health?.toString() ?: "—", 40f))
                        if (health != null) addView(Ui.display(this@BatteryActivity, "%", 18f, Ui.GREY))
                    }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(8), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.text(this@BatteryActivity, "", 11f, Ui.WHITE).apply {
                    val sp = SpannableStringBuilder("● $healthLabel")
                    sp.setSpan(ForegroundColorSpan(Ui.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = sp
                    letterSpacing = 0.14f
                })
            })
            addView(healthDots, LinearLayout.LayoutParams(MATCH, dp(4)).apply { setMargins(0, dp(14), 0, dp(14)) })
            addView(statGrid)
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })

        // Geçmiş
        content.addView(historySection(), LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })

        content.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // Şarj sınırı
        content.addView(View(this).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(14), 0, 0) })
        val note = Ui.text(this, "", 10f, Ui.GREY).apply { letterSpacing = 0.08f; setLineSpacing(0f, 1.2f) }
        fun refreshNote() {
            note.text = if (limit) "%80'e gelince bildirim gelir. Şarjı sen kesersin." else "Kapalı. Bildirim gelmez."
        }
        refreshNote()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
            addView(LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(this@BatteryActivity, "ŞARJ SINIRI · %80", 12f, Ui.WHITE, bold = true).apply { letterSpacing = 0.1f })
                addView(note, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(DesignSwitch(this@BatteryActivity, limit) { on ->
                limit = on
                Prefs.set(this@BatteryActivity, "batt_limit", on)
                refreshNote()
            })
        }, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun historySection(): View {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val cols = if (range == 0) 24 else 7
        val rows = 8
        val bucket = if (range == 0) 3600_000L else 24 * 3600_000L
        val now = System.currentTimeMillis()
        val all = BatteryWatch.samples(this)
        val values = IntArray(cols) { -1 }
        for (c in 0 until cols) {
            val end = now - (cols - 1 - c) * bucket
            val start = end - bucket
            values[c] = all.lastOrNull { it.first in (start + 1)..end }?.second ?: -1
        }
        values[cols - 1] = snap.level
        val dots = DotsView(this, cols, rows, 4f, 4f).apply {
            colors = IntArray(cols * rows) { idx ->
                val r = idx / cols
                val c = idx % cols
                val v = values[c]
                val lit = v >= 0 && v >= ((rows - r).toFloat() / rows) * 100f - 6f
                if (lit) (if (c == cols - 1) Ui.RED else Ui.GREY2) else Ui.OFF
            }
        }
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(this@BatteryActivity, Color.TRANSPARENT, 20f, Ui.LINE)
            clipToOutline = true
            listOf("24 SA" to 0, "7 GÜN" to 1).forEach { (label, idx) ->
                val sel = range == idx
                addView(Ui.text(this@BatteryActivity, label, 10.5f, if (sel) Ui.WHITE else Ui.GREY, bold = true).apply {
                    gravity = Gravity.CENTER
                    letterSpacing = 0.12f
                    setPadding(dp(16), 0, dp(16), 0)
                    setBackgroundColor(if (sel) Ui.OFF else Color.TRANSPARENT)
                    setOnClickListener { range = idx; build() }
                }, LinearLayout.LayoutParams(WRAP, dp(40)))
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(this@BatteryActivity, "ŞARJ GEÇMİŞİ", 10f, Ui.GREY).apply { letterSpacing = 0.18f }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(seg)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, dp(10)) })
            addView(dots, LinearLayout.LayoutParams(MATCH, dp(rows * 4)))
            addView(LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                val l = if (range == 0) "−24 SA" else "−7 GÜN"
                val m = if (range == 0) "−12 SA" else "−3 GÜN"
                for ((i, t) in listOf(l, m, "ŞİMDİ").withIndex()) {
                    addView(Ui.text(this@BatteryActivity, t, 10f, Ui.PROG).apply {
                        letterSpacing = 0.14f
                        gravity = if (i == 0) Gravity.START else if (i == 1) Gravity.CENTER_HORIZONTAL else Gravity.END
                    }, LinearLayout.LayoutParams(0, WRAP, 1f))
                }
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(6), 0, 0) })
        }
    }
}
