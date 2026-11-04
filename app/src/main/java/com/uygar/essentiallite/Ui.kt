package com.uygar.essentiallite

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Ortak renkler, ikonlar, düğme yardımcıları ve animasyonlar. */
object Ui {
    // Tema renkleri: apply() ile koyu/açık değerlere ayarlanır. WHITE = yazı/ikon rengi (koyuda beyaz, açıkta siyah).
    var RED = 0xFFF0303A.toInt()
    var BG = 0xFF000000.toInt()
    var WHITE = 0xFFFFFFFF.toInt()
    var GREY = 0xFF8C8C8C.toInt()
    var GREY2 = 0xFFB3B3B3.toInt()
    var DIM = 0xFF8C8C8C.toInt()
    var OFF = 0xFF1E1E1E.toInt()
    var SURFACE = 0xFF0B0B0B.toInt()
    var SURFACE2 = 0xFF141414.toInt()
    var BORDER = 0xFF222222.toInt()
    var BORDER2 = 0xFF2A2A2A.toInt()
    var LINE = 0xFF333333.toInt()
    var PROG = 0xFF4A4A4A.toInt()
    var MID = 0xFF7A7A7A.toInt()
    var GRID = 0xFF2E2E2E.toInt()
    var RING = 0xFF3A3A3A.toInt()
    var light = false
        private set

