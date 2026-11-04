package com.uygar.essentiallite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * Ekran görüntüsü düzenleyici (tasarım: 06b). Bulanıklaştır, kalem, vurgula, geri al.
 * Kaydedince orijinal değişmez; yeni bir kayıt oluşur.
 */
class EditorActivity : BaseActivity() {

    private lateinit var markup: MarkupView
    private var source: File? = null
    private lateinit var count: TextView
    private lateinit var sizes: LinearLayout
    private lateinit var undoBtn: TextView
    private lateinit var swatches: LinearLayout
    private lateinit var tools: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = intent.getStringExtra("path")?.let { File(it) }
        source = f
        val bmp = f?.let { decodeLimited(it, 2048) }
        if (bmp == null) {
            Store.toast(this, "GÖRSEL AÇILAMADI")
            finish()
            return
        }
        markup = MarkupView(this, bmp).apply {
            onChange = { refreshControls() }
            onTextTap = { x, y -> Sheet.input(this@EditorActivity, "METİN", "yaz...", "") { t -> markup.addText(x, y, t) } }
        }

        count = Ui.text(this, "", 10f, Ui.GREY).apply { letterSpacing = 0.14f }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@EditorActivity, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@EditorActivity, "DÜZENLE"),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(count)
        }

        swatches = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sizes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // Renk ve kalınlık ayrı satırlarda; kalınlık yalnızca ilgili araçlarda görünür.
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(4))
            addView(swatches, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(sizes, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(10), 0, 0)
            })
        }

        tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val cancel = Ui.text(this, "İPTAL", 11f, Ui.GREY, bold = true).apply {
            letterSpacing = 0.16f
            setPadding(0, dp(18), dp(16), dp(18))
            setOnClickListener { finish() }
        }
        undoBtn = Ui.text(this, "GERİ AL", 11f, Ui.GREY, bold = true).apply {
            letterSpacing = 0.16f
            setPadding(dp(12), dp(18), dp(12), dp(18))
            Ui.pressAnim(this)
            setOnClickListener {
                if (markup.crop != null && markup.strokes.isEmpty()) markup.clearCrop() else markup.undo()
            }
        }
        val save = Ui.pill(this, "KAYDET") { save() }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(cancel)
            addView(View(this@EditorActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(undoBtn)
            addView(save)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(24))
            addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(markup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, dp(12), 0, 0)
            })
            addView(colorRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(tools, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(16), 0, 0)
            })
        }
        setContentView(root)
        refreshControls()
    }

    /** Sayaç, renk seçici, araç bilgisi ve araç düğmeleri markup durumuna göre yenilenir. */
    private fun refreshControls() {
        count.text = ""

        val blur = markup.tool == MarkupView.Tool.MOSAIC
        swatches.removeAllViews()
        swatches.visibility = if (blur) View.GONE else View.VISIBLE
        if (!blur) {
            for (c in listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF0303A.toInt(), 0xFFFF8A00.toInt(), 0xFFFFD60A.toInt(), 0xFF3DDC84.toInt(), 0xFF2E9BFF.toInt())) {
                val selected = markup.color == c
                swatches.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        if (selected) setStroke(dp(2), Ui.WHITE)
                    }
                    setOnClickListener {
                        markup.color = c
                        refreshControls()
                    }
                }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { setMargins(0, 0, dp(8), 0) })
            }
        }
        val sizeTool = markup.tool == MarkupView.Tool.PEN || markup.tool == MarkupView.Tool.MARK || markup.tool == MarkupView.Tool.ARROW
        sizes.removeAllViews()
        sizes.visibility = if (sizeTool) View.VISIBLE else View.GONE
        if (sizeTool) {
            sizes.addView(Ui.text(this, "KALINLIK", 9f, Ui.GREY, bold = true).apply { letterSpacing = 0.14f },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, dp(12), 0)
                })
            listOf("S", "M", "L").forEachIndexed { i, label ->
                val on = markup.size == i
                sizes.addView(Ui.text(this, label, 11f, if (on) Color.WHITE else Ui.GREY, bold = true).apply {
                    gravity = Gravity.CENTER
                    background = Ui.rounded(this@EditorActivity, Color.TRANSPARENT, 15f, if (on) Color.WHITE else Ui.LINE)
                    Ui.pressAnim(this)
                    setOnClickListener {
                        markup.size = i
                        refreshControls()
                    }
                }, LinearLayout.LayoutParams(dp(34), dp(30)).apply { setMargins(0, 0, dp(8), 0) })
            }
        }

        tools.removeAllViews()
        cell("BULANIK", Ui.ICON_BLUR, markup.tool == MarkupView.Tool.MOSAIC, true) { markup.tool = MarkupView.Tool.MOSAIC; refreshControls() }
        cell("KALEM", Ui.ICON_PEN, markup.tool == MarkupView.Tool.PEN, true) { markup.tool = MarkupView.Tool.PEN; refreshControls() }
        cell("VURGULA", Ui.ICON_HL, markup.tool == MarkupView.Tool.MARK, true) { markup.tool = MarkupView.Tool.MARK; refreshControls() }
        cell("OK", Ui.ICON_ARROW, markup.tool == MarkupView.Tool.ARROW, true) { markup.tool = MarkupView.Tool.ARROW; refreshControls() }
        cell("METİN", Ui.ICON_TEXT, markup.tool == MarkupView.Tool.TEXT, true) { markup.tool = MarkupView.Tool.TEXT; refreshControls() }
        cell("KIRP", Ui.ICON_CROP, markup.tool == MarkupView.Tool.CROP, true) { markup.tool = MarkupView.Tool.CROP; refreshControls() }
        undoBtn.alpha = if (markup.strokes.isNotEmpty() || markup.crop != null) 1f else 0.35f
    }

    private fun cell(label: String, icon: List<String>, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.rounded(this@EditorActivity, Ui.SURFACE, 22f, if (selected) Ui.WHITE else Ui.BORDER)
            setPadding(0, dp(12), 0, dp(10))
            alpha = if (enabled) 1f else 0.35f
            addView(DotIcon(this@EditorActivity, icon, if (selected) Ui.WHITE else Ui.GREY))
            addView(Ui.text(this@EditorActivity, label, 9f, if (selected) Ui.WHITE else Ui.GREY, bold = true).apply {
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            if (enabled) {
                pressAndClick(this, onClick)
            }
        }
        tools.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, dp(8), 0)
        })
    }

    private fun pressAndClick(v: View, onClick: () -> Unit) {
        Ui.pressAnim(v)
        v.setOnClickListener { onClick() }
    }

    /** Kaydı yeni bir dosya olarak yazar; orijinal değişmez. Kaynak uygulama bilgisi kopyalanır. */
    private fun save() {
        val src = source ?: return
        Store.toast(this, "KAYDEDİLİYOR…")
        val app = applicationContext
        Thread {
            try {
                val out = markup.render()
                val target = Store.newFile(app, "png")
                target.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                out.recycle()
                Store.copyMeta(src, target)
                CaptureWidget.refreshAll(app)
                runOnUiThread {
                    Store.toast(this, "DÜZENLENMİŞ KAYDEDİLDİ")
                    finish()
                }
            } catch (e: Throwable) {
                runOnUiThread { Store.toast(this, "KAYDEDİLEMEDİ") }
            }
        }.start()
    }

    private fun decodeLimited(f: File, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }
}
