package com.uygar.essentiallite

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * Ses açma + ses kısma birlikte basılınca hangi uygulamada olursanız olun hızlı erişim paneli açılır.
 * Panel içinde EKRAN (yakalama), NOT (yazma) ve SES (kayıt) modları var. Tasarım: 10, 11, 12.
 *
 * Panel ve perde pencereleri bir kez oluşturulur ve görünürlük değiştirilerek kullanılır; her açılış
 * ve kapanışta pencere eklenip kaldırılmaz. Böylece sistemin pencere animasyonuyla çakışma olmaz.
 */
class KeyComboService : AccessibilityService() {

    private var volUp = false
    private var volDown = false
    private var fired = false

    // Kalıcı pencereler (bir kez eklenir)
    private var stageContainer: FrameLayout? = null
    private var stageScrim: View? = null
    private var panelFocusable = false

    // Panel şu an gösteriliyorsa dolu
    private var panel: FrameLayout? = null
    private var scrim: View? = null

    // Ekran yakalama sonrası geçici pencereler
    private var thumbWin: View? = null
    private var cardWin: View? = null
    private var shotFile: File? = null

    // Panel içi ses kaydı
    private var rec: MediaRecorder? = null
    private var recFile: File? = null
    private var recStart = 0L
    private var recTick = 0
    private var voiceLabel: TextView? = null
    private var voiceTime: TextView? = null
    private var voiceField: DotField? = null
    private var voiceBtn: TextView? = null

    private val main = Handler(Looper.getMainLooper())

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun wm() = getSystemService(WINDOW_SERVICE) as WindowManager
    private fun dur(ms: Long) = Ui.motion(this, ms)

    private fun ov(w: Int, h: Int, grav: Int, flags: Int) =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
            .apply {
                gravity = grav
                windowAnimations = R.style.NoWindowAnim
            }

    private fun safeRemove(v: View) {
        runCatching { wm().removeView(v) }
    }

    /**
     * Pencereyi doğrudan kaldırmak son karede içeriğin bir an yanlış (beyaz) çizilmesine yol açıyor.
     * Bu yüzden önce görünmez yapılır, birkaç kare sonra kaldırılır.
     */
    private fun retire(v: View) {
        v.visibility = View.INVISIBLE
        main.postDelayed({ safeRemove(v) }, 100)
    }

