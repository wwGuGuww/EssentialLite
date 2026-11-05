package com.uygar.essentiallite

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.math.BigDecimal
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Araçlar bölümü (tasarım 14–20) için ortak yardımcılar: ekran iskeleti, sekmeler, ikonlar, ana ekran şeridi. */
object ToolUi {
    val ICON_BACK = listOf("0001000", "0010000", "0100000", "1111111", "0100000", "0010000", "0001000")
    val ICON_CLOCK = listOf("0011100", "0100010", "1001001", "1001101", "1000001", "0100010", "0011100")
    val ICON_STOPWATCH = listOf("0011100", "0001000", "0111110", "1001001", "1001101", "1000001", "0111110")
    val ICON_CALC = listOf("0111110", "0100010", "0111110", "0000000", "1010101", "0000000", "1010101")
    val ICON_LEVEL = listOf("0011100", "0100010", "1001001", "1011101", "1001001", "0100010", "0011100")
    val ICON_PAUSE = listOf("0000000", "0110110", "0110110", "0110110", "0110110", "0110110", "0000000")
    val ICON_BELL7 = listOf("0001000", "0011100", "0111110", "0111110", "0111110", "1111111", "0001000")

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun p2(n: Long) = n.toString().padStart(2, '0')

    private val TR = Locale("tr", "TR")

    fun dateLabel(): String = SimpleDateFormat("dd.MM EEE", TR).format(Calendar.getInstance().time).uppercase(TR)

    /** "● ETİKET": kırmızı nokta + metin. Nokta [on] değilse saydam (aynı genişlik). */
    fun dotText(label: String, on: Boolean = true): CharSequence {
        val s = SpannableStringBuilder("● $label")
        s.setSpan(ForegroundColorSpan(if (on) Ui.RED else Color.TRANSPARENT), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /**
     * Ortak ekran iskeleti (tasarım): 20 dp yan, 44 dp üst, 28 dp alt boşluk; üstte 44 dp başlık satırı
     * (nokta matrisi geri oku + "● BAŞLIK"), sağda bilgi yazısı. Sağdaki yazıyı döndürür.
     */
    fun screen(a: Activity, title: String, right: String, body: View, topGap: Int = 18): TextView {
        val rightView = Ui.text(a, right, 11f, Ui.GREY).apply { letterSpacing = 0.1f }
        val back = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Geri"
            setPadding(dp(a, 10), 0, dp(a, 10), 0)
            addView(DotIcon(a, ICON_BACK, Ui.WHITE, 27))
            addView(Ui.kindLabel(a, title).apply { textSize = 11f; letterSpacing = 0.16f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(a, 12), 0, 0, 0) })
            setOnClickListener { a.finish() }
        }
        val header = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(back, LinearLayout.LayoutParams(WRAP, dp(a, 44)).apply { setMargins(dp(a, -10), 0, 0, 0) })
            addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
            addView(rightView)
        }
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(a, 20), dp(a, 44), dp(a, 20), dp(a, 28))
            addView(header, LinearLayout.LayoutParams(MATCH, dp(a, 44)))
            addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, dp(a, topGap), 0, 0) })
        }
        a.setContentView(root)
        return rightView
    }

    /** SAAT / ALARM / KRONO / SAYAÇ sekmeleri (tasarım: 44 dp, 22 dp köşe, seçili arka plan #1E1E1E). */
    fun tabs(a: Activity, current: Int): View {
        val classes = listOf(ClockActivity::class.java, AlarmActivity::class.java, StopwatchActivity::class.java, TimerActivity::class.java)
        val names = listOf("SAAT", "ALARM", "KRONO", "SAYAÇ")
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(a, Color.TRANSPARENT, 22f, Ui.LINE)
            clipToOutline = true
            names.forEachIndexed { i, n ->
                val on = i == current
                addView(Ui.text(a, "", 10.5f, if (on) Ui.WHITE else Ui.GREY, bold = true).apply {
                    text = if (on) dotText(n) else n
                    gravity = Gravity.CENTER
                    letterSpacing = 0.08f
                    setBackgroundColor(if (on) Ui.OFF else Color.TRANSPARENT)
                    if (!on) setOnClickListener {
                        a.startActivity(Intent(a, classes[i]))
                        a.overridePendingTransition(0, 0)
                        a.finish()
                    }
                }, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
        }
    }

    /** İnce çerçeveli, yuvarlak düğme. [selected] ise beyaz çerçeve. */
    fun ghost(ctx: Context, label: String, selected: Boolean, onClick: () -> Unit): TextView =
        Ui.text(ctx, label, 11f, if (selected) Ui.WHITE else Ui.GREY, bold = true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            background = Ui.rounded(ctx, Color.TRANSPARENT, 28f, if (selected) Ui.WHITE else Ui.LINE)
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }

    /** Kesik çizgili kenarlık (ana ekrandaki "DAHA FAZLA" kartı). */
    fun dashed(ctx: Context, fill: Int, radiusDp: Float, stroke: Int): GradientDrawable {
        val d = ctx.resources.displayMetrics.density
        return GradientDrawable().apply {
            cornerRadius = radiusDp * d
            setColor(fill)
            setStroke(d.toInt().coerceAtLeast(1), stroke, 4 * d, 3 * d)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Ana ekran şeridi (tasarım Main)
    // -----------------------------------------------------------------------------------------

    fun homeStrip(a: Activity): View {
        fun dpx(v: Int) = dp(a, v)
        fun open(cls: Class<*>) = a.startActivity(Intent(a, cls))
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(a, "", 10f, Ui.GREY).apply {
                text = dotText("ARAÇLAR · $TOOL_COUNT")
                letterSpacing = 0.18f
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(Ui.text(a, "", 10f, Ui.WHITE).apply {
                val s = SpannableStringBuilder("TÜMÜ →")
                s.setSpan(ForegroundColorSpan(Ui.RED), 5, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                text = s
                letterSpacing = 0.18f
                gravity = Gravity.CENTER
                setPadding(dpx(8), 0, dpx(8), 0)
                setOnClickListener { open(ToolsActivity::class.java) }
            }, LinearLayout.LayoutParams(WRAP, dpx(44)).apply { setMargins(dpx(-8), dpx(-14), dpx(-8), dpx(-14)) })
        }

        val track = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        fun tile(label: String, cls: Class<*>?, dashed: Boolean = false, content: LinearLayout.() -> Unit) {
            track.addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpx(14), dpx(12), dpx(14), dpx(12))
                background = if (dashed) dashed(a, Ui.SURFACE, 24f, Ui.LINE) else Ui.rounded(a, Ui.SURFACE, 24f, Ui.BORDER)
                addView(Ui.text(a, label, 9.5f, Ui.GREY).apply { letterSpacing = 0.16f })
                addView(View(a), LinearLayout.LayoutParams(1, 0, 1f))
                content()
                Ui.pressAnim(this)
                if (cls != null) setOnClickListener { open(cls) }
            }, LinearLayout.LayoutParams(dpx(112), dpx(108)).apply { setMargins(0, 0, dpx(8), 0) })
        }

        val now = Calendar.getInstance()
        val hm = p2(now.get(Calendar.HOUR_OF_DAY).toLong()) + ":" + p2(now.get(Calendar.MINUTE).toLong())
        tile("SAAT", ClockActivity::class.java) {
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(Ui.display(a, hm, 20f).apply { maxLines = 1 })
                addView(Ui.display(a, p2(now.get(Calendar.SECOND).toLong()), 8f, Ui.RED).apply { maxLines = 1 }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dpx(3), 0, 0, 0) })
            })
        }
        val next = AlarmStore.next(a)
        tile("ALARM", AlarmActivity::class.java) {
            addView(Ui.display(a, next?.first ?: "--:--", 24f))
            addView(Ui.text(a, next?.second ?: "ALARM YOK", 9f, Ui.GREY).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dpx(6), 0, 0) })
        }
        val snap = BatteryWatch.snapshot(a)
        tile("PİL", BatteryActivity::class.java) {
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(Ui.display(a, snap.level.toString(), 24f))
                addView(Ui.display(a, "%", 10f, Ui.GREY))
            })
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                for (i in 0 until 10) {
                    addView(View(a).apply {
                        background = Ui.rounded(a, if (i < Math.round(snap.level / 10f)) Ui.WHITE else Ui.OFF, 1f)
                    }, LinearLayout.LayoutParams(0, dpx(5), 1f).apply { setMargins(0, 0, if (i < 9) dpx(2) else 0, 0) })
                }
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dpx(7), 0, 0) })
        }
        tile("HESAP", CalcActivity::class.java) {
            addView(KeyDots(a, 10), LinearLayout.LayoutParams(MATCH, dpx(10 * 3 + 5 * 2)))
        }
        tile("SAYAÇ", TimerActivity::class.java) {
            addView(Ui.display(a, "05:00", 24f))
            addView(View(a).apply { background = Ui.rounded(a, Ui.OFF, 2f) }, LinearLayout.LayoutParams(MATCH, dpx(3)).apply { setMargins(0, dpx(7), 0, 0) })
        }
        tile("METRONOM", MetronomeActivity::class.java) {
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(Ui.display(a, "120", 24f), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(View(a).apply { background = Ui.rounded(a, Ui.RED, 5f) }, LinearLayout.LayoutParams(dpx(10), dpx(10)).apply { setMargins(0, 0, dpx(6), dpx(4)) })
            })
        }
        tile("ŞİFRE", PasswordActivity::class.java) {
            addView(Ui.text(a, PasswordActivity.sample(), 13f, Ui.WHITE, bold = true).apply { letterSpacing = 0.1f; setLineSpacing(0f, 1.15f) })
        }
        tile("DAHA FAZLA", ToolsActivity::class.java, dashed = true) {
            addView(Ui.display(a, "+2", 24f))
            addView(Ui.text(a, "KRONO · TERAZİ", 9f, Ui.GREY).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dpx(6), 0, 0) })
        }

        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(HorizontalScrollView(a).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(track)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dpx(10), 0, 0) })
        }
    }

    const val TOOL_COUNT = 9
}

