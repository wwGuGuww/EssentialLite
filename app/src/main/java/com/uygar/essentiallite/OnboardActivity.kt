package com.uygar.essentiallite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * İlk açılış rehberi. Her adım kendi durumunu gösterir (yapıldı / bekliyor) ve tek dokunuşla ilgili ayara götürür.
 * Rehber tamamlanınca ya da "ATLA" ile geçilince bir daha açılmaz; Ayarlar'dan yeniden açılabilir.
 */
class OnboardActivity : BaseActivity() {

    private lateinit var content: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun micOk() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun notifOk() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun accessOk() = AccessSetupActivity.isServiceOn(this)
    private fun overlayOk() = AndroidSettings.canDrawOverlays(this)
    private fun shizukuOk() = try { Shot.ready() } catch (e: Throwable) { false }
    private fun assistantOk() = android.provider.Settings.Secure.getString(contentResolver, "assistant")?.contains(packageName) == true

    private fun steps(): List<Triple<String, String, () -> Boolean>> = listOf(
        Triple("SHİZUKU", "Ekran görüntüsü almak için gerekli. Shizuku'yu açıp Başlat'a bas, sonra izin ver.") { shizukuOk() },
        Triple("ERİŞİLEBİLİRLİK", "Ses tuşlarıyla hızlı erişim paneli için. Essential Lite'ı açman yeterli.") { accessOk() },
        Triple("ÜZERİNDE GÖSTER", "Panelin başka uygulamaların üstünde açılması için.") { overlayOk() },
        Triple("MİKROFON", "Ses kaydı için.") { micOk() },
        Triple("BİLDİRİM", "Hatırlatıcıların zamanında çalması için.") { notifOk() },
        Triple("ASİSTAN", "İsteğe bağlı. Güç tuşuna uzun basınca hızlı erişim paneli açılsın.") { assistantOk() }
    )

    private fun render() {
        content.removeAllViews()
        content.setPadding(dp(20), dp(44), dp(20), dp(32))
        content.addView(Ui.brand(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        content.addView(Ui.kindLabel(this, "KURULUM").apply { setPadding(0, dp(28), 0, 0) })
        content.addView(Ui.text(this, "Birkaç izin ve ayar. Hepsi isteğe bağlı, ama ekran görüntüsü için Shizuku şart.", 12f, Ui.GREY2).apply {
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(10), 0, dp(20))
        })
        val all = steps()
        all.forEachIndexed { i, (title, sub, ok) -> content.addView(stepCard(i + 1, title, sub, ok())) }
        val done = all.count { it.third() }
        content.addView(Ui.text(this, "$done / ${all.size} tamam", 11f, Ui.GREY).apply {
            letterSpacing = 0.14f
            setPadding(0, dp(18), 0, dp(14))
        })
        content.addView(Ui.pill(this, if (done == all.size) "BİTİR" else "ŞİMDİLİK GEÇ") { finishOnboarding() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun stepCard(n: Int, title: String, sub: String, ok: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@OnboardActivity, Ui.SURFACE, 24f, Ui.BORDER)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(LinearLayout(this@OnboardActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(this@OnboardActivity, "%02d".format(n), 13f, Ui.GREY, bold = true),
                    LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(Ui.kindLabel(this@OnboardActivity, title), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Ui.text(this@OnboardActivity, if (ok) "TAMAM" else "BEKLİYOR", 10f, if (ok) Ui.WHITE else Ui.RED, bold = true)
                    .apply { letterSpacing = 0.14f })
            })
            addView(Ui.text(this@OnboardActivity, sub, 12f, Ui.GREY2).apply {
                setLineSpacing(0f, 1.25f)
                setPadding(dp(34), dp(8), 0, 0)
            })
            if (!ok) addView(Ui.text(this@OnboardActivity, "AÇ", 11f, Ui.WHITE, bold = true).apply {
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
                background = Ui.rounded(this@OnboardActivity, Color.TRANSPARENT, 20f, Ui.LINE)
                setPadding(dp(18), dp(10), dp(18), dp(10))
                Ui.pressAnim(this)
                setOnClickListener { act(title) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(34), dp(12), 0, 0)
            })
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(10)) }
        }

    private fun act(title: String) {
        when (title) {
            "SHİZUKU" -> {
                val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (launch != null) startActivity(launch)
                else Store.toast(this, "SHİZUKU KURULU DEĞİL")
            }
            "ERİŞİLEBİLİRLİK" -> startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            "ÜZERİNDE GÖSTER" -> startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            "MİKROFON" -> requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 11)
            "BİLDİRİM" -> if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
            "ASİSTAN" -> try { startActivity(Intent("android.settings.VOICE_INPUT_SETTINGS")) } catch (e: Throwable) { startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
        }
    }

    override fun onBackPressed() = finishOnboarding()

    private fun finishOnboarding() {
        Prefs.set(this, "onboarded", true)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }
}
