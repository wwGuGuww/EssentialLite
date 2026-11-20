package com.uygar.essentiallite

import android.graphics.Color
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * Ses oynatıcı (tasarım 04c). Ses dosyası yalnızca bu uygulamada, bu ekranda çalınır. Dalga formu üstünde
 * konuma dokunup kaydırarak gezilir, işaretler (önemli anlar) kaydedilir, hız ve döngü ayarlanır.
 * Ekrandan çıkınca oynatma durur.
 */
class PlayerActivity : BaseActivity() {

    private lateinit var file: File
    private lateinit var marksFile: File
    private var player: MediaPlayer? = null
    private var durationMs = 0
    private var posMs = 0
    private var loop = false
    private var speedIdx = 0
    private val speeds = floatArrayOf(1f, 1.5f, 2f, 0.75f)
    private val speedLabels = arrayOf("1×", "1.5×", "2×", "0.75×")
    private val marks = mutableListOf<Int>()
    private val handler = Handler(Looper.getMainLooper())

    private val cols = 61
    private val rows = 15
    private lateinit var heights: IntArray
    private lateinit var wave: DotField
    private lateinit var timeText: TextView
    private lateinit var totalText: TextView
    private lateinit var playHolder: FrameLayout
    private lateinit var marksRow: LinearLayout
    private lateinit var speedBtn: TextView
    private lateinit var markBtn: TextView
    private lateinit var loopBtn: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val ticker = object : Runnable {
        override fun run() {
            player?.let { p -> if (p.isPlaying) posMs = p.currentPosition }
            renderPosition()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path")
        if (path == null) { finish(); return }
        file = File(path)
        marksFile = File(file.parentFile, file.nameWithoutExtension + ".marks")
        marks.addAll(loadMarks())
        heights = IntArray(cols) { i ->
            val seed = file.name.hashCode() % 100 / 10.0
            val v = (Math.sin(i * 0.9 + seed) * 0.5 + 0.5) * (Math.sin(i * 0.37 + seed * 2) * 0.4 + 0.6)
            maxOf(1, Math.round(v * rows).toInt())
        }
        setContentView(buildUi())
        openPlayer()
        handler.post(ticker)
    }

    private fun loadMarks(): List<Int> = try {
        marksFile.takeIf { it.exists() }?.readLines()?.mapNotNull { it.trim().toIntOrNull() }?.sorted().orEmpty()
    } catch (e: Throwable) {
        emptyList()
    }

    private fun saveMarks() {
        try {
            if (marks.isEmpty()) marksFile.delete() else marksFile.writeText(marks.sorted().joinToString("\n"))
        } catch (e: Throwable) {
            AppLog.e(this, "marker memo", e)
        }
    }

    private fun openPlayer() {
        try {
            player = MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                durationMs = duration
                setOnCompletionListener {
                    if (loop) { seekTo(0); start() }
                    else { posMs = durationMs; renderPosition(); updatePlayIcon(false) }
                }
            }
            totalText.text = fmt(durationMs)
        } catch (e: Throwable) {
            AppLog.e(this, "audio couldn't be opened", e)
            Store.toast(this, "COULDN'T OPEN AUDIO")
            finish()
        }
    }

