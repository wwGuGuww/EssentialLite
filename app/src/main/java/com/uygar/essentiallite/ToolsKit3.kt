package com.uygar.essentiallite

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** "● ETİKET": seçiliyse kırmızı nokta görünür, değilse aynı genişlikte saydam (tasarımdaki opacity 0). */
private fun dotLabel(label: String, on: Boolean): CharSequence {
    val s = SpannableStringBuilder("● $label")
    s.setSpan(ForegroundColorSpan(if (on) Ui.RED else Color.TRANSPARENT), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return s
}

/** Yuvarlak çerçeveli seçim düğmesi (tasarımdaki mod/ölçü/hazır süre düğmeleri). */
private fun choice(ctx: Context, label: String, on: Boolean, withDot: Boolean, sizeSp: Float, onClick: () -> Unit): TextView =
    Ui.text(ctx, "", sizeSp, if (on) Ui.WHITE else Ui.GREY, bold = true).apply {
        text = if (withDot) dotLabel(label, on) else label
        gravity = Gravity.CENTER
        letterSpacing = 0.1f
        background = Ui.rounded(ctx, Color.TRANSPARENT, 22f, if (on) Ui.WHITE else Ui.LINE)
        Ui.pressAnim(this)
        setOnClickListener { onClick() }
    }

/** Hazır grup: bir satırda eşit genişlikte düğmeler. */
private fun row(ctx: Context, gap: Int, vararg views: View): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    views.forEachIndexed { i, v ->
        addView(v, LinearLayout.LayoutParams(0, ToolUi.dp(ctx, 44), 1f).apply { setMargins(if (i == 0) 0 else ToolUi.dp(ctx, gap) / 2, 0, if (i == views.size - 1) 0 else ToolUi.dp(ctx, gap) / 2, 0) })
    }
}

/** Yuvarlak kontrol düğmesi (88 / 104 dp). */
private fun circleBtn(ctx: Context, label: String, sizeDp: Int, border: Int, onClick: () -> Unit): TextView =
    Ui.text(ctx, label, 11f, Ui.WHITE, bold = true).apply {
        gravity = Gravity.CENTER
        letterSpacing = 0.14f
        background = Ui.rounded(ctx, Color.TRANSPARENT, sizeDp / 2f, border)
        Ui.pressAnim(this)
        setOnClickListener { onClick() }
    }

/** Ortadaki büyük başlat/duraklat düğmesi: 104 dp, nokta matrisi ikon. */
private fun mainBtn(ctx: Context, running: Boolean, onClick: () -> Unit): FrameLayout = FrameLayout(ctx).apply {
    contentDescription = if (running) "Duraklat" else "Başlat"
    background = GradientRound(ctx, if (running) Ui.RED else Ui.WHITE)
    addView(
        DotIcon(ctx, if (running) ToolUi.ICON_PAUSE else Ui.ICON_PLAY, if (running) Ui.RED else Ui.WHITE, 70),
        FrameLayout.LayoutParams(ToolUi.dp(ctx, 70), ToolUi.dp(ctx, 70), Gravity.CENTER)
    )
    Ui.pressAnim(this)
    setOnClickListener { onClick() }
}

@Suppress("FunctionName")
private fun GradientRound(ctx: Context, stroke: Int) = android.graphics.drawable.GradientDrawable().apply {
    shape = android.graphics.drawable.GradientDrawable.OVAL
    setColor(Color.TRANSPARENT)
    setStroke((1.5f * ctx.resources.displayMetrics.density).toInt(), stroke)
}

// ---------------------------------------------------------------------------------------------
// Metronom (tasarım 18)
// ---------------------------------------------------------------------------------------------

/** Kısa, tok bir tık sesi üretir (vurgu 1500 Hz, normal 1000 Hz). */
private class Clicker {
    private fun make(freq: Double): AudioTrack {
        val rate = 44100
        val n = rate * 50 / 1000
        val buf = ShortArray(n) { i ->
            (sin(2 * PI * freq * i / rate) * exp(-i / (rate * 0.008)) * 0.7 * Short.MAX_VALUE).toInt().toShort()
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(buf, 0, n)
        return t
    }

    private val accent = make(1500.0)
    private val normal = make(1000.0)

    fun play(a: Boolean) {
        val t = if (a) accent else normal
        runCatching { t.stop(); t.reloadStaticData(); t.play() }
    }

    fun release() {
        runCatching { accent.release(); normal.release() }
    }
}

/** 61 çubuklu tempo ölçeği: dokunup sürükleyerek 40–220 BPM seçilir. */
private class ScaleView(ctx: Context, private val onBpm: (Int) -> Unit) : View(ctx) {
    var bpm = 120
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (54 * d).toInt())

    override fun onDraw(c: Canvas) {
        val slot = width / 61f
        val cur = Math.round((bpm - 40) / 180f * 60f)
        for (i in 0 until 61) {
            val h = (if (i % 10 == 0) 22 else if (i % 5 == 0) 14 else 8) * d
            paint.color = if (i < cur) Ui.WHITE else if (i == cur) Ui.RED else Ui.BORDER2
            val cx = (i + 0.5f) * slot
            c.drawRoundRect(RectF(cx - 2 * d, height / 2f - h / 2, cx + 2 * d, height / 2f + h / 2), 2 * d, 2 * d, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val f = (e.x / width).coerceIn(0f, 1f)
                onBpm((40 + f * 180).roundToInt().coerceIn(40, 220))
                return true
            }
        }
        return true
    }
}

