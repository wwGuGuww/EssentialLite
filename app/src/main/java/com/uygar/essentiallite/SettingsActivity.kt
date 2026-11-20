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
                contentDescription = "Back"
                setPadding(0, 0, dp(14), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.kindLabel(this@SettingsActivity, "SETTINGS"),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        // GEMİNİ: tek alan, tek kaydet, anahtar varsa silme bağlantısı
        val keyStatus = Ui.text(this, if (hasKey) "KEY STORED" else "NO KEY", 10f, Ui.GREY)
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
        val save = Ui.pill(this, "SAVE") {
            val typed = field.text.toString()
            if (typed.isBlank()) { Store.toast(this, "ENTER KEY"); return@pill }
            val ok = Settings.saveApiKey(this, typed)
            field.text.clear()
            Store.toast(this, if (ok) "KEY SAVED" else "COULDN'T SAVE")
            recreate()
        }
        val removeKey = Ui.text(this, "DELETE KEY", 10f, Ui.RED).apply {
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener {
                Sheet.show(this@SettingsActivity, "DELETE KEY?", null, listOf(
                    Sheet.Action("DELETE", Ui.RED) {
                        Settings.saveApiKey(this@SettingsActivity, "")
                        Store.toast(this@SettingsActivity, "KEY DELETED")
                        recreate()
                    },
                    Sheet.Action("CANCEL", Ui.GREY) {}
                ))
            }
        }

        // ÖZELLİKLER
        val aiOn = Prefs.get(this, "ai_tag", false)
        val aiRow = toggleRow("AI TAGS", "Title and tags for screenshots", aiOn) {
            when {
                aiOn -> { Prefs.set(this, "ai_tag", false); recreate() }
                !hasKey -> Store.toast(this, "ENTER KEY FIRST")
                else -> Sheet.show(this, "AI TAGS", "Text read from the screenshot is sent to Gemini. At most ${Summarizer.DAILY_LIMIT} requests a day.", listOf(
                    Sheet.Action("OPEN", Ui.WHITE) { Prefs.set(this, "ai_tag", true); recreate() },
                    Sheet.Action("CANCEL", Ui.GREY) {}
                ))
            }
        }
        val backupRow = Ui.text(this, "BACKUP AND RESTORE →", 11f, Ui.WHITE, bold = true).apply {
            letterSpacing = 0.14f
            setPadding(0, dp(18), 0, dp(18))
            Ui.pressAnim(this)
            setOnClickListener { startActivity(Intent(this@SettingsActivity, BackupActivity::class.java)) }
        }
        val lockOn = Prefs.get(this, "applock", false)
        val lockRow = toggleRow("APP LOCK", "Fingerprint on launch", lockOn) {
            if (lockOn) {
                Prefs.set(this, "applock", false)
                recreate()
            } else if (!AppLock.available(this)) {
                Store.toast(this, "SET UP A FINGERPRINT FIRST")
            } else {
                AppLock.authenticate(this,
                    onOk = { Prefs.set(this, "applock", true); recreate() },
                    onFail = {},
                    onUnavailable = { Store.toast(this, "COULDN'T OPEN VERIFICATION") })
            }
        }
        val calmOn = Prefs.get(this, "calm", false)
        val calmRow = toggleRow("REDUCE MOTION", "Reduces animations", calmOn) {
            Prefs.set(this, "calm", !calmOn); recreate()
        }
        val hapticOn = Prefs.get(this, "haptic", true)
        val hapticRow = toggleRow("VIBRATION", "Short vibration on long press", hapticOn) {
            Prefs.set(this, "haptic", !hapticOn); recreate()
        }
        val popOn = Prefs.get(this, "pop", true)
        val popRow = toggleRow("STARTUP SOUND", "Short pop on long press", popOn) {
            Prefs.set(this, "pop", !popOn); recreate()
        }
        val holdOn = Prefs.hold(this)
        val triggerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("INSTANT" to false, "HOLD 1 SEC" to true).forEachIndexed { i, (label, value) ->
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
            listOf("SYSTEM", "DARK", "ON").forEachIndexed { i, label ->
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

            addView(section("GEMINI", 28))
            addView(keyStatus, matchWrap(10))
            addView(field, matchWrap(12))
            addView(save, matchWrap(12))
            if (hasKey) addView(removeKey, matchWrap())

            addView(section("FEATURES"))
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
            addView(Ui.text(this@SettingsActivity, "TRIGGER", 10f, Ui.GREY).apply { letterSpacing = 0.14f }, matchWrap(16))
            addView(triggerRow, matchWrap(10))

            if (Vault.hasPin(this@SettingsActivity)) {
                addView(section("ARCHIVE"))
                addView(link("CHANGE PASSWORD") {
                    startActivity(Intent(this@SettingsActivity, VaultActivity::class.java).putExtra("mode", "change"))
                }, matchWrap(12))
            }

            addView(section("THEME"))
            addView(themeRow, matchWrap(12))

            addView(section("SETUP"))
            addView(link("SET UP PERMISSIONS AND ASSISTANT") {
                startActivity(Intent(this@SettingsActivity, OnboardActivity::class.java))
            }, matchWrap(12))

            addView(section("DATA"))
            addView(link("PRIVACY") { startActivity(Intent(this@SettingsActivity, PrivacyActivity::class.java)) }, matchWrap(12))
            addView(link("ERROR LOG") { startActivity(Intent(this@SettingsActivity, LogActivity::class.java)) }, matchWrap(12))
            addView(Ui.text(this@SettingsActivity, "DELETE ALL DATA", 10f, Ui.RED).apply {
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
        Sheet.show(this, "DELETE ALL DATA?",
            "Records, screenshots, the archive and reminders are deleted. This can't be undone.",
            listOf(
                Sheet.Action("DELETE", Ui.RED) {
                    Store.wipeAll(this)
                    Store.toast(this, "ALL DATA DELETED")
                    recreate()
                },
                Sheet.Action("CANCEL", Ui.GREY) {}
            ))
    }
}
