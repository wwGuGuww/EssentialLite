package com.uygar.essentiallite

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.math.BigDecimal
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Araçlar bölümü (tasarım 14–20) için ortak yardımcılar: ekran iskeleti, ikonlar, ana ekran şeridi. */
object ToolUi {
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    val ICON_CLOCK = listOf("0011100", "0100010", "1001001", "1001101", "1000001", "0100010", "0011100")
    val ICON_STOPWATCH = listOf("0011100", "0001000", "0111110", "1001001", "1001101", "1000001", "0111110")
    val ICON_CALC = listOf("0111110", "0100010", "0111110", "0000000", "1010101", "0000000", "1010101")
    val ICON_LEVEL = listOf("0011100", "0100010", "1001001", "1011101", "1001001", "0100010", "0011100")
    val ICON_PAUSE =listOf("0000000", "0110110", "0110110", "0110110", "0110110", "0110110", "0000000")

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun p2(n: Long) = n.toString().padStart(2, '0')

    private val TR = Locale("tr", "TR")

    fun dateLabel(): String = SimpleDateFormat("dd.MM EEE", TR).format(Calendar.getInstance().time).uppercase(TR)

    /** Ortak ekran iskeleti: geri oku, başlık, sağda bilgi yazısı ve gövde. Sağdaki yazıyı döndürür. */
    fun screen(a: Activity, title: String, right: String, body: View): TextView {
        val rightView = Ui.text(a, right, 10f, Ui.GREY).apply { letterSpacing = 0.14f }
        val header = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(a, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(a, 14), 0)
                setOnClickListener { a.finish() }
            })
            addView(Ui.kindLabel(a, title), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(rightView)
        }
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(a, 20), dp(a, 44), dp(a, 20), dp(a, 24))
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, dp(a, 14), 0, 0) })
        }
        a.setContentView(root)
        return rightView
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

    /** Ana ekrandaki "ARAÇLAR" şeridi: başlık, "TÜMÜ" bağlantısı ve dört küçük kısayol. */
    fun homeStrip(a: Activity): View {
        fun open(cls: Class<*>) = a.startActivity(Intent(a, cls))
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(a, "", 10f, Ui.GREY).apply {
                text = Ui.withDot("ARAÇLAR · 6", Ui.RED)
                letterSpacing = 0.18f
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(Ui.text(a, "TÜMÜ →", 10f, Ui.WHITE, bold = true).apply {
                letterSpacing = 0.14f
                setPadding(dp(a, 8), dp(a, 8), 0, dp(a, 8))
                setOnClickListener { open(ToolsActivity::class.java) }
            })
        }
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            val items = listOf(
                Triple("SAAT", ICON_CLOCK, ClockActivity::class.java),
                Triple("KRONO", ICON_STOPWATCH, StopwatchActivity::class.java),
                Triple("HESAP", ICON_CALC, CalcActivity::class.java),
                Triple("ŞİFRE", Ui.ICON_LOCK, PasswordActivity::class.java)
            )
            items.forEachIndexed { i, (label, icon, cls) ->
                addView(mini(a, label, icon) { open(cls) }, LinearLayout.LayoutParams(0, dp(a, 72), 1f).apply {
                    setMargins(if (i == 0) 0 else dp(a, 4), 0, if (i == items.size - 1) 0 else dp(a, 4), 0)
                })
            }
        }
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(a, 4), 0, 0) })
        }
    }

    private fun mini(ctx: Context, label: String, icon: List<String>, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.rounded(ctx, Ui.SURFACE, 22f, Ui.BORDER)
            addView(DotIcon(ctx, icon, Ui.WHITE, 22))
            addView(Ui.text(ctx, label, 9f, Ui.GREY, bold = true).apply {
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                setPadding(0, dp(ctx, 6), 0, 0)
            })
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }
}

