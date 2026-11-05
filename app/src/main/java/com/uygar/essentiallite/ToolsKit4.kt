package com.uygar.essentiallite

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Alarm kayıtları ve zamanlama (tasarım 14b). Günler Pazartesi'den Pazar'a doğru. */
object AlarmStore {
    class A(val id: Long, var m: Int, var label: String, var days: BooleanArray, var on: Boolean, var six: Boolean)

    private const val CHANNEL = "alarm"
    private const val PREF = "essentiallite_prefs"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<A> = runCatching {
        val arr = JSONArray(sp(ctx).getString("alarms", "[]"))
        MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val d = o.getJSONArray("days")
            A(o.getLong("id"), o.getInt("m"), o.getString("label"), BooleanArray(7) { d.getInt(it) == 1 }, o.getBoolean("on"), o.getBoolean("six"))
        }
    }.getOrDefault(mutableListOf())

    fun save(ctx: Context, list: List<A>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("m", a.m).put("label", a.label)
                .put("days", JSONArray(a.days.map { if (it) 1 else 0 })).put("on", a.on).put("six", a.six))
        }
        sp(ctx).edit().putString("alarms", arr.toString()).apply()
    }

    /** Verilen alarmın bir sonraki çalma anı (ms). Gün seçilmemişse bir sonraki ilk uygun saat. */
    fun nextTrigger(a: A, from: Long = System.currentTimeMillis()): Long {
        for (off in 0..8) {
            val c = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, off)
                set(Calendar.HOUR_OF_DAY, a.m / 60); set(Calendar.MINUTE, a.m % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (c.timeInMillis > from && (a.days.none { it } || a.days[idx])) return c.timeInMillis
        }
        return from + 24 * 3600_000L
    }

    /** Açık alarmlar arasından en yakını: ("HH:MM", "n SA m DK SONRA"). */
    fun next(ctx: Context): Pair<String, String>? {
        val now = System.currentTimeMillis()
        val best = load(ctx).filter { it.on }.map { it to nextTrigger(it, now) }.minByOrNull { it.second } ?: return null
        val min = ((best.second - now) / 60000).toInt()
        val t = ToolUi.p2((best.first.m / 60).toLong()) + ":" + ToolUi.p2((best.first.m % 60).toLong())
        return t to ("${min / 60} SA ${min % 60} DK SONRA")
    }

    private fun code(id: Long, k: Int) = ((id % 100000L).toInt() * 10 + k)

    private fun pi(ctx: Context, a: A, k: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, code(a.id, k),
        Intent(ctx, AlarmReceiver::class.java).putExtra("id", a.id).putExtra("k", k).putExtra("m", a.m).putExtra("label", a.label),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancelCodes(ctx: Context, id: Long, ks: IntRange) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dummy = A(id, 0, "", BooleanArray(7), false, false)
        for (k in ks) am.cancel(pi(ctx, dummy, k))
    }

    /** Tüm alarmları baştan kurar. */
    fun schedule(ctx: Context) {
        val list = load(ctx)
        list.forEach { setOne(ctx, it, 0..5) }
        val known = sp(ctx).getString("alarm_ids", "") ?: ""
        known.split(',').mapNotNull { it.toLongOrNull() }.filter { id -> list.none { it.id == id } }.forEach { cancelCodes(ctx, it, 0..5) }
        sp(ctx).edit().putString("alarm_ids", list.joinToString(",") { it.id.toString() }).apply()
    }

    /** Tek bir alarmın verilen kodlarını kurar (kapalıysa iptal eder). */
    fun setOne(ctx: Context, a: A, ks: IntRange) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(ctx, 7003, Intent(ctx, AlarmActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val base = nextTrigger(a)
        for (k in ks) {
            if (!a.on || (k > 0 && !a.six)) { am.cancel(pi(ctx, a, k)); continue }
            val at = base + k * 60_000L
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi(ctx, a, k))
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, a, k))
            }
        }
    }

    fun cancelFollowUps(ctx: Context, id: Long) = cancelCodes(ctx, id, 1..5)

    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
        })
    }

    fun notifyRing(ctx: Context, id: Long, m: Int, label: String) {
        channel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dismiss = PendingIntent.getBroadcast(
            ctx, 7004, Intent(ctx, AlarmDismissReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val time = ToolUi.p2((m / 60).toLong()) + ":" + ToolUi.p2((m % 60).toLong())
        runCatching {
            nm.notify(
                id.toInt(),
                b.setSmallIcon(R.drawable.ic_notif).setContentTitle("ALARM").setContentText("$time · $label")
                    .setCategory(Notification.CATEGORY_ALARM).setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(ctx, 7003, Intent(ctx, AlarmActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                    .addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notif), "KAPAT", dismiss).build())
                    .build()
            )
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val id = intent.getLongExtra("id", 0)
        val k = intent.getIntExtra("k", 0)
        AlarmStore.notifyRing(app, id, intent.getIntExtra("m", 0), intent.getStringExtra("label") ?: "")
        val list = AlarmStore.load(app)
        val a = list.firstOrNull { it.id == id } ?: return
        val repeating = a.days.any { it }
        if (k == 0) {
            if (repeating) AlarmStore.setOne(app, a, 0..0) else { a.on = false; AlarmStore.save(app, list) }
        }
        val last = if (a.six) 5 else 0
        if (k == last && repeating && a.on && a.six) AlarmStore.setOne(app, a, 1..5)
    }
}

class AlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0)
        AlarmStore.cancelFollowUps(ctx.applicationContext, id)
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.toInt())
    }
}

