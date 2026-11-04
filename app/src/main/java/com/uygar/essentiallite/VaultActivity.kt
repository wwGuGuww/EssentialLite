package com.uygar.essentiallite

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Gizli bölüm ("ARŞİV"). Açarken parmak izi (olmazsa telefonun kendi PIN/desen/şifresi) istenir; ekran kilidi hiç
 * yoksa gizli girilen 4 haneli yedek şifre kullanılır. İçerik şifreli saklanır. Ekran görüntüsü alınamaz.
 * Ekrandan çıkınca ya da uygulama arka plana gidince kendiliğinden kapanır.
 */
class VaultActivity : BaseActivity() {

    private lateinit var root: FrameLayout
    private var list: LinearLayout? = null
    private var unlocked = false
    private var editing = false
    private var player: MediaPlayer? = null
    private var holdUntil = 0L
    private val thumbs = LruCache<String, Bitmap>(16)

    private var sheetScrim: View? = null
    private var sheetView: View? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_PICK = 51
        private var wrong = 0
        private var lockedUntil = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        root = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        setContentView(root)
        if (intent.getStringExtra("mode") == "change") changeFlow() else gate()
    }

    private fun show(v: View) {
        root.removeAllViews()
        root.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    // ---------- Kapı ----------

    /** Önce parmak izi (olmazsa telefonun kendi şifresi). Ekran kilidi hiç yoksa 4 haneli yedek şifre. */
    private fun gate() {
        if (System.currentTimeMillis() < lockedUntil) {
            Store.toast(this, "BİRAZ SONRA TEKRAR DENE")
            finish()
            return
        }
        if (AppLock.available(this)) {
            AppLock.authenticate(this, onOk = { open() }, onFail = { finish() }, onUnavailable = { pinGate() })
        } else {
            pinGate()
        }
    }

    private fun pinGate() {
        if (!Vault.hasPin(this)) {
            pinPage("ŞİFRE BELİRLE", "4 hane") { first ->
                pinPage("TEKRAR", "aynı şifreyi gir") { second ->
                    if (first == second && Vault.setPassword(this, first)) open()
                    else { Store.toast(this, "UYUŞMADI"); finish() }
                }
            }
        } else {
            pinPage("ŞİFRE", "", onForgot = { confirmForgot() }) { entered ->
                if (Vault.verify(this, entered)) {
                    wrong = 0
                    open()
                } else {
                    wrong++
                    if (wrong >= 5) {
                        lockedUntil = System.currentTimeMillis() + 60_000L
                        wrong = 0
                        Store.toast(this, "ÇOK FAZLA YANLIŞ DENEME")
                        finish()
                    } else {
                        Store.toast(this, "YANLIŞ ŞİFRE")
                        pinGate()
                    }
                }
            }
        }
    }

    /** 4 haneli gizli giriş sayfası: rakamlar görünmez, yalnızca dolan noktalar. */
    private fun pinPage(title: String, sub: String, onForgot: (() -> Unit)? = null, withHeader: Boolean = false, onDone: (String) -> Unit) {
        val entry = StringBuilder()
        val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val dotViews = (0 until 4).map {
            View(this).also { v ->
                v.background = Ui.rounded(this, Color.TRANSPARENT, 7f, Ui.LINE)
                dots.addView(v, LinearLayout.LayoutParams(dp(14), dp(14)).apply { setMargins(dp(10), 0, dp(10), 0) })
            }
        }
        fun paint() = dotViews.forEachIndexed { i, v ->
            v.background = if (i < entry.length) Ui.rounded(this, Ui.RED, 7f) else Ui.rounded(this, Color.TRANSPARENT, 7f, Ui.LINE)
        }
        fun key(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 22f
            typeface = Typeface.MONOSPACE
            setTextColor(Ui.WHITE)
            gravity = Gravity.CENTER
            background = if (label.isEmpty()) null else Ui.rounded(this@VaultActivity, Ui.SURFACE2, 36f)
            if (label.isNotEmpty()) { Ui.pressAnim(this); setOnClickListener { onClick() } }
        }
        val pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
        rows.forEach { r ->
            pad.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                r.forEach { k ->
                    addView(key(k) {
                        if (k == "⌫") {
                            if (entry.isNotEmpty()) entry.deleteCharAt(entry.length - 1)
                            paint()
                        } else if (entry.length < 4) {
                            entry.append(k)
                            paint()
                            if (entry.length == 4) {
                                val v = entry.toString()
                                entry.clear()
                                dots.postDelayed({ onDone(v) }, 120)
                            }
                        }
                    }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(dp(14), dp(7), dp(14), dp(7)) })
                }
            })
        }
        show(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(52), dp(20), dp(32))
            if (withHeader) addView(pageHeader("ARŞİV ŞİFRESİ"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            else addView(Ui.brand(this@VaultActivity), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(View(this@VaultActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(Ui.kindLabel(this@VaultActivity, title))
            if (sub.isNotEmpty()) addView(Ui.text(this@VaultActivity, sub, 11f, Ui.GREY).apply { setPadding(0, dp(8), 0, 0) })
            addView(dots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(28), 0, dp(36)) })
            addView(pad)
            if (onForgot != null) addView(Ui.text(this@VaultActivity, "ŞİFREMİ UNUTTUM", 10f, Ui.GREY, bold = true).apply {
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
                setOnClickListener { onForgot() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(View(this@VaultActivity), LinearLayout.LayoutParams(1, 0, 1f))
        })
    }

    // ---------- Şifre değiştirme ve sıfırlama ----------

    private fun changeFlow() {
        if (!Vault.hasPin(this)) {
            Store.toast(this, "ARŞİV ŞİFRESİ YOK")
            finish()
            return
        }
        val go = {
            pinPage("ESKİ ŞİFRE", "1 / 3", withHeader = true) { old ->
                if (!Vault.verify(this, old)) {
                    Store.toast(this, "YANLIŞ ŞİFRE")
                    finish()
                } else {
                    pinPage("YENİ ŞİFRE", "2 / 3", withHeader = true) { a ->
                        pinPage("TEKRAR", "3 / 3", withHeader = true) { b ->
                            if (a == b && Vault.setPassword(this, a)) Store.toast(this, "ŞİFRE DEĞİŞTİ")
                            else Store.toast(this, "UYUŞMADI")
                            finish()
                        }
                    }
                }
            }
        }
        if (AppLock.available(this)) AppLock.authenticate(this, onOk = { go() }, onFail = { finish() }, onUnavailable = { go() })
        else go()
    }

    /** Şifre unutulursa: kurtarma yok, bu yüzden arşiv tamamen silinir (onay ister). */
    private fun confirmForgot() {
        showSheet("ARŞİV SİLİNSİN Mİ?", listOf(
            Row("ARŞİVİ SİL VE SIFIRLA", Ui.ICON_TRASH, Ui.RED) {
                Vault.wipe(this)
                Store.toast(this, "ARŞİV SIFIRLANDI")
                finish()
            },
            Row("VAZGEÇ", Ui.ICON_CLOSE, Ui.WHITE) { pinGate() }
        ))
    }

    /** Arşiv içi sayfaların başlığı: geri oku + ARŞİV etiketi (liste ve not ekranlarıyla aynı). */
    private fun pageHeader(label: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Ui.text(this@VaultActivity, "‹", 18f).apply {
                contentDescription = "Geri"
            setPadding(0, 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        addView(Ui.kindLabel(this@VaultActivity, label), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    // ---------- Liste ----------

    private fun open() {
        unlocked = true
        Vault.markReady(this)
        showList()
    }

    private fun showList() {
        editing = false
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@VaultActivity, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@VaultActivity, "ARŞİV"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list = l
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(this@VaultActivity, Ui.SURFACE, 40f, Ui.BORDER2)
            addView(dockItem("NOT", Ui.ICON_NOTE) { editNote(null) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(View(this@VaultActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(dockItem("GÖRSEL", Ui.ICON_SHOT) { pick() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        show(FrameLayout(this).apply {
            addView(LinearLayout(this@VaultActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(52), dp(20), 0)
                addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(16)) })
                addView(ScrollView(this@VaultActivity).apply {
                    overScrollMode = View.OVER_SCROLL_NEVER
                    setPadding(0, 0, 0, dp(120))
                    clipToPadding = false
                    addView(l)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
            })
            addView(dock, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(80), Gravity.BOTTOM).apply {
                setMargins(dp(20), 0, dp(20), dp(24))
            })
        })
        render()
    }

    private fun dockItem(label: String, icon: List<String>, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(DotIcon(this@VaultActivity, icon, Ui.WHITE, 40))
        addView(Ui.text(this@VaultActivity, label, 10f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
        Ui.pressAnim(this)
        setOnClickListener { onClick() }
    }

    private fun render() {
        val l = list ?: return
        l.removeAllViews()
        val items = Vault.list(this)
        if (items.isEmpty()) {
            l.addView(Ui.text(this, "Henüz bir şey yok.", 13f, Ui.GREY).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(64), 0, 0)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            return
        }
        items.forEach { l.addView(card(it)) }
    }

    private fun card(it: Vault.Item): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@VaultActivity, Ui.SURFACE, 24f, Ui.BORDER)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.kindLabel(this@VaultActivity, it.kind), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@VaultActivity, Store.stamp(it.time), 11f, Ui.GREY).apply { letterSpacing = 0.1f })
        })
        when (it.kind) {
            "NOT" -> {
                val body = Ui.text(this, "", 14f, Ui.WHITE).apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setLineSpacing(0f, 1.2f)
                    setPadding(0, dp(10), 0, 0)
                }
                box.addView(body)
                Thread {
                    val t = try { String(Vault.read(this, it.file), Charsets.UTF_8) } catch (e: Throwable) { "" }
                    runOnUiThread { body.text = t.trim() }
                }.start()
            }
            "GÖRSEL" -> {
                val iv = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = Ui.rounded(this@VaultActivity, Ui.BG, 14f, Ui.BORDER2)
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, 14 * v.resources.displayMetrics.density) }
                    }
                }
                box.addView(iv, LinearLayout.LayoutParams(dp(96), dp(96)).apply { setMargins(0, dp(10), 0, 0) })
                thumbs.get(it.file.path)?.let { b -> iv.setImageBitmap(b) } ?: Thread {
                    val b = decode(it, 300)
                    if (b != null) thumbs.put(it.file.path, b)
                    runOnUiThread { if (b != null) iv.setImageBitmap(b) }
                }.start()
            }
        }
        box.setOnClickListener { _ -> view(it) }
        box.setOnLongClickListener { _ -> Feel.pop(this); options(it); true }
        box.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(8))
        }
        return box
    }

    private fun decode(item: Vault.Item, maxSide: Int): Bitmap? = try {
        val bytes = Vault.read(this, item.file)
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }

    // ---------- Eylem sayfası (uygulamadakiyle aynı görünüm) ----------

    private class Row(val label: String, val icon: List<String>, val color: Int, val onClick: () -> Unit)

    private fun showSheet(title: String, rows: List<Row>) {
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
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Ui.SURFACE)
                cornerRadii = floatArrayOf(32 * d, 32 * d, 32 * d, 32 * d, 0f, 0f, 0f, 0f)
                setStroke((1 * d).toInt(), Ui.BORDER2)
            }
            setPadding(dp(20), dp(14), dp(20), dp(32))
            addView(LinearLayout(this@VaultActivity).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
                for (c in intArrayOf(Ui.LINE, Ui.WHITE, Ui.LINE)) {
                    addView(View(this@VaultActivity).apply { background = Ui.rounded(this@VaultActivity, c, 2f) },
                        LinearLayout.LayoutParams(dp(4), dp(4)).apply { setMargins(dp(3), 0, dp(3), 0) })
                }
            })
            addView(Ui.kindLabel(this@VaultActivity, title), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(20), 0, dp(10))
            })
            rows.forEachIndexed { i, r ->
                if (i > 0) addView(View(this@VaultActivity).apply { setBackgroundColor(Ui.BORDER) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
                addView(LinearLayout(this@VaultActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(Ui.text(this@VaultActivity, r.label, 14f, r.color, bold = true).apply { letterSpacing = 0.18f },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(DotIcon(this@VaultActivity, r.icon, r.color, 27), LinearLayout.LayoutParams(dp(27), dp(27)))
                    Ui.pressAnim(this)
                    setOnClickListener { hideSheet(); r.onClick() }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)))
            }
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

    private fun options(item: Vault.Item) {
        showSheet(item.kind, listOf(
            Row("AÇ", Ui.ICON_OPEN, Ui.WHITE) { view(item) },
            Row("GERİ AL", Ui.ICON_UNDO, Ui.WHITE) {
                Thread {
                    val ok = Vault.moveOut(this, item)
                    runOnUiThread { Store.toast(this, if (ok) "ANA LİSTEYE ALINDI" else "TAŞINAMADI"); render() }
                }.start()
            },
            Row("SİL", Ui.ICON_TRASH, Ui.RED) {
                main.postDelayed({
                    showSheet("SİLİNSİN Mİ?", listOf(
                        Row("SİL", Ui.ICON_TRASH, Ui.RED) { Vault.delete(item.file); render() },
                        Row("VAZGEÇ", Ui.ICON_CLOSE, Ui.WHITE) {}
                    ))
                }, 260)
            }
        ))
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    // ---------- İçerik ----------

    private fun view(item: Vault.Item) {
        when (item.kind) {
            "NOT" -> editNote(item)
            "GÖRSEL" -> Thread {
                val b = decode(item, 2048)
                runOnUiThread {
                    if (b == null) { Store.toast(this, "AÇILAMADI"); return@runOnUiThread }
                    val dlg = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
                    dlg.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    dlg.setContentView(ImageView(this).apply {
                        setBackgroundColor(Color.BLACK)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageBitmap(b)
                        setOnClickListener { dlg.dismiss() }
                    })
                    dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    dlg.show()
                }
            }.start()
            "SES" -> play(item)
        }
    }

    private fun play(item: Vault.Item) {
        player?.runCatching { stop(); release() }
        player = null
        Thread {
            try {
                val bytes = Vault.read(this, item.file)
                val src = object : MediaDataSource() {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position >= bytes.size) return -1
                        val n = minOf(size, bytes.size - position.toInt())
                        System.arraycopy(bytes, position.toInt(), buffer, offset, n)
                        return n
                    }
                    override fun getSize() = bytes.size.toLong()
                    override fun close() {}
                }
                runOnUiThread {
                    player = MediaPlayer().apply {
                        setDataSource(src)
                        prepare()
                        start()
                        setOnCompletionListener { it.release(); player = null }
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread { Store.toast(this, "ÇALINAMADI") }
            }
        }.start()
    }

    /** Not yazma/düzenleme sayfası (uygulamadaki not ekranıyla aynı görünüm). */
    private fun editNote(item: Vault.Item?) {
        editing = true
        val et = EditText(this).apply {
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.DIM)
            hint = "Notunu yaz..."
            typeface = Typeface.MONOSPACE
            textSize = 15f
            gravity = Gravity.TOP
            background = DotGrid(dp(24).toFloat(), Ui.GRID)
            setPadding(dp(4), dp(16), dp(4), dp(16))
        }
        if (item != null) Thread {
            val t = try { String(Vault.read(this, item.file), Charsets.UTF_8) } catch (e: Throwable) { "" }
            runOnUiThread { et.setText(t) }
        }.start()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@VaultActivity, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { showList() }
            })
            addView(Ui.kindLabel(this@VaultActivity, "NOT"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@VaultActivity, if (item != null) Store.stamp(item.time) else "YENİ", 10f, Ui.GREY))
        }
        val save = Ui.pill(this, "KAYDET") {
            val text = et.text.toString()
            if (text.isNotBlank()) {
                Vault.write(this, text.toByteArray(Charsets.UTF_8), "txt", item?.time ?: System.currentTimeMillis())
                item?.let { old -> if (old.file.exists()) Vault.delete(old.file) }
            } else if (item != null) {
                Vault.delete(item.file)
            }
            showList()
        }
        show(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(52), dp(20), dp(32))
            addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(12)) })
            addView(et, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(save, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(16), 0, 0) })
        })
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try { startActivityForResult(i, REQ_PICK) } catch (e: Throwable) { Store.toast(this, "GALERİ AÇILAMADI") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (i in 0 until c.itemCount) uris.add(c.getItemAt(i).uri) }
        data.data?.let { if (uris.isEmpty()) uris.add(it) }
        Thread {
            var n = 0
            for (u in uris) {
                try {
                    val mime = contentResolver.getType(u) ?: "image/jpeg"
                    val ext = when { mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; else -> "jpg" }
                    contentResolver.openInputStream(u)?.use { Vault.write(this, it.readBytes(), ext); n++ }
                } catch (e: Throwable) {
                }
            }
            runOnUiThread { Store.toast(this, if (n > 0) "$n EKLENDİ" else "EKLENEMEDİ"); render() }
        }.start()
    }

    override fun onBackPressed() {
        when {
            sheetView != null -> hideSheet()
            editing -> showList()
            else -> super.onBackPressed()
        }
    }

    // Ekrandan çıkılınca ya da uygulama arka plana gidince bölüm kapanır; yeniden açmak için kapı baştan gerekir.
    override fun onStop() {
        super.onStop()
        if (unlocked && !isChangingConfigurations && !isFinishing && System.currentTimeMillis() >= holdUntil) finish()
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int, options: Bundle?) {
        holdUntil = System.currentTimeMillis() + 120_000L // galeri seçici
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onDestroy() {
        player?.runCatching { stop(); release() }
        player = null
        super.onDestroy()
    }
}