    companion object {
        /** Çalışan servis; asistan oturumu (güç tuşu uzun basış) paneli buradan açar. */
        @Volatile var instance: KeyComboService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    /** Paneli (açık değilse) açar. Başka bir yerden, ör. asistan olarak çağrılır. */
    fun showPanel() {
        main.post { if (panel == null) togglePanel() }
    }

    // ---------- PC'den uzaktan kontrol (PC bağlantısı) ----------

    /** Ekranda (x1,y1)'den (x2,y2)'ye [ms] sürede bir dokunuş çizer; aynı nokta ise dokunma / uzun basma olur. */
    fun remoteStroke(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val path = android.graphics.Path().apply { moveTo(x1, y1); if (x1 != x2 || y1 != y2) lineTo(x2, y2) }
        val g = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, ms)).build()
        dispatchGesture(g, null, null)
    }

    fun remoteKey(k: String) {
        when (k) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "power" -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "volup" -> (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).adjustSuggestedStreamVolume(android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.USE_DEFAULT_STREAM_TYPE, android.media.AudioManager.FLAG_SHOW_UI)
            "voldown" -> (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).adjustSuggestedStreamVolume(android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.USE_DEFAULT_STREAM_TYPE, android.media.AudioManager.FLAG_SHOW_UI)
            "del" -> editFocused { it.dropLast(1) }
            "enter" -> rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
    }

    fun remoteText(s: String) = editFocused { it + s }

    private fun editFocused(change: (String) -> String) {
        val node = rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val cur = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val b = android.os.Bundle()
        b.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, change(cur))
        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        stageContainer?.let { safeRemove(it) }
        stageScrim?.let { safeRemove(it) }
        thumbWin?.let { safeRemove(it) }
        cardWin?.let { safeRemove(it) }
        stageContainer = null
        stageScrim = null
        super.onDestroy()
    }

    // ---------- Tuş algılama ----------

    /** Tek basışta karar bekleyen ses tuşları (birlikte basış değilse sonradan yeniden gönderilir). */
    private val pendingKeys = mutableSetOf<Int>()
    /** Kendi yeniden gönderdiğimiz tuşları tekrar yakalamamak için pencere. */
    private var replayUntil = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        if (down && event.repeatCount == 0) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) volUp = true else volDown = true
        }
        if (up) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) volUp = false else volDown = false
        }
        if (!(volUp && volDown)) fired = false

        if (System.currentTimeMillis() < replayUntil) return false

        if (volUp && volDown && !fired) {
            fired = true
            pendingKeys.clear()
            if (Prefs.hold(this)) {
                // 1 sn basılı tut modu: süre dolunca hâlâ basılıysa aç.
                main.postDelayed({ if (volUp && volDown) togglePanel() }, 1000)
            } else {
                main.post { togglePanel() }
            }
            return true
        }

        // Shizuku yoksa ses tuşlarına dokunmadan normal davran.
        if (!Shot.ready()) return false

        if (down && event.repeatCount == 0) {
            // Birlikte basış olabilir diye kısa süre beklenir; tek basışsa sonra yeniden gönderilir.
            pendingKeys.add(code)
            main.postDelayed({ commitSingle(code) }, 180)
            return true
        }
        if (down) return pendingKeys.contains(code)
        if (up && pendingKeys.remove(code)) {
            replaySingle(code)
            return true
        }
        return false
    }

    private fun commitSingle(code: Int) {
        if (pendingKeys.remove(code)) replaySingle(code)
    }

    /** Tek basışı sistemin ses çubuğunu da içeren normal biçimde yeniden gönderir. */
    private fun replaySingle(code: Int) {
        replayUntil = System.currentTimeMillis() + 400
        Thread { Shell.run("input", "keyevent", code.toString()) }.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // ---------- Panel aç / kapa ----------

    private fun togglePanel() {
        Ui.apply(this)
        if (panel != null) {
            hidePanel()
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Store.toast(this, "ÜZERİNDE GÖSTER İZNİ GEREKLİ")
            return
        }
        try {
            vibrate()
            openPanel()
        } catch (e: Throwable) {
            android.util.Log.e("KeyCombo", "panel hatası", e)
            Store.toast(this, "PANEL HATASI: ${e.javaClass.simpleName}")
        }
    }

    /** Titreşim ayarı açıksa panel açılırken kısa bir titreşim. */
    private fun vibrate() {
        if (!Prefs.haptic(this)) return
        runCatching {
            val v = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            v.vibrate(android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Panel ve perde pencerelerini ilk kullanımda bir kez ekler; sonra yalnızca görünürlükleri değişir. */
    private fun ensureStage() {
        if (stageContainer != null) return
        val s = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            visibility = View.GONE
        }
        wm().addView(s, ov(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            Gravity.TOP, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
        val c = FrameLayout(this).apply {
            setPadding(dp(12), 0, dp(12), dp(24))
            visibility = View.GONE
        }
        wm().addView(c, ov(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
        stageScrim = s
        stageContainer = c
        panelFocusable = false
    }

    /** Açılış: perde solarak koyulaşır; panel 56 dp yukarıdan, %94 ölçekten %100'e 340 ms'de gelir. */
    private fun openPanel() {
        ensureStage()
        val c = stageContainer ?: return
        val s = stageScrim ?: return
        c.animate().cancel()
        s.animate().cancel()
        c.removeAllViews()
        c.addView(buildMenu(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        c.translationY = dp(56).toFloat()
        c.scaleX = 0.94f
        c.scaleY = 0.94f
        c.alpha = 0f
        s.alpha = 0f
        c.visibility = View.VISIBLE
        s.visibility = View.VISIBLE
        c.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(dur(340)).setInterpolator(DecelerateInterpolator()).start()
        s.animate().alpha(0.5f).setDuration(dur(260)).start()
        panel = c
        scrim = s
    }

    /** Kapanış: 40 dp aşağı kayar, 240 ms'de söner; bitince pencere gizlenir (kaldırılmaz). */
    private fun hidePanel() {
        if (rec != null) stopVoice(keepPanel = false)
        val c = panel ?: return
        val s = scrim
        panel = null
        scrim = null
        runCatching { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(c.windowToken, 0) }
        val ms = dur(240)
        c.animate().cancel()
        c.animate().translationY(dp(40).toFloat()).alpha(0f).setDuration(ms)
            .withEndAction { finishHide() }.start()
        s?.animate()?.cancel()
        s?.animate()?.alpha(0f)?.setDuration(ms)?.start()
    }

    /** Pencereler görünmez olur; klavye için odak almış olabilirse ayar eski haline döner. */
    private fun finishHide() {
        if (panel != null) return // bu arada yeniden açıldıysa dokunma
        val c = stageContainer ?: return
        c.visibility = View.GONE
        c.removeAllViews()
        stageScrim?.visibility = View.GONE
        resetFocusable()
    }

    private fun resetFocusable() {
        val c = stageContainer ?: return
        if (panelFocusable) {
            panelFocusable = false
            runCatching {
                wm().updateViewLayout(c, ov(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
            }
        }
    }

    /** Ekran yakalama başlarken panel anında kaybolur (animasyonsuz). */
    private fun removePanelNow() {
        stageContainer?.let {
            it.animate().cancel()
            it.visibility = View.GONE
            it.removeAllViews()
        }
        stageScrim?.let {
            it.animate().cancel()
            it.visibility = View.GONE
        }
        panel = null
        scrim = null
        resetFocusable()
    }

    // ---------- Panel içerikleri ----------

    private fun header(title: String, onClose: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(if (title.isEmpty()) Ui.brand(this@KeyComboService) else Ui.kindLabel(this@KeyComboService, title),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(DotIcon(this@KeyComboService, Ui.ICON_CLOSE, Ui.GREY).apply {
            Ui.pressAnim(this)
            setOnClickListener { onClose() }
        })
    }

    /** Menü (tasarım 10): EKRAN / NOT / SES karoları. */
    private fun buildMenu(): View {
        val ctx = this
        val tiles = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
            addView(Ui.tile(ctx, "EKRAN", Ui.ICON_SHOT) { captureFlow() })
            addView(Ui.tile(ctx, "NOT", Ui.ICON_NOTE) { showNoteMode() })
            addView(Ui.tile(ctx, "SES", Ui.ICON_WAVE) { showVoiceMode() })
        }
        // AI satırı (tasarım 10): ekran görüntüsünü alıp Gemini'ye gönderir.
        val ai = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(ctx, Ui.SURFACE2, 32f)
            setPadding(dp(20), 0, dp(22), 0)
            addView(DotIcon(ctx, AI_STAR, Ui.WHITE, 40))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, "AI", 13f, Ui.WHITE, bold = true).apply { letterSpacing = 0.2f })
                addView(Ui.text(ctx, "GEMINI'YE SOR", 10f, Ui.GREY).apply {
                    letterSpacing = 0.14f
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(16), 0, 0, 0) })
            addView(DotIcon(ctx, AI_ARROW, Ui.GREY, 27))
            Ui.pressAnim(this)
            setOnClickListener { askGemini() }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(ctx, Ui.BG, 32f, Ui.BORDER2)
            setPadding(dp(16), dp(6), dp(16), dp(18))
            addView(header("", onClose = { hidePanel() }))
            addView(tiles)
            addView(ai, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply {
                setMargins(dp(4), dp(8), dp(4), 0)
            })
        }
    }

    private val AI_STAR = listOf("0001000", "0001000", "0011100", "1111111", "0011100", "0001000", "0001000")
    private val AI_ARROW = listOf("0000000", "0001000", "0000100", "1111110", "0000100", "0001000", "0000000")

    private var aiWin: View? = null
    private var aiAnim: android.animation.ValueAnimator? = null
    private var aiCancelled = false

    /**
     * AI (tasarım 13): panel kapanır, ekran görüntüsü alınır (panel görüntüye girmesin), "GEMINI AÇILIYOR"
     * kartı oynarken görüntü paylaşım dosyasına yazılır ve Gemini uygulaması görüntü ekli açılır.
     */
    private fun askGemini() {
        aiCancelled = false
        removePanelNow()
        Thread {
            val bmp = try { Shot.grab() } catch (e: Throwable) { null }
            main.post { showAiCard() }
            val t0 = System.currentTimeMillis()
            var uri: android.net.Uri? = null
            if (bmp != null) {
                try {
                    val dir = File(cacheDir, "ai").apply { mkdirs() }
                    val f = File(dir, "ekran.png")
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
                } catch (e: Throwable) {
                }
            }
            val wait = dur(1000) - (System.currentTimeMillis() - t0)
            if (wait > 0) Thread.sleep(wait)
            main.post {
                hideAiCard()
                when {
                    aiCancelled -> {}
                    uri == null -> Store.toast(this, "EKRAN ALINAMADI")
                    else -> launchGemini(uri)
                }
            }
        }.start()
    }

    private fun launchGemini(uri: android.net.Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            grantUriPermission("com.google.android.apps.bard", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent(send).setPackage("com.google.android.apps.bard"))
        } catch (e: Throwable) {
            try {
                startActivity(Intent.createChooser(send, "Gemini").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (t: Throwable) {
                Store.toast(this, "GEMINI AÇILAMADI")
            }
        }
    }

    private fun showAiCard() {
        hideAiCard(animate = false)
        val star = DotField(this, 7, 7)
        val bar = DotField(this, 24, 1)
        val lit = AI_STAR.flatMap { r -> r.map { it == '1' } }
        val total = dur(1000)
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = total
            repeatCount = 0
            addUpdateListener {
                val t = (it.animatedFraction * total).toLong()
                val ms = t * 1000f / total // tasarımdaki zaman ölçeğine çevir
                star.colors = IntArray(49) { i ->
                    if (!lit[i]) return@IntArray Ui.OFF
                    val d = Math.hypot((i / 7 - 3).toDouble(), (i % 7 - 3).toDouble())
                    val pop = ((ms - d * 60) / 250.0).toFloat().coerceIn(0f, 1f)
                    val pulse = 0.65f + 0.35f * Math.sin(ms / 120.0 + d).toFloat()
                    val a = (255 * pop * (if (pop >= 1f) pulse else 1f)).toInt().coerceIn(0, 255)
                    (a shl 24) or (Ui.WHITE and 0x00FFFFFF)
                }
                bar.colors = IntArray(24) { i ->
                    val on = ms >= 130 + i * 34
                    if (on) Ui.WHITE else Ui.OFF
                }
                star.invalidate()
                bar.invalidate()
            }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@KeyComboService, Ui.BG, 32f, Ui.BORDER2)
            setPadding(dp(16), dp(6), dp(16), dp(22))
            addView(LinearLayout(this@KeyComboService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.kindLabel(this@KeyComboService, "AI"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(DotIcon(this@KeyComboService, Ui.ICON_CLOSE, Ui.GREY).apply {
                    Ui.pressAnim(this)
                    setOnClickListener { aiCancelled = true; hideAiCard() }
                })
            })
            addView(star, LinearLayout.LayoutParams(dp(98), dp(98)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(14), 0, dp(18))
            })
            addView(Ui.text(this@KeyComboService, "GEMINI AÇILIYOR", 13f, Ui.WHITE, bold = true).apply {
                letterSpacing = 0.18f
                gravity = Gravity.CENTER
            })
            addView(Ui.text(this@KeyComboService, "Ekran görüntüsü ekleniyor", 11f, Ui.GREY).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(18))
            })
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(7)))
        }
        val holder = FrameLayout(this).apply {
            setPadding(dp(12), 0, dp(12), dp(24))
            addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        card.alpha = 0f
        card.translationY = dp(40).toFloat()
        wm().addView(holder, ov(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
        card.animate().alpha(1f).translationY(0f).setDuration(dur(300)).start()
        aiWin = holder
        aiAnim = anim
        anim.start()
    }

    private fun hideAiCard(animate: Boolean = true) {
        aiAnim?.cancel()
        aiAnim = null
        val w = aiWin ?: return
        aiWin = null
        if (animate) w.animate().alpha(0f).translationY(dp(24).toFloat()).setDuration(dur(240)).withEndAction { retire(w) }.start()
        else safeRemove(w)
    }

    private fun replacePanelContent(card: View, focusable: Boolean) {
        val container = stageContainer ?: return
        container.removeAllViews()
        container.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        if (focusable != panelFocusable) {
            panelFocusable = focusable
            val flags = if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching {
                wm().updateViewLayout(container, ov(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM, flags))
            }
        }
    }

    /** NOT modu (tasarım 12): panelin içinde yazı alanı ve KAYDET. Klavye için pencere odak alır. */
    private fun showNoteMode() {
        if (panel == null) return
        val et = EditText(this).apply {
            hint = "Notunu yaz..."
            setHintTextColor(Ui.DIM)
            setTextColor(Ui.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.TOP
            background = Ui.rounded(this@KeyComboService, Ui.SURFACE, 20f, Ui.BORDER)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@KeyComboService, Ui.BG, 32f, Ui.BORDER2)
            setPadding(dp(16), dp(6), dp(16), dp(16))
            addView(header("NOT", onClose = { hidePanel() }))
            addView(et, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140)).apply {
                setMargins(0, dp(8), 0, dp(12))
            })
            addView(LinearLayout(this@KeyComboService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(View(this@KeyComboService),
                    LinearLayout.LayoutParams(0, 1, 1f))
                addView(Ui.pill(this@KeyComboService, "KAYDET") { saveNoteText(et.text.toString()) })
            })
        }
        replacePanelContent(card, focusable = true)
        et.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun saveNoteText(text: String) {
        if (text.isBlank()) {
            hidePanel()
            return
        }
        val app = applicationContext
        Thread {
            val f = Store.newFile(app, "txt")
            f.writeText(text)
            CaptureWidget.refreshAll(app)
        }.start()
        Store.toast(this, "NOT KAYDEDİLDİ")
        hidePanel()
    }

    /** SES modu (tasarım 11): zaman, seviye alanı ve KAYDET / DURDUR. */
    private fun showVoiceMode() {
        if (panel == null) return
        val label = Ui.text(this, "HAZIR", 11f, Ui.GREY, bold = true).apply { letterSpacing = 0.2f }
        val time = Ui.display(this, "00:00", 64f).apply { gravity = Gravity.CENTER }
        val field = DotField(this, 48, 7).apply { colors = IntArray(48 * 7) { Ui.OFF } }
        val btn = Ui.pill(this, "● KAYDET") { if (rec == null) startVoice() else stopVoice(keepPanel = true) }
        voiceLabel = label
        voiceTime = time
        voiceField = field
        voiceBtn = btn

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@KeyComboService, Ui.BG, 32f, Ui.BORDER2)
            setPadding(dp(16), dp(6), dp(16), dp(18))
            addView(header("", onClose = { if (rec != null) stopVoice(keepPanel = false) else hidePanel() }).apply {
                removeViewAt(0)
                addView(label, 0, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(LinearLayout(this@KeyComboService).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(20), 0, dp(16))
                addView(time)
                addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                    setMargins(0, dp(16), 0, 0)
                })
            })
            addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        replacePanelContent(card, focusable = false)
    }

    private val voiceFrame = object : Runnable {
        override fun run() {
            if (rec == null) return
            recTick++
            paintVoice()
            voiceTime?.text = "%02d:%02d".format(((System.currentTimeMillis() - recStart) / 1000 / 60) % 100,
                ((System.currentTimeMillis() - recStart) / 1000) % 60)
            main.postDelayed(this, 125)
        }
    }

    private fun paintVoice() {
        val field = voiceField ?: return
        val cols = 48
        val rows = 7
        val colors = IntArray(cols * rows) { Ui.OFF }
        for (c in 0 until cols) {
            val t = recTick.toDouble()
            val v = Math.abs(Math.sin(t * 0.37 + c * 0.45) * Math.sin(t * 0.13 + c * 0.9 + 1.2))
            val h = 1 + (v * 6).toInt()
            val half = (h - 1) / 2
            for (r in 0 until rows) {
                if (Math.abs(r - 3) <= half) {
                    colors[r * cols + c] = if (c == cols - 1) Ui.RED else Ui.WHITE
                }
            }
        }
        field.colors = colors
        field.invalidate()
    }

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Store.toast(this, "MİKROFON İZNİ İÇİN UYGULAMAYI AÇ")
            return
        }
        val f = Store.newFile(this, "m4a")
        try {
            rec = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(f.absolutePath)
                prepare()
                start()
            }
        } catch (e: Throwable) {
            rec?.release()
            rec = null
            f.delete()
            Store.toast(this, "KAYIT BAŞLATILAMADI")
            return
        }
        recFile = f
        recStart = System.currentTimeMillis()
        recTick = 0
        voiceLabel?.text = Ui.withDot("KAYIT YAPILIYOR", Ui.RED)
        voiceLabel?.setTextColor(Ui.RED)
        voiceBtn?.text = "■ DURDUR"
        main.post(voiceFrame)
    }

    /** Kaydı bitirir; ses paneli kapanır ve yerine "SES KAYDEDİLDİ" kartı gelir. */
    private fun stopVoice(keepPanel: Boolean) {
        main.removeCallbacks(voiceFrame)
        val f = recFile
        try {
            rec?.stop()
        } catch (e: RuntimeException) {
            f?.delete()
        }
        rec?.release()
        rec = null
        recFile = null
        voiceField = null
        voiceTime = null
        voiceLabel = null
        voiceBtn = null
        val saved = f != null && f.exists()
        removePanelNow()
        if (saved && f != null) {
            shotFile = f
            showCard(f, "SES KAYDEDİLDİ")
        } else {
            Store.toast(this, "KAYIT ÇOK KISA")
        }
    }

    // ---------- Ekran yakalama ----------

    /** Parlama → kısa bekleme → yakalama → küçük resim köşeye iner → "EKRAN KAYDEDİLDİ" kartı. */
    private fun captureFlow() {
        removePanelNow()
        Thread {
            // Hızlı yol: ham görüntü bellekte alınır (~200 ms); parlama ve küçülme hemen başlar.
            // PNG yazma ve kaynak uygulamayı bulma, ekranda animasyon oynarken arka planda yapılır.
            val full = try { Shot.grab() } catch (e: Throwable) { null }
            if (full == null) {
                // Ham biçim desteklenmiyorsa eski (PNG'li) yola dön.
                val file = try { Shot.capture(this, null) } catch (e: Throwable) { null }
                val bmp = file?.let { decodeSample(it, 720) }
                main.post {
                    Ui.flashOverlay(this)
                    if (file != null) showCapture(file, bmp) else Store.toast(this, "EKRAN ALINAMADI")
                }
                return@Thread
            }
            val file = Store.newFile(this, "png")
            val w = 720
            val small = Bitmap.createScaledBitmap(full, w, (full.height * w / full.width.toFloat()).toInt(), true)
            main.post {
                Ui.flashOverlay(this)
                showCapture(file, small)
            }
            try { Shot.write(this, full, file, null) } catch (e: Throwable) { Store.toast(this, "KAYDEDİLEMEDİ") }
            full.recycle()
            val src = try { Shell.foregroundPackage() } catch (e: Throwable) { null }
            if (src != null) {
                Store.saveMeta(file, src)
                CaptureWidget.refreshAll(this)
            }
            // Okunan metin aramada kullanılır; AI etiket ayarı açıksa başlık/etiket de eklenir.
            Ocr.afterSave(this, file)
        }.start()
    }

    private fun showCapture(file: File, bmp: Bitmap?) {
        dismissCapture()
        shotFile = file
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels

        val thumb = buildThumb(bmp, file, 1f)
        wm().addView(thumb, ov(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.START, THUMB_FLAGS))
        thumbWin = thumb

        // Tasarımdaki hedef: sol alt, 16 dp kenar, 0.2 ölçek (556/914 yükseklikte).
        val targetX = dp(16).toFloat()
        val targetY = sh * (556f / 914f)
        thumb.pivotX = 0f
        thumb.pivotY = 0f
        thumb.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        thumb.animate()
            .scaleX(0.2f).scaleY(0.2f)
            .translationX(targetX).translationY(targetY)
            .setStartDelay(dur(140))
            .setDuration(dur(520))
            .setInterpolator(PathInterpolator(0.3f, 0.8f, 0.2f, 1f))
            .withEndAction { shrinkThumb(thumb, bmp, file, sw, sh, targetX, targetY) }
            .start()

        main.postDelayed({ showCard(file, "EKRAN KAYDEDİLDİ") }, dur(140) + dur(520) + 60)
    }

    /** Tam ekran küçük resim penceresi: ekran koordinatları (sistem çubuklarına göre kaymaz). */
    private val THUMB_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /**
     * Beyaz çerçeveli küçük resim. [scale] 1 tam ekran halidir; 0.2'de çerçeve, köşe yuvarlaklığı ve
     * boşluk da aynı oranda küçülür; böylece küçülmüş pencere animasyonun son karesiyle birebir aynı görünür.
     */
    private fun buildThumb(bmp: Bitmap?, file: File, scale: Float): FrameLayout = FrameLayout(this).apply {
        // Zemin şeffaf, yalnızca beyaz çerçeve: görüntü çizilmeden önceki ilk karede ekran beyaz kalmasın.
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 40f * scale * density
            setColor(Color.TRANSPARENT)
            setStroke((6 * density * scale).toInt().coerceAtLeast(1), Ui.WHITE)
        }
        val pad = (6 * density * scale).toInt()
        setPadding(pad, pad, pad, pad)
        val img = ImageView(this@KeyComboService).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = Ui.rounded(this@KeyComboService, Ui.BG, 34f * scale)
            clipToOutline = true
        }
        addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setOnClickListener { openEditor(file) }
    }

    /**
     * Küçülme bitince pencere yeniden boyutlandırılmaz (sistem onu kaydırıp şekli bozuyordu).
     * Bunun yerine aynı görünümde küçük bir pencere eklenir, büyük olan bir kare sonra kaldırılır.
     */
    private fun shrinkThumb(big: View, bmp: Bitmap?, file: File, sw: Int, sh: Int, x: Float, y: Float) {
        if (thumbWin !== big) return
        val small = buildThumb(bmp, file, 0.2f)
        val lp = ov((sw * 0.2f).toInt(), (sh * 0.2f).toInt(), Gravity.TOP or Gravity.START,
            THUMB_FLAGS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        lp.x = x.toInt()
        lp.y = y.toInt()
        small.setOnTouchListener { _, ev -> if (ev.action == MotionEvent.ACTION_OUTSIDE) { dismissCapture(); true } else false }
        runCatching { wm().addView(small, lp) }
        thumbWin = small
        // Küçük pencere çizilene kadar büyük olan görünür kalır; sonra önce gizlenir, ardından kaldırılır.
        main.postDelayed({ retire(big) }, 80)
    }

    /** Kaydedildi kartı (tasarım 10): başlık, GERİ AL, DÜZENLE (yalnızca ekran görüntüsü) ve TAMAM. */
    private fun showCard(file: File, title: String) {
        if (shotFile != file) return
        val isShot = title.startsWith("EKRAN")
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@KeyComboService, Ui.BG, 32f, Ui.BORDER2)
            setPadding(dp(20), dp(16), dp(14), dp(12))
            addView(Ui.kindLabel(this@KeyComboService, title))
            if (isShot) {
                // Ekrandaki yazı: varsa gösterilir. Yazıya dokunmak kartı kapatmaz; basılı tutmak kopyalar.
                addView(Ui.text(this@KeyComboService, "", 11f, Ui.GREY).apply {
                    letterSpacing = 0.04f
                    setLineSpacing(0f, 1.35f)
                    maxLines = 6
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(10), dp(6), dp(4))
                    visibility = View.GONE
                    isClickable = true
                    setOnLongClickListener {
                        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("EL", text))
                        Store.toast(this@KeyComboService, "METİN KOPYALANDI")
                        true
                    }
                    ocrView = this
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            addView(LinearLayout(this@KeyComboService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(6), 0, 0)
                addView(action("GERİ AL", Ui.GREY) { undoCapture() })
                if (isShot) {
                    addView(action("DÜZENLE", Ui.WHITE) {
                        openEditor(file)
                        dismissCapture()
                    })
                }
                addView(action("TAMAM", Ui.WHITE) { dismissCapture() }.apply {
                    // Basılı tutunca ekran görüntüsündeki okunmuş metin panoya kopyalanır.
                    setOnLongClickListener {
                        val text = Info.readOcr(file).trim()
                        if (text.isEmpty()) Store.toast(this@KeyComboService, "METİN YOK")
                        else {
                            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText("EL", text))
                            Store.toast(this@KeyComboService, "METİN KOPYALANDI")
                        }
                        true
                    }
                })
            })
        }
        // Pencere tüm ekranı kaplar: kartın dışındaki boş yere dokunmak kartı kapatır.
        // Kartın kendisi dokunuşu yutar, böylece kart içine basmak kapatmaz.
        val holder = FrameLayout(this).apply {
            setOnClickListener { dismissCapture() }
            addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(dp(12), 0, dp(12), dp(24))
            })
        }
        card.alpha = 0f
        card.translationY = dp(24).toFloat()
        // Belli süre işlem yapılmazsa kart ve küçük resim kendiliğinden kalkar.
        wm().addView(holder, ov(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.START, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
        card.animate().alpha(1f).translationY(0f).setDuration(dur(300)).start()
        cardWin = holder
        // OCR ekran görüntüsü kaydedildikten sonra arka planda biter; hazır olunca yazıyı göster (en çok 8 sn bekler).
        ocrView?.let { v ->
            var tries = 0
            val poll = object : Runnable {
                override fun run() {
                    if (cardWin !== holder) return
                    val t = Info.readOcr(file).trim()
                    if (t.isNotEmpty()) { v.text = t; v.visibility = View.VISIBLE; return }
                    if (++tries < 27) main.postDelayed(this, 300)
                }
            }
            main.postDelayed(poll, 300)
        }
        autoDismiss?.let { main.removeCallbacks(it) }
        autoDismiss = Runnable { if (shotFile == file) dismissCapture() }
        main.postDelayed(autoDismiss!!, AUTO_DISMISS_MS)
    }

    private var autoDismiss: Runnable? = null
    private var ocrView: TextView? = null
    private val AUTO_DISMISS_MS = 6000L

    private fun action(label: String, color: Int, onClick: () -> Unit) =
        Ui.text(this, label, 11f, color, bold = true).apply {
            letterSpacing = 0.14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }

    private fun undoCapture() {
        val f = shotFile
        dismissCapture()
        if (f != null) {
            Thread {
                Store.delete(f)
                CaptureWidget.refreshAll(applicationContext)
            }.start()
            Store.toast(this, "GERİ ALINDI")
        }
    }

    private fun openEditor(file: File) {
        startActivity(Intent(this, EditorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("path", file.absolutePath))
    }

    /** Küçük resim ve kart kısa bir solmayla kaldırılır. */
    private fun dismissCapture() {
        autoDismiss?.let { main.removeCallbacks(it) }
        autoDismiss = null
        shotFile = null
        val t = thumbWin
        val c = cardWin
        thumbWin = null
        cardWin = null
        if (t != null) {
            t.animate().cancel()
            t.animate().alpha(0f).setDuration(dur(200)).withEndAction { retire(t) }.start()
        }
        if (c != null) {
            c.animate().cancel()
            c.animate().alpha(0f).setDuration(dur(200)).withEndAction { retire(c) }.start()
        }
    }

    private fun decodeSample(f: File, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }
}
