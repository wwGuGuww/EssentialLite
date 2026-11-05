package com.uygar.essentiallite

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/** Son 30 günün nokta şeridi: noktalar sırayla büyür, bugünün noktası nabız atar. */
private class StripView(ctx: android.content.Context) : View(ctx) {
    var colors = IntArray(30)
    private var reveal = 0f
    private var pulse = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    private var revealAnim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null

    fun play(animated: Boolean) {
        revealAnim?.cancel()
        if (!animated) {
            reveal = 30f + 5f
        } else {
            reveal = 0f
            revealAnim = ValueAnimator.ofFloat(0f, 30f + 5f).apply {
                duration = Ui.motion(context, 120 + 30 * 24 + 380)
                startDelay = Ui.motion(context, 120)
                addUpdateListener { reveal = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        if (pulseAnim == null && Ui.motion(context, 1) > 1) {
            pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2400
                repeatCount = ValueAnimator.INFINITE
                startDelay = 1200
                addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        revealAnim?.cancel()
        pulseAnim?.cancel()
        pulseAnim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val r = 3 * d
        val step = (width - 2 * r) / 29f
        for (i in 0 until 30) {
            val p = (reveal - i).coerceIn(0f, 5f) / 5f
            if (p <= 0f) continue
            val cx = r + step * i
            val cy = height / 2f
            if (i == 29 && pulse > 0f) {
                paint.color = Ui.RED
                paint.alpha = ((1f - pulse) * 150).toInt()
                c.drawCircle(cx, cy, r + 7 * d * pulse, paint)
            }
            paint.color = colors.getOrElse(i) { Ui.OFF }
            c.drawCircle(cx, cy, r * p, paint)
        }
    }
}

/** Gün başlığındaki noktalı çizgi. */
private class DottedLine(ctx: android.content.Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = Ui.LINE }
    private val d = ctx.resources.displayMetrics.density
    override fun onDraw(c: Canvas) {
        var x = d
        while (x < width) {
            c.drawCircle(x, height / 2f, 0.7f * d, paint)
            x += 4 * d
        }
    }
}

class MainActivity : BaseActivity(), Shizuku.OnRequestPermissionResultListener {

    private lateinit var status: TextView
    private lateinit var stats: LinearLayout
    private lateinit var strip: StripView
    private lateinit var search: EditText
    private lateinit var chips: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var filterRow: LinearLayout
    private lateinit var searchRow: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var root: FrameLayout
    private lateinit var dock: LinearLayout
    private var filter = Store.ALL
    private var firstShow = true
    private var cardGen = 0
    private var shownCount = 0
    private var targetCount = 0
    private var countText: TextView? = null
    private val main = Handler(Looper.getMainLooper())
    private val pendingDelete = LinkedHashMap<String, Entry>()
    private var lastDeleted: Entry? = null
    private var toastView: View? = null
    private var commitRunnable: Runnable? = null

    private val thumbCache = LruCache<String, Bitmap>(24)
    private var player: MediaPlayer? = null
    private var playingFile: File? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fdp(v: Float) = v * resources.displayMetrics.density
    private val ease = PathInterpolator(0.2f, 0.9f, 0.2f, 1f)

    /** Tasarımdaki "riseIn / cardIn": aşağıdan yukarı kayarak belirir. */
    private fun rise(v: View, delay: Long, dur: Long, dy: Float) {
        if (Ui.motion(this, 1) <= 1L) return
        v.alpha = 0f
        v.translationY = fdp(dy)
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(dur).setInterpolator(ease).start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.get(this, "onboarded", false)) {
            startActivity(Intent(this, OnboardActivity::class.java))
        }

        status = Ui.text(this, "", 11f, Ui.GREY).apply { letterSpacing = 0.14f }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Gizli giriş: başlığa 4 sn içinde 7 kez dokununca gizli bölüm kapısı açılır. Hiçbir ipucu göstermez.
            addView(Ui.brand(this@MainActivity).apply { setOnClickListener { secretTap() } },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@MainActivity, "AYAR", 11f, Ui.GREY).apply {
                letterSpacing = 0.14f
                setPadding(dp(8), dp(8), dp(16), dp(8))
                setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
            })
            addView(status)
        }

        stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        strip = StripView(this)
        val stripLabels = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.text(this@MainActivity, "SON 30 GÜN", 10f, Ui.GREY).apply { letterSpacing = 0.16f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@MainActivity, "● BUGÜN", 10f, Ui.RED).apply { letterSpacing = 0.16f })
        }
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(stats)
            addView(strip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply { setMargins(0, dp(14), 0, dp(14)) })
            addView(stripLabels)
        }

        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        search = EditText(this).apply {
            hint = "ARA: metin, tarih, tür"
            setHintTextColor(Ui.DIM)
            setTextColor(Ui.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setSingleLine(true)
            background = Ui.rounded(this@MainActivity, Ui.SURFACE, 22f, Ui.LINE)
            setPadding(dp(16), 0, dp(16), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { cardGen++; renderList(false); renderRecent() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        val searchBtn = FrameLayout(this).apply {
            background = Ui.rounded(this@MainActivity, Ui.SURFACE, 22f, Ui.BORDER)
            contentDescription = "Ara"
            addView(DotIcon(this@MainActivity, Ui.ICON_SEARCH, Ui.WHITE, 27), FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER))
            Ui.pressAnim(this)
            setOnClickListener { openSearch() }
        }
        filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(chips, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(-12), 0, 0, 0) })
            addView(searchBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(search, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(Ui.text(this@MainActivity, "İPTAL", 11f, Ui.GREY2, bold = true).apply {
                letterSpacing = 0.14f
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(4), 0)
                setOnClickListener { closeSearch() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
        }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        quickScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(quickRow)
        }
        recentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        renderQuick()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(52), dp(20), dp(150))
            addView(header)
            addView(summary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(20), 0, 0) })
            addView(ToolUi.homeStrip(this@MainActivity), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(18), 0, 0) })
            addView(filterRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(16), 0, 0) })
            addView(quickScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) })
            addView(searchRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(12), 0, 0) })
            addView(recentRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) })
            addView(list)
        }
        if (firstShow) {
            rise(header, 0, Ui.motion(this, 500), 10f)
            rise(summary, 80, Ui.motion(this, 600), 10f)
            rise(filterRow, 160, Ui.motion(this, 600), 10f)
        }

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(this@MainActivity, Ui.SURFACE, 40f, Ui.BORDER2)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, fdp(40f)) }
            }
            addView(dockItem("EKRAN", Ui.ICON_SHOT) { takeShot() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(View(this@MainActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(dockItem("NOT", Ui.ICON_NOTE) { startActivity(Intent(this@MainActivity, NoteActivity::class.java)) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(View(this@MainActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(dockItem("SES", Ui.ICON_WAVE) { startActivity(Intent(this@MainActivity, RecordActivity::class.java)) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }

        scroll = object : ScrollView(this) {
            override fun getTopFadingEdgeStrength() = 0f
        }.apply {
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(150))
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Ui.BG)
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(dock, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(80), Gravity.BOTTOM).apply {
                setMargins(dp(20), 0, dp(20), dp(24))
            })
        }
        setContentView(root)
        if (firstShow) {
            dock.alpha = 0f
            dock.translationY = fdp(48f)
            dock.animate().alpha(1f).translationY(0f).setStartDelay(240).setDuration(Ui.motion(this, 600)).setInterpolator(ease).start()
        }

        Shizuku.addRequestPermissionResultListener(this)
        refreshStatus()
        if (Shizuku.pingBinder() && !Shot.ready()) Shizuku.requestPermission(0)
    }

    private fun dockItem(label: String, icon: List<String>, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(DotIcon(this@MainActivity, icon, Ui.WHITE, 40))
        addView(Ui.text(this@MainActivity, label, 10f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
        Ui.pressAnim(this)
        setOnClickListener { onClick() }
    }

    override fun onResume() {
        super.onResume()
        Ocr.backlog(this)
        pollHandler.removeCallbacks(statusPoll)
        pollHandler.post(statusPoll)
        refreshStatus()
        renderChips()
        renderList(firstShow)
        firstShow = false
    }

    override fun onPause() {
        commitDeletes()
        player?.runCatching { stop(); release() }
        player = null
        playingFile = null
        super.onPause()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) = refreshStatus()

    private fun refreshStatus() {
        status.text = when {
            !Shizuku.pingBinder() -> "SHİZUKU KAPALI"
            Shot.ready() -> "HAZIR"
            else -> "İZİN BEKLENİYOR"
        }
    }

    private fun takeShot() {
        Ui.flashActivity(this)
        Thread {
            val msg = try { Shot.take(this) } catch (e: Throwable) { "HATA" }
            runOnUiThread { Store.toast(this, msg); renderList(false) }
        }.start()
    }

    // --- Arama ---

    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

    private fun openSearch() {
        filterRow.visibility = View.GONE
        searchRow.visibility = View.VISIBLE
        searchRow.pivotX = searchRow.width.toFloat().takeIf { it > 0 } ?: fdp(372f)
        searchRow.alpha = 0f
        searchRow.scaleX = 0.4f
        searchRow.animate().alpha(1f).scaleX(1f).setDuration(Ui.motion(this, 280)).setInterpolator(ease).start()
        search.requestFocus()
        imm().showSoftInput(search, 0)
        renderRecent()
    }

    private fun closeSearch() {
        imm().hideSoftInputFromWindow(search.windowToken, 0)
        rememberQuery(search.text.toString())
        recentRow.visibility = View.GONE
        search.setText("")
        searchRow.visibility = View.GONE
        filterRow.visibility = View.VISIBLE
        cardGen++
        renderList(false)
    }

    // --- Filtre ---

    private fun renderChips() {
        chips.removeAllViews()
        for (k in listOf(Store.ALL, "EKRAN", "NOT", "SES")) {
            val selected = k == filter
            chips.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                val dot = View(this@MainActivity).apply {
                    background = Ui.rounded(this@MainActivity, Ui.RED, 3f)
                    scaleX = if (selected) 1f else 0f
                    scaleY = scaleX
                }
                addView(dot, LinearLayout.LayoutParams(dp(5), dp(5)).apply { setMargins(0, 0, dp(6), 0) })
                addView(Ui.text(this@MainActivity, k, 12f, if (selected) Ui.WHITE else Ui.GREY, bold = true).apply { letterSpacing = 0.14f })
                contentDescription = k
                setOnClickListener {
                    if (filter != k) {
                        filter = k
                        cardGen++
                        renderChips()
                        renderList(false)
                    }
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
        }
    }

    // --- Özet ---

    private fun renderStats(all: List<Entry>, animate: Boolean) {
        stats.removeAllViews()
        val big = Ui.display(this, "000", 58f, if (all.isEmpty()) Ui.BORDER2 else Ui.WHITE).apply {
            letterSpacing = -0.02f
            minWidth = dp(120)
        }
        countText = big
        stats.addView(big, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (k in listOf("EKRAN", "NOT", "SES")) {
            val n = all.count { it.kind == k || (k == "EKRAN" && it.kind == "GÖRSEL") }
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Ui.text(this@MainActivity, k, 11f, Ui.GREY).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(Ui.text(this@MainActivity, n.toString().padStart(3, '0'), 11f, if (all.isEmpty()) Ui.GREY else Ui.WHITE).apply {
                    letterSpacing = 0.14f
                    gravity = Gravity.END
                }, LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), 0, 0) })
        }
        stats.addView(col)

        val now = System.currentTimeMillis()
        val perDay = (29 downTo 0).map { back ->
            val key = Store.dayKey(now - back * 86_400_000L)
            all.count { Store.dayKey(it.time) == key }
        }
        strip.colors = IntArray(30) { i ->
            when {
                i == 29 -> Ui.RED
                perDay[i] == 0 -> Ui.OFF
                perDay[i] == 1 -> Ui.PROG
                perDay[i] == 2 -> Ui.MID
                else -> Ui.WHITE
            }
        }
        strip.play(animate)

        targetCount = all.size
        if (animate && Ui.motion(this, 1) > 1) shownCount = 0 else shownCount = targetCount
        big.text = shownCount.toString().padStart(3, '0')
        if (shownCount != targetCount) tickCount()
    }

    private fun tickCount() {
        val tv = countText ?: return
        if (shownCount == targetCount) return
        val step = max(1, ceil(abs(targetCount - shownCount) / 9.0).toInt())
        shownCount += sign((targetCount - shownCount).toDouble()).toInt() * step
        tv.text = shownCount.toString().padStart(3, '0')
        if (shownCount != targetCount) main.postDelayed({ tickCount() }, 35)
    }

    // --- Liste ---

    private fun renderList(animate: Boolean) {
        val all = Store.all(this).filter { it.file.path !in pendingDelete }
        renderStats(all, animate)
        list.removeAllViews()
        if (all.isEmpty()) {
            list.addView(emptyView())
            return
        }
        val items = applyQuick(Store.filter(all, filter, search.text.toString()))
        if (items.isEmpty()) {
            val filtered = quick.isNotEmpty() || search.text.isNotEmpty()
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(48), 0, dp(48))
                addView(Ui.text(this@MainActivity, if (filtered) "Bu aramada kayıt yok." else "Kayıt yok.", 13f, Ui.GREY2).apply {
                    gravity = Gravity.CENTER
                })
                if (filtered) addView(Ui.text(this@MainActivity, "FİLTRELERİ TEMİZLE", 10f, Ui.WHITE, bold = true).apply {
                    letterSpacing = 0.16f
                    gravity = Gravity.CENTER
                    background = Ui.rounded(this@MainActivity, Color.TRANSPARENT, 20f, Ui.LINE)
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                    Ui.pressAnim(this)
                    setOnClickListener { clearFilters() }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(16), 0, 0)
                })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            return
        }
        var idx = 0
        val baseDelay = if (animate) 380L else 0L
        fun addCard(e: Entry) {
            val c = card(e)
            list.addView(c)
            if (animate || cardGen > 0) rise(c, baseDelay + min(idx, 8) * 70L, Ui.motion(this, 460), 18f)
            idx++
        }
        // Sabitlenenler en üstte kendi grubunda; kalanlar güne göre.
        val visible = items.take(showLimit)
        val pinned = visible.filter { it.pinned }
        if (pinned.isNotEmpty()) {
            list.addView(sectionHeader("SABİTLENENLER", pinned.size))
            pinned.forEach { addCard(it) }
        }
        val groups = visible.filter { !it.pinned }.groupBy { Store.dayKey(it.time) }
        for (group in groups.values) {
            list.addView(dayHeader(group.first().time, group.size))
            group.forEach { addCard(it) }
        }
        if (items.size > showLimit) {
            list.addView(Ui.pill(this, "DAHA FAZLA · ${items.size - showLimit}") {
                showLimit += 40
                renderList(false)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, dp(24))
            })
        }
    }

    private fun sectionHeader(title: String, count: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(14), 0, dp(12))
        addView(Ui.dayLabel(this@MainActivity, title, ""))
        addView(DottedLine(this@MainActivity), LinearLayout.LayoutParams(0, dp(4), 1f).apply { setMargins(dp(10), 0, dp(10), 0) })
        addView(Ui.text(this@MainActivity, count.toString().padStart(2, '0'), 11f, Ui.GREY).apply { letterSpacing = 0.14f })
    }

    /** Karttaki etiketler (dokununca o etiketle arar) ve varsa hatırlatıcı saati. */
    private fun tagRow(e: Entry): View? {
        if (e.tags.isEmpty() && e.remind == 0L) return null
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            e.tags.take(3).forEach { t ->
                addView(Ui.text(this@MainActivity, "#$t", 10f, Ui.GREY2).apply {
                    letterSpacing = 0.1f
                    background = Ui.rounded(this@MainActivity, Color.TRANSPARENT, 12f, Ui.LINE)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    setOnClickListener { searchFor(t) }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, dp(6), 0)
                })
            }
            if (e.remind > 0) addView(Ui.text(this@MainActivity, "● ${Store.stamp(e.remind)}", 10f, Ui.RED).apply { letterSpacing = 0.1f })
        }
    }

    private val secretTaps = ArrayDeque<Long>()

    private fun secretTap() {
        val now = System.currentTimeMillis()
        secretTaps.addLast(now)
        while (secretTaps.isNotEmpty() && now - secretTaps.first() > 4000) secretTaps.removeFirst()
        if (secretTaps.size >= 7) {
            secretTaps.clear()
            startActivity(Intent(this, VaultActivity::class.java))
        }
    }

    /** Kaydı şifreli gizli bölüme taşır (bölüm kurulmuşsa görünür). */
    private fun moveToVault(e: Entry) {
        Thread {
            val ok = Vault.moveIn(this, e)
            runOnUiThread {
                Store.toast(this, if (ok) "TAŞINDI" else "TAŞINAMADI (NOTU YA DA EKİ VAR)")
                cardGen++
                renderList(false)
            }
            CaptureWidget.refreshAll(applicationContext)
        }.start()
    }

    private fun searchFor(q: String) {
        if (searchRow.visibility != View.VISIBLE) openSearch()
        search.setText(q)
        search.setSelection(search.text.length)
    }

    private fun togglePin(e: Entry) {
        Info.update(e.file) { it.copy(pinned = !it.pinned) }
        cardGen++
        renderList(false)
    }

    /** Tarih ve saat seçtirip kayıt için hatırlatıcı kurar; varsa kaldırır. */
    private fun toggleReminder(e: Entry) {
        if (e.remind > System.currentTimeMillis()) {
            Reminders.cancel(this, e.file)
            Store.toast(this, "HATIRLATICI KALDIRILDI")
            cardGen++
            renderList(false)
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        startActivity(Intent(this, PickerActivity::class.java).putExtra("path", e.file.absolutePath))
    }

    private fun editTags(e: Entry) {
        showInput("ETİKETLER", "iş, fatura, fikir", e.tags.joinToString(", ")) { raw ->
            Info.update(e.file) { it.copy(tags = Info.parseTags(raw)) }
            cardGen++
            renderList(false)
        }
    }

    /** Boş durum (tasarım 02): 13×13 halka, başlık ve gerekiyorsa mikrofon izni kartı. */
    private fun emptyView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(28), 0, 0)
        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.rounded(this@MainActivity, Ui.SURFACE, 24f, Ui.BORDER)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                addView(Ui.kindLabel(this@MainActivity, "İZİN BEKLENİYOR"))
                addView(Ui.text(this@MainActivity, "Ses kaydı için mikrofon izni gerekli.", 12f, Ui.GREY2)
                    .apply { setPadding(0, dp(8), 0, dp(12)) })
                addView(Ui.pill(this@MainActivity, "İZİN VER") {
                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 2)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(28))
            })
        }
        val ring = IntArray(13 * 13)
        for (r in 0 until 13) for (c in 0 until 13) {
            val d = Math.hypot((r - 6).toDouble(), (c - 6).toDouble())
            ring[r * 13 + c] = when {
                d < 0.5 -> Ui.RED
                d > 2.2 && d < 3.1 -> Ui.RING
                d > 4.6 && d < 6.1 -> Ui.GREY
                else -> 0
            }
        }
        val dots = DotField(this@MainActivity, 13, 13).apply { colors = ring }
        addView(dots, LinearLayout.LayoutParams(dp(126), dp(126)))
        if (Ui.motion(this@MainActivity, 1) > 1) {
            ValueAnimator.ofFloat(0.35f, 1f).apply {
                duration = 1300
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { dots.alpha = it.animatedValue as Float }
                start()
            }
        }
        addView(Ui.text(this@MainActivity, "Henüz kayıt yok.", 15f, Ui.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
        })
        addView(Ui.text(this@MainActivity, "Aşağıdan ilk kaydını al.", 12f, Ui.GREY).apply { gravity = Gravity.CENTER })
    }

    private fun dayHeader(t: Long, count: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(14), 0, dp(12))
        val key = Store.dayKey(t)
        val name = when (key) {
            Store.dayKey(System.currentTimeMillis()) -> "BUGÜN"
            Store.dayKey(System.currentTimeMillis() - 86_400_000L) -> "DÜN"
            else -> ""
        }
        addView(Ui.dayLabel(this@MainActivity, name, Store.dateOf(t)))
        addView(DottedLine(this@MainActivity), LinearLayout.LayoutParams(0, dp(4), 1f).apply { setMargins(dp(10), 0, dp(10), 0) })
        addView(Ui.text(this@MainActivity, count.toString().padStart(2, '0'), 11f, Ui.GREY).apply { letterSpacing = 0.14f })
    }

    // --- Kartlar ---

    private fun loadThumb(f: File, into: ImageView, meta: TextView?) {
        into.tag = f.path
        thumbCache.get(f.path)?.let { into.setImageBitmap(it) }
        Thread {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            var bmp = thumbCache.get(f.path)
            if (bmp == null) {
                var sample = 1
                while (b.outWidth / (sample * 2) >= 200) sample *= 2
                bmp = try { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) } catch (t: Throwable) { null }
                if (bmp != null) thumbCache.put(f.path, bmp)
            }
            runOnUiThread {
                if (into.tag == f.path && bmp != null) into.setImageBitmap(bmp)
                if (meta != null && b.outWidth > 0) meta.text = "${b.outWidth}×${b.outHeight} · ${f.extension.uppercase()}"
            }
        }.start()
    }

    private fun shotThumb(f: File, meta: TextView): View = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = DotGrid(dp(8).toFloat(), Ui.GRID)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, fdp(14f)) }
        }
        loadThumb(f, this, meta)
    }

    /** Ses kartı: oynat/duraklat düğmesi ve 38×5 nokta dalgası; oynarken ilerleme beyaz, geçerli sütun kırmızı. */
    private fun voiceRow(e: Entry): View {
        val cols = 38
        val rows = 5
        val seed = (e.file.name.hashCode() % 100) / 10.0
        val heights = IntArray(cols) { i ->
            val v = (sin(i * 0.9 + seed) * 0.5 + 0.5) * (sin(i * 0.37 + seed * 2) * 0.4 + 0.6)
            max(1, Math.round(v * rows).toInt())
        }
        val wave = DotField(this, cols, rows)
        fun paint(prog: Int) {
            val half = (rows - 1) / 2.0
            wave.colors = IntArray(cols * rows) { idx ->
                val c = idx % cols
                val r = idx / cols
                val on = abs(r - half) < heights[c] / 2.0
                when {
                    !on -> Ui.OFF
                    prog < 0 -> Ui.WHITE
                    c < prog -> Ui.WHITE
                    c == prog -> Ui.RED
                    else -> Ui.PROG
                }
            }
            wave.invalidate()
        }
        paint(-1)
        val isPlaying = playingFile == e.file
        val btn = FrameLayout(this).apply {
            background = Ui.rounded(this@MainActivity, Color.TRANSPARENT, 22f, if (isPlaying) Ui.RED else Ui.LINE)
            contentDescription = if (isPlaying) "Duraklat" else "Oynat"
            addView(DotIcon(this@MainActivity, if (isPlaying) PAUSE else Ui.ICON_PLAY, Ui.WHITE, 27),
                FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER))
            setOnClickListener { togglePlay(e.file) }
        }
        if (isPlaying) {
            val dur = (player?.duration ?: 1).coerceAtLeast(1)
            val tick = object : Runnable {
                override fun run() {
                    val p = player ?: return
                    if (playingFile != e.file || !wave.isAttachedToWindow && wave.parent == null) {
                        if (playingFile != e.file) return
                    }
                    paint((p.currentPosition.toFloat() / dur * cols).toInt().coerceIn(0, cols - 1))
                    main.postDelayed(this, 120)
                }
            }
            main.post(tick)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(btn, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(wave, LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(dp(12), 0, 0, 0) })
            addView(Ui.text(this@MainActivity, speedLabel(), 10f, Ui.GREY2, bold = true).apply {
                letterSpacing = 0.1f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = Ui.rounded(this@MainActivity, Color.TRANSPARENT, 14f, Ui.LINE)
                Ui.pressAnim(this)
                setOnClickListener { cycleSpeed(); renderList(false) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, 0, 0) })
        }
    }

    private fun togglePlay(f: File) {
        val same = playingFile == f
        player?.runCatching { stop(); release() }
        player = null
        playingFile = null
        if (!same) {
            try {
                player = MediaPlayer().apply {
                    setDataSource(f.path)
                    prepare()
                    setOnCompletionListener {
                        playingFile = null
                        player = null
                        it.release()
                        renderList(false)
                    }
                    start()
                }
                playingFile = f
                runCatching { player?.playbackParams = android.media.PlaybackParams().setSpeed(voiceSpeed) }
            } catch (t: Throwable) {
                Store.toast(this, "SES ÇALINAMADI")
            }
        }
        renderList(false)
    }

    private fun cardBody(e: Entry): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@MainActivity, Ui.SURFACE, 24f, Ui.BORDER)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        fun header(withDuration: Boolean = false): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.kindLabel(this@MainActivity, e.kind), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            e.sourceLabel?.let { app ->
                addView(Ui.text(this@MainActivity, app, 10f, Ui.GREY).apply { setPadding(dp(8), 0, dp(8), 0) })
            }
            addView(Ui.text(this@MainActivity, Store.clock(e.time), 11f, Ui.GREY).apply { letterSpacing = 0.1f; gravity = Gravity.END })
        }
        when (e.kind) {
            "EKRAN", "GÖRSEL" -> {
                box.orientation = LinearLayout.HORIZONTAL
                val meta = Ui.text(this, "", 10f, Ui.GREY).apply { letterSpacing = 0.14f }
                box.addView(shotThumb(e.file, meta), LinearLayout.LayoutParams(dp(64), dp(112)))
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(header())
                    if (e.title.isNotBlank()) {
                        addView(Ui.text(this@MainActivity, e.title, 13f, Ui.WHITE, bold = true).apply {
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setPadding(0, dp(10), 0, 0)
                        })
                    }
                    if (e.text.isNotBlank()) {
                        addView(Ui.text(this@MainActivity, e.text.trim(), 13f, Ui.GREY2).apply {
                            maxLines = if (e.title.isBlank()) 3 else 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setLineSpacing(0f, 1.15f)
                            setPadding(0, dp(if (e.title.isBlank()) 10 else 6), 0, 0)
                        })
                    }
                    addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
                    tagRow(e)?.let { addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(8)) }) }
                    addView(meta)
                }
                box.addView(col, LinearLayout.LayoutParams(0, dp(112), 1f).apply { setMargins(dp(16), 0, 0, 0) })
            }
            "SES" -> {
                box.addView(header())
                box.addView(voiceRow(e), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(14), 0, 0)
                })
                tagRow(e)?.let { box.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(12), 0, 0) }) }
            }
            else -> {
                box.addView(header())
                if (e.title.isNotBlank()) {
                    box.addView(Ui.text(this, e.title, 13f, Ui.WHITE, bold = true).apply {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(0, dp(10), 0, 0)
                    })
                }
                if (e.text.isNotBlank()) {
                    box.addView(Ui.text(this, e.text.trim(), 14f, Ui.WHITE).apply {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setLineSpacing(0f, 1.2f)
                        setPadding(0, dp(10), 0, 0)
                    })
                }
                val atts = Store.attachments(e.file)
                if (atts.isNotEmpty()) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    atts.take(4).forEach { img ->
                        val iv = ImageView(this).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = Ui.rounded(this@MainActivity, Ui.BG, 10f, Ui.BORDER2)
                            clipToOutline = true
                            outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, fdp(10f)) }
                            }
                        }
                        loadThumb(img, iv, null)
                        row.addView(iv, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(0, 0, dp(6), 0) })
                    }
                    if (atts.size > 4) row.addView(Ui.text(this, "+${atts.size - 4}", 11f, Ui.GREY).apply { letterSpacing = 0.14f })
                    box.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, dp(12), 0, 0)
                    })
                }
                tagRow(e)?.let { box.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(12), 0, 0) }) }
            }
        }
        return box
    }

    /** Kart + altındaki kırmızı "SİL" katmanı. Sola çekilince katman görünür; 110 dp'yi geçince bırakınca silinir. */
    private fun card(e: Entry): View {
        val wrap = FrameLayout(this)
        val trashDots = DotIcon(this, Ui.ICON_TRASH, Ui.RED, 27)
        val label = Ui.text(this, "SİL", 12f, Ui.RED, bold = true).apply { letterSpacing = 0.18f }
        val layer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(0, 0, dp(22), 0)
            background = Ui.rounded(this@MainActivity, Ui.BG, 24f, Ui.BORDER2)
            alpha = 0f
            addView(trashDots, LinearLayout.LayoutParams(dp(27), dp(27)).apply { setMargins(0, 0, dp(12), 0) })
            addView(label)
        }
        val body = cardBody(e)
        wrap.addView(layer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        wrap.addView(body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        // Katman, kartın yüksekliğini alsın.
        body.addOnLayoutChangeListener { _, _, t, _, b, _, _, _, _ ->
            val h = b - t
            if (wrap.layoutParams != null && layer.layoutParams.height != h) {
                layer.layoutParams = (layer.layoutParams as FrameLayout.LayoutParams).apply { height = h }
            }
        }

        val slop = fdp(8f)
        val threshold = fdp(110f)
        val maxPull = fdp(150f)
        var x0 = 0f
        var y0 = 0f
        var claimed = false
        var cur = 0f

        fun applyPull(v: Float) {
            cur = v
            body.translationX = v
            val p = (-v / threshold).coerceIn(0f, 1f)
            val past = -v >= threshold
            layer.alpha = p
            layer.background = Ui.rounded(this, if (past) 0x29F0303A else Ui.BG, 24f, if (past) Ui.RED else Ui.BORDER2)
            label.text = if (past) "BIRAK" else "SİL"
            label.setTextColor(if (past) Ui.WHITE else Ui.RED)
            val s = if (past) 1.35f else 1f
            trashDots.animate().scaleX(s).scaleY(s).setDuration(200).start()
        }

        body.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = ev.rawX; y0 = ev.rawY; claimed = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - x0
                    val dy = ev.rawY - y0
                    if (!claimed) {
                        if (abs(dx) > slop && abs(dx) > abs(dy) && dx < 0) {
                            claimed = true
                            v.parent.requestDisallowInterceptTouchEvent(true)
                            val c = MotionEvent.obtain(ev)
                            c.action = MotionEvent.ACTION_CANCEL
                            v.onTouchEvent(c)
                            c.recycle()
                        } else return@setOnTouchListener false
                    }
                    var pull = min(0f, dx)
                    if (pull < -maxPull) pull = -maxPull + (pull + maxPull) * 0.3f
                    applyPull(pull)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!claimed) return@setOnTouchListener false
                    claimed = false
                    if (cur <= -threshold) removeCard(e, wrap, body, layer)
                    else {
                        body.animate().translationX(0f).setDuration(Ui.motion(this, 320)).setInterpolator(PathInterpolator(0.2f, 1.2f, 0.3f, 1f)).start()
                        layer.animate().alpha(0f).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }
        // Dokununca kaydı aç; basılı tutunca titreşim + pop sesiyle menü (sabitle, etiket, hatırlatıcı, gizle, paylaş, sil) açılır.
        body.setOnClickListener { if (selecting) toggleSelect(e) else if (e.kind == "SES") startActivity(Intent(this, PlayerActivity::class.java).putExtra("path", e.file.absolutePath)) else open(e.file) }
        if (selecting && e.file.path in selected) wrap.foreground = GradientDrawable().apply { cornerRadius = fdp(24f); setStroke(dp(2), Ui.RED); setColor(Color.TRANSPARENT) }
        body.setOnLongClickListener { Feel.pop(this); showSheet(e); true }
        wrap.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(8))
        }
        return wrap
    }

    // --- Silme (kaydır, daralt, geri al) ---

    /** 220 ms sola uçar, 260 ms'de boşluğu kapanır; sonra liste yenilenir ve "KAYIT SİLİNDİ" çubuğu çıkar. */
    private fun removeCard(e: Entry, wrap: View, body: View, layer: View) {
        layer.alpha = 1f
        body.animate().translationX(-(wrap.width + fdp(40f))).setDuration(Ui.motion(this, 220))
            .setInterpolator(android.view.animation.AccelerateInterpolator()).withEndAction {
                val h = wrap.height
                val lp = wrap.layoutParams as LinearLayout.LayoutParams
                val mb = lp.bottomMargin
                ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = Ui.motion(this@MainActivity, 260)
                    addUpdateListener {
                        val f = it.animatedValue as Float
                        lp.height = (h * f).toInt()
                        lp.bottomMargin = (mb * f).toInt()
                        wrap.layoutParams = lp
                        wrap.alpha = f
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: android.animation.Animator) { softDelete(e) }
                    })
                    start()
                }
            }.start()
    }

    // ---------- Çoklu seçim ----------

    private val selected = LinkedHashSet<String>()
    private var selecting = false
    private var selBar: LinearLayout? = null

    private fun enterSelection(e: Entry) {
        if (selecting) return
        selecting = true
        selected.clear()
        selected.add(e.file.path)
        dock.visibility = View.GONE
        showSelBar()
        cardGen++
        renderList(false)
    }

    private fun exitSelection() {
        if (!selecting) return
        selecting = false
        selected.clear()
        selBar?.let { runCatching { root.removeView(it) } }
        selBar = null
        dock.visibility = View.VISIBLE
        cardGen++
        renderList(false)
    }

    private fun toggleSelect(e: Entry) {
        if (!selected.add(e.file.path)) selected.remove(e.file.path)
        if (selected.isEmpty()) { exitSelection(); return }
        updateSelBar()
        cardGen++
        renderList(false)
    }

    private fun showSelBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(this@MainActivity, Ui.SURFACE, 40f, Ui.BORDER2)
            setPadding(dp(8), 0, dp(8), 0)
        }
        selBar = bar
        root.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(80), Gravity.BOTTOM).apply {
            setMargins(dp(20), 0, dp(20), dp(24))
        })
        updateSelBar()
    }

    private fun updateSelBar() {
        val bar = selBar ?: return
        bar.removeAllViews()
        bar.addView(Ui.text(this, selected.size.toString().padStart(2, '0'), 14f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.1f
            setPadding(dp(10), 0, dp(4), 0)
        })
        fun act(label: String, color: Int, onClick: () -> Unit) = Ui.text(this, label, 10f, color, bold = true).apply {
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }
        listOf(
            act("SİL", Ui.RED) { bulkDelete() },
            act("ETİKET", Ui.WHITE) { bulkTag() },
            act("GİZLE", Ui.WHITE) { bulkVault() },
            act("PAYLAŞ", Ui.WHITE) { bulkShare() },
            act("İPTAL", Ui.GREY) { exitSelection() }
        ).forEach { bar.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)) }
    }

    private fun selectedEntries(): List<Entry> = Store.all(this).filter { it.file.path in selected }

    private fun bulkDelete() {
        val items = selectedEntries()
        val n = items.size
        showChoices("$n KAYIT SİLİNSİN Mİ?", listOf(
            Triple("SİL", Ui.RED) {
                exitSelection()
                Thread {
                    items.forEach { Store.delete(it.file) }
                    CaptureWidget.refreshAll(applicationContext)
                    runOnUiThread { renderList(false); Store.toast(this, "$n KAYIT SİLİNDİ") }
                }.start()
            },
            Triple("VAZGEÇ", Ui.WHITE) {}
        ))
    }

    private fun bulkTag() {
        val items = selectedEntries()
        showInput("ETİKET EKLE", "iş, fatura, fikir") { raw ->
            val tags = Info.parseTags(raw)
            if (tags.isNotEmpty()) Thread {
                items.forEach { e -> Info.update(e.file) { d -> d.copy(tags = (d.tags + tags).distinct().take(8)) } }
                runOnUiThread { exitSelection(); Store.toast(this, "ETİKET EKLENDİ") }
            }.start()
        }
    }

    private fun bulkVault() {
        if (!Vault.configured(this)) {
            Store.toast(this, "ARŞİV HENÜZ AÇILMADI")
            return
        }
        val items = selectedEntries()
        exitSelection()
        Thread {
            var ok = 0
            var skipped = 0
            items.forEach { if (Vault.moveIn(this, it)) ok++ else skipped++ }
            runOnUiThread {
                Store.toast(this, if (skipped == 0) "$ok KAYIT ARŞİVDE" else "$ok ARŞİVDE, $skipped TAŞINAMADI")
                renderList(false)
            }
            CaptureWidget.refreshAll(applicationContext)
        }.start()
    }

    private fun bulkShare() {
        val items = selectedEntries()
        if (items.isEmpty()) return
        Sheet.show(this, "PAYLAŞ", null, listOf(
            Sheet.Action("PDF OLARAK", Ui.WHITE) { sharePdf(items) },
            Sheet.Action("GÖRSEL VEYA METİN", Ui.WHITE) { shareRaw(items) },
            Sheet.Action("VAZGEÇ", Ui.GREY) {}
        ))
    }

    private fun sharePdf(items: List<Entry>) {
        Thread {
            val f = try { PdfExport.write(applicationContext, items) } catch (t: Throwable) { AppLog.e(applicationContext, "PDF", t); null }
            runOnUiThread {
                if (f == null) {
                    Store.toast(this, "PDF OLUŞTURULAMADI")
                } else {
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
                    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, "Paylaş"))
                }
            }
        }.start()
    }

    private fun shareRaw(items: List<Entry>) {
        val intent = if (items.all { Store.isImage(it.file) }) {
            val uris = ArrayList(items.map { FileProvider.getUriForFile(this, "$packageName.files", it.file) })
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            val text = items.joinToString("\n\n") { e ->
                (if (e.title.isNotBlank()) e.title + "\n" else "") + e.text.trim()
            }.trim()
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Paylaş"))
    }

    /** Uygulamanın kendi sayfa tarzında, etiketli seçenekler listesi. */
    private fun showChoices(title: String, rows: List<Triple<String, Int, () -> Unit>>) {
        if (sheetView != null) return
        val decor = window.decorView as ViewGroup
        val d = resources.displayMetrics.density
        val ms = Ui.motion(this, 220)
        val scrim = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            setOnClickListener { hideSheet() }
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                cornerRadii = floatArrayOf(32 * d, 32 * d, 32 * d, 32 * d, 0f, 0f, 0f, 0f)
                setStroke((1 * d).toInt(), Ui.BORDER2)
            }
            setPadding(dp(20), dp(14), dp(20), dp(32))
            addView(Ui.kindLabel(this@MainActivity, title), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(20), 0, dp(10))
            })
            rows.forEachIndexed { i, (label, color, onClick) ->
                if (i > 0) addView(View(this@MainActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
                addView(Ui.text(this@MainActivity, label, 14f, color, bold = true).apply {
                    letterSpacing = 0.18f
                    gravity = Gravity.CENTER_VERTICAL
                    Ui.pressAnim(this)
                    setOnClickListener { hideSheet(); onClick() }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)))
            }
        }
        attachSheet(decor, scrim, sheet, ms)
    }

    /** Tek satırlık metin girişi: aynı sayfa tarzında. */
    private fun showInput(title: String, hint: String, initial: String = "", onOk: (String) -> Unit) {
        if (sheetView != null) return
        val decor = window.decorView as ViewGroup
        val d = resources.displayMetrics.density
        val ms = Ui.motion(this, 220)
        val scrim = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            setOnClickListener { hideSheet() }
        }
        val field = EditText(this).apply {
            this.hint = hint
            setText(initial)
            setHintTextColor(Ui.DIM)
            setTextColor(Ui.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setSingleLine(true)
            background = Ui.rounded(this@MainActivity, Ui.BG, 22f, Ui.BORDER2)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                cornerRadii = floatArrayOf(32 * d, 32 * d, 32 * d, 32 * d, 0f, 0f, 0f, 0f)
                setStroke((1 * d).toInt(), Ui.BORDER2)
            }
            setPadding(dp(20), dp(14), dp(20), dp(32))
            addView(Ui.kindLabel(this@MainActivity, title), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(20), 0, dp(12))
            })
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(Ui.text(this@MainActivity, "Virgülle ayır. En fazla 8 etiket.", 11f, Ui.GREY).apply {
                setPadding(0, dp(10), 0, dp(12))
            })
            addView(Ui.pill(this@MainActivity, "KAYDET") {
                val v = field.text.toString()
                hideSheet()
                onOk(v)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        attachSheet(decor, scrim, sheet, ms)
    }

    private fun attachSheet(decor: ViewGroup, scrim: View, sheet: View, ms: Long) {
        decor.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        decor.addView(sheet, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        sheet.translationY = dp(700).toFloat()
        sheet.animate().translationY(0f).setDuration(ms).setInterpolator(DecelerateInterpolator()).start()
        scrim.animate().alpha(0.78f).setDuration(ms).start()
        sheetScrim = scrim
        sheetView = sheet
    }

    // ---------- Hızlı filtreler, sıralama, son aramalar, OCR ilerlemesi ----------

    private var quick = ""
    private var sortOld = false
    private lateinit var quickRow: LinearLayout
    private lateinit var quickScroll: HorizontalScrollView
    private lateinit var recentRow: LinearLayout
    private val pollHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun renderQuick() {
        if (!::quickRow.isInitialized) return
        quickRow.removeAllViews()
        listOf("BUGÜN" to "BUGÜN", "HAFTA" to "BU HAFTA", "SABİT" to "SABİT", "ETİKET" to "ETİKETLİ").forEach { (key, label) ->
            val on = quick == key
            quickRow.addView(Ui.text(this, label, 10f, if (on) Ui.WHITE else Ui.GREY, bold = on).apply {
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                background = Ui.rounded(this@MainActivity, Color.TRANSPARENT, 16f, if (on) Ui.WHITE else Ui.LINE)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                Ui.pressAnim(this)
                setOnClickListener {
                    quick = if (quick == key) "" else key
                    cardGen++
                    renderQuick()
                    renderList(false)
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(6), 0) })
        }
        quickRow.addView(Ui.text(this, if (sortOld) "ESKİ ▲" else "YENİ ▼", 10f, Ui.GREY, bold = true).apply {
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            background = Ui.rounded(this@MainActivity, Color.TRANSPARENT, 16f, Ui.LINE)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            Ui.pressAnim(this)
            setOnClickListener {
                sortOld = !sortOld
                cardGen++
                renderQuick()
                renderList(false)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun applyQuick(xs: List<Entry>): List<Entry> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val r = when (quick) {
            "BUGÜN" -> xs.filter { Store.dayKey(it.time) == Store.dayKey(now) }
            "HAFTA" -> xs.filter { now - it.time < 7 * day }
            "SABİT" -> xs.filter { it.pinned }
            "ETİKET" -> xs.filter { it.tags.isNotEmpty() }
            else -> xs
        }
        return if (sortOld) r.sortedBy { it.time } else r
    }

    private fun rememberQuery(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        Prefs.setRecent(this, listOf(t) + Prefs.recent(this).filter { it != t })
    }

    private fun renderRecent() {
        if (!::recentRow.isInitialized) return
        recentRow.removeAllViews()
        val show = searchRow.visibility == View.VISIBLE && search.text.isEmpty()
        val items = Prefs.recent(this)
        recentRow.visibility = if (show && items.isNotEmpty()) View.VISIBLE else View.GONE
        items.forEach { q ->
            recentRow.addView(Ui.text(this, q, 11f, Ui.GREY2).apply {
                letterSpacing = 0.06f
                background = Ui.rounded(this@MainActivity, Color.TRANSPARENT, 16f, Ui.LINE)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                Ui.pressAnim(this)
                setOnClickListener {
                    search.setText(q)
                    search.setSelection(q.length)
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(6), 0) })
        }
    }

    /** Filtre ya da arama boş sonuç verdiğinde, tek dokunuşla filtreleri temizleme düğmesi. */
    private fun clearFilters() {
        quick = ""
        search.setText("")
        cardGen++
        renderQuick()
        renderList(false)
    }

    /** Ana durum yazısı: OCR çalışıyorsa ilerlemeyi gösterir, yoksa normal durumu. */
    private val statusPoll = object : Runnable {
        override fun run() {
            if (isFinishing) return
            if (Ocr.pending > Ocr.done) status.text = "OKUNUYOR ${Ocr.done}/${Ocr.pending}"
            else refreshStatus()
            pollHandler.postDelayed(this, 700)
        }
    }

    // ---------- Ses hızı, kayıt adı, sayfalama ----------

    private var voiceSpeed = 1f
    private var showLimit = 40

    private fun speedLabel() = when (voiceSpeed) {
        1.5f -> "1.5×"
        2f -> "2×"
        else -> "1×"
    }

    private fun cycleSpeed() {
        voiceSpeed = when (voiceSpeed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        player?.let { p -> runCatching { p.playbackParams = android.media.PlaybackParams().setSpeed(voiceSpeed) } }
    }

    private fun renameEntry(e: Entry) {
        showInput("KAYIT ADI", "boş bırakırsan ad silinir", e.title) { v ->
            Info.update(e.file) { it.copy(title = v.trim()) }
            cardGen++
            renderList(false)
        }
    }

    private fun softDelete(e: Entry) {
        commitDeletes(keepLast = false)
        pendingDelete[e.file.path] = e
        lastDeleted = e
        renderList(false)
        showToast()
        commitRunnable?.let { main.removeCallbacks(it) }
        commitRunnable = Runnable { commitDeletes(); hideToast() }
        main.postDelayed(commitRunnable!!, 5200)
    }

    /** Bekleyen silmeleri kalıcı yapar. */
    private fun commitDeletes(keepLast: Boolean = false) {
        if (pendingDelete.isEmpty()) return
        val files = pendingDelete.values.map { it.file }
        pendingDelete.clear()
        if (!keepLast) lastDeleted = null
        commitRunnable?.let { main.removeCallbacks(it) }
        commitRunnable = null
        val app = applicationContext
        Thread {
            files.forEach { Store.delete(it) }
            CaptureWidget.refreshAll(app)
        }.start()
    }

    private fun undoDelete() {
        val e = lastDeleted ?: return
        pendingDelete.remove(e.file.path)
        lastDeleted = null
        commitRunnable?.let { main.removeCallbacks(it) }
        commitRunnable = null
        hideToast()
        cardGen++
        renderList(false)
    }

    private fun showToast() {
        hideToast(false)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(this@MainActivity, Ui.BG, 28f, Ui.BORDER2)
            setPadding(dp(22), 0, dp(8), 0)
            addView(Ui.kindLabel(this@MainActivity, "KAYIT SİLİNDİ").apply { text = Ui.withDot("KAYIT SİLİNDİ", Ui.RED) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "GERİ AL"
                textSize = 11f
                setTextColor(Ui.WHITE)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
                background = Ui.rounded(this@MainActivity, Color.TRANSPARENT, 22f, Ui.WHITE)
                setPadding(dp(20), 0, dp(20), 0)
                Ui.pressAnim(this)
                setOnClickListener { undoDelete() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
        }
        root.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(56), Gravity.BOTTOM).apply {
            setMargins(dp(20), 0, dp(20), dp(116))
        })
        bar.alpha = 0f
        bar.translationY = fdp(24f)
        bar.scaleX = 0.96f
        bar.scaleY = 0.96f
        bar.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(Ui.motion(this, 320)).setInterpolator(ease).start()
        toastView = bar
    }

    private fun hideToast(animate: Boolean = true) {
        val t = toastView ?: return
        toastView = null
        if (animate) t.animate().alpha(0f).translationY(fdp(12f)).setDuration(Ui.motion(this, 200)).withEndAction { runCatching { root.removeView(t) } }.start()
        else runCatching { root.removeView(t) }
    }

    private val PAUSE = listOf("0000000", "0110110", "0110110", "0110110", "0110110", "0110110", "0000000")

    // --- Eylem sayfası (tasarım 03) ---

    private var sheetScrim: View? = null
    private var sheetView: View? = null

    private fun showSheet(e: Entry) {
        if (sheetView != null) return
        val decor = window.decorView as ViewGroup
        val d = resources.displayMetrics.density
        val ms = Ui.motion(this, 220)
        val scrim = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            setOnClickListener { hideSheet() }
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                cornerRadii = floatArrayOf(32 * d, 32 * d, 32 * d, 32 * d, 0f, 0f, 0f, 0f)
                setStroke((1 * d).toInt(), Ui.BORDER2)
            }
            setPadding(dp(20), dp(12), dp(20), dp(24))
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
                for (c in intArrayOf(Ui.LINE, Ui.WHITE, Ui.LINE)) {
                    addView(View(this@MainActivity).apply { background = Ui.rounded(this@MainActivity, c, 2f) },
                        LinearLayout.LayoutParams(dp(4), dp(4)).apply { setMargins(dp(3), 0, dp(3), 0) })
                }
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.kindLabel(this@MainActivity, e.kind), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Ui.text(this@MainActivity, Store.stamp(e.time), 11f, Ui.GREY).apply { letterSpacing = 0.14f })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(14), 0, dp(6)) })
            if (e.preview.isNotEmpty()) {
                addView(Ui.text(this@MainActivity, e.text.trim(), 12f, Ui.GREY2).apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setLineSpacing(0f, 1.15f)
                    setPadding(0, 0, 0, dp(10))
                })
            }
            addView(sheetRow("AÇ", Ui.ICON_OPEN, Ui.WHITE, false) { hideSheet(); open(e.file) })
            addView(sheetRow("SEÇ", Ui.ICON_SHOT, Ui.WHITE, true) { hideSheet(); enterSelection(e) })
            addView(sheetRow("ADI DEĞİŞTİR", Ui.ICON_TAG, Ui.WHITE, true) { hideSheet(); renameEntry(e) })
            addView(sheetRow("PAYLAŞ", Ui.ICON_SHARE, Ui.WHITE, true) { hideSheet(); share(e.file) })
            addView(sheetRow(if (e.pinned) "SABİTLEMEYİ KALDIR" else "SABİTLE", Ui.ICON_PIN, Ui.WHITE, true) { hideSheet(); togglePin(e) })
            addView(sheetRow("ETİKET", Ui.ICON_TAG, Ui.WHITE, true) { hideSheet(); editTags(e) })
            if (Vault.configured(this@MainActivity)) {
                addView(sheetRow("GİZLE", Ui.ICON_LOCK, Ui.WHITE, true) { hideSheet(); moveToVault(e) })
            }
            addView(sheetRow(if (e.remind > System.currentTimeMillis()) "HATIRLATICIYI KALDIR" else "HATIRLATICI", Ui.ICON_BELL, Ui.WHITE, true) {
                hideSheet(); toggleReminder(e)
            })
            addView(sheetRow("SİL", Ui.ICON_TRASH, Ui.RED, true) { hideSheet(); softDelete(e) })
        }
        decor.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        decor.addView(sheet, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        sheet.translationY = dp(700).toFloat()
        sheet.animate().translationY(0f).setDuration(ms).setInterpolator(DecelerateInterpolator()).start()
        scrim.animate().alpha(0.78f).setDuration(ms).start()
        sheetScrim = scrim
        sheetView = sheet
    }

    private fun hideSheet() {
        val s = sheetView ?: return
        val sc = sheetScrim
        sheetView = null
        sheetScrim = null
        val decor = window.decorView as ViewGroup
        val ms = Ui.motion(this, 200)
        s.animate().translationY(dp(700).toFloat()).setDuration(ms).withEndAction { runCatching { decor.removeView(s) } }.start()
        sc?.animate()?.alpha(0f)?.setDuration(ms)?.withEndAction { runCatching { decor.removeView(sc) } }?.start()
    }

    override fun onBackPressed() {
        if (sheetView != null) hideSheet() else if (selecting) exitSelection() else super.onBackPressed()
    }

    private fun sheetRow(label: String, icon: List<String>, color: Int, divider: Boolean, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46))
            if (divider) {
                foreground = null
            }
            addView(Ui.text(this@MainActivity, label, 11f, color, bold = true).apply { letterSpacing = 0.14f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(DotIcon(this@MainActivity, icon, color, 18), LinearLayout.LayoutParams(dp(18), dp(18)))
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }

    private fun mimeOf(f: File) = when (f.extension) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "m4a" -> "audio/mp4"
        else -> "text/plain"
    }

    private fun open(f: File) {
        when (f.extension) {
            "txt" -> startActivity(Intent(this, NoteActivity::class.java).putExtra("path", f.absolutePath))
            "png", "jpg", "jpeg", "webp" -> startActivity(Intent(this, ShotActivity::class.java).putExtra("path", f.absolutePath))
            else -> {
                val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
    }

    private fun share(f: File) {
        val intent = if (f.extension == "txt") {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, f.readText())
        } else {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            Intent(Intent.ACTION_SEND).setType(mimeOf(f)).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(Intent.createChooser(intent, "Paylaş"))
    }
}
