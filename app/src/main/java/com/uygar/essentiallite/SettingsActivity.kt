package com.uygar.essentiallite

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Ayarlar, gruplar halinde: GEMİNİ · ÖZELLİKLER · ARŞİV · TEMA · VERİ. Her grupta bir tane düğme/anahtar; aynı işi
 * yapan birden çok düğme yok.
 */
class SettingsActivity : BaseActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun matchWrap(top: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(top), 0, 0)
        }

    private fun section(title: String, top: Int = 32) = Ui.kindLabel(this, title).apply {
        layoutParams = matchWrap(top)
    }

    private fun divider() = View(this).apply { setBackgroundColor(Ui.BORDER) }

    /** Başlık, kısa açıklama ve sürgü. Satıra dokununca değişir. */
    private fun toggleRow(title: String, sub: String, on: Boolean, onToggle: () -> Unit): View {
        val sw = FrameLayout(this).apply {
            background = Ui.rounded(this@SettingsActivity, Color.TRANSPARENT, 16f, if (on) Ui.RED else Ui.LINE)
            addView(View(this@SettingsActivity).apply {
                background = Ui.rounded(this@SettingsActivity, if (on) Ui.RED else Ui.GREY, 10f)
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                setMargins(if (on) dp(30) else dp(5), 0, 0, 0)
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(0, dp(10), 0, dp(10))
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(this@SettingsActivity, title, 12f, Ui.WHITE, bold = true).apply { letterSpacing = 0.14f })
                addView(Ui.text(this@SettingsActivity, sub, 11f, Ui.GREY).apply { setPadding(0, dp(4), 0, 0) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(16), 0) })
            addView(sw, LinearLayout.LayoutParams(dp(56), dp(32)))
            Ui.pressAnim(this)
            setOnClickListener { onToggle() }
        }
    }

    private fun link(label: String, color: Int = Ui.WHITE, onClick: () -> Unit) = Ui.pill(this, label) { onClick() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasKey = Settings.apiKey(this) != null

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@SettingsActivity, "‹", 18f).apply {
                contentDescription = "Geri"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@SettingsActivity, "AYARLAR"),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        // GEMİNİ: tek alan, tek kaydet, anahtar varsa silme bağlantısı
        val keyStatus = Ui.text(this, if (hasKey) "ANAHTAR KAYITLI" else "ANAHTAR YOK", 10f, Ui.GREY)
            .apply { letterSpacing = 0.14f }
        val field = EditText(this).apply {
            hint = "AIza..."
            setHintTextColor(Ui.DIM)
            setTextColor(Ui.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            background = Ui.rounded(this@SettingsActivity, Ui.SURFACE, 22f, Ui.BORDER)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val save = Ui.pill(this, "KAYDET") {
            val typed = field.text.toString()
            if (typed.isBlank()) { Store.toast(this, "ANAHTARI GİR"); return@pill }
            val ok = Settings.saveApiKey(this, typed)
            field.text.clear()
            Store.toast(this, if (ok) "ANAHTAR KAYDEDİLDİ" else "KAYDEDİLEMEDİ")
            recreate()
        }
        val removeKey = Ui.text(this, "ANAHTARI SİL", 10f, Ui.RED).apply {
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener {
                Sheet.show(this@SettingsActivity, "ANAHTAR SİLİNSİN Mİ?", null, listOf(
                    Sheet.Action("SİL", Ui.RED) {
                        Settings.saveApiKey(this@SettingsActivity, "")
                        Store.toast(this@SettingsActivity, "ANAHTAR SİLİNDİ")
                        recreate()
                    },
                    Sheet.Action("VAZGEÇ", Ui.GREY) {}
                ))
            }
        }

        // ÖZELLİKLER
        val aiOn = Prefs.get(this, "ai_tag", false)
        val aiRow = toggleRow("AI ETİKET", "Ekran görüntüsüne başlık ve etiket", aiOn) {
            when {
                aiOn -> { Prefs.set(this, "ai_tag", false); recreate() }
                !hasKey -> Store.toast(this, "ÖNCE ANAHTAR GİR")
                else -> Sheet.show(this, "AI ETİKET", "Ekran görüntüsünden okunan metin Gemini'ye gönderilir. Günde en fazla ${Summarizer.DAILY_LIMIT} istek.", listOf(
                    Sheet.Action("AÇ", Ui.WHITE) { Prefs.set(this, "ai_tag", true); recreate() },
                    Sheet.Action("VAZGEÇ", Ui.GREY) {}
                ))
            }
        }
        val backupRow = Ui.text(this, "YEDEK VE GERİ YÜKLEME →", 11f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.14f
            setPadding(0, dp(18), 0, dp(18))
            Ui.pressAnim(this)
            setOnClickListener { startActivity(Intent(this@SettingsActivity, BackupActivity::class.java)) }
        }
        val lockOn = Prefs.get(this, "applock", false)
        val lockRow = toggleRow("UYGULAMA KİLİDİ", "Açılışta parmak izi", lockOn) {
            if (lockOn) {
                Prefs.set(this, "applock", false)
                recreate()
            } else if (!AppLock.available(this)) {
                Store.toast(this, "ÖNCE PARMAK İZİ KUR")
            } else {
                AppLock.authenticate(this,
                    onOk = { Prefs.set(this, "applock", true); recreate() },
                    onFail = {},
                    onUnavailable = { Store.toast(this, "DOĞRULAMA AÇILAMADI") })
            }
        }
        val calmOn = Prefs.get(this, "calm", false)
        val calmRow = toggleRow("HAREKETİ AZALT", "Animasyonları kısar", calmOn) {
            Prefs.set(this, "calm", !calmOn); recreate()
        }
        val hapticOn = Prefs.get(this, "haptic", true)
        val hapticRow = toggleRow("TİTREŞİM", "Basılı tutunca kısa titreşim", hapticOn) {
            Prefs.set(this, "haptic", !hapticOn); recreate()
        }
        val popOn = Prefs.get(this, "pop", true)
        val popRow = toggleRow("AÇILIŞ SESİ", "Basılı tutunca kısa pop", popOn) {
            Prefs.set(this, "pop", !popOn); recreate()
        }
        val holdOn = Prefs.hold(this)
        val triggerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("ANINDA" to false, "1 SN BASILI" to true).forEachIndexed { i, (label, value) ->
                val selected = holdOn == value
                addView(Ui.text(this@SettingsActivity, label, 11f, if (selected) Ui.WHITE else Ui.GREY).apply {
                    letterSpacing = 0.1f
                    gravity = Gravity.CENTER
                    background = Ui.rounded(this@SettingsActivity, Color.TRANSPARENT, 24f, if (selected) Ui.WHITE else Ui.BORDER)
                    Ui.pressAnim(this)
                    setOnClickListener { Prefs.set(this@SettingsActivity, "hold", value); recreate() }
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(if (i == 0) 0 else dp(4), 0, if (i == 0) dp(4) else 0, 0)
                })
            }
        }

        // TEMA
        val theme = Prefs.theme(this)
        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("SİSTEM", "KOYU", "AÇIK").forEachIndexed { i, label ->
                val selected = theme == i
                addView(Ui.text(this@SettingsActivity, label, 11f, if (selected) Ui.WHITE else Ui.GREY).apply {
                    letterSpacing = 0.1f
                    gravity = Gravity.CENTER
                    background = Ui.rounded(this@SettingsActivity, Color.TRANSPARENT, 24f, if (selected) Ui.WHITE else Ui.BORDER)
                    Ui.pressAnim(this)
                    setOnClickListener {
                        Prefs.setTheme(this@SettingsActivity, i)
                        Ui.apply(this@SettingsActivity)
                        recreate()
                    }
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(if (i == 0) 0 else dp(4), 0, if (i == 2) 0 else dp(4), 0)
                })
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(32))
            addView(header, matchWrap())

            addView(section("GEMİNİ", 28))
            addView(keyStatus, matchWrap(10))
            addView(field, matchWrap(12))
            addView(save, matchWrap(12))
            if (hasKey) addView(removeKey, matchWrap())

            addView(section("ÖZELLİKLER"))
            addView(aiRow, matchWrap(8))
            addView(divider())
            addView(lockRow)
            addView(backupRow)
            addView(divider())
            addView(calmRow)
            addView(divider())
            addView(hapticRow)
            addView(divider())
            addView(popRow)
            addView(divider())
            addView(Ui.text(this@SettingsActivity, "TETİK", 10f, Ui.GREY).apply { letterSpacing = 0.14f }, matchWrap(16))
            addView(triggerRow, matchWrap(10))

            if (Vault.hasPin(this@SettingsActivity)) {
                addView(section("ARŞİV"))
                addView(link("ŞİFREYİ DEĞİŞTİR") {
                    startActivity(Intent(this@SettingsActivity, VaultActivity::class.java).putExtra("mode", "change"))
                }, matchWrap(12))
            }

            addView(section("TEMA"))
            addView(themeRow, matchWrap(12))

            addView(section("KURULUM"))
            addView(link("İZİNLERİ VE ASİSTANI AYARLA") {
                startActivity(Intent(this@SettingsActivity, OnboardActivity::class.java))
            }, matchWrap(12))

            addView(section("VERİ"))
            addView(link("GİZLİLİK") { startActivity(Intent(this@SettingsActivity, PrivacyActivity::class.java)) }, matchWrap(12))
            addView(link("HATA GÜNLÜĞÜ") { startActivity(Intent(this@SettingsActivity, LogActivity::class.java)) }, matchWrap(12))
            addView(Ui.text(this@SettingsActivity, "TÜM VERİLERİ SİL", 10f, Ui.RED).apply {
                letterSpacing = 0.14f
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
                setOnClickListener { confirmWipe() }
            }, matchWrap())
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(content)
        })
    }

    private fun confirmWipe() {
        Sheet.show(this, "TÜM VERİLER SİLİNSİN Mİ?",
            "Kayıtlar, ekran görüntüleri, arşiv ve hatırlatıcılar silinir. Geri alınamaz.",
            listOf(
                Sheet.Action("SİL", Ui.RED) {
                    Store.wipeAll(this)
                    Store.toast(this, "TÜM VERİLER SİLİNDİ")
                    recreate()
                },
                Sheet.Action("VAZGEÇ", Ui.GREY) {}
            ))
    }
}
