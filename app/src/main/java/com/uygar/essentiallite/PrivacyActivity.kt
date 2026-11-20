package com.uygar.essentiallite

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView

/** Gizlilik: neyin Gemini'ye gittiği ve neyin cihazda kaldığı. */
class PrivacyActivity : BaseActivity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sections = listOf(
            "WHAT GEMINI RECEIVES" to "Sent only when you ask. The key is stored encrypted on the device.",
            "SUMMARY" to "The text of the note you pick, when you tap SEND.",
            "AI TAGS (IF ENABLED)" to "Text read on device from the screenshot. The image itself isn't sent. At most 30 requests a day.",
            "NEVER LEAVES DEVICE" to "Images, voice memos, the archive (hidden section) and the on-device text recognition used for OCR.",
            "GOOGLE'S FREE TIER" to "On the free tier, Google may use the data sent to improve its products. Avoid sending sensitive information.",
            "KEEP" to "Settings > DELETE ALL DATA removes the archive and records from the device."
        )
        val header = Ui.kindLabel(this, "PRIVACY")
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(32))
            addView(header)
            sections.forEach { (title, body) ->
                addView(Ui.text(this@PrivacyActivity, title, 10f, Ui.WHITE, bold = true).apply {
                    letterSpacing = 0.16f
                    setPadding(0, dp(24), 0, dp(8))
                })
                addView(Ui.text(this@PrivacyActivity, body, 13f, Ui.GREY2).apply { setLineSpacing(0f, 1.3f) })
            }
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(col)
        })
    }
}