/** Vuruş paneli: ölçüdeki vuruş sayısı kadar nokta; çalan vuruş büyür. */
private class BeatView(ctx: Context) : View(ctx) {
    var sig = 4
    var beat = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val gap = 22 * d
        val widths = (0 until sig).map { (if (it == beat) 40 else 18) * d }
        val total = widths.sum() + gap * (sig - 1)
        var x = (width - total) / 2f
        for (i in 0 until sig) {
            paint.color = if (i == beat) (if (i == 0) Ui.RED else Ui.WHITE) else if (i == 0) 0xFF5A1015.toInt() else Ui.LINE
            c.drawCircle(x + widths[i] / 2f, height / 2f, widths[i] / 2f, paint)
            x += widths[i] + gap
        }
    }
}

class MetronomeActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var bpm = 120
    private var sig = 4
    private var running = false
    private var sound = true
    private var beat = -1
    private var nextAt = 0L
    private val taps = ArrayList<Long>()
    private val clicker = Clicker()
    private lateinit var bigBpm: TextView
    private lateinit var right: TextView
    private lateinit var beats: BeatView
    private lateinit var scale: ScaleView
    private lateinit var sigRow: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var soundBtn: TextView

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            beat = (beat + 1) % sig
            if (sound) clicker.play(beat == 0)
            beats.beat = beat
            beats.invalidate()
            nextAt += (60000L / bpm)
            h.postAtTime(this, nextAt)
        }
    }

    private fun tempoName(): String = when {
        bpm < 60 -> "LARGO"
        bpm < 76 -> "ADAGIO"
        bpm < 108 -> "ANDANTE"
        bpm < 120 -> "MODERATO"
        bpm < 156 -> "ALLEGRO"
        bpm < 176 -> "VIVACE"
        else -> "PRESTO"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        bigBpm = Ui.display(this, "120", 112f)
        beats = BeatView(this)
        scale = ScaleView(this) { setBpm(it) }
        sigRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        soundBtn = Ui.text(this, "", 12f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.14f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setOnClickListener { sound = !sound; renderControls() }
        }

        val tempo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(bigBpm, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(Ui.text(this@MetronomeActivity, "BPM", 12f, Ui.GREY).apply { letterSpacing = 0.2f })
        }
        val beatPanel = FrameLayout(this).apply {
            background = Ui.rounded(this@MetronomeActivity, Ui.SURFACE, 32f, Ui.BORDER)
            addView(beats, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val axis = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("40", "130", "220").forEachIndexed { i, t ->
                addView(Ui.text(this@MetronomeActivity, t, 10f, Ui.PROG).apply {
                    letterSpacing = 0.14f
                    gravity = if (i == 0) Gravity.START else if (i == 1) Gravity.CENTER_HORIZONTAL else Gravity.END
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
        }
        val nudges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("−5" to -5, "−1" to -1, "+1" to 1, "+5" to 5).forEachIndexed { i, (l, dlt) ->
                addView(Ui.text(this@MetronomeActivity, l, 13f, Ui.WHITE, bold = true).apply {
                    gravity = Gravity.CENTER
                    letterSpacing = 0.06f
                    background = Ui.rounded(this@MetronomeActivity, Color.TRANSPARENT, 24f, Ui.LINE)
                    Ui.pressAnim(this)
                    setOnClickListener { setBpm(bpm + dlt) }
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(if (i == 0) 0 else dp(4), 0, if (i == 3) 0 else dp(4), 0) })
            }
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tempo, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(beatPanel, LinearLayout.LayoutParams(MATCH, dp(120)).apply { setMargins(0, dp(18), 0, 0) })
            addView(scale, LinearLayout.LayoutParams(MATCH, dp(54)).apply { setMargins(0, dp(18), 0, 0) })
            addView(axis, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(nudges, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(sigRow, LinearLayout.LayoutParams(MATCH, dp(44)).apply { setMargins(0, dp(8), 0, 0) })
            addView(View(this@MetronomeActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(4), dp(18), dp(4), 0) })
        }
        right = ToolUi.screen(this, "METRONOM", "", ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(body)
        })
        right.textSize = 11f
        render()
    }

    private fun setBpm(v: Int) {
        bpm = v.coerceIn(40, 220)
        render()
    }

    private fun render() {
        bigBpm.text = bpm.toString()
        scale.bpm = bpm
        scale.invalidate()
        right.text = "$sig/4 · ${tempoName()}"
        beats.sig = sig
        beats.invalidate()
        sigRow.removeAllViews()
        val sigs = listOf(2, 3, 4, 6)
        sigs.forEachIndexed { i, g ->
            sigRow.addView(choice(this, "$g/4", sig == g, true, 12f) {
                sig = g; beat = -1; beats.beat = -1; render()
            }, LinearLayout.LayoutParams(0, ToolUi.dp(this, 44), 1f).apply {
                setMargins(if (i == 0) 0 else ToolUi.dp(this@MetronomeActivity, 4), 0, if (i == sigs.size - 1) 0 else ToolUi.dp(this@MetronomeActivity, 4), 0)
            })
        }
        renderControls()
    }

    private fun renderControls() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        soundBtn.text = dotLabel(if (sound) "AÇIK" else "KAPALI", true)
        soundBtn.setTextColor(if (sound) Ui.WHITE else Ui.GREY)
        controls.removeAllViews()
        controls.addView(circleBtn(this, "TAP", 88, Ui.LINE) { tap() }, LinearLayout.LayoutParams(dp(88), dp(88)))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(mainBtn(this, running) { toggle() }, LinearLayout.LayoutParams(dp(104), dp(104)))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(Ui.text(this@MetronomeActivity, "SES", 10f, Ui.GREY).apply { letterSpacing = 0.14f })
            (soundBtn.parent as? ViewGroup)?.removeView(soundBtn)
            addView(soundBtn, LinearLayout.LayoutParams(WRAP, dp(44)))
        }, LinearLayout.LayoutParams(dp(88), WRAP))
    }

    private fun tap() {
        val now = System.currentTimeMillis()
        taps.removeAll { now - it >= 2500 }
        taps.add(now)
        while (taps.size > 6) taps.removeAt(0)
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
            setBpm((60000.0 / avg).roundToInt())
        }
    }

    private fun toggle() {
        running = !running
        if (running) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            beat = -1
            nextAt = SystemClock.uptimeMillis()
            h.post(tick)
        } else stop()
        renderControls()
    }

    private fun stop() {
        running = false
        h.removeCallbacks(tick)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        beat = -1
        beats.beat = -1
        beats.invalidate()
    }

    override fun onPause() {
        super.onPause()
        if (running) { stop(); renderControls() }
    }

    override fun onDestroy() {
        super.onDestroy()
        clicker.release()
    }
}

