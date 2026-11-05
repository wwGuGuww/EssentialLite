package com.uygar.essentiallite

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private val PHONE_ICON = listOf("0011100", "0010100", "0010100", "0010100", "0010100", "0011100", "0011100")
private val PC_ICON = listOf("1111111", "1000001", "1000001", "1111111", "0011100", "0111110", "0000000")
private val LOCK_ICON = listOf("0011100", "0100010", "0100010", "1111111", "1110111", "1110111", "1111111")
private val IMG_ICON = listOf("1111111", "1000001", "1010101", "1001001", "1010101", "1000001", "1111111")
private val NOTE_ICON = listOf("0111110", "0100010", "0111110", "0100010", "0111110", "0100010", "0111110")
private val FILE_ICON = listOf("0111100", "0100110", "0100011", "0100001", "0100001", "0100001", "0111111")
private val WAVE_ICON = listOf("0001000", "0101010", "0101010", "1101011", "0101010", "0101010", "0001000")

private fun iconFor(name: String): List<String> = when (name.substringAfterLast('.', "").lowercase()) {
    "png", "jpg", "jpeg", "webp" -> IMG_ICON
    "txt" -> NOTE_ICON
    in Store.AUDIO -> WAVE_ICON
    else -> FILE_ICON
}

private fun dot(s: String, color: Int): CharSequence {
    val sp = SpannableStringBuilder(s)
    if (s.startsWith("●")) sp.setSpan(ForegroundColorSpan(color), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return sp
}

private fun sectionLabel(ctx: Context, s: String) = Ui.text(ctx, s, 10f, Ui.RED).apply {
    letterSpacing = 0.2f
    setPadding(0, ToolUi.dp(ctx, 8), 0, ToolUi.dp(ctx, 8))
}

// ---------------------------------------------------------------------------------------------
// Ana ekran (tasarım 21): bağlantı kartı, hızlı eylemler, senkron anahtarları, son aktarımlar
// ---------------------------------------------------------------------------------------------

class LinkActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var t = 0
    private var wire: DotsView? = null
    private var pingView: TextView? = null
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private val listener: () -> Unit = { render() }

    private val tick = object : Runnable {
        override fun run() {
            t++
            wire?.let { w ->
                val head = (t / 2) % 22
                val on = Link.live
                w.colors = IntArray(18) { i ->
                    if (!on) Ui.OFF else if (i == head) Ui.RED else if (i < head && head < 18) Ui.GREY2 else Ui.PROG
                }
                w.invalidate()
            }
            h.postDelayed(this, 120)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(root, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        status = ToolUi.screen(this, "PC BAĞLANTISI", "", scroll, topGap = 16)
    }

    override fun onResume() {
        super.onResume()
        Link.listeners.add(listener)
        if (Link.st == Link.St.OFF && Link.prefs(this).getBoolean("auto", false)) Link.reconnectSaved(this)
        render()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        Link.listeners.remove(listener)
        h.removeCallbacks(tick)
    }

    private fun render() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val live = Link.live
        val connecting = Link.st == Link.St.CONNECTING
        val saved = Link.saved(this)
        status.text = dot(if (live) "● BAĞLI" else if (connecting) "BAĞLANIYOR…" else "BAĞLI DEĞİL", Ui.RED)
        status.setTextColor(if (live) Ui.WHITE else Ui.GREY)
        root.removeAllViews()

        // Bağlantı kartı
        val pcShort = if (live) Link.pcName.take(10) else "PC YOK"
        val col = { icon: List<String>, label: String ->
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(DotIcon(this@LinkActivity, icon, if (live) Ui.WHITE else Ui.PROG, 47))
                addView(Ui.text(this@LinkActivity, label, 9.5f, Ui.GREY).apply { letterSpacing = 0.14f; maxLines = 1 }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(8), 0, 0) })
            }
        }
        val w = DotsView(this, 18, 1, 4f, 4f).also { wire = it }
        val link = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(col(PHONE_ICON, "PHONE (1)"), LinearLayout.LayoutParams(dp(74), WRAP))
            addView(w, LinearLayout.LayoutParams(0, dp(4), 1f).apply { setMargins(dp(6), 0, dp(6), dp(14)) })
            addView(col(PC_ICON, pcShort), LinearLayout.LayoutParams(dp(74), WRAP))
        }
        val info = when {
            live -> "${Link.pcOs} · Wİ-Fİ · ŞİFRELİ"
            connecting -> "ŞİFRELİ KANAL KURULUYOR"
            Link.note.isNotEmpty() -> Link.note
            saved -> "KAYITLI: " + Link.prefs(this).getString("pc", "PC")
            else -> "PC'DEKİ QR KODU OKUT"
        }
        val pingTv = Ui.text(this, "", 10f, Ui.GREY).apply {
            letterSpacing = 0.12f
            gravity = Gravity.END
            setLineSpacing(0f, 1.5f)
            val sp = SpannableStringBuilder("GECİKME\n" + if (live && Link.ping > 0) "${Link.ping} MS" else "--")
            sp.setSpan(ForegroundColorSpan(Ui.WHITE), 8, sp.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = sp
        }.also { pingView = it }
        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(LinearLayout(this@LinkActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.display(this@LinkActivity, if (live) Link.pcName.take(12) else if (connecting) "BAĞLANIYOR" else "BAĞLI DEĞİL", 34f, if (live || connecting) Ui.WHITE else Ui.PROG).apply { maxLines = 1 })
                addView(Ui.text(this@LinkActivity, info, 10f, Ui.GREY).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(8), 0, 0) })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(pingTv)
        }
        val main = outlineBtn(this, if (live) "BAĞLANTIYI KES" else "QR İLE BAĞLAN", true, if (live) Ui.LINE else Ui.WHITE, if (live) Ui.GREY else Ui.WHITE) {
            if (live) Link.disconnect(this, forget = false)
            else startActivity(Intent(this, LinkScanActivity::class.java))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@LinkActivity, Ui.SURFACE, 32f, Ui.BORDER)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(link)
            addView(nameRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(main, LinearLayout.LayoutParams(MATCH, dp(56)).apply { setMargins(0, dp(18), 0, 0) })
            if (saved && !live && !connecting) addView(outlineBtn(this@LinkActivity, "KAYITLI PC'YE BAĞLAN", false, Ui.LINE, Ui.GREY) {
                Link.prefs(this@LinkActivity).edit().putBoolean("auto", true).apply()
                Link.reconnectSaved(this@LinkActivity)
            }, LinearLayout.LayoutParams(MATCH, dp(48)).apply { setMargins(0, dp(10), 0, 0) })
        }
        root.addView(card, LinearLayout.LayoutParams(MATCH, WRAP))

        // Hızlı eylemler
        fun quick(label: String, big: CharSequence, cls: Class<*>) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@LinkActivity, Ui.SURFACE, 24f, Ui.BORDER)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(Ui.text(this@LinkActivity, label, 10f, Ui.GREY).apply { letterSpacing = 0.16f })
            addView(View(this@LinkActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(Ui.display(this@LinkActivity, "", 24f).apply { text = big })
            Ui.pressAnim(this)
            setOnClickListener {
                if (!Link.live) Store.toast(this@LinkActivity, "ÖNCE PC'YE BAĞLAN") else startActivity(Intent(this@LinkActivity, cls))
            }
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(quick("PC'YE GÖNDER", "DOSYA ↗", LinkSendActivity::class.java), LinearLayout.LayoutParams(0, dp(92), 1f).apply { setMargins(0, 0, dp(5), 0) })
            addView(quick("PC'DEN YÖNET", SpannableStringBuilder("KONTROL ●").also { it.setSpan(ForegroundColorSpan(Ui.RED), 8, 9, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }, LinkControlActivity::class.java), LinearLayout.LayoutParams(0, dp(92), 1f).apply { setMargins(dp(5), 0, 0, 0) })
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(16), 0, 0) })

        // Senkron
        root.addView(sectionLabel(this, "● SENKRON"), LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(16), 0, 0) })
        data class F(val k: String, val label: String, val sub: String, val def: Boolean)
        val feats = listOf(
            F("notif", "BİLDİRİMLERİ PC'YE YOLLA", "Tüm uygulamalar · yanıt PC'den", true),
            F("clip", "PANOYU EŞLE", "Kopyala telefonda, yapıştır PC'de", true),
            F("files", "DOSYA AKTARIMI", "Essential Lite kayıtları · sürükle-bırak", true),
            F("mirror", "EKRANI PC'YE YANSIT", "Sadece izle · kontrol ayrı izin", false)
        )
        feats.forEach { f ->
            val on = Link.feature(this, f.k, f.def) && (f.k != "mirror" || LinkControl.active)
            root.addView(View(this).apply { setBackgroundColor(Ui.OFF) }, LinearLayout.LayoutParams(MATCH, 1))
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                addView(LinearLayout(this@LinkActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@LinkActivity, f.label, 12f, if (on && live) Ui.WHITE else Ui.GREY, bold = true).apply { letterSpacing = 0.1f })
                    addView(Ui.text(this@LinkActivity, f.sub, 10f, Ui.GREY).apply { letterSpacing = 0.06f; setLineSpacing(0f, 1.3f) }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(5), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(DesignSwitch(this@LinkActivity, on) { v -> featureChanged(f.k, v) })
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // Son aktarımlar
        root.addView(sectionLabel(this, "● SON AKTARIMLAR"), LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(16), 0, 0) })
        val recent = Link.recent(this).take(3)
        if (recent.isEmpty()) root.addView(Ui.text(this, "HENÜZ AKTARIM YOK", 10f, Ui.GREY).apply { letterSpacing = 0.14f; setPadding(0, dp(12), 0, dp(12)) })
        recent.forEach { r ->
            root.addView(View(this).apply { setBackgroundColor(Ui.OFF) }, LinearLayout.LayoutParams(MATCH, 1))
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
                addView(DotIcon(this@LinkActivity, iconFor(r.name), Ui.WHITE, 21))
                addView(LinearLayout(this@LinkActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@LinkActivity, r.name, 12f, Ui.WHITE).apply { letterSpacing = 0.08f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    addView(Ui.text(this@LinkActivity, r.meta + " · " + ago(r.at), 10f, Ui.GREY).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(4), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
                addView(Ui.text(this@LinkActivity, if (r.toPc) "→ PC" else "← PC", 10f, if (r.toPc) Ui.RED else Ui.GREY).apply { letterSpacing = 0.12f })
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        root.addView(View(this).apply { setBackgroundColor(Ui.BORDER2) }, LinearLayout.LayoutParams(MATCH, 1).apply { setMargins(0, dp(8), 0, 0) })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(24))
            addView(DotIcon(this@LinkActivity, LOCK_ICON, Ui.GREY, 21))
            addView(Ui.text(this@LinkActivity, "Uçtan uca şifreli. Sunucuda hiçbir şey saklanmaz.", 10f, Ui.GREY).apply { letterSpacing = 0.08f; setLineSpacing(0f, 1.3f) },
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(10), 0, 0, 0) })
        }, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun ago(t: Long): String {
        val m = (System.currentTimeMillis() - t) / 60000
        return when {
            m < 1 -> "ŞİMDİ"
            m < 60 -> "$m DK ÖNCE"
            m < 1440 -> "${m / 60} SA ÖNCE"
            else -> Store.dateOf(t)
        }
    }

    private fun featureChanged(k: String, on: Boolean) {
        Link.setFeature(this, k, on)
        when (k) {
            "notif" -> if (on && !LinkNotif.enabled(this)) {
                Store.toast(this, "ESSENTIAL LITE İÇİN BİLDİRİM ERİŞİMİ VER")
                runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            "mirror" -> if (on) {
                if (!Link.live) { Store.toast(this, "ÖNCE PC'YE BAĞLAN"); Link.setFeature(this, k, false) }
                else startActivity(Intent(this, LinkControlActivity::class.java).putExtra("mirror", true))
            } else LinkControl.stopAll()
        }
        render()
    }
}

