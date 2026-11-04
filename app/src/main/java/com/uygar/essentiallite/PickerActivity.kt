package com.uygar.essentiallite

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.Calendar

/**
 * Hatırlatıcı tarih ve saat seçici (tasarım 06c): TARİH ve SAAT sekmeleri, hızlı seçimler, ay takvimi,
 * saat ve dakika artırıcıları. Geçmiş zaman seçilemez. KUR'a basınca kayda hatırlatıcı kurulur.
 */
class PickerActivity : BaseActivity() {

    private lateinit var file: File
    private lateinit var body: LinearLayout
    private lateinit var diffView: TextView
    private lateinit var kur: TextView

    private var tab = "date"
    private var vy = 0
    private var vm = 0
    private var sel = Triple(0, 0, 0) // yıl, ay (0-11), gün
    private var hour = 9
    private var minute = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path")
        if (path == null) { finish(); return }
        file = File(path)

        // Varsayılan: bir saat sonrası (dakika 0), yani en yakın uygun zaman.
        val base = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        sel = Triple(base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH))
        hour = base.get(Calendar.HOUR_OF_DAY)
        minute = 0
        vy = sel.first
        vm = sel.second

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        diffView = Ui.text(this, "", 11f, Ui.GREY).apply { letterSpacing = 0.14f }
        kur = Ui.text(this, "KUR", 12f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
            Ui.pressAnim(this)
            setOnClickListener { if (targetMillis() > System.currentTimeMillis()) save() }
        }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@PickerActivity, "İPTAL", 11f, Ui.GREY, bold = true).apply {
                letterSpacing = 0.16f
                setPadding(dp(8), dp(18), dp(16), dp(18))
                setOnClickListener { finish() }
            })
            addView(View(this@PickerActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(kur, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(56)))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(28))
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(12), 0, 0)
            })
        }
        setContentView(root)
        render()
    }

    private fun targetMillis(): Long = Calendar.getInstance().apply {
        set(sel.first, sel.second, sel.third, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun save() {
        val at = targetMillis()
        Reminders.set(this, file, at)
        Store.toast(this, "HATIRLATICI: ${Store.stamp(at)}")
        finish()
    }

    private fun render() {
        body.removeAllViews()
        // Her çizimde yeni bir etiket: önceki satırda kalan görünüm yeniden eklenemez.
        diffView = Ui.text(this, "", 11f, Ui.GREY).apply { letterSpacing = 0.14f }
        val at = targetMillis()
        val diff = (at - System.currentTimeMillis()) / 60_000L
        val ok = diff > 0
        diffView.text = when {
            !ok -> "GEÇMİŞ ZAMAN"
            diff >= 1440 -> "${diff / 1440} GÜN ${diff % 1440 / 60} SA SONRA"
            diff >= 60 -> "${diff / 60} SA ${diff % 60} DK SONRA"
            else -> "$diff DK SONRA"
        }
        diffView.setTextColor(if (ok) Ui.GREY else Ui.RED)
        kur.setTextColor(if (ok) Ui.WHITE else Ui.GREY)
        kur.background = Ui.rounded(this, Color.TRANSPARENT, 28f, if (ok) Ui.WHITE else Ui.BORDER2)

        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.kindLabel(this@PickerActivity, "HATIRLAT"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(diffView)
        })

        val dayNames = arrayOf("PAZ", "PZT", "SAL", "ÇAR", "PER", "CUM", "CMT")
        val weekday = dayNames[Calendar.getInstance().apply { set(sel.first, sel.second, sel.third) }.get(Calendar.DAY_OF_WEEK) - 1]
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(tabBox("TARİH · $weekday", "%02d.%02d".format(sel.third, sel.second + 1), tab == "date") { tab = "date"; render() },
                LinearLayout.LayoutParams(0, dp(92), 1f).apply { setMargins(0, dp(18), dp(4), 0) })
            addView(tabBox("SAAT", "%02d:%02d".format(hour, minute), tab == "time") { tab = "time"; render() },
                LinearLayout.LayoutParams(0, dp(92), 1f).apply { setMargins(dp(4), dp(18), 0, 0) })
        })

        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        quickChip(quick, "BUGÜN 18:00", 0, 18)
        quickChip(quick, "YARIN 09:00", 1, 9)
        quickChip(quick, "HAFTA 09:00", 7, 9)
        body.addView(quick)

        val content = FrameLayout(this)
        content.addView(if (tab == "date") calendarView() else timeView())
        body.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, dp(14), 0, 0) })
    }

    private fun tabBox(label: String, value: String, selected: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(10))
        background = Ui.rounded(this@PickerActivity, Color.BLACK, 24f, if (selected) Ui.WHITE else Ui.BORDER)
        Ui.pressAnim(this)
        setOnClickListener { onClick() }
        addView(Ui.text(this@PickerActivity, label, 10f, Ui.GREY).apply { letterSpacing = 0.14f })
        addView(Ui.display(this@PickerActivity, value, 40f, if (selected) Ui.WHITE else Ui.GREY2))
    }

    /** Hızlı seçim: bugünden [addDays] gün sonra, [hh]:00. Geçmiş zamanlar soluk ve seçilemez. */
    private fun quickChip(row: LinearLayout, label: String, addDays: Int, hh: Int) {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, addDays)
            set(Calendar.HOUR_OF_DAY, hh)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val valid = c.timeInMillis > System.currentTimeMillis()
        row.addView(Ui.text(this, label, 10f, if (valid) Ui.GREY2 else Ui.DIM).apply {
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            background = Ui.rounded(this@PickerActivity, Color.TRANSPARENT, 20f, Ui.LINE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            if (valid) {
                Ui.pressAnim(this)
                setOnClickListener {
                    sel = Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    vy = sel.first
                    vm = sel.second
                    hour = hh
                    minute = 0
                    render()
                }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(8), 0) })
    }

    private fun calendarView(): View {
        val months = arrayOf("OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN", "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK")
        val today = Calendar.getInstance()
        val tY = today.get(Calendar.YEAR)
        val tM = today.get(Calendar.MONTH)
        val tD = today.get(Calendar.DAY_OF_MONTH)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val canPrev = vy > tY || (vy == tY && vm > tM)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@PickerActivity, "‹", 22f, if (canPrev) Ui.WHITE else Ui.DIM).apply {
                setPadding(dp(16), 0, dp(16), 0)
                if (canPrev) setOnClickListener { shiftMonth(-1) }
            })
            addView(Ui.text(this@PickerActivity, "${months[vm]} $vy", 12f, Ui.WHITE, bold = true).apply {
                letterSpacing = 0.2f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@PickerActivity, "›", 22f, Ui.WHITE).apply {
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener { shiftMonth(1) }
            })
        })

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            arrayOf("PZT", "SAL", "ÇAR", "PER", "CUM", "CMT", "PAZ").forEach { d ->
                addView(Ui.text(this@PickerActivity, d, 9f, Ui.GREY).apply {
                    gravity = Gravity.CENTER
                    letterSpacing = 0.06f
                }, LinearLayout.LayoutParams(0, dp(28), 1f))
            }
        })

        val first = Calendar.getInstance().apply { set(vy, vm, 1) }
        val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        var day = 1 - offset
        repeat(6) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(7) {
                val d = day
                val inMonth = d in 1..days
                val past = inMonth && Calendar.getInstance().apply { set(vy, vm, d, 23, 59) }.timeInMillis < System.currentTimeMillis()
                val picked = inMonth && sel.first == vy && sel.second == vm && sel.third == d
                row.addView(FrameLayout(this).apply {
                    if (inMonth) {
                        addView(Ui.text(this@PickerActivity, d.toString(), 13f,
                            if (picked) Color.BLACK else if (past) Ui.DIM else Ui.WHITE, bold = picked).apply {
                            gravity = Gravity.CENTER
                            background = if (picked) Ui.rounded(this@PickerActivity, Ui.WHITE, 19f) else null
                        }, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
                        if (vy == tY && vm == tM && d == tD) {
                            addView(View(this@PickerActivity).apply { background = Ui.rounded(this@PickerActivity, Ui.RED, 2f) },
                                FrameLayout.LayoutParams(dp(4), dp(4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
                        }
                        if (!past) {
                            Ui.pressAnim(this)
                            setOnClickListener {
                                sel = Triple(vy, vm, d)
                                render()
                            }
                        }
                    }
                }, LinearLayout.LayoutParams(0, dp(42), 1f))
                day++
            }
            col.addView(row)
        }
        return col
    }

    private fun shiftMonth(dir: Int) {
        val c = Calendar.getInstance().apply { set(vy, vm, 1); add(Calendar.MONTH, dir) }
        vy = c.get(Calendar.YEAR)
        vm = c.get(Calendar.MONTH)
        render()
    }

    private fun timeView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(stepper(hour) { d -> hour = (hour + d + 24) % 24 })
        addView(Ui.display(this@PickerActivity, ":", 64f, Ui.RED).apply { setPadding(dp(10), 0, dp(10), dp(14)) })
        addView(stepper(minute) { d -> minute = (minute + 5 * d + 60) % 60 })
    }

    /** Değer, yukarı ve aşağı oklarıyla. [onStep] +1 ya da -1 alır. */
    private fun stepper(value: Int, onStep: (Int) -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(arrow("+") { onStep(1); render() })
        addView(Ui.display(this@PickerActivity, "%02d".format(value), 96f, Ui.WHITE).apply { gravity = Gravity.CENTER })
        addView(arrow("−") { onStep(-1); render() })
    }

    private fun arrow(label: String, onClick: () -> Unit): TextView = Ui.text(this, label, 26f, Ui.GREY2, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(6), 0, dp(6))
        Ui.pressAnim(this)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(96), dp(52))
    }
}