/** Araçlar sayfası (tasarım 14). */
class ToolsActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var hm: TextView
    private lateinit var ss: TextView
    private val tick = object : Runnable {
        override fun run() {
            val c = Calendar.getInstance()
            hm.text = ToolUi.p2(c.get(Calendar.HOUR_OF_DAY).toLong()) + ":" + ToolUi.p2(c.get(Calendar.MINUTE).toLong())
            ss.text = ToolUi.p2(c.get(Calendar.SECOND).toLong())
            h.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        hm = Ui.display(this, "", 56f)
        ss = Ui.display(this, "", 22f, Ui.RED)

        fun tile(label: String, bottom: View, cls: Class<*>) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@ToolsActivity, Ui.SURFACE, 28f, Ui.BORDER)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(Ui.text(this@ToolsActivity, label, 10f, Ui.GREY).apply { letterSpacing = 0.18f })
            addView(View(this@ToolsActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(bottom)
            Ui.pressAnim(this)
            setOnClickListener { startActivity(Intent(this@ToolsActivity, cls)) }
        }

        val clockTile = tile("SAAT", LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(hm)
            addView(ss, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, 0, 0) })
        }, ClockActivity::class.java)
        val swTile = tile("KRONOMETRE", DotIcon(this, ToolUi.ICON_STOPWATCH, Ui.WHITE, 32), StopwatchActivity::class.java)
        val calcTile = tile("HESAP", DotIcon(this, ToolUi.ICON_CALC, Ui.WHITE, 32), CalcActivity::class.java)
        val passTile = tile("ŞİFRE ÜRETİCİ", DotIcon(this, Ui.ICON_LOCK, Ui.WHITE, 32), PasswordActivity::class.java)
        val levelTile = tile("SU TERAZİSİ", DotIcon(this, ToolUi.ICON_LEVEL, Ui.WHITE, 32), LevelActivity::class.java)
        val snap = BatteryWatch.snapshot(this)
        val battBottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@ToolsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                for (i in 0 until 10) {
                    addView(View(this@ToolsActivity).apply {
                        background = Ui.rounded(this@ToolsActivity, if (i < Math.round(snap.level / 10f)) Ui.WHITE else Ui.OFF, 2f)
                    }, LinearLayout.LayoutParams(0, dp(22), 1f).apply { setMargins(0, 0, if (i < 9) dp(3) else 0, 0) })
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(this@ToolsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(Ui.display(this@ToolsActivity, snap.level.toString(), 32f))
                addView(Ui.display(this@ToolsActivity, "%", 14f, Ui.GREY))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, 0) })
        }
        val battTile = tile("PİL", battBottom, BatteryActivity::class.java)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@ToolsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(Ui.display(this@ToolsActivity, "06", 56f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Ui.text(this@ToolsActivity, "ARAÇ", 10f, Ui.GREY).apply { letterSpacing = 0.16f })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(14)) })
            addView(clockTile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(148)))
            addView(LinearLayout(this@ToolsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(swTile, LinearLayout.LayoutParams(0, dp(148), 1f).apply { setMargins(0, 0, dp(5), 0) })
                addView(calcTile, LinearLayout.LayoutParams(0, dp(148), 1f).apply { setMargins(dp(5), 0, 0, 0) })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) })
            addView(LinearLayout(this@ToolsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(battTile, LinearLayout.LayoutParams(0, dp(148), 1f).apply { setMargins(0, 0, dp(5), 0) })
                addView(levelTile, LinearLayout.LayoutParams(0, dp(148), 1f).apply { setMargins(dp(5), 0, 0, 0) })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) })
            addView(passTile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(148)).apply { setMargins(0, dp(10), 0, 0) })
        }
        ToolUi.screen(this, "ARAÇLAR", ToolUi.dateLabel(), ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        })
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