// ---------------------------------------------------------------------------------------------
// QR okut (tasarım 21a)
// ---------------------------------------------------------------------------------------------

private class ScanFrame(ctx: Context) : View(ctx) {
    var found = false
    var line = 0f
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.RED }

    override fun onDraw(c: Canvas) {
        p.color = if (found) Ui.RED else Ui.WHITE
        val s = 34 * d
        val m = 24 * d
        val r = 14 * d
        val w = width.toFloat()
        val h = height.toFloat()
        fun corner(x: Float, y: Float, sx: Int, sy: Int) {
            val path = android.graphics.Path()
            path.moveTo(x, y + sy * s)
            path.lineTo(x, y + sy * r)
            path.quadTo(x, y, x + sx * r, y)
            path.lineTo(x + sx * s, y)
            c.drawPath(path, p)
        }
        corner(m, m, 1, 1); corner(w - m, m, -1, 1); corner(m, h - m, 1, -1); corner(w - m, h - m, -1, -1)
        if (!found) c.drawRect(40 * d, h * (0.06f + 0.86f * line), w - 40 * d, h * (0.06f + 0.86f * line) + 2 * d, fill)
    }
}

class LinkScanActivity : BaseActivity() {
    private class Owner : LifecycleOwner {
        val reg = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = reg
    }

