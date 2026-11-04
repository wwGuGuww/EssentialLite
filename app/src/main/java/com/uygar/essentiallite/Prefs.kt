package com.uygar.essentiallite

import android.content.Context

/** Hızlı erişim ayarları (tasarım: Hızlı erişim kurulumu). */
object Prefs {
    private const val FILE = "essentiallite_prefs"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(ctx: Context, key: String, def: Boolean): Boolean = sp(ctx).getBoolean(key, def)

    fun set(ctx: Context, key: String, v: Boolean) {
        sp(ctx).edit().putBoolean(key, v).apply()
    }

    /** Hareketi azalt: animasyonlar kapanır. */
    fun calm(ctx: Context) = get(ctx, "calm", false)

    /** Titreşim: panel açılırken kısa titreşim. */
    fun haptic(ctx: Context) = get(ctx, "haptic", true)

    /** Tema: 0 sistem, 1 koyu, 2 açık. */
    fun theme(ctx: Context): Int = sp(ctx).getInt("theme", 0)

    fun setTheme(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("theme", v).apply()
    }

    /** Son aramalar (en yeni başta, en fazla 5). */
    fun recent(ctx: Context): List<String> =
        (sp(ctx).getString("recent_q", "") ?: "").split("001F").filter { it.isNotBlank() }

    fun setRecent(ctx: Context, items: List<String>) {
        sp(ctx).edit().putString("recent_q", items.take(5).joinToString("001F")).apply()
    }

    /** Tetik şekli: false = aynı anda bas, true = 1 sn basılı tut. */
    fun hold(ctx: Context) = get(ctx, "hold", false)
}