/** Şifre üretici (tasarım 20). Cihazda, SecureRandom ile üretilir; hiçbir yere gönderilmez. */
class PasswordActivity : BaseActivity() {
    private val rnd = SecureRandom()
    private var len = 16
    private val on = booleanArrayOf(true, true, true, true)
    private val names = listOf("BÜYÜK HARF", "KÜÇÜK HARF", "RAKAM", "SEMBOL")
    private val sets = listOf("ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghjkmnpqrstuvwxyz", "23456789", "#%&*+=?!@-_")
    private var pw = ""
    private lateinit var pwView: TextView
    private lateinit var strengthText: TextView
    private lateinit var lenText: TextView
    private lateinit var bars: LinearLayout
    private val optViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        pwView = Ui.text(this, "", 22f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.06f
            setLineSpacing(0f, 1.25f)
            minHeight = dp(110)
        }
        strengthText = Ui.text(this, "", 11f, Ui.WHITE, bold = true).apply { letterSpacing = 0.14f }
        bars = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lenText = Ui.display(this, "", 36f)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@PasswordActivity, Ui.SURFACE, 28f, Ui.BORDER)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            addView(LinearLayout(this@PasswordActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Ui.text(this@PasswordActivity, "ŞİFRE", 10f, Ui.GREY).apply { letterSpacing = 0.18f },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(strengthText)
            })
            addView(pwView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(14), 0, dp(14)) })
            addView(bars)
        }

        val seek = SeekBar(this).apply {
            max = 32
            progress = len - 8
            progressTintList = ColorStateList.valueOf(Ui.WHITE)
            thumbTintList = ColorStateList.valueOf(Ui.RED)
            progressBackgroundTintList = ColorStateList.valueOf(Ui.LINE)
            contentDescription = "Uzunluk"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    len = p + 8
                    generate()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val lenRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(Ui.text(this@PasswordActivity, "UZUNLUK", 10f, Ui.GREY).apply { letterSpacing = 0.18f },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(lenText)
        }

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 2) {
                val i = r * 2 + c
                val v = ToolUi.ghost(this, names[i], true) {
                    val after = on.copyOf().also { it[i] = !it[i] }
                    if (after.none { it }) return@ghost
                    on[i] = !on[i]
                    generate()
                }
                optViews.add(v)
                row.addView(v, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    setMargins(if (c == 0) 0 else dp(4), 0, if (c == 0) dp(4) else 0, 0)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, 0) })
        }

        val copy = ToolUi.ghost(this, "KOPYALA", true) { copy() }.apply { setTextColor(Ui.WHITE) }
        val regen = ToolUi.ghost(this, "YENİLE", false) { generate() }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(copy, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(0, 0, dp(8), 0) })
            addView(regen, LinearLayout.LayoutParams(dp(120), dp(54)))
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(lenRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(20), 0, 0) })
            addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
            addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) })
            addView(View(this@PasswordActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(16), 0, 0) })
        }
        ToolUi.screen(this, "ŞİFRE", "CİHAZDA ÜRETİLİR", ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(body)
        })
        generate()
    }

    private fun generate() {
        val active = sets.filterIndexed { i, _ -> on[i] }
        val pool = active.joinToString("")
        val chars = ArrayList<Char>(len)
        // Açık olan her tür en az bir kez geçsin.
        for (s in active) chars.add(s[rnd.nextInt(s.length)])
        while (chars.size < len) chars.add(pool[rnd.nextInt(pool.length)])
        for (i in chars.size - 1 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        pw = chars.joinToString("")
        pwView.text = pw
        lenText.text = len.toString()

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
            val c = if (i < score) (if (score <= 2) Ui.RED else Ui.WHITE) else Ui.OFF
            bars.addView(View(this).apply { background = Ui.rounded(this@PasswordActivity, c, 2f) },
                LinearLayout.LayoutParams(0, ToolUi.dp(this, 3), 1f).apply { setMargins(0, 0, if (i < 4) ToolUi.dp(this@PasswordActivity, 5) else 0, 0) })
        }
        optViews.forEachIndexed { i, v ->
            v.setTextColor(if (on[i]) Ui.WHITE else Ui.GREY)
            v.background = Ui.rounded(this, Color.TRANSPARENT, 28f, if (on[i]) Ui.WHITE else Ui.LINE)
            v.text = if (on[i]) "● ${names[i]}" else names[i]
        }
    }

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Şifre", pw)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        cm.setPrimaryClip(clip)
        Store.toast(this, "KOPYALANDI")
    }
}