// ---------------------------------------------------------------------------------------------
// Sayaç · Odak (tasarım 14d)
// ---------------------------------------------------------------------------------------------

object TimerAlarm {
    const val CHANNEL = "timer"

    private fun pi(ctx: Context, label: String) = PendingIntent.getBroadcast(
        ctx, 7002, Intent(ctx, TimerReceiver::class.java).putExtra("label", label),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun set(ctx: Context, atMs: Long, label: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi(ctx, label))
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx, ""))
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "SAYAÇ BİTTİ"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(TimerAlarm.CHANNEL, "Sayaç", NotificationManager.IMPORTANCE_HIGH))
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, TimerAlarm.CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        runCatching {
            nm.notify(
                7002,
                b.setSmallIcon(R.drawable.ic_notif).setContentTitle(label).setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(ctx, 0, Intent(ctx, TimerActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                    .build()
            )
        }
    }
}

/** 60 noktalı geri sayım halkası: dolu noktalar kalan süreyi, baş nokta vurgu rengini gösterir. */
private class RingView(ctx: Context) : View(ctx) {
    var lit = 60
    var accent = Ui.RED
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val rad = 140 * d
        for (i in 0 until 60) {
            val a = Math.PI * 2 * i / 60 - Math.PI / 2
            val head = i == lit - 1 && lit > 0
            val sz = if (head) 8f else if (i % 5 == 0) 6f else 4f
            paint.color = if (head) accent else if (i < lit) Ui.WHITE else Ui.BORDER2
            c.drawCircle(cx + (rad * Math.cos(a)).toFloat(), cy + (rad * Math.sin(a)).toFloat(), sz / 2 * d, paint)
        }
    }
}

class TimerActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var mode = 0 // 0 sayaç, 1 odak
    private var total = 5 * 60
    private var running = false
    private var endAt = 0L
    private var restMs = -1L
    private var onBreak = false
    private var rounds = 0
    private lateinit var ring: RingView
    private lateinit var timeView: TextView
    private lateinit var phaseView: TextView
    private lateinit var stateView: TextView
    private lateinit var modeRow: LinearLayout
    private lateinit var roundsRow: LinearLayout
    private lateinit var presetRow: LinearLayout
    private lateinit var controls: LinearLayout

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (endAt - System.currentTimeMillis() <= 0) finishRound() else refresh()
            h.postDelayed(this, 200)
        }
    }

    private fun left(): Int {
        val ms = if (running) max(0L, endAt - System.currentTimeMillis()) else if (restMs < 0) total * 1000L else restMs
        return Math.ceil(ms / 1000.0).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        ring = RingView(this)
        timeView = Ui.display(this, "05:00", 56f).apply { gravity = Gravity.CENTER_HORIZONTAL }
        phaseView = Ui.text(this, "", 10f, Ui.GREY).apply { letterSpacing = 0.2f; gravity = Gravity.CENTER_HORIZONTAL }
        modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        roundsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        val face = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(LinearLayout(this@TimerActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(timeView, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(phaseView, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(14), 0, 0) })
            }, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ToolUi.tabs(this@TimerActivity, 3), LinearLayout.LayoutParams(MATCH, dp(44)))
            addView(modeRow, LinearLayout.LayoutParams(MATCH, dp(44)).apply { setMargins(0, dp(18), 0, 0) })
            addView(face, LinearLayout.LayoutParams(dp(300), dp(300)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(18), 0, 0) })
            addView(roundsRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(presetRow, LinearLayout.LayoutParams(MATCH, dp(44)).apply { setMargins(0, dp(18), 0, 0) })
            addView(View(this@TimerActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(4), dp(18), dp(4), 0) })
        }
        stateView = ToolUi.screen(this, "SAYAÇ", "HAZIR", ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(body)
        })
        stateView.textSize = 11f
        render()
    }

    private fun setMode(m: Int) {
        mode = m; running = false; restMs = -1; total = if (m == 1) 25 * 60 else 5 * 60; onBreak = false; rounds = 0
        TimerAlarm.cancel(this)
        render()
    }

    private fun toggle() {
        if (running) {
            restMs = endAt - System.currentTimeMillis()
            running = false
            TimerAlarm.cancel(this)
        } else {
            val r = if (restMs <= 0) total * 1000L else restMs
            running = true
            endAt = System.currentTimeMillis() + r
            restMs = r
            TimerAlarm.set(this, endAt, alarmLabel())
            h.post(tick)
        }
        render()
    }

    private fun alarmLabel() = if (mode == 1) (if (onBreak) "MOLA BİTTİ" else "ODAK BİTTİ") else "SAYAÇ BİTTİ"

    private fun reset() {
        running = false; restMs = -1; onBreak = false
        if (mode == 1) rounds = 0
        TimerAlarm.cancel(this)
        render()
    }

    private fun skip() {
        if (mode == 1) { if (running) finishRound() }
        else {
            total += 60
            if (running) { endAt += 60000; TimerAlarm.set(this, endAt, alarmLabel()) } else restMs = -1
            render()
        }
    }

    private fun finishRound() {
        notifyUser()
        if (mode == 1) {
            val brk = !onBreak
            val newRounds = if (brk) min(4, rounds + 1) else if (rounds % 4 == 0 && rounds >= 4) 0 else rounds
            val dur = if (brk) (if (rounds + 1 >= 4) 15 * 60 else 5 * 60) else 25 * 60
            onBreak = brk; rounds = newRounds; total = dur; restMs = dur * 1000L; running = true
            endAt = System.currentTimeMillis() + dur * 1000L
            TimerAlarm.set(this, endAt, alarmLabel())
        } else {
            running = false; restMs = 0
            TimerAlarm.cancel(this)
        }
        flash()
        render()
    }

    private fun notifyUser() {
        runCatching {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1)) else @Suppress("DEPRECATION") v.vibrate(400)
            RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play()
        }
    }

    private fun flash() {
        timeView.startAnimation(AlphaAnimation(1f, 0.2f).apply { duration = 300; repeatCount = 5; repeatMode = android.view.animation.Animation.REVERSE })
    }

    private fun refresh() {
        val l = left()
        val frac = if (total > 0) l / total.toFloat() else 0f
        ring.lit = (frac * 60f).roundToInt()
        ring.accent = if (mode == 1 && onBreak) Ui.WHITE else Ui.RED
        ring.invalidate()
        timeView.text = ToolUi.p2((l / 60).toLong()) + ":" + ToolUi.p2((l % 60).toLong())
        stateView.text = if (running) "ÇALIŞIYOR" else if (l == 0) "BİTTİ" else "HAZIR"
    }

    private fun render() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        phaseView.text = if (mode == 1) (if (onBreak) "MOLA" else "ODAK") else "GERİ SAYIM"
        modeRow.removeAllViews()
        listOf("SAYAÇ" to 0, "ODAK 25/5" to 1).forEachIndexed { i, (l, m) ->
            modeRow.addView(choice(this, l, mode == m, true, 11f) { setMode(m) },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(if (i == 0) 0 else dp(4), 0, if (i == 1) 0 else dp(4), 0) })
        }
        roundsRow.visibility = if (mode == 1) View.VISIBLE else View.GONE
        roundsRow.removeAllViews()
        for (i in 0 until 4) {
            roundsRow.addView(View(this).apply {
                background = Ui.rounded(this@TimerActivity, if (i < rounds) Ui.RED else Color.TRANSPARENT, 5f, if (i < rounds) Ui.RED else Ui.LINE)
            }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { setMargins(dp(5), 0, dp(5), 0) })
        }
        roundsRow.addView(Ui.text(this, "$rounds / 4 TUR", 10f, Ui.GREY).apply { letterSpacing = 0.16f; setPadding(dp(6), 0, 0, 0) })
        presetRow.visibility = if (mode == 0) View.VISIBLE else View.GONE
        presetRow.removeAllViews()
        val pre = listOf(1, 5, 10, 30)
        pre.forEachIndexed { i, m ->
            presetRow.addView(choice(this, "$m DK", total == m * 60, false, 11f) {
                total = m * 60; restMs = -1; running = false; TimerAlarm.cancel(this); render()
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(if (i == 0) 0 else dp(3), 0, if (i == pre.size - 1) 0 else dp(3), 0) })
        }
        controls.removeAllViews()
        controls.addView(circleBtn(this, "SIFIRLA", 88, Ui.LINE) { reset() }, LinearLayout.LayoutParams(dp(88), dp(88)))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(mainBtn(this, running) { toggle() }, LinearLayout.LayoutParams(dp(104), dp(104)))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(circleBtn(this, if (mode == 1) "ATLA" else "+1 DK", 88, Ui.LINE) { skip() }, LinearLayout.LayoutParams(dp(88), dp(88)))
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (running) h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
    }
}