    /** Tema seçimi (0 sistem, 1 koyu, 2 açık) ve sistem moduna göre açık mı. */
    fun isLight(ctx: Context): Boolean = when (Prefs.theme(ctx)) {
        1 -> false
        2 -> true
        else -> (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun apply(ctx: Context) {
        light = isLight(ctx)
        if (light) {
            RED = 0xFFD81B24.toInt(); BG = 0xFFFFFFFF.toInt(); WHITE = 0xFF000000.toInt()
            GREY = 0xFF6E6E6E.toInt(); GREY2 = 0xFF444444.toInt(); DIM = 0xFF8A8A8A.toInt()
            OFF = 0xFFE4E4E4.toInt(); SURFACE = 0xFFF4F4F4.toInt(); SURFACE2 = 0xFFECECEC.toInt()
            BORDER = 0xFFE2E2E2.toInt(); BORDER2 = 0xFFD6D6D6.toInt(); LINE = 0xFFCFCFCF.toInt()
            PROG = 0xFFB5B5B5.toInt(); MID = 0xFF8A8A8A.toInt(); GRID = 0xFFDCDCDC.toInt(); RING = 0xFFC4C4C4.toInt()
        } else {
            RED = 0xFFF0303A.toInt(); BG = 0xFF000000.toInt(); WHITE = 0xFFFFFFFF.toInt()
            GREY = 0xFF8C8C8C.toInt(); GREY2 = 0xFFB3B3B3.toInt(); DIM = 0xFF8C8C8C.toInt()
            OFF = 0xFF1E1E1E.toInt(); SURFACE = 0xFF0B0B0B.toInt(); SURFACE2 = 0xFF141414.toInt()
            BORDER = 0xFF222222.toInt(); BORDER2 = 0xFF2A2A2A.toInt(); LINE = 0xFF333333.toInt()
            PROG = 0xFF4A4A4A.toInt(); MID = 0xFF7A7A7A.toInt(); GRID = 0xFF2E2E2E.toInt(); RING = 0xFF3A3A3A.toInt()
        }
    }

    /** 7x7 nokta matrisi ikonları. */
    val ICON_SHOT = listOf("1110111", "1000001", "1000001", "0000000", "1000001", "1000001", "1110111")
    val ICON_NOTE = listOf("1111111", "0000000", "1111111", "0000000", "1111100", "0000000", "1110000")
    val ICON_WAVE: List<String> = listOf(1, 5, 3, 7, 3, 5, 1).let { h ->
        (0..6).map { r -> h.joinToString("") { if (Math.abs(r - 3) * 2 + 1 <= it) "1" else "0" } }
    }

    /** Düzenleyici araç ikonları (7x7 nokta). */
    val ICON_BLUR = listOf("1010101", "0101010", "1010101", "0101010", "1010101", "0101010", "1010101")
    val ICON_PEN = listOf("0000011", "0000110", "0001100", "0011000", "0110000", "1100000", "1000000")
    val ICON_HL = listOf("0000000", "1111111", "1111111", "1111111", "0000000", "1010101", "0000000")
    val ICON_UNDO = listOf("0010000", "0110000", "1111110", "0110001", "0010001", "0000010", "0111100")

    /** "Hareketi azalt" açıksa (animasyon ölçeği 0) süreler neredeyse anında biter. */
    fun motion(ctx: Context, ms: Long): Long {
        if (Prefs.calm(ctx)) return 1L
        val scale = try {
            android.provider.Settings.Global.getFloat(
                ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        } catch (e: Throwable) {
            1f
        }
        return if (scale == 0f) 1L else ms
    }

    fun density(ctx: Context) = ctx.resources.displayMetrics.density

    fun text(ctx: Context, s: String, size: Float, color: Int = WHITE, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /** Nokta matrisi gösterge yazısı (Doto Black): sayaçlar ve zamanlayıcılar için. */
    fun display(ctx: Context, s: String, size: Float, color: Int = WHITE): TextView =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.doto_family)
                ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setLineSpacing(0f, 0.85f)
        }

    /** Yuvarlak köşeli dolgu, isteğe bağlı kontur. */
    fun rounded(ctx: Context, fill: Int, radiusDp: Float, stroke: Int = 0): GradientDrawable {
        val d = density(ctx)
        return GradientDrawable().apply {
            cornerRadius = radiusDp * d
            setColor(fill)
            if (stroke != 0) setStroke((1 * d).toInt(), stroke)
        }
    }

    /** Başta kırmızı nokta olan metin: "● NOT". */
    fun kindLabel(ctx: Context, kind: String): TextView {
        val s = SpannableStringBuilder("● $kind")
        s.setSpan(ForegroundColorSpan(RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.BOLD), 2, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text(ctx, "", 11f).apply {
            text = s
            letterSpacing = 0.14f
        }
    }

    /** Gün başlığı: "● BUGÜN  04.11" (ad beyaz, tarih gri). */
    fun dayLabel(ctx: Context, name: String, date: String): TextView {
        val head = if (name.isEmpty()) "● " else "● $name  "
        val s = SpannableStringBuilder(head + date)
        s.setSpan(ForegroundColorSpan(RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (name.isNotEmpty()) s.setSpan(StyleSpan(Typeface.BOLD), 2, 2 + name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(GREY), head.length, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text(ctx, "", 11f).apply {
            text = s
            letterSpacing = 0.14f
        }
    }

    /** Üst başlık: "ESSENTIAL ● LITE" (nokta ve LITE kırmızı). */
    fun brand(ctx: Context): TextView {
        val s = SpannableStringBuilder("ESSENTIAL ● LITE")
        val i = s.indexOf("●")
        s.setSpan(ForegroundColorSpan(RED), i, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text(ctx, "", 12f, WHITE, bold = true).apply {
            text = s
            letterSpacing = 0.16f
        }
    }

    /** Başında kırmızı nokta olan durum metni: "● KAYIT YAPILIYOR". */
    fun withDot(s: String, color: Int): CharSequence {
        val b = SpannableStringBuilder("● $s")
        b.setSpan(ForegroundColorSpan(RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        b.setSpan(ForegroundColorSpan(color), 2, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return b
    }

    /** Dokunulunca hafifçe küçülen, bırakınca geri dönen basma efekti. Tıklamayı bozmaz. */
    fun pressAnim(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
    }

    /** Ana ekran kartı: üstte nokta ikonu, altta etiket. Eşit genişlikte. */
    fun button(ctx: Context, label: String, icon: List<String>, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.tile)
            setPadding(0, 40, 0, 32)
            addView(DotIcon(ctx, icon))
            addView(text(ctx, label, 12f, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            })
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 16, 0)
            }
            pressAnim(this)
            setOnClickListener { onClick() }
        }

    /** Alt menü öğesi: nokta ikonu + etiket. */
    fun navItem(ctx: Context, label: String, icon: List<String>, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(DotIcon(ctx, icon))
            addView(text(ctx, label, 10f, WHITE, bold = true).apply {
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
            })
            pressAnim(this)
            setOnClickListener { onClick() }
        }

    /** Panel ikonları (tasarım 04: AÇ, PAYLAŞ, SİL, kapat). */
    val ICON_SEARCH = listOf("0111000", "1000100", "1000100", "1000100", "0111100", "0000010", "0000001")
    val ICON_PLAY = listOf("0100000", "0110000", "0111000", "0111100", "0111000", "0110000", "0100000")
    val ICON_LOCK = listOf("0011100", "0100010", "0100010", "1111111", "1111111", "1110111", "1111111")
    val ICON_ARROW = listOf("0000000", "0001000", "0000100", "1111110", "0000100", "0001000", "0000000")
    val ICON_TEXT = listOf("1111111", "0001000", "0001000", "0001000", "0001000", "0001000", "0001000")
    val ICON_CROP = listOf("1100011", "1000001", "0000000", "0000000", "0000000", "1000001", "1100011")
    val ICON_PIN = listOf("0011100", "0010100", "0011100", "0001000", "0001000", "0001000", "0001000")
    val ICON_TAG = listOf("0100100", "1111111", "0100100", "0100100", "1111111", "0100100", "0100100")
    val ICON_BELL = listOf("0001000", "0011100", "0011100", "0111110", "0111110", "1111111", "0001000")
    val ICON_OPEN = listOf("0011111", "0000011", "0000101", "0001001", "0010001", "0100000", "1000000")
    val ICON_SHARE = listOf("0000011", "0000011", "0011000", "1100000", "0011000", "0000011", "0000011")
    val ICON_TRASH = listOf("0011100", "1111111", "0000000", "0111110", "0101010", "0101010", "0111110")
    val ICON_CLOSE = listOf("1000001", "0100010", "0010100", "0001000", "0010100", "0100010", "1000001")

    /** Panel karosu (tasarım 10): koyu yüzey, 24 dp köşe, 108 dp yükseklik, ikon + etiket. */
    fun tile(ctx: Context, label: String, icon: List<String>, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(ctx, SURFACE2, 24f)
            addView(DotIcon(ctx, icon))
            addView(text(ctx, label, 10f, WHITE, bold = true).apply {
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
                setPadding(0, (10 * density(ctx)).toInt(), 0, 0)
            })
            layoutParams = LinearLayout.LayoutParams(0, (108 * density(ctx)).toInt(), 1f).apply {
                setMargins((4 * density(ctx)).toInt(), 0, (4 * density(ctx)).toInt(), 0)
            }
            pressAnim(this)
            setOnClickListener { onClick() }
        }

    /** Çerçeveli hap düğmesi (ince beyaz kontur). */
    fun pill(ctx: Context, label: String, onClick: () -> Unit): TextView {
        val d = density(ctx)
        return text(ctx, label, 13f, bold = true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            background = GradientDrawable().apply {
                cornerRadius = 1000f
                setColor(Color.TRANSPARENT)
                setStroke((2 * d).toInt(), WHITE)
            }
            setPadding((32 * d).toInt(), (14 * d).toInt(), (32 * d).toInt(), (14 * d).toInt())
            pressAnim(this)
            setOnClickListener { onClick() }
        }
    }

    /** Filtre çipi: seçiliyse kırmızı nokta ile vurgulanır. */
    fun chip(ctx: Context, label: String, selected: Boolean, onClick: () -> Unit) =
        text(ctx, if (selected) "● $label" else label, 11f, if (selected) RED else GREY).apply {
            setPadding(0, 16, 40, 16)
            setOnClickListener { onClick() }
        }

    /** Uygulama içinde ekran görüntüsü anı: beyaz bir flaş kısa süre belirir ve söner. */
    fun flashActivity(activity: Activity) {
        val decor = activity.window.decorView as ViewGroup
        val v = View(activity).apply {
            setBackgroundColor(WHITE)
            alpha = 0f
        }
        decor.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        v.animate().alpha(0.85f).setDuration(90).withEndAction {
            v.animate().alpha(0f).setDuration(260).withEndAction { decor.removeView(v) }.start()
        }.start()
    }

    /** Erişilebilirlik servisinden (başka uygulamanın üstünde) flaş. */
    fun flashOverlay(ctx: Context) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = View(ctx).apply {
            setBackgroundColor(WHITE)
            alpha = 0f
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { windowAnimations = R.style.NoWindowAnim }
        try {
            wm.addView(v, lp)
        } catch (e: Throwable) {
            return
        }
        // Tasarım: 60 ms'de %92 beyaz, sonra 320 ms'de sönüş.
        v.animate().alpha(0.92f).setDuration(motion(ctx, 60)).withEndAction {
            v.animate().alpha(0f).setDuration(motion(ctx, 320)).withEndAction {
                // Doğrudan kaldırmak son karede pencerenin bir an beyaz çizilmesine yol açıyordu (ikinci "parlama").
                // Önce görünmez yap, birkaç kare sonra kaldır.
                v.visibility = View.INVISIBLE
                v.postDelayed({ runCatching { wm.removeView(v) } }, 100)
            }.start()
        }.start()
    }
}
