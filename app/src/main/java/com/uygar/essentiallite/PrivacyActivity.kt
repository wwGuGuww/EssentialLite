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
            "GEMİNİ'YE NE GİDER" to "Yalnızca sen istediğinde gider. Anahtar cihazda şifreli saklanır.",
            "ÖZET" to "Seçtiğin notun metni, sen GÖNDER dediğinde.",
            "AI ETİKET (AYAR AÇIKSA)" to "Ekran görüntüsünden cihazda okunan metin. Görselin kendisi gitmez. Günde en fazla 30 istek.",
            "HİÇ GİTMEYENLER" to "Görseller, ses kayıtları, arşiv (gizli bölüm) ve OCR için kullanılan cihaz içi metin tanıma.",
            "GOOGLE'IN ÜCRETSİZ KATMANI" to "Google, ücretsiz katımda gönderilen verileri ürün geliştirmek için kullanabilir. Hassas bilgi göndermemeye dikkat et.",
            "SİLME" to "Ayarlar > TÜM VERİLERİ SİL, arşivi ve kayıtları cihazdan kaldırır."
        )
        val header = Ui.kindLabel(this, "GİZLİLİK")
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