// ---------------------------------------------------------------------------------------------
// Gelişmiş hesap (tasarım 15b)
// ---------------------------------------------------------------------------------------------

class CalcAdvActivity : BaseActivity() {
    private var mode = 0 // 0 bilim, 1 kdv, 2 kredi, 3 birim
    private lateinit var area: FrameLayout

    // Bilimsel
    private var cur = "0"
    private var acc: Double? = null
    private var op: Char? = null
    private var fresh = false
    private var deg = true
    private var expr = ""

    // Ortak sayı girişi (KDV / birim)
    private var value = "1250"

    // KDV
    private var rate = 20
    private var incl = false

    // Kredi
    private var loanP = 100000.0
    private var loanR = 3.5
    private var loanN = 24

    // Birim
    private var cat = 0
    private var fi = 0
    private var ti = 1

    private val sym = mapOf('+' to "+", '-' to "−", '*' to "×", '/' to "÷", '^' to "xʸ")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        area = FrameLayout(this)
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("BİLİM", "KDV", "KREDİ", "BİRİM").forEachIndexed { i, l ->
                addView(choice(this@CalcAdvActivity, l, mode == i, true, 10.5f) { mode = i; build() },
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(if (i == 0) 0 else dp(3), 0, if (i == 3) 0 else dp(3), 0) })
            }
        }
        navRow = nav
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nav, LinearLayout.LayoutParams(MATCH, dp(44)))
            addView(area, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, dp(14), 0, 0) })
        }
        val right = ToolUi.screen(this, "GELİŞMİŞ", "← BASİT", body)
        right.apply {
            setTextColor(Ui.WHITE)
            textSize = 11f
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            background = Ui.rounded(this@CalcAdvActivity, Color.TRANSPARENT, 22f, Ui.LINE)
            setPadding(dp(18), 0, dp(18), 0)
            minHeight = dp(44)
            text = SpannableStringBuilder("← BASİT").also { it.setSpan(ForegroundColorSpan(Ui.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            setOnClickListener { finish() }
        }
        build()
    }

    private lateinit var navRow: LinearLayout

    private fun build() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        navRow.removeAllViews()
        listOf("BİLİM", "KDV", "KREDİ", "BİRİM").forEachIndexed { i, l ->
            navRow.addView(choice(this, l, mode == i, true, 10.5f) { mode = i; build() },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(if (i == 0) 0 else dp(3), 0, if (i == 3) 0 else dp(3), 0) })
        }
        area.removeAllViews()
        area.addView(
            when (mode) { 0 -> sci(); 1 -> vat(); 2 -> loan(); else -> unit() },
            FrameLayout.LayoutParams(MATCH, MATCH)
        )
    }

    // --- ortak ---
    private fun num(s: String): Double = if (s == "HATA" || s.isBlank()) 0.0 else s.replace(',', '.').toDoubleOrNull() ?: 0.0

    private fun toStr(n: Double, d: Int = 9): String {
        if (!n.isFinite()) return "HATA"
        val k = 10.0.pow(d)
        val r = Math.rint(n * k) / k
        return BigDecimal.valueOf(r).stripTrailingZeros().toPlainString().replace('.', ',')
    }

    private fun group(s: String): String {
        if (s == "HATA") return s
        val p = s.split(',')
        val neg = p[0].startsWith("-")
        val d = p[0].removePrefix("-").replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ".")
        return (if (neg) "-" else "") + d + (if (p.size > 1) "," + p[1] else "")
    }

    private fun fix2(n: Double) = group(String.format(Locale.US, "%.2f", n).replace('.', ','))

    private fun label(t: String) = Ui.text(this, t, 10f, Ui.GREY).apply { letterSpacing = 0.16f }

    /** Büyük sayı yazısı (Doto). */
    private fun big(t: String, sp: Float, color: Int = Ui.WHITE) = Ui.display(this, t, sp, color)

    private fun key(l: String, fg: Int, border: Int, sizeSp: Float, heightDp: Int, radius: Float, onClick: () -> Unit) =
        Ui.text(this, l, sizeSp, fg, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(this@CalcAdvActivity, Color.TRANSPARENT, radius, border)
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
            minHeight = ToolUi.dp(this@CalcAdvActivity, heightDp)
        }

    /** 3 sütunlu sayı tuşları: 1-9, virgül, 0, sil. */
    private fun pad(): LinearLayout {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", "⌫")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            for (r in 0 until 4) {
                addView(LinearLayout(this@CalcAdvActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (c in 0 until 3) {
                        val l = keys[r * 3 + c]
                        addView(key(l, if (l == "⌫") Ui.GREY2 else Ui.WHITE, Ui.BORDER, 20f, 54, 27f) { valueKey(l) },
                            LinearLayout.LayoutParams(0, ToolUi.dp(this@CalcAdvActivity, 54), 1f).apply {
                                setMargins(if (c == 0) 0 else ToolUi.dp(this@CalcAdvActivity, 4), 0, if (c == 2) 0 else ToolUi.dp(this@CalcAdvActivity, 4), 0)
                            })
                    }
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, ToolUi.dp(this@CalcAdvActivity, 8)) })
            }
        }
    }

    private fun valueKey(l: String) {
        if (l == "⌫") value = if (value.length <= 1) "0" else value.dropLast(1)
        else {
            if (l == "," && value.contains(',')) return
            if (value.replace(",", "").length >= 10) return
            value = if (value == "0" && l != ",") l else value + l
        }
        build()
    }

    // --- bilimsel ---
    private fun calc(a: Double, o: Char, b: Double): Double = when (o) {
        '+' -> a + b
        '-' -> a - b
        '*' -> a * b
        '/' -> if (b == 0.0) Double.NaN else a / b
        '^' -> a.pow(b)
        else -> b
    }

    private fun sci(): View {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val shown = group(cur)
        fun put(ch: String) {
            if (cur == "HATA" || fresh) { cur = if (ch == ",") "0," else ch; fresh = false }
            else {
                if (ch == "," && cur.contains(',')) return
                if (cur.replace(",", "").replace("-", "").length >= 12) return
                cur = if (cur == "0" && ch != ",") ch else cur + ch
            }
            build()
        }
        fun pick(o: Char) {
            if (cur == "HATA") return
            val a = acc
            val p = op
            if (p != null && !fresh && a != null) {
                val r = calc(a, p, num(cur)); acc = r; cur = toStr(r); op = o; fresh = true; expr = toStr(r) + " " + sym[o]
            } else { acc = num(cur); op = o; fresh = true; expr = cur + " " + sym[o] }
            build()
        }
        fun equals() {
            val a = acc ?: return
            val p = op ?: return
            if (cur == "HATA") return
            val r = calc(a, p, num(cur))
            expr = toStr(a) + " " + sym[p] + " " + cur + " ="
            cur = toStr(r); acc = null; op = null; fresh = true
            build()
        }
        fun clear() { cur = "0"; acc = null; op = null; fresh = false; expr = ""; build() }
        fun back() {
            if (fresh || cur == "HATA") { clear(); return }
            cur = if (cur.length <= 1 || (cur.length == 2 && cur[0] == '-')) "0" else cur.dropLast(1)
            build()
        }
        fun un(name: String, f: (Double) -> Double) {
            if (cur == "HATA") return
            val r = f(num(cur)); expr = "$name($cur)"; cur = toStr(r); fresh = true; build()
        }
        fun rad(x: Double) = if (deg) Math.toRadians(x) else x
        data class K(val l: String, val kind: String, val act: () -> Unit, val opc: Char? = null)
        val keys = listOf(
            K("sin", "fn", { un("sin") { Math.sin(rad(it)) } }), K("cos", "fn", { un("cos") { Math.cos(rad(it)) } }), K("tan", "fn", { un("tan") { Math.tan(rad(it)) } }),
            K("ln", "fn", { un("ln") { Math.log(it) } }), K("log", "fn", { un("log") { Math.log10(it) } }),
            K("√", "fn", { un("√") { Math.sqrt(it) } }), K("x²", "fn", { un("sqr") { it * it } }), K(sym['^']!!, "op", { pick('^') }, '^'),
            K("π", "fn", { cur = "3,14159265"; fresh = true; build() }), K("e", "fn", { cur = "2,71828183"; fresh = true; build() }),
            K("7", "n", { put("7") }), K("8", "n", { put("8") }), K("9", "n", { put("9") }), K(sym['/']!!, "op", { pick('/') }, '/'), K("AC", "fn", { clear() }),
            K("4", "n", { put("4") }), K("5", "n", { put("5") }), K("6", "n", { put("6") }), K(sym['*']!!, "op", { pick('*') }, '*'), K("⌫", "fn", { back() }),
            K("1", "n", { put("1") }), K("2", "n", { put("2") }), K("3", "n", { put("3") }), K(sym['-']!!, "op", { pick('-') }, '-'),
            K("±", "fn", { if (cur != "0" && cur != "HATA") { cur = if (cur[0] == '-') cur.drop(1) else "-$cur"; build() } }),
            K("0", "n", { put("0") }), K(",", "n", { put(",") }),
            K("n!", "fn", { un("fakt") { x -> if (x < 0 || x > 20 || x != Math.floor(x)) Double.NaN else (2..x.toInt()).fold(1.0) { a, b -> a * b } } }),
            K(sym['+']!!, "op", { pick('+') }, '+'), K("=", "eq", { equals() })
        )
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            for (r in 0 until 6) {
                addView(LinearLayout(this@CalcAdvActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (c in 0 until 5) {
                        val k = keys[r * 5 + c]
                        val active = k.opc != null && op == k.opc && fresh
                        val border = if (active) Ui.WHITE else when (k.kind) { "op" -> Ui.PROG; "eq" -> Ui.RED; else -> Ui.BORDER }
                        val fg = when (k.kind) { "eq" -> Ui.RED; "fn" -> Ui.GREY2; else -> Ui.WHITE }
                        addView(key(k.l, fg, border, if (k.kind == "fn") 13f else 18f, 56, 28f) { k.act() },
                            LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(if (c == 0) 0 else dp(4), 0, if (c == 4) 0 else dp(4), 0) })
                    }
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, dp(8)) })
            }
        }
        val degBtn = Ui.text(this, "", 11f, Ui.WHITE, bold = true).apply {
            text = SpannableStringBuilder("● " + (if (deg) "DERECE" else "RADYAN")).also { it.setSpan(ForegroundColorSpan(Ui.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = Ui.rounded(this@CalcAdvActivity, Color.TRANSPARENT, 22f, Ui.LINE)
            setOnClickListener { deg = !deg; build() }
        }
        val fs = when { shown.length > 12 -> 30f; shown.length > 9 -> 38f; shown.length > 7 -> 48f; else -> 60f }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@CalcAdvActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(dp(4), 0, dp(4), dp(4))
                addView(LinearLayout(this@CalcAdvActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(degBtn, LinearLayout.LayoutParams(WRAP, dp(44)))
                    addView(View(this@CalcAdvActivity), LinearLayout.LayoutParams(0, 1, 1f))
                    addView(Ui.text(this@CalcAdvActivity, expr, 12f, Ui.GREY).apply { letterSpacing = 0.12f })
                }, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(big(shown, fs).apply { gravity = Gravity.END; maxLines = 1 }, LinearLayout.LayoutParams(MATCH, WRAP))
            }, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(grid, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(12), 0, 0) })
        }
    }

    // --- KDV ---
    private fun vat(): View {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val v = num(value)
        val base = if (incl) v / (1 + rate / 100.0) else v
        val vatAmt = base * (rate / 100.0)
        val total = base + vatAmt
        val rates = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(1, 10, 20).forEachIndexed { i, r ->
                addView(choice(this@CalcAdvActivity, "%$r", rate == r, false, 12f) { rate = r; build() },
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(if (i == 0) 0 else dp(3), 0, if (i == 2) 0 else dp(3), 0) })
            }
        }
        val dir = Ui.text(this, "", 11f, Ui.WHITE, bold = true).apply {
            text = SpannableStringBuilder("● " + (if (incl) "KDV DAHİL → HARİÇ" else "KDV HARİÇ → DAHİL")).also { it.setSpan(ForegroundColorSpan(Ui.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            background = Ui.rounded(this@CalcAdvActivity, Color.TRANSPARENT, 22f, Ui.LINE)
            setOnClickListener { incl = !incl; build() }
        }
        val outs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(Triple("MATRAH", fix2(base), Ui.WHITE), Triple("KDV %$rate", fix2(vatAmt), Ui.RED), Triple("TOPLAM", fix2(total), Ui.WHITE)).forEachIndexed { i, (l, t, c) ->
                addView(LinearLayout(this@CalcAdvActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@CalcAdvActivity, l, 10f, Ui.GREY).apply { letterSpacing = 0.14f })
                    addView(big(t, 18f, c), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, if (i < 2) dp(8) else 0, 0) })
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(rates, LinearLayout.LayoutParams(MATCH, dp(44)))
            addView(dir, LinearLayout.LayoutParams(MATCH, dp(44)).apply { setMargins(0, dp(12), 0, 0) })
            addView(LinearLayout(this@CalcAdvActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(6), dp(4), 0)
                addView(label("TUTAR · TL"))
                addView(big(group(value), 40f), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(4), 0, 0) })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(12), 0, 0) })
            addView(View(this@CalcAdvActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(12), 0, dp(12)) })
            addView(outs, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(View(this@CalcAdvActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(pad(), LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    // --- kredi ---
    private fun loan(): View {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val r = loanR / 100.0
        val pmt = if (r == 0.0) loanP / loanN else (loanP * r) / (1 - (1 + r).pow(-loanN.toDouble()))
        val paid = pmt * loanN
        val interest = paid - loanP
        val shareI = ((interest / paid) * 40).roundToInt().coerceIn(0, 40)
        val dots = DotsView(this, 40, 1, 5f, 5f).apply {
            colors = IntArray(40) { i -> if (i >= 40 - shareI) Ui.RED else Ui.WHITE }
        }
        fun step(k: Char, d: Double, mn: Double, mx: Double) {
            val cv = when (k) { 'P' -> loanP; 'R' -> loanR; else -> loanN.toDouble() }
            val nv = Math.round((cv + d) * 100) / 100.0
            if (nv < mn || nv > mx) return
            when (k) { 'P' -> loanP = nv; 'R' -> loanR = nv; else -> loanN = nv.toInt() }
            build()
        }
        data class S(val l: String, val v: String, val dn: () -> Unit, val up: () -> Unit)
        val steppers = listOf(
            S("KREDİ TUTARI · TL", group(loanP.toLong().toString()), { step('P', -10000.0, 10000.0, 2000000.0) }, { step('P', 10000.0, 10000.0, 2000000.0) }),
            S("AYLIK FAİZ · %", toStr(loanR, 2), { step('R', -0.1, 0.0, 10.0) }, { step('R', 0.1, 0.0, 10.0) }),
            S("VADE · AY", loanN.toString(), { step('N', -3.0, 3.0, 120.0) }, { step('N', 3.0, 3.0, 120.0) })
        )
        fun stepBtn(t: String, act: () -> Unit) = Ui.text(this, t, 18f, Ui.WHITE).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(this@CalcAdvActivity, Color.TRANSPARENT, 22f, Ui.LINE)
            Ui.pressAnim(this)
            setOnClickListener { act() }
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(this@CalcAdvActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)))
            steppers.forEach { s ->
                addView(LinearLayout(this@CalcAdvActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(10), 0, dp(10))
                    addView(LinearLayout(this@CalcAdvActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label(s.l))
                        addView(big(s.v, 22f), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(5), 0, 0) })
                    }, LinearLayout.LayoutParams(0, WRAP, 1f))
                    addView(stepBtn("−", s.dn), LinearLayout.LayoutParams(dp(52), dp(44)).apply { setMargins(0, 0, dp(8), 0) })
                    addView(stepBtn("+", s.up), LinearLayout.LayoutParams(dp(52), dp(44)))
                }, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(View(this@CalcAdvActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)))
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@CalcAdvActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(4), dp(4), 0)
                addView(label("AYLIK TAKSİT · TL"))
                addView(big(group(Math.round(pmt).toString()), 56f), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(dots, LinearLayout.LayoutParams(MATCH, dp(5)).apply { setMargins(dp(4), dp(14), dp(4), 0) })
            addView(LinearLayout(this@CalcAdvActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), 0, dp(4), 0)
                addView(Ui.text(this@CalcAdvActivity, "● ANAPARA ${group(loanP.toLong().toString())}", 10f, Ui.GREY).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.text(this@CalcAdvActivity, "● FAİZ ${group(Math.round(interest).toString())}", 10f, Ui.RED).apply { letterSpacing = 0.12f })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(14), 0, 0) })
            addView(list, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(14), 0, 0) })
            addView(LinearLayout(this@CalcAdvActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Ui.text(this@CalcAdvActivity, "TOPLAM ÖDEME", 11f, Ui.GREY).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.text(this@CalcAdvActivity, group(Math.round(paid).toString()) + " TL", 11f, Ui.WHITE).apply { letterSpacing = 0.1f })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(14), 0, 0) })
            addView(Ui.text(this@CalcAdvActivity, "KKDF / BSMV VERGİLERİ DAHİL DEĞİL", 10f, Ui.PROG).apply { letterSpacing = 0.1f },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(14), 0, 0) })
        }
    }

    // --- birim ---
    private class Cat(val l: String, val u: List<String>, val f: List<Double>?)

    private val cats = listOf(
        Cat("UZUNLUK", listOf("m", "km", "cm", "mil", "ft", "in"), listOf(1.0, 1000.0, 0.01, 1609.344, 0.3048, 0.0254)),
        Cat("AĞIRLIK", listOf("kg", "g", "lb", "oz"), listOf(1.0, 0.001, 0.45359237, 0.0283495231)),
        Cat("SICAKLIK", listOf("°C", "°F", "K"), null)
    )

    private fun unit(): View {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val c = cats[cat]
        val from = fi % c.u.size
        val to = ti % c.u.size
        val v = num(value)
        val out = if (c.f != null) v * c.f[from] / c.f[to] else {
            val cel = if (from == 0) v else if (from == 1) (v - 32) / 1.8 else v - 273.15
            if (to == 0) cel else if (to == 1) cel * 1.8 + 32 else cel + 273.15
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            cats.forEachIndexed { i, ct ->
                addView(choice(this@CalcAdvActivity, ct.l, cat == i, false, 10.5f) { cat = i; fi = 0; ti = 1; build() },
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(if (i == 0) 0 else dp(3), 0, if (i == cats.size - 1) 0 else dp(3), 0) })
            }
        }
        fun unitBtn(t: String, act: () -> Unit) = Ui.text(this, "$t ▾", 12f, Ui.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            setPadding(dp(16), 0, dp(16), 0)
            background = Ui.rounded(this@CalcAdvActivity, Color.TRANSPARENT, 22f, Ui.LINE)
            setOnClickListener { act() }
        }
        fun block(title: String, unitT: String, act: () -> Unit, text: String, color: Int, top: Boolean) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), 0)
            addView(LinearLayout(this@CalcAdvActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label(title), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(unitBtn(unitT, act), LinearLayout.LayoutParams(WRAP, dp(44)))
            })
            addView(big(text, 40f, color), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabs, LinearLayout.LayoutParams(MATCH, dp(44)))
            addView(block("DEĞER", c.u[from], { fi = (from + 1) % c.u.size; build() }, group(value), Ui.WHITE, true), LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(12), 0, 0) })
            addView(View(this@CalcAdvActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(12), 0, 0) })
            addView(block("SONUÇ", c.u[to], { ti = (to + 1) % c.u.size; build() }, group(toStr(out, 4)), Ui.RED, false), LinearLayout.LayoutParams(MATCH, WRAP))
            addView(View(this@CalcAdvActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(pad(), LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }
}