    private fun fmt(ms: Int): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun isPlaying() = player?.isPlaying == true

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (posMs >= durationMs - 50) { p.seekTo(0); posMs = 0 }
            p.start()
            applySpeed()
        }
        updatePlayIcon(p.isPlaying)
    }

    private fun seekTo(ms: Int) {
        val p = player ?: return
        val target = ms.coerceIn(0, durationMs)
        p.seekTo(target)
        posMs = target
        renderPosition()
    }

    private fun applySpeed() {
        runCatching { player?.playbackParams = PlaybackParams().setSpeed(speeds[speedIdx]) }
    }

    private fun toggleMark() {
        // Yakın bir işaret varsa kaldır (1 sn içinde), yoksa ekle.
        val near = marks.firstOrNull { Math.abs(it - posMs) < 1000 }
        if (near != null) marks.remove(near) else marks.add(posMs)
        marks.sort()
        saveMarks()
        renderMarks()
        renderPosition()
    }

    private fun renderPosition() {
        timeText.text = fmt(posMs)
        val cur = if (durationMs > 0) (posMs.toFloat() / durationMs * (cols - 1)).toInt().coerceIn(0, cols - 1) else 0
        wave.colors = IntArray(cols * rows) { idx ->
            val c = idx % cols
            val r = idx / cols
            val on = Math.abs(r - (rows - 1) / 2.0) < heights[c] / 2.0
            when {
                !on -> Ui.OFF
                c < cur -> Color.WHITE
                c == cur -> Ui.RED
                else -> 0xFF4A4A4A.toInt()
            }
        }
        wave.invalidate()
        markBtn.text = "● MARK ${marks.size.toString().padStart(2, '0')}"
    }

    private fun renderMarks() {
        marksRow.removeAllViews()
        if (marks.isEmpty()) {
            marksRow.addView(Ui.text(this, "NO MARKS · TAP A POINT ON THE WAVE", 10f, Ui.GREY).apply { letterSpacing = 0.1f })
            return
        }
        marks.forEach { m ->
            marksRow.addView(Ui.text(this, "● ${fmt(m)}", 10f, Ui.WHITE, bold = true).apply {
                letterSpacing = 0.1f
                gravity = Gravity.CENTER
                background = Ui.rounded(this@PlayerActivity, Color.TRANSPARENT, 18f, Ui.LINE)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                Ui.pressAnim(this)
                setOnClickListener { seekTo(m) }
                setOnLongClickListener { marks.remove(m); saveMarks(); renderMarks(); renderPosition(); true }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dp(8), 0)
            })
        }
    }

    private fun updatePlayIcon(playing: Boolean) {
        playHolder.removeAllViews()
        playHolder.addView(DotIcon(this, if (playing) PAUSE else Ui.ICON_PLAY, if (playing) Ui.RED else Ui.WHITE, 40),
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        playHolder.background = Ui.rounded(this, Color.TRANSPARENT, 52f, if (playing) Ui.RED else Ui.WHITE)
    }

    private fun buildUi(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@PlayerActivity, "‹", 18f).apply {
                contentDescription = "Back"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@PlayerActivity, "VOICE"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@PlayerActivity, Store.stamp(file.lastModified()), 10f, Ui.GREY))
        }

        val privacy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@PlayerActivity, Ui.SURFACE, 24f, Ui.BORDER)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(Ui.text(this@PlayerActivity, "IN THIS APP ONLY", 11f, Ui.WHITE, bold = true).apply { letterSpacing = 0.14f })
            addView(Ui.text(this@PlayerActivity, "The file is in a private folder. Gallery and other apps can't see it.", 10f, Ui.GREY).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }

        timeText = Ui.display(this, "00:00", 96f, Ui.WHITE)
        totalText = Ui.text(this, "00:00", 11f, Ui.GREY).apply { letterSpacing = 0.14f; setPadding(0, 0, 0, dp(12)) }
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(timeText)
            addView(View(this@PlayerActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(totalText)
        }

        wave = DotField(this, cols, rows).apply { colors = IntArray(cols * rows) { Ui.OFF } }
        wave.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val f = (e.x / v.width).coerceIn(0f, 1f)
                    seekTo((f * durationMs).toInt())
                }
            }
            true
        }
        val waveBox = FrameLayout(this).apply { addView(wave, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(120))) }

        marksRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val marksScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(marksRow)
        }

        val back10 = Ui.text(this, "‹‹ 10", 11f, Ui.GREY2, bold = true).apply {
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            Ui.pressAnim(this)
            setOnClickListener { seekTo(posMs - 10_000) }
        }
        val fwd10 = Ui.text(this, "10 ››", 11f, Ui.GREY2, bold = true).apply {
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            Ui.pressAnim(this)
            setOnClickListener { seekTo(posMs + 10_000) }
        }
        playHolder = FrameLayout(this).apply {
            Ui.pressAnim(this)
            setOnClickListener { togglePlay() }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(back10, LinearLayout.LayoutParams(0, dp(88), 1f))
            addView(playHolder, LinearLayout.LayoutParams(dp(92), dp(92)))
            addView(fwd10, LinearLayout.LayoutParams(0, dp(88), 1f))
        }

        speedBtn = chip(speedLabels[speedIdx]) {
            speedIdx = (speedIdx + 1) % speeds.size
            applySpeed()
            speedBtn.text = speedLabels[speedIdx]
        }
        markBtn = chip("● MARK") { toggleMark() }
        loopBtn = chip("LOOP") {
            loop = !loop
            loopBtn.setTextColor(if (loop) Color.WHITE else Ui.GREY)
            loopBtn.background = Ui.rounded(this, Color.TRANSPARENT, 20f, if (loop) Ui.WHITE else Ui.LINE)
        }
        val options = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(speedBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(6), 0) })
            addView(markBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(loopBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(6), 0, 0, 0) })
        }

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(Ui.text(this@PlayerActivity, "${file.extension.uppercase()} · ON DEVICE", 10f, Ui.GREY).apply { letterSpacing = 0.14f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@PlayerActivity, "DELETE", 11f, Ui.RED, bold = true).apply {
                letterSpacing = 0.18f
                setPadding(dp(12), dp(12), dp(4), dp(12))
                Ui.pressAnim(this)
                setOnClickListener { confirmDelete() }
            })
        }

        renderMarks()
        renderPosition()
        updatePlayIcon(false)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(24))
            addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(privacy, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(16), 0, 0) })
            addView(timeRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(22), 0, 0) })
            addView(waveBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) })
            addView(marksScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(14), 0, 0) })
            addView(View(this@PlayerActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(options, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(14), 0, 0) })
            addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun chip(label: String, onClick: () -> Unit): TextView = Ui.text(this, label, 11f, Ui.GREY2, bold = true).apply {
        letterSpacing = 0.1f
        gravity = Gravity.CENTER
        background = Ui.rounded(this@PlayerActivity, Color.TRANSPARENT, 24f, Ui.LINE)
        Ui.pressAnim(this)
        setOnClickListener { onClick() }
    }

    private fun confirmDelete() {
        Sheet.show(this, "DELETE VOICE MEMO?", "This can't be undone.", listOf(
            Sheet.Action("DELETE", Ui.RED) {
                player?.runCatching { stop(); release() }
                player = null
                Store.delete(file)
                marksFile.delete()
                CaptureWidget.refreshAll(applicationContext)
                finish()
            },
            Sheet.Action("CANCEL", Ui.GREY) {}
        ))
    }

    override fun onPause() {
        player?.let { if (it.isPlaying) it.pause() }
        updatePlayIcon(false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player?.runCatching { stop(); release() }
        player = null
        super.onDestroy()
    }

    companion object {
        private val PAUSE = listOf("0000000", "0110110", "0110110", "0110110", "0110110", "0110110", "0000000")
    }
}
