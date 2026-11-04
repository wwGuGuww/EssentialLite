package com.uygar.essentiallite

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Tüm ekranların ortak davranışı: tema (koyu/açık), uygulama kilidi ve ses açma + ses kısma tuşları birlikte
 * basılı tutulursa uygulamanın tüm ekranlarının kapanması. Yalnızca uygulama ön plandayken çalışır.
 */
open class BaseActivity : Activity() {

    companion object {
        private var visible = 0
    }

    private var volUp = false
    private var volDown = false
    private var appliedLight = false
    private var lockCover: View? = null
    private var authenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.apply(this)
        appliedLight = Ui.light
        setTheme(if (Ui.light) R.style.Theme_EssentialLite_Light else R.style.Theme_EssentialLite_Dark)
        super.onCreate(savedInstanceState)
        applyBars()
    }

    private fun applyBars() {
        window.setBackgroundDrawable(ColorDrawable(Ui.BG))
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        var flags = window.decorView.systemUiVisibility
        val bits = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        flags = if (Ui.light) flags or bits else flags and bits.inv()
        window.decorView.systemUiVisibility = flags
    }

    // Görünür ekran sayısı: sıfıra inince uygulama arka plana gitmiş demektir (tema değişimi yeniden kurulumu hariç).
    override fun onStart() {
        super.onStart()
        visible++
    }

    override fun onStop() {
        super.onStop()
        visible--
        if (visible <= 0 && !isChangingConfigurations) AppLock.onBackground()
    }

    // Uygulama dışına (galeri, paylaşım menüsü, sistem ayarları) açılırken geri dönüşte kilit sorulmasın.
    private fun external(intent: android.content.Intent?) {
        val pkg = intent?.component?.packageName
        if (pkg == null || pkg != packageName) AppLock.holdForExternal()
    }

    override fun startActivity(intent: android.content.Intent?) {
        external(intent)
        super.startActivity(intent)
    }

    override fun startActivity(intent: android.content.Intent?, options: Bundle?) {
        external(intent)
        super.startActivity(intent, options)
    }

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: android.content.Intent?, requestCode: Int, options: Bundle?) {
        external(intent)
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onResume() {
        super.onResume()
        // Tema ayarı ya da sistem modu başka yerde değiştiyse bu ekranı yeni renklerle yeniden kur.
        if (Ui.isLight(this) != appliedLight) {
            Ui.apply(this)
            recreate()
            return
        }
        if (AppLock.needs(this)) showLock() else hideLock()
    }

    /** Kilit açılana kadar içeriği örten tam ekran perde. */
    private fun showLock() {
        if (lockCover == null) {
            val d = resources.displayMetrics.density
            val cover = FrameLayout(this).apply {
                setBackgroundColor(Ui.BG)
                isClickable = true
                addView(LinearLayout(this@BaseActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(Ui.brand(this@BaseActivity))
                    addView(Ui.pill(this@BaseActivity, "KİLİDİ AÇ") { authenticate() },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, (32 * d).toInt(), 0, 0)
                        })
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            (window.decorView as ViewGroup).addView(cover, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            lockCover = cover
        }
        authenticate()
    }

    private fun authenticate() {
        if (authenticating) return
        authenticating = true
        AppLock.authenticate(this,
            onOk = { authenticating = false; hideLock() },
            onFail = { authenticating = false },
            // Doğrulama hiç açılamıyorsa kullanıcı uygulamadan dışarıda kalmasın: kilit kapatılır.
            onUnavailable = { authenticating = false; Prefs.set(this, "applock", false); hideLock() })
    }

    private fun hideLock() {
        val c = lockCover ?: return
        lockCover = null
        runCatching { (window.decorView as ViewGroup).removeView(c) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> volUp = true
            KeyEvent.KEYCODE_VOLUME_DOWN -> volDown = true
        }
        if (volUp && volDown) {
            finishAffinity()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> volUp = false
            KeyEvent.KEYCODE_VOLUME_DOWN -> volDown = false
        }
        return super.onKeyUp(keyCode, event)
    }
}