/** Saat (tasarım 14a): yerel saat, 60 noktalı saniye şeridi ve dünya saatleri. */
class ClockActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var hm: TextView
    private lateinit var ss: TextView
    private lateinit var dots: DotField
    private lateinit var infoView: TextView
    private class City(val name: String, val zone: String)
    private val cities = listOf(
        City("LONDRA", "Europe/London"), City("NEW YORK", "America/New_York"),
        City("TOKYO", "Asia/Tokyo"), City("DUBAİ", "Asia/Dubai")
    )
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
        hm = Ui.display(this, "", 84f)
        ss = Ui.display(this, "", 28f, Ui.RED)
        dots = DotField(this, 60, 1)
        infoView = Ui.text(this, "", 10f, Ui.GREY).apply { letterSpacing = 0.14f }

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (c in cities) {
            val time = Ui.display(this, "", 32f)
            val note = Ui.text(this, "", 10f, Ui.GREY).apply { letterSpacing = 0.12f }
            val dot = View(this)
            timeViews.add(time); noteViews.add(note); dayViews.add(dot)
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                addView(LinearLayout(this@ClockActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@ClockActivity, c.name, 13f, Ui.WHITE, bold = true).apply { letterSpacing = 0.14f })
                    addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, 0) })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(0, 0, dp(12), 0) })
                addView(time)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            list.addView(View(this).apply { setBackgroundColor(Ui.BORDER) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@ClockActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(hm)
                addView(ss, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, 0, 0) })
            })
            addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply { setMargins(0, dp(14), 0, 0) })
            addView(infoView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(12), 0, dp(18)) })
            addView(View(this@ClockActivity).apply { setBackgroundColor(Ui.BORDER) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            addView(list)
        }
        ToolUi.screen(this, "SAAT", ToolUi.dateLabel(), ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        })
        update()
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
        val utc = "UTC" + (if (off >= 0) "+" else "−") + (if (off % 1.0 == 0.0) Math.abs(off).toInt().toString() else Math.abs(off).toString())
        infoView.text = "$city · $day · $utc"

        cities.forEachIndexed { i, c ->
            val z = TimeZone.getTimeZone(c.zone)
            val cal = Calendar.getInstance(z)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            timeViews[i].text = ToolUi.p2(hour.toLong()) + ":" + ToolUi.p2(cal.get(Calendar.MINUTE).toLong())
            val diffMin = (z.getOffset(now.timeInMillis) - tz.getOffset(now.timeInMillis)) / 60_000
            val sign = if (diffMin >= 0) "+" else "−"
            val a = Math.abs(diffMin)
            val rel = if (a % 60 == 0) "$sign${a / 60} SA" else "$sign${a / 60},${a % 60} SA"
            val localDay = now.get(Calendar.DAY_OF_YEAR) + now.get(Calendar.YEAR) * 366
            val cityDay = cal.get(Calendar.DAY_OF_YEAR) + cal.get(Calendar.YEAR) * 366
            val dayText = when {
                cityDay > localDay -> "YARIN"
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

/** 60 noktalı saniye halkası. [sec] dolu noktaları, geçerli saniye kırmızı baş noktayı belirler. */
private class StopRing(ctx: Context) : View(ctx) {
    var sec = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val rad = minOf(cx, cy) - ToolUi.dp(context, 6)
        for (i in 0 until 60) {
            val a = Math.PI * 2 * i / 60 - Math.PI / 2
            val head = i == sec
            val r = ToolUi.dp(context, if (head) 4 else if (i % 5 == 0) 3 else 2) .toFloat()
            paint.color = if (head) Ui.RED else if (i < sec) Ui.WHITE else Ui.GRID
            canvas.drawCircle(cx + (rad * Math.cos(a)).toFloat(), cy + (rad * Math.sin(a)).toFloat(), r, paint)
        }
    }
}

/** Kronometre (tasarım 14c): başlat/durdur, tur, sıfırla. */
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
        mainView = Ui.display(this, "00:00", 46f).apply { gravity = Gravity.CENTER_HORIZONTAL }
        csView = Ui.display(this, ".00", 22f, Ui.RED).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(mainView)
            addView(csView)
        }
        val face = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(center, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        leftBtn = Ui.text(this, "", 11f, Ui.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            background = Ui.rounded(this@StopwatchActivity, Color.TRANSPARENT, 60f, Ui.LINE)
            Ui.pressAnim(this)
            setOnClickListener { lapOrReset() }
        }
        centerBtn = FrameLayout(this).apply {
            contentDescription = "Başlat / durdur"
            Ui.pressAnim(this)
            setOnClickListener { toggle() }
        }
        lapCount = Ui.display(this, "00", 26f)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(leftBtn, LinearLayout.LayoutParams(dp(80), dp(80)))
            addView(View(this@StopwatchActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(centerBtn, LinearLayout.LayoutParams(dp(96), dp(96)))
            addView(View(this@StopwatchActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(LinearLayout(this@StopwatchActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(Ui.text(this@StopwatchActivity, "TUR", 10f, Ui.GREY).apply { letterSpacing = 0.14f })
                addView(lapCount)
            }, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        lapsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(face, LinearLayout.LayoutParams(dp(260), dp(260)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(16), 0, dp(12)) })
            addView(View(this@StopwatchActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            addView(ScrollView(this@StopwatchActivity).apply {
                isVerticalScrollBarEnabled = false
                addView(lapsBox)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
        leftBtn.alpha = if (running || elapsed() > 0) 1f else 0.35f
        leftBtn.isEnabled = running || elapsed() > 0
        centerBtn.background = Ui.rounded(this, Color.TRANSPARENT, 60f, if (running) Ui.RED else Ui.WHITE)
        centerBtn.removeAllViews()
        centerBtn.addView(
            DotIcon(this, if (running) ToolUi.ICON_PAUSE else Ui.ICON_PLAY, if (running) Ui.RED else Ui.WHITE, 40),
            FrameLayout.LayoutParams(ToolUi.dp(this, 40), ToolUi.dp(this, 40), Gravity.CENTER)
        )
        lapCount.text = ToolUi.p2(laps.size.toLong())
    }

    private fun renderLaps() {
        lapsBox.removeAllViews()
        if (laps.isEmpty()) {
            lapsBox.addView(Ui.text(this, "TUR YOK · ÇALIŞIRKEN TUR'A BAS", 10f, Ui.GREY).apply {
                letterSpacing = 0.14f
                setPadding(0, ToolUi.dp(this@StopwatchActivity, 16), 0, 0)
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
                setPadding(0, ToolUi.dp(this@StopwatchActivity, 11), 0, ToolUi.dp(this@StopwatchActivity, 11))
                addView(Ui.text(this@StopwatchActivity, ToolUi.p2((i + 1).toLong()), 12f, Ui.GREY), LinearLayout.LayoutParams(ToolUi.dp(this@StopwatchActivity, 40), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(Ui.text(this@StopwatchActivity, mark, 10f, if (mark == "EN HIZLI") Ui.WHITE else Ui.RED).apply { letterSpacing = 0.1f },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Ui.text(this@StopwatchActivity, "+" + fmt(split), 12f, Ui.GREY).apply { setPadding(0, 0, ToolUi.dp(this@StopwatchActivity, 14), 0) })
                addView(Ui.text(this@StopwatchActivity, fmt(total), 12f, Ui.WHITE))
            })
            lapsBox.addView(View(this).apply { setBackgroundColor(Ui.BORDER) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolUi.dp(this, 1)))
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

/** Basit hesap makinesi (tasarım 15a). */
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
        exprView = Ui.text(this, "", 12f, Ui.GREY).apply { letterSpacing = 0.14f; gravity = Gravity.END }
        dispView = Ui.display(this, "0", 62f).apply {
            gravity = Gravity.END
            maxLines = 1
        }
        keysBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(4), 0, dp(4), dp(8))
            addView(exprView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(dispView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(display, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(keysBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        ToolUi.screen(this, "HESAP", "BASİT", body)
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

    private fun clear() {
        cur = "0"; acc = null; op = null; fresh = false; expr = ""
        render()
    }

    private fun back() {
        if (fresh || cur == "HATA") { clear(); return }
        cur = if (cur.length <= 1 || (cur.length == 2 && cur[0] == '-')) "0" else cur.dropLast(1)
        render()
    }

    private fun sign() {
        if (cur != "0" && cur != "HATA") {
            cur = if (cur[0] == '-') cur.drop(1) else "-$cur"
            render()
        }
    }

    private fun pct() {
        if (cur != "HATA") {
            cur = toStr(num(cur) / 100); fresh = true
            render()
        }
    }

    private fun key(label: String, kind: String, active: Boolean = false, onClick: () -> Unit): TextView {
        val border = when {
            active -> Ui.WHITE
            kind == "eq" -> Ui.RED
            kind == "op" -> Ui.PROG
            else -> Ui.BORDER2
        }
        val fg = when {
            active -> Ui.WHITE
            kind == "eq" -> Ui.RED
            kind == "fn" -> Ui.GREY2
            else -> Ui.WHITE
        }
        return Ui.text(this, label, if (kind == "op" || kind == "eq") 22f else 18f, fg, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(this@CalcActivity, Color.TRANSPARENT, 32f, border)
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }
    }

    private fun render() {
        exprView.text = expr
        val shown = group(cur)
        dispView.text = shown
        dispView.textSize = when {
            shown.length > 12 -> 30f
            shown.length > 9 -> 38f
            shown.length > 7 -> 48f
            else -> 62f
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
        for (r in rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.forEachIndexed { i, k ->
                row.addView(k, LinearLayout.LayoutParams(0, ToolUi.dp(this, 64), 1f).apply {
                    setMargins(if (i == 0) 0 else 5 * d, 0, if (i == r.size - 1) 0 else 5 * d, 0)
                })
            }
            keysBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, ToolUi.dp(this@CalcActivity, 10)) })
        }
    }
}