class AlarmActivity : BaseActivity() {
    private var alarms = mutableListOf<AlarmStore.A>()
    private lateinit var nextView: TextView
    private lateinit var nextIn: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var overlay: FrameLayout
    private lateinit var panel: LinearLayout
    private var edit: AlarmStore.A? = null
    private var editNew = false
    private val L = listOf("P", "S", "Ç", "P", "C", "C", "P")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        nextView = Ui.display(this, "--:--", 48f)
        nextIn = Ui.text(this, "", 11f, Ui.GREY).apply { letterSpacing = 0.14f }
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val add = outlineBtn(this, "ALARM EKLE", true, Ui.WHITE, Ui.WHITE) { openSheet(null) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ToolUi.tabs(this@AlarmActivity, 1), LinearLayout.LayoutParams(MATCH, dp(44)))
            addView(LinearLayout(this@AlarmActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(this@AlarmActivity, "SONRAKİ", 10f, Ui.GREY).apply { letterSpacing = 0.18f })
                addView(nextView, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(8), 0, 0) })
                addView(nextIn, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(10), 0, 0) })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(View(this@AlarmActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(18), 0, 0) })
            addView(ScrollView(this@AlarmActivity).apply {
                isVerticalScrollBarEnabled = false
                addView(listBox)
            }, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(add, LinearLayout.LayoutParams(MATCH, dp(56)).apply { setMargins(0, dp(18), 0, 0) })
        }
        ToolUi.screen(this, "ALARM", ToolUi.dateLabel(), body)

        // Düzenleme sayfası: koyu perde + alttan çıkan panel.
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                val r = 32 * resources.displayMetrics.density
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), Ui.BORDER)
            }
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        overlay = FrameLayout(this).apply {
            visibility = View.GONE
            addView(View(this@AlarmActivity).apply {
                setBackgroundColor(0xB8000000.toInt())
                setOnClickListener { closeSheet() }
            }, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        }
        (findViewById<ViewGroup>(android.R.id.content)).addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

        alarms = AlarmStore.load(this)
        render()
    }

    private fun fmt(m: Int) = ToolUi.p2((m / 60).toLong()) + ":" + ToolUi.p2((m % 60).toLong())

    private fun persist() {
        AlarmStore.save(this, alarms)
        AlarmStore.schedule(this)
        render()
    }

    private fun render() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val nx = AlarmStore.next(this)
        nextView.text = nx?.first ?: "--:--"
        nextIn.text = nx?.second ?: "AÇIK ALARM YOK"
        listBox.removeAllViews()
        alarms.forEach { a ->
            val sub = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(this@AlarmActivity, a.label, 10f, if (a.on) Ui.WHITE else Ui.PROG).apply { letterSpacing = 0.14f })
                addView(LinearLayout(this@AlarmActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    L.forEachIndexed { j, l ->
                        addView(Ui.text(this@AlarmActivity, l, 10f, if (a.days[j]) (if (a.on) Ui.WHITE else Ui.PROG) else Ui.BORDER2).apply { letterSpacing = 0.14f },
                            LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(5), 0, 0, 0) })
                    }
                }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(10), 0, 0, 0) })
                if (a.six) addView(Ui.text(this@AlarmActivity, "● ×6", 10f, Ui.RED).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(10), 0, 0, 0) })
            }
            listBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                addView(LinearLayout(this@AlarmActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.display(this@AlarmActivity, fmt(a.m), 34f, if (a.on) Ui.WHITE else Ui.PROG))
                    addView(sub, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(8), 0, 0) })
                    setOnClickListener { openSheet(a) }
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(DesignSwitch(this@AlarmActivity, a.on) { on -> a.on = on; persist() })
            }, LinearLayout.LayoutParams(MATCH, WRAP))
            listBox.addView(View(this).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)))
        }
    }

    private fun openSheet(a: AlarmStore.A?) {
        editNew = a == null
        edit = a?.let { AlarmStore.A(it.id, it.m, it.label, it.days.copyOf(), it.on, it.six) }
            ?: AlarmStore.A(System.currentTimeMillis(), 420, "ALARM", booleanArrayOf(true, true, true, true, true, false, false), true, false)
        buildSheet()
        overlay.visibility = View.VISIBLE
        panel.translationY = 2000f
        panel.animate().translationY(0f).setDuration(Ui.motion(this, 360)).start()
    }

    private fun closeSheet() {
        panel.animate().translationY(panel.height.toFloat()).setDuration(Ui.motion(this, 240)).withEndAction { overlay.visibility = View.GONE }.start()
    }

    private fun buildSheet() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val e = edit ?: return
        panel.removeAllViews()
        val title = Ui.text(this, "", 11f, Ui.WHITE, bold = true).apply {
            text = ToolUi.dotText(if (editNew) "YENİ ALARM" else "ALARMI DÜZENLE")
            letterSpacing = 0.18f
        }
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(Ui.text(this@AlarmActivity, "İPTAL", 11f, Ui.GREY).apply {
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { closeSheet() }
            }, LinearLayout.LayoutParams(WRAP, dp(44)))
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        fun stepper(value: String, up: () -> Unit, dn: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            fun arrow(t: String, act: () -> Unit) = Ui.text(this@AlarmActivity, t, 14f, Ui.WHITE).apply {
                gravity = Gravity.CENTER
                background = Ui.rounded(this@AlarmActivity, Color.TRANSPARENT, 22f, Ui.LINE)
                Ui.pressAnim(this)
                setOnClickListener { act() }
            }
            addView(arrow("▲", up), LinearLayout.LayoutParams(dp(72), dp(44)))
            addView(Ui.display(this@AlarmActivity, value, 72f), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, dp(6)) })
            addView(arrow("▼", dn), LinearLayout.LayoutParams(dp(72), dp(44)))
        }
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(stepper(ToolUi.p2((e.m / 60).toLong()), { e.m = (e.m + 60) % 1440; buildSheet() }, { e.m = (e.m - 60 + 1440) % 1440; buildSheet() }))
            addView(Ui.display(this@AlarmActivity, ":", 48f, Ui.RED), LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(10), 0, dp(10), dp(6)) })
            addView(stepper(ToolUi.p2((e.m % 60).toLong()), { e.m = (e.m + 5) % 1440; buildSheet() }, { e.m = (e.m - 5 + 1440) % 1440; buildSheet() }))
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })

        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            L.forEachIndexed { j, l ->
                val on = e.days[j]
                addView(Ui.text(this@AlarmActivity, l, 11f, if (on) Ui.WHITE else Ui.GREY, bold = true).apply {
                    gravity = Gravity.CENTER
                    background = Ui.rounded(this@AlarmActivity, Color.TRANSPARENT, 22f, if (on) Ui.WHITE else Ui.LINE)
                    setOnClickListener { e.days[j] = !e.days[j]; buildSheet() }
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(if (j == 0) 0 else dp(3), 0, if (j == 6) 0 else dp(3), 0) })
            }
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })

        val note = Ui.text(this, if (e.six) fmt(e.m) + " → " + fmt((e.m + 5) % 1440) + " · UYANANA KADAR" else "KAPALI · TEK ALARM", 10f, Ui.GREY).apply { letterSpacing = 0.1f }
        panel.addView(View(this).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(18), 0, 0) })
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(LinearLayout(this@AlarmActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(this@AlarmActivity, "6 ALARM · 1 DK ARAYLA", 12f, Ui.WHITE, bold = true).apply { letterSpacing = 0.12f })
                addView(note, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(DesignSwitch(this@AlarmActivity, e.six) { on -> e.six = on; buildSheet() })
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            if (!editNew) addView(Ui.text(this@AlarmActivity, "SİL", 12f, Ui.RED, bold = true).apply {
                gravity = Gravity.CENTER
                letterSpacing = 0.18f
                setPadding(dp(22), 0, dp(22), 0)
                background = Ui.rounded(this@AlarmActivity, Color.TRANSPARENT, 28f, Ui.LINE)
                setOnClickListener { alarms.removeAll { it.id == e.id }; closeSheet(); persist() }
            }, LinearLayout.LayoutParams(WRAP, dp(56)).apply { setMargins(0, 0, dp(10), 0) })
            addView(outlineBtn(this@AlarmActivity, "KAYDET", false, Ui.WHITE, Ui.WHITE) {
                e.on = true
                val i = alarms.indexOfFirst { it.id == e.id }
                if (i >= 0) alarms[i] = e else alarms.add(e)
                alarms.sortBy { it.m }
                closeSheet()
                persist()
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
    }

    override fun onBackPressed() {
        if (overlay.visibility == View.VISIBLE) closeSheet() else super.onBackPressed()
    }
}