/** 4×3 mini tuş noktaları: sırayla biri yanar (tasarım: Hesap kartı). */
private class KeyDots(ctx: Context, private val dotDp: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    private var hot = 0
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { hot = (hot + 1) % 12; invalidate(); h.postDelayed(this, 840) }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); h.postDelayed(tick, 840) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); h.removeCallbacks(tick) }

    override fun onDraw(c: Canvas) {
        val cw = width / 4f
        for (i in 0 until 12) {
            val x = i % 4
            val y = i / 4
            val cx = (x + 0.5f) * cw
            val cy = (y * (dotDp + 5) + dotDp / 2f) * d
            val on = i == hot
            paint.style = Paint.Style.FILL
            paint.color = if (on) Ui.WHITE else Color.TRANSPARENT
            c.drawCircle(cx, cy, dotDp / 2f * d, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = d
            paint.color = if (i == 11) Ui.RED else if (on) Ui.WHITE else Ui.LINE
            c.drawCircle(cx, cy, dotDp / 2f * d - d / 2, paint)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Araçlar sayfası (tasarım 14)
// ---------------------------------------------------------------------------------------------

class ToolsActivity : BaseActivity(), SensorEventListener {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var grid: LinearLayout
    private lateinit var countView: TextView
    private var cat = 0
    private var query = ""
    private lateinit var catRow: LinearLayout
    private var hm: TextView? = null
    private var ss: TextView? = null
    private var secDots: DotsView? = null
    private var bubble: MiniBubble? = null
    private var tilt: TextView? = null
    private lateinit var sm: SensorManager
    private val TR = Locale("tr", "TR")

    private class Tool(val id: String, val cat: Int, val keys: String, val span2: Boolean)

    private val tools = listOf(
        Tool("clock", 1, "saat zaman dünya", true), Tool("alarm", 1, "alarm uyandır zaman", false),
        Tool("battery", 4, "pil şarj sağlık sistem", false), Tool("calc", 2, "hesap makinesi kdv kredi birim çevirici ölçü", false),
        Tool("timer", 1, "sayaç zamanlayıcı odak pomodoro zaman", false), Tool("stopwatch", 1, "kronometre tur zaman", false),
        Tool("level", 2, "su terazisi düzlem ölçü", false), Tool("metro", 3, "metronom bpm tempo müzik", true),
        Tool("pass", 5, "şifre parola üretici güvenlik", true)
    )
    private val cats = listOf("HEPSİ", "ZAMAN", "ÖLÇÜ", "MÜZİK", "SİSTEM", "ÜRET")

    private val tick = object : Runnable {
        override fun run() {
            val c = Calendar.getInstance()
            val sec = c.get(Calendar.SECOND)
            hm?.text = ToolUi.p2(c.get(Calendar.HOUR_OF_DAY).toLong()) + ":" + ToolUi.p2(c.get(Calendar.MINUTE).toLong())
            ss?.text = ToolUi.p2(sec.toLong())
            secDots?.let { it.colors = IntArray(60) { i -> if (i < sec) Ui.WHITE else if (i == sec) Ui.RED else Ui.OFF }; it.invalidate() }
            h.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        countView = Ui.display(this, "", 64f)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(countView, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(Ui.text(this@ToolsActivity, "", 11f, Ui.GREY).apply {
                val s = SpannableStringBuilder("ARAÇ\n/ ${ToolUi.p2(ToolUi.TOOL_COUNT.toLong())}")
                s.setSpan(ForegroundColorSpan(Ui.WHITE), 5, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                text = s
                letterSpacing = 0.16f
                gravity = Gravity.END
                setLineSpacing(0f, 1.3f)
            })
        }

        val search = EditText(this).apply {
            hint = "ARA…"
            setHintTextColor(Ui.GREY)
            setTextColor(Ui.WHITE)
            textSize = 12f
            letterSpacing = 0.14f
            typeface = android.graphics.Typeface.MONOSPACE
            setSingleLine(true)
            background = null
            setPadding(0, 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    query = s.toString().lowercase(TR).trim(); renderGrid()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            background = Ui.rounded(this@ToolsActivity, Color.TRANSPARENT, 22f, Ui.LINE)
            addView(DotIcon(this@ToolsActivity, Ui.ICON_SEARCH, Ui.WHITE, 21))
            addView(search, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(dp(12), 0, 0, 0) })
        }

        catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(searchBox, LinearLayout.LayoutParams(MATCH, dp(44)).apply { setMargins(0, dp(14), 0, 0) })
            addView(HorizontalScrollView(this@ToolsActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(catRow)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(14), 0, 0) })
            addView(ScrollView(this@ToolsActivity).apply {
                isVerticalScrollBarEnabled = false
                addView(grid)
            }, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, dp(14), 0, 0) })
        }
        ToolUi.screen(this, "ARAÇLAR", ToolUi.dateLabel(), body)
        renderCats()
        renderGrid()
    }

    private fun renderCats() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        catRow.removeAllViews()
        cats.forEachIndexed { i, l ->
            val on = i == cat
            catRow.addView(Ui.text(this, "", 10.5f, if (on) Ui.WHITE else Ui.GREY, bold = true).apply {
                text = ToolUi.dotText(l, on)
                gravity = Gravity.CENTER
                letterSpacing = 0.12f
                setPadding(dp(16), 0, dp(16), 0)
                background = Ui.rounded(this@ToolsActivity, Color.TRANSPARENT, 22f, if (on) Ui.WHITE else Ui.LINE)
                Ui.pressAnim(this)
                setOnClickListener { cat = i; renderCats(); renderGrid() }
            }, LinearLayout.LayoutParams(WRAP, dp(44)).apply { setMargins(0, 0, dp(6), 0) })
        }
    }

    private fun renderGrid() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        h.removeCallbacks(tick)
        hm = null; ss = null; secDots = null; bubble = null; tilt = null
        sm.unregisterListener(this)
        grid.removeAllViews()
        val shown = tools.filter { (cat == 0 || it.cat == cat) && (query.isEmpty() || it.keys.contains(query)) }
        countView.text = ToolUi.p2(shown.size.toLong())
        if (shown.isEmpty()) {
            grid.addView(Ui.text(this, "SONUÇ YOK", 11f, Ui.GREY).apply {
                letterSpacing = 0.16f; gravity = Gravity.CENTER; setPadding(0, dp(40), 0, dp(40))
            }, LinearLayout.LayoutParams(MATCH, WRAP))
            return
        }
        var open: LinearLayout? = null
        fun newRow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }.also {
            grid.addView(it, LinearLayout.LayoutParams(MATCH, dp(148)).apply { setMargins(0, 0, 0, dp(10)) })
        }
        for (t in shown) {
            val v = tileFor(t.id)
            if (t.span2) {
                newRow().addView(v, LinearLayout.LayoutParams(MATCH, MATCH))
            } else {
                val row = open
                if (row == null) {
                    val r = newRow()
                    r.addView(v, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(0, 0, dp(5), 0) })
                    r.addView(View(this).apply { tag = "ph" }, LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(5), 0, 0, 0) })
                    open = r
                } else {
                    row.removeViewAt(row.childCount - 1)
                    row.addView(v, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(dp(5), 0, 0, 0) })
                    open = null
                }
            }
        }
        h.post(tick)
        if (bubble != null) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun tileFor(id: String): View {
        fun dp(v: Int) = ToolUi.dp(this, v)
        fun box(label: String, cls: Class<*>, bottom: LinearLayout.() -> Unit, top: (LinearLayout.() -> Unit)? = null): View {
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                if (top != null) {
                    addView(LinearLayout(this@ToolsActivity).apply { orientation = LinearLayout.VERTICAL; top() })
                } else addView(Ui.text(this@ToolsActivity, label, 10f, Ui.GREY).apply { letterSpacing = 0.18f })
                addView(View(this@ToolsActivity), LinearLayout.LayoutParams(1, 0, 1f))
                bottom()
            }
            return FrameLayout(this).apply {
                background = Ui.rounded(this@ToolsActivity, Ui.SURFACE, 28f, Ui.BORDER)
                addView(inner, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(Ui.text(this@ToolsActivity, "↗", 12f, Ui.PROG), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(12), dp(14), 0) })
                Ui.pressAnim(this)
                setOnClickListener { startActivity(Intent(this@ToolsActivity, cls)) }
            }
        }
        return when (id) {
            "clock" -> {
                val city = TimeZone.getDefault().id.substringAfter('/').replace('_', ' ').uppercase(TR)
                val hmv = Ui.display(this, "", 56f)
                val ssv = Ui.display(this, "", 20f, Ui.RED)
                val dots = DotsView(this, 60, 1, 3f, 3f)
                hm = hmv; ss = ssv; secDots = dots
                box("SAAT · $city", ClockActivity::class.java, {
                    addView(LinearLayout(this@ToolsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.BOTTOM
                        addView(hmv)
                        addView(ssv, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(8), 0, 0, 0) })
                    })
                    addView(dots, LinearLayout.LayoutParams(MATCH, dp(3)).apply { setMargins(0, dp(8), 0, 0) })
                })
            }
            "alarm" -> {
                val next = AlarmStore.next(this)
                box("", AlarmActivity::class.java, {
                    addView(Ui.display(this@ToolsActivity, next?.first ?: "--:--", 36f))
                    addView(Ui.text(this@ToolsActivity, "ALARM · " + (next?.second ?: "YOK"), 10f, Ui.GREY).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(8), 0, 0) })
                }, top = { addView(DotIcon(this@ToolsActivity, ToolUi.ICON_BELL7, Ui.WHITE, 42)) })
            }
            "battery" -> {
                val s = BatteryWatch.snapshot(this)
                val health = if (s.fullMah != null && s.designMah != null) (s.fullMah / s.designMah * 100).coerceAtMost(100.0).roundToInt() else null
                box("PİL", BatteryActivity::class.java, {
                    addView(LinearLayout(this@ToolsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        for (i in 0 until 10) addView(View(this@ToolsActivity).apply {
                            background = Ui.rounded(this@ToolsActivity, if (i < Math.round(s.level / 10f)) Ui.WHITE else Ui.OFF, 2f)
                        }, LinearLayout.LayoutParams(0, dp(22), 1f).apply { setMargins(0, 0, if (i < 9) dp(3) else 0, 0) })
                    })
                    addView(LinearLayout(this@ToolsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.BOTTOM
                        addView(LinearLayout(this@ToolsActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.BOTTOM
                            addView(Ui.display(this@ToolsActivity, s.level.toString(), 30f))
                            addView(Ui.display(this@ToolsActivity, "%", 12f, Ui.GREY))
                        }, LinearLayout.LayoutParams(0, WRAP, 1f))
                        if (health != null) addView(Ui.text(this@ToolsActivity, "", 10f, Ui.GREY).apply {
                            val sp = SpannableStringBuilder("SAĞLIK $health")
                            sp.setSpan(ForegroundColorSpan(Ui.WHITE), 7, sp.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            text = sp; letterSpacing = 0.1f
                        })
                    }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(8), 0, 0) })
                })
            }
            "calc" -> box("HESAP", CalcActivity::class.java, {
                addView(KeyDots(this@ToolsActivity, 14), LinearLayout.LayoutParams(MATCH, dp(14 * 3 + 6 * 2)))
                addView(Ui.display(this@ToolsActivity, "0", 18f).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(MATCH, WRAP))
            })
            "timer" -> box("SAYAÇ · ODAK", TimerActivity::class.java, {
                addView(Ui.display(this@ToolsActivity, "05:00", 34f))
                addView(View(this@ToolsActivity).apply { background = Ui.rounded(this@ToolsActivity, Ui.OFF, 2f) }, LinearLayout.LayoutParams(MATCH, dp(3)).apply { setMargins(0, dp(10), 0, 0) })
            })
            "stopwatch" -> box("KRONOMETRE", StopwatchActivity::class.java, {
                addView(Ui.display(this@ToolsActivity, "00:00.0", 30f))
                addView(Ui.text(this@ToolsActivity, "HAZIR", 10f, Ui.GREY).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(8), 0, 0) })
            })
            "level" -> {
                val b = MiniBubble(this)
                val t = Ui.text(this, "—", 10f, Ui.GREY).apply { letterSpacing = 0.12f }
                bubble = b; tilt = t
                box("SU TERAZİSİ", LevelActivity::class.java, {
                    addView(b, LinearLayout.LayoutParams(dp(9 * 12), dp(9 * 12)).apply { gravity = Gravity.CENTER_HORIZONTAL })
                    addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
                })
            }
            "metro" -> box("METRONOM · MÜZİK", MetronomeActivity::class.java, {
                addView(LinearLayout(this@ToolsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    addView(LinearLayout(this@ToolsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.BOTTOM
                        addView(Ui.display(this@ToolsActivity, "120", 48f))
                        addView(Ui.text(this@ToolsActivity, "BPM", 11f, Ui.GREY).apply { letterSpacing = 0.14f; setPadding(dp(8), 0, 0, 0) })
                    }, LinearLayout.LayoutParams(0, WRAP, 1f))
                    for (i in 0 until 4) addView(View(this@ToolsActivity).apply {
                        background = Ui.rounded(this@ToolsActivity, if (i == 0) Ui.RED else Ui.LINE, 8f)
                    }, LinearLayout.LayoutParams(dp(if (i == 0) 14 else 8), dp(if (i == 0) 14 else 8)).apply { setMargins(dp(12), 0, 0, dp(4)) })
                })
                addView(Ui.text(this@ToolsActivity, "4/4 · ALLEGRO", 10f, Ui.GREY).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
            })
            else -> {
                val pw = PasswordActivity.sample(12)
                box("ŞİFRE ÜRETİCİ", PasswordActivity::class.java, {
                    addView(Ui.text(this@ToolsActivity, pw, 22f, Ui.WHITE, bold = true).apply { letterSpacing = 0.18f; maxLines = 1 })
                    addView(LinearLayout(this@ToolsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(LinearLayout(this@ToolsActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            for (i in 0 until 5) addView(View(this@ToolsActivity).apply {
                                background = Ui.rounded(this@ToolsActivity, if (i < 4) Ui.WHITE else Ui.BORDER2, 2f)
                            }, LinearLayout.LayoutParams(dp(18), dp(3)).apply { setMargins(0, 0, dp(4), 0) })
                        }, LinearLayout.LayoutParams(0, WRAP, 1f))
                        addView(Ui.text(this@ToolsActivity, "12 KRK · GÜÇLÜ", 10f, Ui.GREY).apply { letterSpacing = 0.14f })
                    }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(10), 0, 0) })
                })
            }
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val ax = e.values[0]; val ay = e.values[1]; val az = e.values[2]
        val tx = Math.toDegrees(Math.atan2(ax.toDouble(), Math.sqrt((ay * ay + az * az).toDouble()))).toFloat()
        val ty = -Math.toDegrees(Math.atan2(ay.toDouble(), Math.sqrt((ax * ax + az * az).toDouble()))).toFloat()
        bubble?.let { it.bx = tx * 0.4f; it.by = ty * 0.4f; it.invalidate() }
        val dist = hypot(tx, ty)
        tilt?.text = if (dist < 0.5f) "● DÜZ" else String.format(Locale.US, "%.1f", dist * 0.6f).replace('.', ',') + "° EĞİM"
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onResume() {
        super.onResume()
        h.post(tick)
        if (bubble != null) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
        sm.unregisterListener(this)
    }
}

