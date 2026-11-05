package com.uygar.essentiallite

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Uygulamanın kendi alt sayfa tarzında onay ve seçenek penceresi. Sistem diyaloğu yerine kullanılır.
 * Bir seçeneğe dokununca ya da dışarı basınca kapanır.
 */
object Sheet {
    class Action(val label: String, val color: Int, val onClick: () -> Unit)

    fun show(activity: Activity, title: String, body: String?, actions: List<Action>) {
        val decor = activity.window.decorView as ViewGroup
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        lateinit var scrim: View
        lateinit var sheet: View
        fun close(after: () -> Unit) {
            sheet.animate().translationY(dp(700).toFloat()).setDuration(200).withEndAction {
                runCatching { decor.removeView(sheet) }
                runCatching { decor.removeView(scrim) }
                after()
            }.start()
            scrim.animate().alpha(0f).setDuration(200).start()
        }

        scrim = View(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            setOnClickListener { close {} }
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                cornerRadii = floatArrayOf(32 * density, 32 * density, 32 * density, 32 * density, 0f, 0f, 0f, 0f)
                setStroke(dp(1), Ui.BORDER2)
            }
            setPadding(dp(20), dp(14), dp(20), dp(32))
        }
        box.addView(Ui.kindLabel(activity, title), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(20), 0, dp(10)) })
        if (body != null) box.addView(Ui.text(activity, body, 13f, Ui.GREY2).apply {
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, dp(12))
        })
        actions.forEachIndexed { i, a ->
            if (i > 0) box.addView(View(activity).apply { setBackgroundColor(Ui.BORDER) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
            box.addView(Ui.text(activity, a.label, 14f, a.color, bold = true).apply {
                letterSpacing = 0.18f
                gravity = Gravity.CENTER_VERTICAL
                Ui.pressAnim(this)
                setOnClickListener { close { a.onClick() } }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)))
        }
        sheet = box
        decor.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        decor.addView(sheet, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        sheet.translationY = dp(700).toFloat()
        sheet.animate().translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        scrim.animate().alpha(0.78f).setDuration(220).start()
    }

    /** Tek satır metin girişi, uygulamanın alt sayfa tarzında. Boş bırakılırsa [onOk] çağrılmaz. */
    fun input(activity: Activity, title: String, hint: String, initial: String, onOk: (String) -> Unit) {
        val decor = activity.window.decorView as ViewGroup
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        lateinit var scrim: View
        lateinit var sheet: View
        fun close(after: () -> Unit) {
            sheet.animate().translationY(dp(700).toFloat()).setDuration(200).withEndAction {
                runCatching { decor.removeView(sheet) }
                runCatching { decor.removeView(scrim) }
                after()
            }.start()
            scrim.animate().alpha(0f).setDuration(200).start()
        }
        scrim = View(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            setOnClickListener { close {} }
        }
        val field = android.widget.EditText(activity).apply {
            this.hint = hint
            setHintTextColor(Ui.DIM)
            setTextColor(Ui.WHITE)
            textSize = 14f
            setSingleLine(true)
            setText(initial)
            background = Ui.rounded(activity, Ui.BG, 22f, Ui.BORDER2)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                cornerRadii = floatArrayOf(32 * density, 32 * density, 32 * density, 32 * density, 0f, 0f, 0f, 0f)
                setStroke(dp(1), Ui.BORDER2)
            }
            setPadding(dp(20), dp(14), dp(20), dp(32))
            addView(Ui.kindLabel(activity, title), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(20), 0, dp(12)) })
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(Ui.pill(activity, "KAYDET") {
                val v = field.text.toString()
                close { if (v.isNotBlank()) onOk(v) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(14), 0, 0) })
        }
        sheet = box
        decor.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        decor.addView(sheet, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        sheet.translationY = dp(700).toFloat()
        sheet.animate().translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        scrim.animate().alpha(0.78f).setDuration(220).start()
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            // Klavye girişi örtmesin: sayfanın alt kenarı klavye yüksekliği kadar yukarı alınır.
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
                val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                (v.layoutParams as FrameLayout.LayoutParams).bottomMargin = ime
                v.requestLayout()
                insets
            }
        }
        field.requestFocus()
    }
}
