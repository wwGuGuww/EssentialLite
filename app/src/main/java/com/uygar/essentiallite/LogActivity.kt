package com.uygar.essentiallite

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Hata günlüğü: son hatalar, temizle ve paylaş. */
class LogActivity : BaseActivity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = AppLog.read(this).ifBlank { "Kayıtlı hata yok." }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@LogActivity, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@LogActivity, "HATA GÜNLÜĞÜ"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val body = Ui.text(this, text, 11f, Ui.GREY2).apply {
            setLineSpacing(0f, 1.3f)
            setTextIsSelectable(true)
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.pill(this@LogActivity, "PAYLAŞ") {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, AppLog.read(this@LogActivity).ifBlank { "Hata yok" }), "Paylaş"))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(6), 0) })
            addView(Ui.pill(this@LogActivity, "TEMİZLE") {
                AppLog.clear(this@LogActivity)
                recreate()
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(28))
            addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(16)) })
            addView(ScrollView(this@LogActivity).apply { addView(body) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(16), 0, 0) })
        }
        setContentView(root)
    }
}