/** 9×9 mini baloncuk alanı (Araçlar sayfasındaki Su terazisi kartı). */
private class MiniBubble(ctx: Context) : View(ctx) {
    var bx = 0f
    var by = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val cell = 12 * d
        for (r in -4..4) for (col in -4..4) {
            val dist = hypot(col.toFloat(), r.toFloat())
            val bd = hypot(col - bx, r - by)
            val color = when {
                bd < 1.35f -> Ui.WHITE
                dist > 3.4f && dist < 4.4f -> Ui.LINE
                dist < 0.6f -> Ui.BORDER2
                else -> 0
            }
            if (color == 0) continue
            paint.color = color
            c.drawCircle((col + 4 + 0.5f) * cell, (r + 4 + 0.5f) * cell, 4.5f * d, paint)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Şifre üretici (tasarım 20)
// ---------------------------------------------------------------------------------------------

/** 33 noktalı uzunluk ölçeği: dokunup sürükleyerek 8–40 arası seçilir. */
private class DotScale(ctx: Context, private val onValue: (Int) -> Unit) : View(ctx) {
    var value = 16
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (30 * d).toInt())

    override fun onDraw(c: Canvas) {
        val slot = width / 33f
        val cur = value - 8
        for (i in 0 until 33) {
            paint.color = if (i < cur) Ui.WHITE else if (i == cur) Ui.RED else Ui.BORDER2
            c.drawCircle((i + 0.5f) * slot, 16 * d, 2 * d, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val f = (e.x / width).coerceIn(0f, 1f)
            onValue((8 + (f * 32).roundToInt()).coerceIn(8, 40))
        }
        return true
    }
}

class PasswordActivity : BaseActivity() {
    companion object {
        private val SETS = listOf("ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghjkmnpqrstuvwxyz", "23456789", "#%&*+=?!@-_")

        fun make(len: Int, on: BooleanArray, rnd: SecureRandom = SecureRandom()): String {
            val active = SETS.filterIndexed { i, _ -> on[i] }
            val pool = active.joinToString("")
            val chars = ArrayList<Char>(len)
            for (s in active) chars.add(s[rnd.nextInt(s.length)])
            while (chars.size < len) chars.add(pool[rnd.nextInt(pool.length)])
            for (i in chars.size - 1 downTo 1) {
                val j = rnd.nextInt(i + 1)
                val t = chars[i]; chars[i] = chars[j]; chars[j] = t
            }
            return chars.joinToString("")
        }

        fun sample(len: Int = 16): String = make(len, booleanArrayOf(true, true, true, true))
    }

    private val h = Handler(Looper.getMainLooper())
    private val rnd = SecureRandom()
    private var len = 16
    private val on = booleanArrayOf(true, true, true, true)
    private val names = listOf("BÜYÜK HARF", "KÜÇÜK HARF", "RAKAM", "SEMBOL")
    private var pw = ""
    private lateinit var pwView: TextView
    private lateinit var strengthText: TextView
    private lateinit var lenText: TextView
    private lateinit var bars: LinearLayout
    private lateinit var scale: DotScale
    private lateinit var copyBtn: TextView
    private val optViews = mutableListOf<LinearLayout>()
    private val optDots = mutableListOf<TextView>()
    private val optLabels = mutableListOf<TextView>()
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        pwView = Ui.text(this, "", 30f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.08f
            setLineSpacing(0f, 1.2f)
            minHeight = dp(132)
        }
        strengthText = Ui.text(this, "", 11f, Ui.WHITE, bold = true).apply { letterSpacing = 0.14f }
        bars = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lenText = Ui.display(this, "", 44f)
        scale = DotScale(this) { v -> if (v != len) { len = v; generate() } }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@PasswordActivity, Ui.SURFACE, 32f, Ui.BORDER)
            setPadding(dp(20), dp(22), dp(20), dp(22))
            addView(LinearLayout(this@PasswordActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(this@PasswordActivity, "ŞİFRE", 10f, Ui.GREY).apply { letterSpacing = 0.18f }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(strengthText)
            })
            addView(pwView, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, dp(18)) })
            addView(bars)
        }

        val lenRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(Ui.text(this@PasswordActivity, "UZUNLUK", 10f, Ui.GREY).apply { letterSpacing = 0.18f }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(lenText)
        }

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 2) {
                val i = r * 2 + c
                val label = Ui.text(this, names[i], 11f, Ui.WHITE, bold = true).apply { letterSpacing = 0.1f }
                val dot = Ui.text(this, "●", 11f, Ui.RED)
                optLabels.add(label); optDots.add(dot)
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, dp(16), 0)
                    addView(label, LinearLayout.LayoutParams(0, WRAP, 1f))
                    addView(dot)
                    Ui.pressAnim(this)
                    setOnClickListener {
                        val after = on.copyOf().also { it[i] = !it[i] }
                        if (after.none { it }) return@setOnClickListener
                        on[i] = !on[i]
                        generate()
                    }
                }
                optViews.add(cell)
                row.addView(cell, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(if (c == 0) 0 else dp(4), 0, if (c == 0) dp(4) else 0, 0) })
            }
            grid.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, if (r == 0) 0 else dp(8), 0, 0) })
        }

        copyBtn = outlineBtn(this, "KOPYALA", true, Ui.WHITE, Ui.WHITE) { copy() }
        val regen = outlineBtn(this, "YENİLE", false, Ui.LINE, Ui.WHITE) { generate() }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(copyBtn, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(0, 0, dp(10), 0) })
            addView(regen, LinearLayout.LayoutParams(dp(120), dp(56)))
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(LinearLayout(this@PasswordActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(lenRow, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(scale, LinearLayout.LayoutParams(MATCH, dp(30)))
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(grid, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(View(this@PasswordActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
        }
        ToolUi.screen(this, "ŞİFRE", "CİHAZDA ÜRETİLİR", ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(body)
        })
        generate()
    }

    /** Yeni şifre üretir; ekranda soldan sağa çözülen karışık karakter animasyonu (tasarım: 40 ms adım). */
    private fun generate() {
        pw = make(len, on, rnd)
        val pool = SETS.filterIndexed { i, _ -> on[i] }.joinToString("")
        h.removeCallbacksAndMessages(null)
        step = 0
        val tick = object : Runnable {
            override fun run() {
                step++
                val reveal = step * 2
                if (reveal >= len) { pwView.text = pw; return }
                val sb = StringBuilder()
                for (i in 0 until len) sb.append(if (i < reveal) pw[i] else pool[rnd.nextInt(pool.length)])
                pwView.text = sb
                h.postDelayed(this, 40)
            }
        }
        pwView.text = pw.take(0)
        h.post(tick)

        lenText.text = len.toString()
        scale.value = len
        scale.invalidate()
        val kinds = on.count { it }
        val score = minOf(5, Math.round(len / 40f * 3f + kinds * 0.6f))
        strengthText.text = when {
            score <= 1 -> "ZAYIF"
            score == 2 -> "ORTA"
            score == 3 -> "İYİ"
            score == 4 -> "GÜÇLÜ"
            else -> "ÇOK GÜÇLÜ"
        }
        bars.removeAllViews()
        for (i in 0 until 5) {
            val c = if (i < score) (if (score <= 2) Ui.RED else Ui.WHITE) else Ui.BORDER2
            bars.addView(View(this).apply { background = Ui.rounded(this@PasswordActivity, c, 2f) },
                LinearLayout.LayoutParams(0, ToolUi.dp(this, 3), 1f).apply { setMargins(0, 0, if (i < 4) ToolUi.dp(this@PasswordActivity, 5) else 0, 0) })
        }
        optViews.forEachIndexed { i, v ->
            v.background = Ui.rounded(this, Color.TRANSPARENT, 28f, if (on[i]) Ui.WHITE else Ui.LINE)
            optLabels[i].setTextColor(if (on[i]) Ui.WHITE else Ui.GREY)
            optDots[i].alpha = if (on[i]) 1f else 0f
        }
    }

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Şifre", pw)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        cm.setPrimaryClip(clip)
        copyBtn.text = ToolUi.dotText("KOPYALANDI")
        h.postDelayed({ copyBtn.text = ToolUi.dotText("KOPYALA") }, 1500)
    }

    override fun onDestroy() {
        super.onDestroy()
        h.removeCallbacksAndMessages(null)
    }
}

// ---------------------------------------------------------------------------------------------
// Saat (tasarım 14a)
// ---------------------------------------------------------------------------------------------

class ClockActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var hm: TextView
    private lateinit var ss: TextView
    private lateinit var dots: DotsView
    private lateinit var infoView: TextView
    private lateinit var list: LinearLayout
    private class City(val name: String, val zone: String)

    private val all = listOf(
        City("LONDRA", "Europe/London"), City("NEW YORK", "America/New_York"),
        City("TOKYO", "Asia/Tokyo"), City("DUBAİ", "Asia/Dubai"),
        City("PARİS", "Europe/Paris"), City("BERLİN", "Europe/Berlin"), City("MOSKOVA", "Europe/Moscow"),
        City("LOS ANGELES", "America/Los_Angeles"), City("SİDNEY", "Australia/Sydney"),
        City("PEKİN", "Asia/Shanghai"), City("SİNGAPUR", "Asia/Singapore"), City("SAO PAULO", "America/Sao_Paulo")
    )
    private val shown = mutableListOf<City>()
    private val timeViews = mutableListOf<TextView>()
    private val noteViews = mutableListOf<TextView>()
    private val dayViews = mutableListOf<View>()
    private val TR = Locale("tr", "TR")

    private val tick = object : Runnable {
        override fun run() {
            update()
            h.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        val ids = getSharedPreferences("essentiallite_prefs", MODE_PRIVATE).getString("clock_cities", null)
        (ids?.split(',')?.mapNotNull { it.toIntOrNull() }?.filter { it in all.indices } ?: listOf(0, 1, 2, 3)).forEach { shown.add(all[it]) }
        hm = Ui.display(this, "", 84f)
        ss = Ui.display(this, "", 28f, Ui.RED)
        dots = DotsView(this, 60, 1, 4f, 4f)
        infoView = Ui.text(this, "", 11f, Ui.GREY).apply { letterSpacing = 0.16f }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val add = outlineBtn(this, "ŞEHİR EKLE", true, Ui.LINE, Ui.WHITE) { pickCity() }.apply { textSize = 11f; letterSpacing = 0.16f; setPadding(dp(20), 0, dp(20), 0) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ToolUi.tabs(this@ClockActivity, 0), LinearLayout.LayoutParams(MATCH, dp(44)))
            addView(LinearLayout(this@ClockActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(this@ClockActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    addView(hm)
                    addView(ss, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(10), 0, 0, dp(2)) })
                })
                addView(dots, LinearLayout.LayoutParams(MATCH, dp(4)).apply { setMargins(0, dp(18), 0, 0) })
                addView(infoView, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(14), 0, 0) })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(View(this@ClockActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(18), 0, 0) })
            addView(ScrollView(this@ClockActivity).apply {
                isVerticalScrollBarEnabled = false
                addView(LinearLayout(this@ClockActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(list)
                    addView(add, LinearLayout.LayoutParams(WRAP, dp(44)).apply { setMargins(0, dp(14), 0, 0) })
                })
            }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        ToolUi.screen(this, "SAAT", ToolUi.dateLabel(), body)
        buildList()
        update()
    }

    private fun save() {
        getSharedPreferences("essentiallite_prefs", MODE_PRIVATE).edit()
            .putString("clock_cities", shown.joinToString(",") { all.indexOf(it).toString() }).apply()
    }

    private fun pickCity() {
        val free = all.filter { it !in shown }
        if (free.isEmpty()) { Store.toast(this, "TÜM ŞEHİRLER EKLİ"); return }
        Sheet.show(this, "ŞEHİR EKLE", null, free.take(6).map { c ->
            Sheet.Action(c.name, Ui.WHITE) { shown.add(c); save(); buildList(); update() }
        } + Sheet.Action("VAZGEÇ", Ui.GREY) {})
    }

    private fun buildList() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        list.removeAllViews()
        timeViews.clear(); noteViews.clear(); dayViews.clear()
        for (c in shown) {
            val time = Ui.display(this, "", 32f)
            val note = Ui.text(this, "", 10f, Ui.GREY).apply { letterSpacing = 0.14f }
            val dot = View(this)
            timeViews.add(time); noteViews.add(note); dayViews.add(dot)
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                addView(LinearLayout(this@ClockActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@ClockActivity, c.name, 13f, Ui.WHITE, bold = true).apply { letterSpacing = 0.14f })
                    addView(note, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(0, 0, dp(12), 0) })
                addView(time)
                setOnLongClickListener {
                    Sheet.show(this@ClockActivity, c.name, null, listOf(
                        Sheet.Action("KALDIR", Ui.RED) { shown.remove(c); save(); buildList(); update() },
                        Sheet.Action("VAZGEÇ", Ui.GREY) {}
                    ))
                    true
                }
            }, LinearLayout.LayoutParams(MATCH, WRAP))
            list.addView(View(this).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)))
        }
    }

    private fun update() {
        val now = Calendar.getInstance()
        val sec = now.get(Calendar.SECOND)
        hm.text = ToolUi.p2(now.get(Calendar.HOUR_OF_DAY).toLong()) + ":" + ToolUi.p2(now.get(Calendar.MINUTE).toLong())
        ss.text = ToolUi.p2(sec.toLong())
        dots.colors = IntArray(60) { i -> if (i < sec) Ui.WHITE else if (i == sec) Ui.RED else Ui.OFF }
        dots.invalidate()

        val tz = TimeZone.getDefault()
        val city = tz.id.substringAfter('/').replace('_', ' ').uppercase(TR)
        val day = SimpleDateFormat("EEEE · d MMMM", TR).format(now.time).uppercase(TR)
        val off = tz.getOffset(now.timeInMillis) / 3_600_000.0
        val utc = "UTC" + (if (off >= 0) "+" else "−") + (if (off % 1.0 == 0.0) abs(off).toInt().toString() else abs(off).toString())
        infoView.text = "$city · $day · $utc"

        shown.forEachIndexed { i, c ->
            val z = TimeZone.getTimeZone(c.zone)
            val cal = Calendar.getInstance(z)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            timeViews[i].text = ToolUi.p2(hour.toLong()) + ":" + ToolUi.p2(cal.get(Calendar.MINUTE).toLong())
            val diffMin = (z.getOffset(now.timeInMillis) - tz.getOffset(now.timeInMillis)) / 60_000
            val sign = if (diffMin >= 0) "+" else "−"
            val a = abs(diffMin)
            val rel = if (a % 60 == 0) "$sign${a / 60} SA" else "$sign${a / 60},${a % 60} SA"
            val localDay = now.get(Calendar.DAY_OF_YEAR) + now.get(Calendar.YEAR) * 366
            val cityDay = cal.get(Calendar.DAY_OF_YEAR) + cal.get(Calendar.YEAR) * 366
            val dayText = when {
                cityDay > localDay -> if (hour < 12) "YARIN SABAH" else "YARIN"
                cityDay < localDay -> "DÜN"
                else -> "AYNI GÜN"
            }
            noteViews[i].text = "$dayText · $rel"
            dayViews[i].background = Ui.rounded(this, if (hour in 7..18) Ui.WHITE else Ui.LINE, 4f)
        }
    }

    override fun onResume() {
        super.onResume()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
    }
}

