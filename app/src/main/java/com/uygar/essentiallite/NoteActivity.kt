package com.uygar.essentiallite

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File

class NoteActivity : BaseActivity() {

    private var file: File? = null
    private lateinit var target: File
    private lateinit var count: TextView
    private lateinit var board: FrameLayout
    private var saved = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = intent.getStringExtra("path")?.let { File(it) }
        val f = file
        target = f ?: Store.newFile(this, "txt")

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@NoteActivity, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@NoteActivity, "NOT"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@NoteActivity, if (f != null) Store.stamp(f.lastModified()) else "YENİ", 10f, Ui.GREY))
        }

        val editor = EditText(this).apply {
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.DIM)
            hint = "Notunu yaz..."
            typeface = Typeface.MONOSPACE
            textSize = 15f
            gravity = Gravity.TOP
            background = DotGrid(dp(24).toFloat(), Ui.GRID)
            setPadding(dp(4), dp(16), dp(4), dp(16))
            if (f != null && f.exists()) setText(f.readText())
        }
        count = Ui.text(this, "", 10f, Ui.GREY).apply { letterSpacing = 0.14f }
        updateCount(editor.text.length)
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = updateCount(s?.length ?: 0)
            override fun afterTextChanged(s: Editable?) {}
        })

        // Görsel ekleri: galeriden ya da uygulamadaki ekran görüntülerinden.
        // Metin alanının üstünde serbest tuval: görseller sürüklenip istenen yere bırakılır, iki parmakla boyutlanır.
        board = FrameLayout(this).apply {
            clipChildren = true
            addView(editor, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(smallButton("+ GALERİ") { pickFromGallery() })
            addView(smallButton("+ EKRAN GÖRÜNTÜSÜ") { showShotPicker() }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, 0, 0) })
        }

        val save = Ui.pill(this, "KAYDET") { save(editor.text.toString()) }
        val summarize = Ui.text(this, "ÖZETLE", 10f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.14f
            setPadding(0, 0, dp(20), 0)
            setOnClickListener { summarize(editor.text.toString()) }
        }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
            addView(count, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(summarize)
            addView(save)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(32))
            addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            })
            addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(addRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(10), 0, 0)
            })
            addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
        board.post { renderBoard() }
    }

    override fun onDestroy() {
        // Kaydedilmemiş yeni notun ekleri yetim kalmasın.
        if (!saved && file == null) Store.attDir(target).deleteRecursively()
        super.onDestroy()
    }

    private fun smallButton(label: String, onClick: () -> Unit) =
        Ui.text(this, label, 10f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            background = Ui.rounded(this@NoteActivity, Color.TRANSPARENT, 20f, Ui.LINE)
            setPadding(dp(16), 0, dp(16), 0)
            minimumHeight = dp(40)
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }

    // --- Ekler ---

    // Yerleşim: attDir/layout.json → { "<dosya>": { "x": 0..1, "y": 0..1, "w": 0..1 } } (tuvalin genişlik/yüksekliğine oranla)
    private fun layoutFile() = File(Store.attDir(target).apply { mkdirs() }, "layout.json")

    private fun readLayout(): org.json.JSONObject = try {
        org.json.JSONObject(layoutFile().readText())
    } catch (e: Throwable) {
        org.json.JSONObject()
    }

    private fun saveLayout(name: String, x: Float, y: Float, w: Float) {
        try {
            val j = readLayout()
            j.put(name, org.json.JSONObject().put("x", x.toDouble()).put("y", y.toDouble()).put("w", w.toDouble()))
            layoutFile().writeText(j.toString())
        } catch (e: Throwable) {
        }
    }

    private fun forgetLayout(name: String) {
        try {
            val j = readLayout()
            j.remove(name)
            layoutFile().writeText(j.toString())
        } catch (e: Throwable) {
        }
    }

    /** Görselleri tuvale koyar: tek parmakla sürükle, iki parmakla boyutlandır, dokununca aç, × ile kaldır. */
    private fun renderBoard() {
        while (board.childCount > 1) board.removeViewAt(1)
        val bw = board.width
        val bh = board.height
        if (bw == 0 || bh == 0) return
        val layout = readLayout()
        Store.attachments(target).forEachIndexed { i, img ->
            val s = layout.optJSONObject(img.name)
            val wFrac = (s?.optDouble("w", 0.45) ?: 0.45).toFloat()
            val xFrac = (s?.optDouble("x", 0.08 + 0.05 * i) ?: (0.08 + 0.05 * i)).toFloat()
            val yFrac = (s?.optDouble("y", 0.10 + 0.05 * i) ?: (0.10 + 0.05 * i)).toFloat()
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(img.path, b)
            val ratio = if (b.outWidth > 0) b.outHeight.toFloat() / b.outWidth else 1f

            val cell = FrameLayout(this)
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = Ui.rounded(this@NoteActivity, Ui.BG, 14f, Ui.BORDER2)
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, 14 * v.resources.displayMetrics.density) }
                }
            }
            loadInto(img, iv, 900)
            cell.addView(iv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            cell.addView(Ui.text(this, "×", 14f, 0xFFFFFFFF.toInt(), bold = true).apply {
                gravity = Gravity.CENTER
                background = Ui.rounded(this@NoteActivity, 0xCC000000.toInt(), 13f)
                contentDescription = "Görseli kaldır"
                setOnClickListener {
                    img.delete()
                    forgetLayout(img.name)
                    renderBoard()
                }
            }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })

            val w0 = (bw * wFrac).toInt().coerceAtLeast(dp(60))
            board.addView(cell, FrameLayout.LayoutParams(w0, (w0 * ratio).toInt().coerceAtLeast(dp(40))))
            cell.x = bw * xFrac
            cell.y = bh * yFrac

            var downX = 0f
            var downY = 0f
            var startX = 0f
            var startY = 0f
            var moved = false
            var rebase = false
            val scaler = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                    val lp = cell.layoutParams
                    val nw = (lp.width * d.scaleFactor).toInt().coerceIn(dp(60), bw)
                    val nh = (nw * ratio).toInt().coerceAtLeast(dp(40))
                    cell.x -= (nw - lp.width) / 2f
                    cell.y -= (nh - lp.height) / 2f
                    lp.width = nw
                    lp.height = nh
                    cell.layoutParams = lp
                    moved = true
                    rebase = true
                    return true
                }
            })
            fun persist() = saveLayout(img.name, cell.x / bw, cell.y / bh, cell.layoutParams.width.toFloat() / bw)

            cell.setOnTouchListener { v, ev ->
                scaler.onTouchEvent(ev)
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.bringToFront()
                        v.parent.requestDisallowInterceptTouchEvent(true)
                        startX = v.x; startY = v.y; downX = ev.rawX; downY = ev.rawY; moved = false; rebase = false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress && ev.pointerCount == 1) {
                        if (rebase) { startX = v.x; startY = v.y; downX = ev.rawX; downY = ev.rawY; rebase = false }
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true
                        if (moved) {
                            v.x = (startX + dx).coerceIn(-v.width * 0.7f, bw - v.width * 0.3f)
                            v.y = (startY + dy).coerceIn(-v.height * 0.7f, bh - v.height * 0.3f)
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> if (moved) persist() else viewImage(img)
                    android.view.MotionEvent.ACTION_CANCEL -> persist()
                }
                true
            }
        }
    }

    private fun viewImage(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun loadInto(f: File, into: ImageView, maxSide: Int) {
        into.tag = f.path
        Thread {
            val bmp = decode(f, maxSide)
            runOnUiThread { if (into.tag == f.path && bmp != null) into.setImageBitmap(bmp) }
        }.start()
    }

    private fun decode(f: File, maxSide: Int): Bitmap? = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }

    private fun pickFromGallery() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            startActivityForResult(i, REQ_GALLERY)
        } catch (e: Throwable) {
            Store.toast(this, "GALERİ AÇILAMADI")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_GALLERY || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (i in 0 until c.itemCount) uris.add(c.getItemAt(i).uri) }
        data.data?.let { if (uris.isEmpty()) uris.add(it) }
        if (uris.isEmpty()) return
        Thread {
            var added = 0
            for (u in uris) {
                try {
                    val mime = contentResolver.getType(u) ?: "image/jpeg"
                    val ext = when {
                        mime.contains("png") -> "png"
                        mime.contains("webp") -> "webp"
                        else -> "jpg"
                    }
                    contentResolver.openInputStream(u)?.use { Store.addAttachment(target, it, ext); added++ }
                } catch (e: Throwable) {
                }
            }
            runOnUiThread {
                renderBoard()
                Store.toast(this, if (added > 0) "$added GÖRSEL EKLENDİ" else "GÖRSEL EKLENEMEDİ")
            }
        }.start()
    }

    /** Uygulamadaki ekran görüntülerinden seçim: 3 sütunlu küçük resim ızgarası. */
    private fun showShotPicker() {
        val shots = Store.all(this).filter { Store.isImage(it.file) || it.kind == "EKRAN" }
        if (shots.isEmpty()) {
            Store.toast(this, "EKRAN GÖRÜNTÜSÜ YOK")
            return
        }
        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val side = (resources.displayMetrics.widthPixels - dp(40) - dp(16)) / 3
        shots.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { e ->
                val iv = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = Ui.rounded(this@NoteActivity, Ui.BG, 14f, Ui.BORDER2)
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, 14 * v.resources.displayMetrics.density) }
                    }
                    setOnClickListener {
                        dialog.dismiss()
                        Thread {
                            try {
                                e.file.inputStream().use { Store.addAttachment(target, it, e.file.extension.ifEmpty { "png" }) }
                            } catch (t: Throwable) {
                            }
                            runOnUiThread { renderBoard(); Store.toast(this@NoteActivity, "GÖRSEL EKLENDİ") }
                        }.start()
                    }
                }
                loadInto(e.file, iv, 300)
                row.addView(iv, LinearLayout.LayoutParams(side, (side * 1.5f).toInt()).apply { setMargins(0, 0, dp(8), dp(8)) })
            }
            grid.addView(row)
        }
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.kindLabel(this@NoteActivity, "EKRAN GÖRÜNTÜSÜ SEÇ"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@NoteActivity, "KAPAT", 10f, Ui.GREY, bold = true).apply {
                letterSpacing = 0.14f
                setPadding(dp(12), dp(10), 0, dp(10))
                setOnClickListener { dialog.dismiss() }
            })
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(20))
            addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(16)) })
            addView(ScrollView(this@NoteActivity).apply { addView(grid) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        dialog.setContentView(content)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
    }

    // --- Özet ---

    /** Özetleme ağa çıkar; önce açık onay istenir, anahtar yoksa Ayarlar'a yönlendirilir. */
    private var lastSummaryAt = 0L

    private fun summarize(body: String) {
        if (body.isBlank()) {
            Store.toast(this, "ÖNCE NOT YAZ")
            return
        }
        // Ücretsiz kotayı korumak için sınırlar: uzunluk ve arka arkaya çağrı.
        if (body.length > MAX_SUMMARY_CHARS) {
            Store.toast(this, "NOT ÇOK UZUN (en fazla $MAX_SUMMARY_CHARS karakter)")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSummaryAt < SUMMARY_COOLDOWN_MS) {
            Store.toast(this, "BİRAZ BEKLE")
            return
        }
        if (!Summarizer.withinDailyLimit(this)) {
            Store.toast(this, "GÜNLÜK LİMİT DOLDU (${Summarizer.DAILY_LIMIT})")
            return
        }
        lastSummaryAt = now
        val key = Settings.apiKey(this)
        if (key == null) {
            Store.toast(this, "ÖNCE ANAHTAR GİR")
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        Sheet.show(this, "ÖZET", "Bu not Google Gemini'ye gönderilip özetlensin mi?", listOf(
            Sheet.Action("GÖNDER", Ui.WHITE) { runSummary(key, body) },
            Sheet.Action("VAZGEÇ", Ui.GREY) {}
        ))
    }

    private fun runSummary(key: String, body: String) {
        Store.toast(this, "ÖZETLENİYOR…")
        Summarizer.recordUse(this)
        Thread {
            var error = ""
            val result = try {
                Summarizer.summarize(key, body)
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
                null
            }
            runOnUiThread {
                if (result == null) {
                    Store.toast(this, "ÖZET HATASI: ${error.take(150)}")
                } else {
                    Sheet.show(this, "ÖZET", result, listOf(Sheet.Action("TAMAM", Ui.WHITE) {}))
                }
            }
        }.start()
    }

    companion object {
        private const val MAX_SUMMARY_CHARS = 4000
        private const val SUMMARY_COOLDOWN_MS = 10_000L
        private const val REQ_GALLERY = 41
    }

    private fun updateCount(n: Int) {
        count.text = ""
    }

    /** Metin boş ve görsel yoksa mevcut not silinir; görsel varsa metinsiz not olarak kalır. */
    private fun save(body: String) {
        val f = file
        val hasImages = Store.attachments(target).isNotEmpty()
        if (body.isBlank() && !hasImages) {
            if (f != null && f.exists()) {
                Store.delete(f)
                Store.toast(this, "NOT SİLİNDİ")
                refreshWidgets()
            }
            finish()
            return
        }
        target.writeText(body)
        saved = true
        Store.toast(this, "NOT KAYDEDİLDİ")
        refreshWidgets()
        // Nottaki tarih/saat (ör. "yarın 15:00") bulunursa hatırlatıcı önerilir.
        val at = Reminders.parse(body)
        if (at != null && Info.read(target).remind != at) askReminder(at) else finish()
    }

    private fun askReminder(at: Long) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        Sheet.show(this, "HATIRLATICI", "${Store.stamp(at)} için hatırlatıcı kurulsun mu?", listOf(
            Sheet.Action("KUR", Ui.WHITE) {
                Reminders.set(this, target, at)
                refreshWidgets()
                Store.toast(this, "HATIRLATICI: ${Store.stamp(at)}")
                finish()
            },
            Sheet.Action("GEREK YOK", Ui.GREY) { finish() }
        ))
    }

    private fun refreshWidgets() {
        val app = applicationContext
        Thread { CaptureWidget.refreshAll(app) }.start()
    }
}
