package com.uygar.essentiallite

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import java.io.File

/** Ekran görüntüsünü gösterir ve altına not eklenmesini sağlar. Not, aynı adlı .txt olarak saklanır. */
class ShotActivity : BaseActivity() {

    private var file: File? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = intent.getStringExtra("path")?.let { File(it) }
        file = f

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@ShotActivity, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@ShotActivity, "EKRAN"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.text(this@ShotActivity, f?.let { it.nameWithoutExtension.toLongOrNull()?.let { t -> Store.stamp(t) } } ?: "", 10f, Ui.GREY))
        }

        // Çerçeveli önizleme: ekran görüntüsü oranını koruyarak sığdırılır.
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = Ui.rounded(this@ShotActivity, Ui.BG, 20f, Ui.BORDER)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            f?.let { setImageBitmap(decodeScaled(it, 1280)) }
        }

        val editor = EditText(this).apply {
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.DIM)
            hint = "Bu ekran görüntüsüne not..."
            typeface = Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.TOP
            background = Ui.rounded(this@ShotActivity, Ui.SURFACE, 22f, Ui.BORDER)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            f?.let { setText(Store.noteOf(it)) }
        }

        val save = Ui.pill(this, "KAYDET") { save(editor.text.toString()) }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
            addView(View(this@ShotActivity),
                LinearLayout.LayoutParams(0, 1, 1f))
            addView(Ui.text(this@ShotActivity, "DÜZENLE", 10f, Ui.WHITE, bold = true).apply {
                letterSpacing = 0.14f
                setPadding(0, 0, dp(20), 0)
                setOnClickListener {
                    startActivity(Intent(this@ShotActivity, EditorActivity::class.java)
                        .putExtra("path", intent.getStringExtra("path")))
                }
            })
            addView(save)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(32))
            addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            })
            addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))
            // Ekrandaki bulunan öğeler (telefon, IBAN, tarih, takip no, link, adres): dokununca işlem yapılır.
            val found = f?.let { Entities.find(Info.readOcr(it)) }.orEmpty()
            if (found.isNotEmpty()) addView(HorizontalScrollView(this@ShotActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(LinearLayout(this@ShotActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    found.forEach { e ->
                        addView(Ui.text(this@ShotActivity, e.chip, 10f, Ui.WHITE, bold = true).apply {
                            letterSpacing = 0.1f
                            gravity = Gravity.CENTER
                            setPadding(dp(14), dp(10), dp(14), dp(10))
                            background = Ui.rounded(this@ShotActivity, Color.TRANSPARENT, 20f, Ui.LINE)
                            Ui.pressAnim(this)
                            setOnClickListener { Entities.act(this@ShotActivity, e) }
                        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { setMargins(0, 0, dp(8), 0) })
                    }
                })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(12), 0, 0)
            })
            addView(editor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, dp(14), 0, 0)
            })
            addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
    }

    private fun save(body: String) {
        val f = file ?: return finish()
        Store.saveNote(f, body)
        Store.toast(this, if (body.isBlank()) "NOT SİLİNDİ" else "NOT KAYDEDİLDİ")
        val app = applicationContext
        Thread { CaptureWidget.refreshAll(app) }.start()
        finish()
    }

    /** Belleği korumak için görüntüyü en uzun kenarı [maxSide] olacak şekilde küçültür. */
    private fun decodeScaled(f: File, maxSide: Int) = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }
}