// ---------------------------------------------------------------------------------------------
// Kronometre (tasarım 14c)
// ---------------------------------------------------------------------------------------------

/** 60 noktalı saniye halkası (300 dp alan, 140 dp yarıçap). */
private class StopRing(ctx: Context) : View(ctx) {
    var sec = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((300 * d).toInt(), (300 * d).toInt())

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        for (i in 0 until 60) {
            val a = Math.PI * 2 * i / 60 - Math.PI / 2
            val head = i == sec
            val sz = if (head) 8f else if (i % 5 == 0) 6f else 4f
            paint.color = if (head) Ui.RED else if (i < sec) Ui.WHITE else Ui.BORDER2
            canvas.drawCircle(cx + (140 * d * Math.cos(a)).toFloat(), cy + (140 * d * Math.sin(a)).toFloat(), sz / 2 * d, paint)
        }
    }
}

class StopwatchActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private var t0 = 0L
    private var acc = 0L
    private val laps = mutableListOf<Pair<Long, Long>>() // (tur süresi, toplam)
    private lateinit var ring: StopRing
    private lateinit var mainView: TextView
    private lateinit var csView: TextView
    private lateinit var leftBtn: TextView
    private lateinit var centerBtn: FrameLayout
    private lateinit var lapCount: TextView
    private lateinit var lapsBox: LinearLayout
    private lateinit var stateView: TextView

    private val tick = object : Runnable {
        override fun run() {
            update()
            if (running) h.postDelayed(this, 40)
        }
    }

    private fun elapsed() = if (running) acc + SystemClock.elapsedRealtime() - t0 else acc

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        ring = StopRing(this)
        mainView = Ui.display(this, "00:00", 48f).apply { gravity = Gravity.CENTER_HORIZONTAL }
        csView = Ui.display(this, ".00", 21f, Ui.RED).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val face = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(dp(300), dp(300)))
            addView(LinearLayout(this@StopwatchActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(mainView, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(csView, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(12), 0, 0) })
            }, FrameLayout.LayoutParams(dp(300), dp(300)))
        }

        leftBtn = Ui.text(this, "", 11f, Ui.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            background = Ui.rounded(this@StopwatchActivity, Color.TRANSPARENT, 44f, Ui.LINE)
            Ui.pressAnim(this)
            setOnClickListener { lapOrReset() }
        }
        centerBtn = FrameLayout(this).apply {
            contentDescription = "Başlat / durdur"
            Ui.pressAnim(this)
            setOnClickListener { toggle() }
        }
        lapCount = Ui.display(this, "00", 24f)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(leftBtn, LinearLayout.LayoutParams(dp(88), dp(88)))
            addView(View(this@StopwatchActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(centerBtn, LinearLayout.LayoutParams(dp(104), dp(104)))
            addView(View(this@StopwatchActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(LinearLayout(this@StopwatchActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(Ui.text(this@StopwatchActivity, "TUR", 10f, Ui.GREY).apply { letterSpacing = 0.14f })
                addView(lapCount)
            }, LinearLayout.LayoutParams(dp(88), WRAP))
        }
        lapsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ToolUi.tabs(this@StopwatchActivity, 2), LinearLayout.LayoutParams(MATCH, dp(44)))
            addView(face, LinearLayout.LayoutParams(dp(300), dp(300)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(18), 0, 0) })
            addView(controls, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(4), dp(18), dp(4), 0) })
            addView(View(this@StopwatchActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(18), 0, 0) })
            addView(ScrollView(this@StopwatchActivity).apply {
                isVerticalScrollBarEnabled = false
                addView(lapsBox)
            }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        stateView = ToolUi.screen(this, "KRONOMETRE", "HAZIR", body)
        update()
        renderControls()
        renderLaps()
    }

    private fun toggle() {
        if (running) {
            acc = elapsed()
            running = false
            h.removeCallbacks(tick)
        } else {
            t0 = SystemClock.elapsedRealtime()
            running = true
            h.post(tick)
        }
        update()
        renderControls()
    }

    private fun lapOrReset() {
        if (running) {
            val total = elapsed()
            val last = laps.lastOrNull()?.second ?: 0L
            laps.add((total - last) to total)
        } else {
            acc = 0
            laps.clear()
        }
        update()
        renderControls()
        renderLaps()
    }

    private fun fmt(ms: Long) = ToolUi.p2(ms / 60000) + ":" + ToolUi.p2(ms / 1000 % 60) + "." + ToolUi.p2(ms / 10 % 100)

    private fun update() {
        val e = elapsed()
        val sec = (e / 1000 % 60).toInt()
        ring.sec = sec
        ring.invalidate()
        mainView.text = ToolUi.p2(e / 60000) + ":" + ToolUi.p2(sec.toLong())
        csView.text = "." + ToolUi.p2(e % 1000 / 10)
        stateView.text = if (running) "ÇALIŞIYOR" else if (e > 0) "DURDU" else "HAZIR"
    }

    private fun renderControls() {
        leftBtn.text = if (running) "TUR" else "SIFIRLA"
        leftBtn.isEnabled = running || elapsed() > 0
        leftBtn.alpha = if (leftBtn.isEnabled) 1f else 0.35f
        centerBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke((1.5f * resources.displayMetrics.density).toInt(), if (running) Ui.RED else Ui.WHITE)
        }
        centerBtn.removeAllViews()
        centerBtn.addView(
            DotIcon(this, if (running) ToolUi.ICON_PAUSE else Ui.ICON_PLAY, if (running) Ui.RED else Ui.WHITE, 70),
            FrameLayout.LayoutParams(ToolUi.dp(this, 70), ToolUi.dp(this, 70), Gravity.CENTER)
        )
        lapCount.text = ToolUi.p2(laps.size.toLong())
    }

    private fun renderLaps() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        lapsBox.removeAllViews()
        if (laps.isEmpty()) {
            lapsBox.addView(Ui.text(this, "TUR YOK · ÇALIŞIRKEN TUR'A BAS", 10f, Ui.GREY).apply {
                letterSpacing = 0.16f
                setPadding(0, dp(18), 0, 0)
            })
            return
        }
        val splits = laps.map { it.first }
        val fast = splits.minOrNull()
        val slow = splits.maxOrNull()
        for (i in laps.indices.reversed()) {
            val (split, total) = laps[i]
            val mark = if (laps.size < 3) "" else if (split == fast) "EN HIZLI" else if (split == slow) "EN YAVAŞ" else ""
            lapsBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(11), 0, dp(11))
                addView(Ui.text(this@StopwatchActivity, ToolUi.p2((i + 1).toLong()), 12f, Ui.GREY).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(dp(56), WRAP))
                addView(Ui.text(this@StopwatchActivity, mark, 12f, if (mark == "EN HIZLI") Ui.WHITE else Ui.RED).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.text(this@StopwatchActivity, "+" + fmt(split), 12f, Ui.GREY).apply { letterSpacing = 0.1f; gravity = Gravity.END }, LinearLayout.LayoutParams(dp(96), WRAP))
                addView(Ui.text(this@StopwatchActivity, fmt(total), 12f, Ui.WHITE).apply { letterSpacing = 0.1f; gravity = Gravity.END }, LinearLayout.LayoutParams(dp(92), WRAP))
            })
            lapsBox.addView(View(this).apply { setBackgroundColor(Ui.OFF) }, LinearLayout.LayoutParams(MATCH, dp(1)))
        }
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
// Hesap makinesi, basit (tasarım 15a)
// ---------------------------------------------------------------------------------------------

