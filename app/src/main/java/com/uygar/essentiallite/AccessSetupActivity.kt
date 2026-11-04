package com.uygar.essentiallite

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Hızlı erişim kurulumu (tasarım: 09). Durum kartı, adımlar, tetik şekli ve panel ayarları. */
class AccessSetupActivity : BaseActivity() {

    private lateinit var content: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(44), dp(20), dp(28))
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        val ctx = this
        val on = isAccessibilityOn()

        content.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(ctx, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(ctx, "HIZLI ERİŞİM"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }, matchWrap(bottom = 20))

        // Durum kartı
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(ctx, Ui.SURFACE, 28f, Ui.BORDER)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(Ui.kindLabel(ctx, if (on) "KISAYOL AÇIK" else "KISAYOL KAPALI"))
            addView(Ui.text(ctx,
                "Instagram, WhatsApp, her yerde: SES AÇ ve SES KIS'a aynı anda bas. Uygulamadan çıkmadan yakalama paneli üstte açılır.",
                12f, Ui.GREY2).apply {
                setPadding(0, dp(12), 0, dp(12))
                setLineSpacing(0f, 1.3f)
            })
            val steps = listOf(
                "Erişilebilirlik ayarlarını aç",
                "Yüklü uygulamalar → Essential Lite",
                "Kısayolu aç, tuş izni ver"
            )
            steps.forEachIndexed { i, text -> addView(stepRow(i + 1, text, on)) }
            addView(buttonRow(on), matchWrap(top = 16))
            // Panel başka uygulamaların üstünde açıldığı için bu izin de gerekli; yoksa buradan istenir.
            if (!AndroidSettings.canDrawOverlays(ctx)) {
                addView(Ui.pill(ctx, "ÜZERİNDE GÖSTER İZNİ") {
                    startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                }, matchWrap(top = 12))
            }
        }
        content.addView(card, matchWrap(bottom = 24))

        // Tetik şekli
        content.addView(Ui.kindLabel(ctx, "TETİK"), matchWrap(bottom = 12))
        val hold = Prefs.hold(ctx)
        content.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(chip("AYNI ANDA BAS", !hold) { Prefs.set(ctx, "hold", false); render() },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) })
            addView(chip("1 SN BASILI TUT", hold) { Prefs.set(ctx, "hold", true); render() },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, 0, 0) })
        }, matchWrap(bottom = 8))
        content.addView(Ui.text(ctx,
            if (hold) "Yanlışlıkla tetiklenmez. Ses ayarı yaparken rahatsız etmez."
            else "En hızlısı. Ses ayarı yaparken yanlışlıkla açılabilir.",
            11f, Ui.GREY).apply { setLineSpacing(0f, 1.2f) }, matchWrap(bottom = 24))

        // Panel ayarları
        content.addView(Ui.kindLabel(ctx, "PANEL"), matchWrap(bottom = 8))
        toggleRow("HAREKETİ AZALT", "Panelde dalga ve halo animasyonu durur.", "calm", false)
        toggleRow("TİTREŞİM", "Panel açılırken ve kayıt bitince titrer.", "haptic", true)
    }

    private fun stepRow(n: Int, text: String, on: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(View(this@AccessSetupActivity).apply { setBackgroundColor(Ui.BORDER) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        addView(LinearLayout(this@AccessSetupActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            addView(Ui.text(this@AccessSetupActivity, "%02d".format(n), 16f, Ui.GREY, bold = true),
                LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(Ui.text(this@AccessSetupActivity, text, 12f, if (on) Ui.WHITE else Ui.GREY2).apply {
                setLineSpacing(0f, 1.2f)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(View(this@AccessSetupActivity).apply {
                background = Ui.rounded(this@AccessSetupActivity, if (on) Ui.RED else Color.TRANSPARENT, 5f,
                    if (on) 0 else Ui.PROG)
            }, LinearLayout.LayoutParams(dp(10), dp(10)))
        })
    }

    private fun buttonRow(on: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (!on) {
            addView(Ui.pill(this@AccessSetupActivity, "AYARLARI AÇ") { openAccessibility() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        } else {
            addView(Ui.pill(this@AccessSetupActivity, "TEST ET") {
                Store.toast(this@AccessSetupActivity, "Ses tuşlarına birlikte bas")
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(12), 0) })
            addView(Ui.text(this@AccessSetupActivity, "KAPAT", 11f, Ui.GREY, bold = true).apply {
                letterSpacing = 0.14f
                setPadding(dp(12), dp(14), dp(12), dp(14))
                setOnClickListener { openAccessibility() }
            })
        }
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit) =
        Ui.text(this, label, 11f, if (selected) Ui.WHITE else Ui.GREY).apply {
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            background = Ui.rounded(this@AccessSetupActivity, Color.TRANSPARENT, 24f,
                if (selected) Ui.WHITE else Ui.BORDER)
            Ui.pressAnim(this)
            setOnClickListener { onClick() }
        }

    /** Sıra: başlık ve açıklama solda, sürgü sağda. Satıra dokununca ayar değişir. */
    private fun toggleRow(title: String, sub: String, key: String, def: Boolean) {
        val ctx = this
        val on = Prefs.get(ctx, key, def)
        val sw = FrameLayout(ctx).apply {
            background = Ui.rounded(ctx, Color.TRANSPARENT, 16f, if (on) Ui.RED else Ui.LINE)
            addView(View(ctx).apply {
                background = Ui.rounded(ctx, if (on) Ui.RED else Ui.GREY, 10f)
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                setMargins(if (on) dp(30) else dp(5), 0, 0, 0)
            })
        }
        content.addView(View(ctx).apply { setBackgroundColor(Ui.BORDER) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        content.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(0, dp(10), 0, dp(10))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, title, 12f, Ui.WHITE, bold = true).apply { letterSpacing = 0.14f })
                addView(Ui.text(ctx, sub, 11f, Ui.GREY).apply { setPadding(0, dp(4), 0, 0) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(16), 0) })
            addView(sw, LinearLayout.LayoutParams(dp(56), dp(32)))
            Ui.pressAnim(this)
            setOnClickListener {
                Prefs.set(ctx, key, !on)
                render()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun openAccessibility() {
        startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    companion object {
        fun isServiceOn(ctx: android.content.Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val pkg = ctx.packageName
            return enabled.contains("$pkg/.KeyComboService") || enabled.contains("$pkg/$pkg.KeyComboService")
        }
    }

    private fun isAccessibilityOn(): Boolean {
        val enabled = AndroidSettings.Secure.getString(
            contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/.KeyComboService") ||
            enabled.contains("$packageName/$packageName.KeyComboService")
    }

    private fun matchWrap(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(top), 0, dp(bottom))
        }
}