    private val owner = Owner()
    private val exec = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    private lateinit var frame: ScanFrame
    private lateinit var hint: TextView
    private lateinit var status: TextView
    private lateinit var preview: PreviewView
    private var handled = false
    private var host = ""
    private var port = 0
    private var sheet: View? = null
    private var scrim: View? = null
    private var anim: ValueAnimator? = null
    private val listener: () -> Unit = { onLink() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner.reg.currentState = Lifecycle.State.CREATED
        fun dp(v: Int) = ToolUi.dp(this, v)
        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
        frame = ScanFrame(this)
        hint = Ui.text(this, "KODU ÇERÇEVEYE AL", 10f, Ui.GREY).apply { letterSpacing = 0.18f; gravity = Gravity.CENTER }
        val cam = FrameLayout(this).apply {
            background = Ui.rounded(this@LinkScanActivity, Ui.SURFACE, 32f, Ui.BORDER)
            clipToOutline = true
            addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(frame, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(hint, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(0, 0, 0, dp(16)) })
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.display(this@LinkScanActivity, "PC'DEKİ\nKODU OKUT", 44f).apply { setLineSpacing(0f, 0.9f) })
            addView(Ui.text(this@LinkScanActivity, "", 11f, Ui.GREY).apply {
                val s = SpannableStringBuilder("01 PC'de bilgisayardaki Essential Lite'ı aç\n02 Ekrandaki QR'ı çerçeveye al")
                s.setSpan(ForegroundColorSpan(Ui.RED), 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(ForegroundColorSpan(Ui.RED), s.indexOf("02"), s.indexOf("02") + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                text = s
                letterSpacing = 0.08f
                setLineSpacing(0f, 1.5f)
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(14), 0, 0) })
            addView(cam, LinearLayout.LayoutParams(dp(320), dp(320)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(18), 0, 0) })
            addView(LinearLayout(this@LinkScanActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(View(this@LinkScanActivity).apply { background = Ui.rounded(this@LinkScanActivity, Ui.RED, 6f) }, LinearLayout.LayoutParams(dp(6), dp(6)))
                addView(Ui.text(this@LinkScanActivity, "AYNI Wİ-Fİ'DE OLMAK YETERLİ · İNTERNETSİZ ÇALIŞIR", 10f, Ui.GREY).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(8), 0, 0, 0) })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(View(this@LinkScanActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(LinearLayout(this@LinkScanActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(outlineBtn(this@LinkScanActivity, "KODU ELLE GİR", false, Ui.LINE, Ui.WHITE) { manual() }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(0, 0, dp(5), 0) })
                addView(outlineBtn(this@LinkScanActivity, "YENİDEN", false, Ui.LINE, Ui.GREY) { rescan() }, LinearLayout.LayoutParams(dp(120), dp(56)).apply { setMargins(dp(5), 0, 0, 0) })
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        status = ToolUi.screen(this, "QR OKUT", "ARANIYOR", body, topGap = 18)
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { frame.line = it.animatedFraction; frame.invalidate() }
            start()
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 41)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (code == 41 && res.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
        else { hint.text = "KAMERA İZNİ GEREKLİ · KODU ELLE GİR" }
    }

    private fun startCamera() {
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val provider = fut.get()
            val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val an = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            an.setAnalyzer(exec) { proxy ->
                val img = proxy.image
                if (img == null || handled) { proxy.close(); return@setAnalyzer }
                scanner.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { list ->
                        val raw = list.firstOrNull { it.rawValue != null }?.rawValue
                        if (raw != null) runOnUiThread { onCode(raw) }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, an)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCode(raw: String) {
        if (handled) return
        val p = Link.parse(raw)
        if (p == null) { hint.text = "BU BİR ESSENTIAL KODU DEĞİL"; return }
        handled = true
        host = p.first; port = p.second
        frame.found = true; frame.invalidate()
        hint.text = "QR OKUNDU"; hint.setTextColor(Ui.RED)
        status.text = dot("● ALINDI", Ui.RED); status.setTextColor(Ui.GREY)
        Link.connect(this, p.first, p.second, p.third, true)
    }

    private fun manual() {
        Sheet.input(this, "BAĞLANTI KODU", "el://…", "") { v ->
            if (Link.parse(v) == null) Store.toast(this, "KOD GEÇERSİZ") else onCode(v.trim())
        }
    }

    private fun rescan() {
        handled = false
        Link.disconnect(this, forget = false, quiet = true)
        closeSheet()
        frame.found = false; frame.invalidate()
        hint.text = "KODU ÇERÇEVEYE AL"; hint.setTextColor(Ui.GREY)
        status.text = "ARANIYOR"
    }

    private fun onLink() {
        when (Link.st) {
            Link.St.CONFIRM -> if (sheet == null) openSheet()
            Link.St.OFF -> if (handled && sheet == null) {
                hint.text = Link.note.ifEmpty { "BAĞLANTI KESİLDİ" }
                hint.setTextColor(Ui.RED)
                handled = false
                frame.found = false; frame.invalidate()
            }
            else -> {}
        }
    }

    private fun openSheet() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val decor = window.decorView as ViewGroup
        val sc = View(this).apply { setBackgroundColor(0xC7000000.toInt()); isClickable = true }
        val code = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            Link.code.forEachIndexed { i, ch ->
                addView(Ui.display(this@LinkScanActivity, ch.toString(), 64f).apply {
                    gravity = Gravity.CENTER
                    alpha = 0f
                    animate().alpha(1f).setStartDelay(i * 60L).setDuration(Ui.motion(this@LinkScanActivity, 360)).start()
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Ui.SURFACE)
                val r = 32 * resources.displayMetrics.density
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setStroke(dp(1), Ui.BORDER)
            }
            setPadding(dp(20), dp(26), dp(20), dp(28))
            addView(LinearLayout(this@LinkScanActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(this@LinkScanActivity, "", 11f, Ui.WHITE, bold = true).apply { text = dot("● PC BULUNDU", Ui.RED); letterSpacing = 0.18f }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.text(this@LinkScanActivity, "${Link.pcName} · ${Link.pcOs}", 10f, Ui.GREY).apply { letterSpacing = 0.14f })
            })
            addView(Ui.text(this@LinkScanActivity, "ONAY KODU · PC'DEKİYLE AYNI MI?", 10f, Ui.GREY).apply { letterSpacing = 0.18f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(code, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(10), 0, 0) })
            addView(Ui.text(this@LinkScanActivity, "Bu PC bildirimlerini alacak ve dosya yollayabilecek. Uzak kontrol için ayrıca izin istenir.", 10f, Ui.GREY).apply { letterSpacing = 0.08f; setLineSpacing(0f, 1.5f) }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(18), 0, 0) })
            addView(LinearLayout(this@LinkScanActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(outlineBtn(this@LinkScanActivity, "REDDET", false, Ui.LINE, Ui.RED) { rescan() }, LinearLayout.LayoutParams(dp(120), dp(56)).apply { setMargins(0, 0, dp(5), 0) })
                addView(outlineBtn(this@LinkScanActivity, "ONAYLA", true, Ui.WHITE, Ui.WHITE) {
                    Link.accept(this@LinkScanActivity, host, port)
                    Store.toast(this@LinkScanActivity, "PC BAĞLANDI")
                    finish()
                }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(dp(5), 0, 0, 0) })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
        }
        decor.addView(sc, FrameLayout.LayoutParams(MATCH, MATCH))
        decor.addView(box, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        box.translationY = dp(500).toFloat()
        box.animate().translationY(0f).setDuration(Ui.motion(this, 380)).setInterpolator(DecelerateInterpolator()).start()
        sheet = box; scrim = sc
    }

    private fun closeSheet() {
        val decor = window.decorView as ViewGroup
        sheet?.let { runCatching { decor.removeView(it) } }
        scrim?.let { runCatching { decor.removeView(it) } }
        sheet = null; scrim = null
    }

    override fun onStart() { super.onStart(); owner.reg.currentState = Lifecycle.State.STARTED }
    override fun onResume() { super.onResume(); Link.listeners.add(listener); owner.reg.currentState = Lifecycle.State.RESUMED }
    override fun onPause() { super.onPause(); Link.listeners.remove(listener); owner.reg.currentState = Lifecycle.State.STARTED }
    override fun onStop() { super.onStop(); owner.reg.currentState = Lifecycle.State.CREATED }

    override fun onDestroy() {
        anim?.cancel()
        owner.reg.currentState = Lifecycle.State.DESTROYED
        exec.shutdown()
        scanner.close()
        // Onaylanmadan çıkıldıysa yarım kalan bağlantı kapatılır.
        if (Link.st == Link.St.CONFIRM || Link.st == Link.St.CONNECTING) Link.disconnect(this, forget = false, quiet = true)
        super.onDestroy()
    }
}

// ---------------------------------------------------------------------------------------------
// PC'ye gönder (tasarım 21b)
// ---------------------------------------------------------------------------------------------

class LinkSendActivity : BaseActivity() {
    private var tab = 0
    private val selected = LinkedHashSet<String>()
    private var entries: List<Entry> = emptyList()
    private var phase = 0 // 0 seçim, 1 gönderiliyor, 2 bitti
    private var pct = 0
    private lateinit var count: TextView
    private lateinit var size: TextView
    private lateinit var list: LinearLayout
    private lateinit var tabsRow: LinearLayout
    private lateinit var prog: LinearLayout
    private lateinit var progText: TextView
    private lateinit var progDots: DotsView
    private lateinit var btn: TextView
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        count = Ui.display(this, "00", 72f)
        size = Ui.text(this, "", 11f, Ui.GREY).apply { letterSpacing = 0.14f; gravity = Gravity.END; setLineSpacing(0f, 1.5f) }
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        progText = Ui.text(this, "", 10f, Ui.RED).apply { letterSpacing = 0.14f }
        progDots = DotsView(this, 40, 1, 4f, 4f)
        prog = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@LinkSendActivity, Ui.SURFACE, 24f, Ui.BORDER2)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            visibility = View.GONE
            addView(progText)
            addView(progDots, LinearLayout.LayoutParams(MATCH, dp(4)).apply { setMargins(0, dp(10), 0, 0) })
        }
        btn = Ui.text(this, "", 12f, Ui.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            Ui.pressAnim(this)
            setOnClickListener { onButton() }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(count, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(size)
        }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
        val body = FrameLayout(this).apply {
            addView(LinearLayout(this@LinkSendActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(head)
                addView(tabsRow, LinearLayout.LayoutParams(MATCH, dp(44)).apply { setMargins(0, dp(16), 0, 0) })
                addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, dp(16), 0, 0) })
            }, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(LinearLayout(this@LinkSendActivity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.BG)
                setPadding(0, dp(8), 0, 0)
                addView(prog, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, dp(10)) })
                addView(btn, LinearLayout.LayoutParams(MATCH, dp(64)))
            }, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        }
        ToolUi.screen(this, "PC'YE GÖNDER", "→ " + Link.pcName.ifEmpty { "PC" }, body, topGap = 16)
        Thread {
            val all = Store.all(this)
            runOnUiThread { entries = all; render() }
        }.start()
        render()
    }

    private fun nameOf(e: Entry): String = when {
        e.title.isNotBlank() -> e.title
        e.kind == "NOT" -> e.preview.ifEmpty { "Not" }
        e.kind == "SES" -> "Ses kaydı " + Store.clock(e.time)
        e.kind == "EKRAN" -> "Ekran görüntüsü " + Store.clock(e.time)
        else -> e.file.name
    }

    private fun render() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val names = listOf("HEPSİ", "EKRAN", "NOT", "SES")
        tabsRow.removeAllViews()
        names.forEachIndexed { i, n ->
            tabsRow.addView(ToolUi.ghost(this, n, tab == i) { tab = i; render() }.apply { textSize = 10.5f; letterSpacing = 0.08f; background = Ui.rounded(this@LinkSendActivity, Color.TRANSPARENT, 22f, if (tab == i) Ui.WHITE else Ui.LINE) },
                LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(if (i == 0) 0 else dp(3), 0, if (i == 3) 0 else dp(3), 0) })
        }
        val shown = entries.filter { tab == 0 || (tab == 1 && it.kind == "EKRAN") || (tab == 2 && it.kind == "NOT") || (tab == 3 && it.kind == "SES") }
        list.removeAllViews()
        if (shown.isEmpty()) list.addView(Ui.text(this, "KAYIT YOK", 11f, Ui.GREY).apply { letterSpacing = 0.16f; gravity = Gravity.CENTER; setPadding(0, dp(40), 0, dp(40)) }, LinearLayout.LayoutParams(MATCH, WRAP))
        for (e in shown) {
            val on = e.file.absolutePath in selected
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(this@LinkSendActivity, if (on) Ui.SURFACE else Color.TRANSPARENT, 24f, if (on) Ui.WHITE else Ui.BORDER)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                addView(View(this@LinkSendActivity).apply {
                    background = Ui.rounded(this@LinkSendActivity, Color.TRANSPARENT, 11f, if (on) Ui.RED else Ui.LINE)
                    if (on) (background as android.graphics.drawable.GradientDrawable).also { }
                }.let { ring ->
                    FrameLayout(this@LinkSendActivity).apply {
                        addView(ring, FrameLayout.LayoutParams(MATCH, MATCH))
                        if (on) addView(View(this@LinkSendActivity).apply { background = Ui.rounded(this@LinkSendActivity, Ui.RED, 5f) }, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER))
                    }
                }, LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(LinearLayout(this@LinkSendActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@LinkSendActivity, nameOf(e), 12f, Ui.WHITE).apply { letterSpacing = 0.06f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    addView(Ui.text(this@LinkSendActivity, Store.stamp(e.time) + " · " + e.file.extension.uppercase() + " · " + Link.sizeText(e.file.length()), 10f, Ui.GREY).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(6), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(14), 0, dp(8), 0) })
                addView(Ui.text(this@LinkSendActivity, e.kind, 10f, Ui.RED).apply { letterSpacing = 0.14f })
                setOnClickListener {
                    if (phase == 1) return@setOnClickListener
                    if (!selected.add(e.file.absolutePath)) selected.remove(e.file.absolutePath)
                    phase = 0
                    render()
                }
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, dp(8)) })
        }
        list.addView(View(this), LinearLayout.LayoutParams(1, dp(150)))
        val total = selected.sumOf { File(it).length() }
        count.text = selected.size.toString().padStart(2, '0')
        size.text = SpannableStringBuilder("SEÇİLİ\n" + if (total < 100 * 1024) "< 0,1 MB" else Link.sizeText(total)).also { it.setSpan(ForegroundColorSpan(Ui.WHITE), 7, it.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        val ready = selected.isNotEmpty() || phase == 2
        val label = when (phase) { 1 -> "GÖNDERİLİYOR…"; 2 -> "TAMAM · YENİ SEÇİM"; else -> if (selected.isNotEmpty()) "PC'YE GÖNDER · ${selected.size}" else "ÖĞE SEÇ" }
        btn.text = dot("● $label", Ui.RED)
        btn.setTextColor(if (ready) Ui.WHITE else Ui.GREY)
        btn.background = Ui.rounded(this, Ui.BG, 32f, if (ready) Ui.WHITE else Ui.LINE)
        prog.visibility = if (phase == 0) View.GONE else View.VISIBLE
        progText.text = if (phase == 2) "● GÖNDERİLDİ · PC İNDİRİLENLER" else "GÖNDERİLİYOR… %$pct"
        progText.setTextColor(if (phase == 2) Ui.WHITE else Ui.RED)
        paintProg()
    }

    private fun paintProg() {
        val lit = (pct * 0.4f).roundToInt()
        progDots.colors = IntArray(40) { i -> if (i < lit) Ui.WHITE else if (i == lit) Ui.RED else Ui.BORDER2 }
        progDots.invalidate()
    }

    private fun onButton() {
        if (phase == 2) { phase = 0; pct = 0; render(); return }
        if (phase == 1 || selected.isEmpty()) return
        if (!Link.live) { Store.toast(this, "PC BAĞLI DEĞİL"); return }
        phase = 1; pct = 0
        val files = selected.map { File(it) }
        render()
        Link.sendFiles(this, files, { p, _ -> ui.post { pct = p; progText.text = "GÖNDERİLİYOR… %$p"; paintProg() } }) {
            phase = 2; pct = 100; selected.clear(); render()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Uzaktan kontrol (tasarım 21c)
// ---------------------------------------------------------------------------------------------

private class CursorField(ctx: Context) : View(ctx) {
    var live = false
    var t = 0
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((11 * 16 * d).toInt(), (7 * 16 * d).toInt())

    override fun onDraw(c: Canvas) {
        val cx = 5 + Math.round(sin(t * 0.18) * 3.6)
        val cy = 3 + Math.round(cos(t * 0.13) * 2.2)
        val click = (t / 14) % 2 == 0 && t % 14 < 3
        for (r in 0 until 7) for (col in 0 until 11) {
            val dist = hypot((col - cx).toDouble(), (r - cy).toDouble())
            var color = Ui.OFF
            var s = 3f
            if (live && dist < 0.9) { color = if (click) Ui.RED else Ui.WHITE; s = if (click) 9f else 7f }
            else if (live && dist < 1.6) { color = Ui.PROG }
            p.color = if (live) color else if (color == Ui.OFF) Ui.OFF else color
            c.drawCircle((col * 16 + 8) * d, (r * 16 + 8) * d, s / 2 * d, p)
        }
    }
}

class LinkControlActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var t = 0
    private lateinit var timeView: TextView
    private lateinit var limitView: TextView
    private lateinit var field: CursorField
    private lateinit var liveRow: LinearLayout
    private lateinit var idleTxt: TextView
    private lateinit var preview: FrameLayout
    private lateinit var status: TextView
    private lateinit var permBox: LinearLayout
    private lateinit var main: TextView
    private var mirrorOnly = false
    private val listener: () -> Unit = { paint() }

    private val tick = object : Runnable {
        override fun run() {
            t++
            field.t = t; field.live = LinkControl.active; field.invalidate()
            if (LinkControl.active) {
                val s = (System.currentTimeMillis() - LinkControl.startedAt) / 1000
                timeView.text = ToolUi.p2(s / 60) + ":" + ToolUi.p2(s % 60)
            }
            h.postDelayed(this, 125)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        mirrorOnly = intent.getBooleanExtra("mirror", false)
        timeView = Ui.display(this, "00:00", 84f)
        limitView = Ui.text(this, "", 11f, Ui.GREY).apply { letterSpacing = 0.14f; gravity = Gravity.END; setLineSpacing(0f, 1.5f) }
        field = CursorField(this)
        liveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(this@LinkControlActivity).apply { background = Ui.rounded(this@LinkControlActivity, Ui.RED, 4f) }, LinearLayout.LayoutParams(dp(8), dp(8)))
            addView(Ui.text(this@LinkControlActivity, "${Link.pcName.ifEmpty { "PC" }} KONTROL EDİYOR", 10f, Ui.WHITE).apply { letterSpacing = 0.16f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(8), 0, 0, 0) })
        }
        idleTxt = Ui.text(this, "OTURUM BEKLİYOR", 10f, Ui.GREY).apply { letterSpacing = 0.18f }
        preview = FrameLayout(this).apply {
            addView(field, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            addView(liveRow, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(dp(18), dp(16), 0, 0) })
            addView(idleTxt, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(0, 0, 0, dp(14)) })
        }
        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        main = outlineBtn(this, "", true, Ui.WHITE, Ui.WHITE) { toggle() }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(timeView, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(limitView)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(head)
            addView(preview, LinearLayout.LayoutParams(MATCH, dp(188)).apply { setMargins(0, dp(16), 0, 0) })
            addView(ScrollView(this@LinkControlActivity).apply { isVerticalScrollBarEnabled = false; addView(permBox) }, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, dp(16), 0, 0) })
            addView(main, LinearLayout.LayoutParams(MATCH, dp(60)).apply { setMargins(0, dp(16), 0, 0) })
        }
        status = ToolUi.screen(this, "UZAKTAN KONTROL", "", body, topGap = 16)
        paint()
        if (mirrorOnly && Link.live && !LinkControl.active) requestProjection()
    }

    override fun onResume() {
        super.onResume()
        Link.listeners.add(listener)
        h.post(tick)
        paint()
    }

    override fun onPause() {
        super.onPause()
        Link.listeners.remove(listener)
        h.removeCallbacks(tick)
    }

    private fun accessOk() = KeyComboService.instance != null

    private fun paint() {
        fun dp(v: Int) = ToolUi.dp(this, v)
        val live = LinkControl.active
        status.text = dot(if (live) "● AKTİF" else "KAPALI", Ui.RED)
        status.setTextColor(if (live) Ui.RED else Ui.GREY)
        if (!live) timeView.text = "00:00"
        timeView.setTextColor(if (live) Ui.WHITE else Ui.PROG)
        limitView.text = SpannableStringBuilder("OTURUM\n" + if (Link.feature(this, "c_auto", true)) "MAKS 05:00" else "SINIRSIZ").also { it.setSpan(ForegroundColorSpan(Ui.WHITE), 7, it.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        liveRow.visibility = if (live) View.VISIBLE else View.GONE
        idleTxt.visibility = if (live) View.GONE else View.VISIBLE
        preview.background = Ui.rounded(this, Ui.SURFACE, 28f, if (live) Ui.RED else Ui.BORDER)
        field.alpha = if (live) 1f else 0.45f
        field.live = live

        permBox.removeAllViews()
        permBox.addView(sectionLabel(this, "● GEREKEN İZİNLER").apply { setPadding(0, dp(4), 0, dp(8)) })
        fun perm(label: String, sub: String, ok: Boolean, onTap: () -> Unit) {
            permBox.addView(View(this).apply { setBackgroundColor(Ui.OFF) }, LinearLayout.LayoutParams(MATCH, 1))
            permBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(11), 0, dp(11))
                addView(View(this@LinkControlActivity).apply { background = Ui.rounded(this@LinkControlActivity, if (ok) Ui.RED else Color.TRANSPARENT, 5f, Ui.RED) }, LinearLayout.LayoutParams(dp(10), dp(10)))
                addView(LinearLayout(this@LinkControlActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@LinkControlActivity, label, 12f, Ui.WHITE, bold = true).apply { letterSpacing = 0.08f })
                    addView(Ui.text(this@LinkControlActivity, sub, 10f, Ui.GREY).apply { letterSpacing = 0.06f }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(4), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
                addView(Ui.text(this@LinkControlActivity, if (ok) "VERİLDİ" else "VER", 10f, if (ok) Ui.GREY else Ui.WHITE, bold = !ok).apply { letterSpacing = 0.12f; setPadding(dp(8), dp(10), 0, dp(10)) })
                if (!ok) setOnClickListener { onTap() }
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        perm("ERİŞİLEBİLİRLİK HİZMETİ", "Dokunma, kaydırma ve yazmayı PC'den uygular", accessOk()) {
            Store.toast(this, "ESSENTIAL LITE'I AÇ")
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        perm("EKRAN YAKALAMA", "Ekranı PC'ye şifreli akıtır", live) { toggle() }
        permBox.addView(sectionLabel(this, "● GİZLİLİK").apply { setPadding(0, dp(14), 0, dp(8)) })
        fun opt(k: String, def: Boolean, label: String, sub: String) {
            permBox.addView(View(this).apply { setBackgroundColor(Ui.OFF) }, LinearLayout.LayoutParams(MATCH, 1))
            permBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(60)
                addView(LinearLayout(this@LinkControlActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@LinkControlActivity, label, 12f, Ui.WHITE, bold = true).apply { letterSpacing = 0.1f })
                    addView(Ui.text(this@LinkControlActivity, sub, 10f, Ui.GREY).apply { letterSpacing = 0.06f; setLineSpacing(0f, 1.3f) }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(5), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(DesignSwitch(this@LinkControlActivity, Link.feature(this@LinkControlActivity, k, def)) { v ->
                    Link.setFeature(this@LinkControlActivity, k, v)
                    if (k == "c_dim" && v && !Shot.ready()) Store.toast(this@LinkControlActivity, "KARARTMA İÇİN SHIZUKU GEREKİR")
                    paint()
                })
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        opt("c_touch", true, "DOKUNMA VE YAZMA", "Kapalıyken PC sadece izler")
        opt("c_dim", false, "TELEFON EKRANINI KARART", "Yanındaki biri oturumu görmesin")
        opt("c_auto", true, "5 DK SONRA OTOMATİK KES", "Unutursan oturum kendiliğinden kapanır")

        main.text = dot(if (live && LinkControl.watchOnly) "● KONTROLE İZİN VER" else if (live) "● OTURUMU SONLANDIR" else "● KONTROLE İZİN VER", Ui.RED)
        main.setTextColor(if (live) Ui.RED else Ui.WHITE)
        main.background = Ui.rounded(this, Color.TRANSPARENT, 30f, if (live) Ui.RED else Ui.WHITE)
    }

    private fun toggle() {
        if (LinkControl.active && LinkControl.watchOnly) {
            // Sadece izleme açıkken "kontrole izin ver": oturum yeniden kurulmadan tam kontrole geçilir.
            if (!accessOk()) {
                Store.toast(this, "ÖNCE ERİŞİLEBİLİRLİK HİZMETİNİ AÇ")
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            } else { LinkControl.watchOnly = false; Link.setFeature(this, "mirror", false); paint() }
            return
        }
        if (LinkControl.active) { LinkControl.stopAll(); return }
        if (!Link.live) { Store.toast(this, "ÖNCE PC'YE BAĞLAN"); return }
        if (!accessOk() && Link.feature(this, "c_touch", true)) {
            Store.toast(this, "ÖNCE ERİŞİLEBİLİRLİK HİZMETİNİ AÇ")
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            return
        }
        requestProjection()
    }

    private fun requestProjection() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mgr.createScreenCaptureIntent(), 77)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(req, res, data)
        if (req == 77 && res == RESULT_OK && data != null) {
            LinkControl.begin(this, res, data, watchOnly = mirrorOnly)
            if (mirrorOnly) { Link.setFeature(this, "mirror", true); finish() }
        } else if (req == 77) {
            Link.setFeature(this, "mirror", false)
            if (mirrorOnly) finish()
        }
        paint()
    }
}