class CalcActivity : BaseActivity() {
    private var cur = "0"
    private var acc: Double? = null
    private var op: Char? = null
    private var fresh = false
    private var expr = ""
    private lateinit var exprView: TextView
    private lateinit var dispView: TextView
    private lateinit var keysBox: LinearLayout
    private val sym = mapOf('+' to "+", '-' to "−", '*' to "×", '/' to "÷")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        exprView = Ui.text(this, "", 12f, Ui.GREY).apply { letterSpacing = 0.14f; gravity = Gravity.END; minHeight = dp(16) }
        dispView = Ui.display(this, "0", 92f).apply { gravity = Gravity.END; maxLines = 1 }
        keysBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(4), 0, dp(4), 0)
            addView(exprView, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(dispView, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(10), 0, 0) })
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(display, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(keysBox, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(16), 0, 0) })
        }
        val right = ToolUi.screen(this, "HESAP", "", body, topGap = 16)
        right.apply {
            val s = SpannableStringBuilder("GELİŞMİŞ →")
            s.setSpan(ForegroundColorSpan(Ui.RED), 8, 9, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = s
            setTextColor(Ui.WHITE)
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            background = Ui.rounded(this@CalcActivity, Color.TRANSPARENT, 22f, Ui.LINE)
            setPadding(dp(18), 0, dp(18), 0)
            minHeight = dp(44)
            setOnClickListener { startActivity(Intent(this@CalcActivity, CalcAdvActivity::class.java)) }
        }
        render()
    }

    private fun calc(a: Double, o: Char, b: Double): Double = when (o) {
        '+' -> a + b
        '-' -> a - b
        '*' -> a * b
        '/' -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    private fun toStr(n: Double): String {
        if (!n.isFinite()) return "HATA"
        val r = Math.rint(n * 1e9) / 1e9
        return BigDecimal.valueOf(r).stripTrailingZeros().toPlainString().replace('.', ',')
    }

    private fun num(s: String): Double = if (s == "HATA") 0.0 else s.replace(',', '.').toDouble()

    private fun group(s: String): String {
        if (s == "HATA") return s
        val p = s.split(',')
        val neg = p[0].startsWith("-")
        val d = p[0].removePrefix("-").replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ".")
        return (if (neg) "-" else "") + d + (if (p.size > 1) "," + p[1] else "")
    }

    private fun put(ch: String) {
        if (cur == "HATA") {
            cur = if (ch == ",") "0," else ch; fresh = false; expr = ""
        } else if (fresh) {
            cur = if (ch == ",") "0," else ch; fresh = false
        } else {
            if (ch == "," && cur.contains(',')) return
            if (cur.replace(",", "").replace("-", "").length >= 12) return
            cur = if (cur == "0" && ch != ",") ch else cur + ch
        }
        render()
    }

    private fun pick(o: Char) {
        if (cur == "HATA") return
        val a = acc
        val p = op
        if (p != null && !fresh && a != null) {
            val r = calc(a, p, num(cur))
            acc = r; cur = toStr(r); op = o; fresh = true; expr = toStr(r) + " " + sym[o]
        } else {
            acc = num(cur); op = o; fresh = true; expr = cur + " " + sym[o]
        }
        render()
    }

    private fun equals() {
        val a = acc ?: return
        val p = op ?: return
        if (cur == "HATA") return
        val r = calc(a, p, num(cur))
        expr = toStr(a) + " " + sym[p] + " " + cur + " ="
        cur = toStr(r); acc = null; op = null; fresh = true
        render()
    }

    private fun clear() { cur = "0"; acc = null; op = null; fresh = false; expr = ""; render() }

    private fun back() {
        if (fresh || cur == "HATA") { clear(); return }
        cur = if (cur.length <= 1 || (cur.length == 2 && cur[0] == '-')) "0" else cur.dropLast(1)
        render()
    }

    private fun sign() {
        if (cur != "0" && cur != "HATA") { cur = if (cur[0] == '-') cur.drop(1) else "-$cur"; render() }
    }

    private fun pct() {
        if (cur != "HATA") { cur = toStr(num(cur) / 100); fresh = true; render() }
    }

    private fun key(label: String, kind: String, active: Boolean = false, onClick: () -> Unit): TextView {
        val border = when {
            active -> Ui.WHITE
            kind == "eq" -> Ui.RED
            kind == "op" -> Ui.PROG
            else -> Ui.BORDER
        }
        val fg = when {
            kind == "eq" -> Ui.RED
            kind == "fn" -> Ui.GREY2
            else -> Ui.WHITE
        }
        return Ui.text(this, label, if (kind == "op" || kind == "eq") 26f else 22f, fg, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(this@CalcActivity, Color.TRANSPARENT, 39f, border)
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }
    }

    private fun render() {
        exprView.text = expr
        val shown = group(cur)
        dispView.text = shown
        dispView.textSize = when {
            shown.length > 12 -> 44f
            shown.length > 9 -> 56f
            shown.length > 7 -> 70f
            else -> 92f
        }
        fun act(o: Char) = op == o && fresh
        val rows = listOf(
            listOf(key("AC", "fn") { clear() }, key("±", "fn") { sign() }, key("%", "fn") { pct() }, key(sym['/']!!, "op", act('/')) { pick('/') }),
            listOf(key("7", "n") { put("7") }, key("8", "n") { put("8") }, key("9", "n") { put("9") }, key(sym['*']!!, "op", act('*')) { pick('*') }),
            listOf(key("4", "n") { put("4") }, key("5", "n") { put("5") }, key("6", "n") { put("6") }, key(sym['-']!!, "op", act('-')) { pick('-') }),
            listOf(key("1", "n") { put("1") }, key("2", "n") { put("2") }, key("3", "n") { put("3") }, key(sym['+']!!, "op", act('+')) { pick('+') }),
            listOf(key("0", "n") { put("0") }, key(",", "n") { put(",") }, key("⌫", "fn") { back() }, key("=", "eq") { equals() })
        )
        keysBox.removeAllViews()
        val d = ToolUi.dp(this, 1)
        for ((ri, r) in rows.withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.forEachIndexed { i, k ->
                row.addView(k, LinearLayout.LayoutParams(0, ToolUi.dp(this, 78), 1f).apply {
                    setMargins(if (i == 0) 0 else 5 * d, 0, if (i == r.size - 1) 0 else 5 * d, 0)
                })
            }
            keysBox.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, if (ri == rows.size - 1) 0 else ToolUi.dp(this@CalcActivity, 10)) })
        }
    }
}
