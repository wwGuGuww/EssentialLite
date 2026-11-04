package com.uygar.essentiallite

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Kullanıcının kendi API anahtarı. Koda gömülmez; cihazda şifreli saklanır.
 * Okuma/yazma hatalarında uygulama çökmez, anahtar yok sayılır.
 */
object Settings {
    private const val FILE = "essentiallite_secure"
    private const val KEY_API = "anthropic_api_key"

    private fun prefs(ctx: Context): SharedPreferences? = try {
        val app = ctx.applicationContext
        val master = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            FILE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        null
    }

    fun apiKey(ctx: Context): String? = try {
        prefs(ctx)?.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        null
    }

    /** Gizli bölüm şifresinin özeti gibi küçük sırlar: şifreli tercihlerde saklanır. */
    fun secretGet(ctx: Context, key: String): String? = try { prefs(ctx)?.getString(key, null) } catch (e: Throwable) { null }

    fun secretPut(ctx: Context, key: String, value: String): Boolean = try {
        prefs(ctx)?.edit()?.putString(key, value)?.apply() != null
    } catch (e: Throwable) { false }

    fun secretRemove(ctx: Context, key: String): Boolean = try {
        prefs(ctx)?.edit()?.remove(key)?.apply() != null
    } catch (e: Throwable) { false }

    /** Boş metin verilirse anahtar silinir. Başarılıysa true döner. */
    fun saveApiKey(ctx: Context, key: String): Boolean {
        return try {
            val p = prefs(ctx) ?: return false
            val editor = p.edit()
            if (key.isBlank()) editor.remove(KEY_API) else editor.putString(KEY_API, key.trim())
            editor.apply()
            true
        } catch (e: Throwable) {
            false
        }
    }
}
