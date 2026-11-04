package com.uygar.essentiallite

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Uygulama kilidi: ayardan açılırsa uygulama ön plana gelince parmak izi (olmazsa ekran kilidi) istenir.
 * Uygulama arka plana gidince hemen kilitlenir. Yalnızca uygulamanın kendi açtığı dış ekranlar (galeri, paylaşım,
 * sistem ayarları) için kısa bir istisna vardır, böylece oradan dönerken yeniden sorulmaz.
 */
object AppLock {
    private const val EXTERNAL_HOLD_MS = 90_000L
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    @Volatile private var unlocked = false
    @Volatile private var holdUntil = 0L

    fun enabled(ctx: Context) = Prefs.get(ctx, "applock", false)

    fun needs(ctx: Context) = enabled(ctx) && !unlocked

    fun markUnlocked() {
        unlocked = true
    }

    /** Uygulamanın hiçbir ekranı görünmez olunca çağrılır: istisna yoksa kilitler. */
    fun onBackground() {
        if (System.currentTimeMillis() < holdUntil) return
        unlocked = false
    }

    /** Uygulamanın kendi başlattığı dış ekran (galeri, paylaşım vb.) açılırken kısa süre kilitlenmeyi erteler. */
    fun holdForExternal() {
        holdUntil = System.currentTimeMillis() + EXTERNAL_HOLD_MS
    }

    /** Cihazda parmak izi ya da ekran kilidi kurulu mu? (Android 11 ve üstü) */
    fun available(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return try {
            val bm = ctx.getSystemService(BiometricManager::class.java) ?: return false
            bm.canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Throwable) {
            false
        }
    }

    /** Kimlik doğrulama penceresini açar; başarılıysa [onOk] çağrılır. */
    fun authenticate(activity: Activity, onOk: () -> Unit, onFail: () -> Unit, onUnavailable: () -> Unit = onFail) {
        if (Build.VERSION.SDK_INT < 30) { onOk(); return }
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("ESSENTIAL LITE")
                .setAllowedAuthenticators(AUTH)
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    markUnlocked()
                    onOk()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onFail()
                }
            })
        } catch (e: Throwable) {
            // Doğrulama penceresi açılamadı: çağıran karar verir (kilit açılmaz ya da dışarıda kalmamak için kapatılır).
            onUnavailable()
        }
    }
}
