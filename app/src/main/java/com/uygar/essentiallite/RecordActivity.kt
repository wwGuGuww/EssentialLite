package com.uygar.essentiallite

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Kayıt ekranı (tasarım 04a/04b): seviye alanı, zamanlayıcı, halo ve halkalı kayıt düğmesi. */
class RecordActivity : BaseActivity() {

    private val cols = 33
    private val rows = 7
    private var recorder: MediaRecorder? = null
    private var out: File? = null
    private var startedAt = 0L
    private var tick = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var label: TextView
    private lateinit var timer: TextView
    private lateinit var field: DotField
    private lateinit var halo: HaloView
    private lateinit var ring: View
    private lateinit var core: View
    private lateinit var hint: TextView

    private val frame = object : Runnable {
        override fun run() {
            if (recorder == null) return
            tick++
            paintField()
            halo.tick = tick
            halo.invalidate()
            timer.text = clockText((System.currentTimeMillis() - startedAt) / 1000)
            handler.postDelayed(this, 125)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** 28 noktalı halo: kayıt sırasında kırmızı bir kuyruk halka boyunca döner. */
    private inner class HaloView(ctx: Context) : View(ctx) {
        var tick = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(cx, cy) - dp(4)
            val recording = recorder != null
            val n = 28
            for (i in 0 until n) {
                val a = (i.toDouble() / n) * Math.PI * 2 - Math.PI / 2
                val lag = (((tick - i) % n) + n) % n
                paint.color = when {
                    !recording -> Ui.OFF
                    lag < 4 -> Ui.RED
                    lag < 8 -> 0xFF7A1A20.toInt()
                    lag < 12 -> 0xFF3A1215.toInt()
                    else -> Ui.OFF
                }
                c.drawCircle((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat(), dp(2).toFloat(), paint)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@RecordActivity, "‹", 18f).apply {
                contentDescription = "Back"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@RecordActivity, "VOICE"),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        label = Ui.text(this, "READY", 11f, Ui.GREY, bold = true).apply {
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        }
        timer = Ui.display(this, "00:00", 80f).apply { gravity = Gravity.CENTER }
        field = DotField(this, cols, rows).apply { colors = IntArray(cols * rows) { Ui.OFF } }

        halo = HaloView(this)
        ring = View(this).apply { setOnClickListener { toggle() } }
        core = View(this)
        val button = FrameLayout(this).apply {
            addView(halo, FrameLayout.LayoutParams(dp(136), dp(136), Gravity.CENTER))
            addView(ring, FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER))
            addView(core, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        }

        hint = Ui.text(this, "", 11f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
            setOnClickListener { toggle() }
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(timer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(20), 0, dp(28))
            })
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))
            addView(button, LinearLayout.LayoutParams(dp(136), dp(136)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(28), 0, dp(12))
            })
            addView(hint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(40))
            addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(center, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        refreshButton()
    }

    private fun clockText(sec: Long) = "%02d:%02d".format((sec / 60) % 100, sec % 60)

    /** Halka rengi ve iç şekil: boşta kırmızı daire, kayıtta kırmızı kare ve kırmızı halka. */
    private fun refreshButton() {
        val rec = recorder != null
        ring.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(2), if (rec) Ui.RED else Ui.WHITE)
        }
        core.background = GradientDrawable().apply {
            shape = if (rec) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
            if (rec) cornerRadius = dp(6).toFloat()
            setColor(Ui.RED)
        }
        hint.text = if (rec) "■ STOP" else "● SAVE"
    }

    /** Kayıt sırasında her kare yeni bir seviye deseni çizer; kayıt yokken sönük. */
    private fun paintField() {
        val colors = IntArray(cols * rows)
        if (recorder == null) {
            colors.fill(Ui.OFF)
        } else {
            for (c in 0 until cols) {
                val t = tick.toDouble()
                val v = abs(sin(t * 0.37 + c * 0.45)) * abs(sin(t * 0.13 + c * 0.9 + 1.2))
                val h = 1 + (v * 6).roundToInt()
                val half = (h - 1) / 2
                for (r in 0 until rows) {
                    val on = abs(r - 3) <= half
                    colors[r * cols + c] = when {
                        !on -> Ui.OFF
                        c == cols - 1 -> Ui.RED
                        else -> Ui.WHITE
                    }
                }
            }
        }
        field.colors = colors
        field.invalidate()
    }

    private fun toggle() {
        if (recorder == null) start() else stop()
    }

    private fun start() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        val f = Store.newFile(this, "m4a")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(f.absolutePath)
            prepare()
            start()
        }
        out = f
        startedAt = System.currentTimeMillis()
        tick = 0
        label.text = Ui.withDot("RECORDING", Ui.RED)
        label.setTextColor(Ui.RED)
        refreshButton()
        handler.post(frame)
    }

    private fun stop() {
        handler.removeCallbacks(frame)
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            out?.delete() // çok kısa kayıtta stop() hata verir, dosyayı sil
        }
        recorder?.release()
        recorder = null
        paintField()
        refreshButton()
        val saved = out?.exists() == true
        Store.toast(this, if (saved) "VOICE MEMO SAVED" else "RECORDING TOO SHORT")
        if (saved) {
            val app = applicationContext
            Thread { CaptureWidget.refreshAll(app) }.start()
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(frame)
        recorder?.release()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) start()
        else Store.toast(this, "MICROPHONE PERMISSION NEEDED")
    }
}
