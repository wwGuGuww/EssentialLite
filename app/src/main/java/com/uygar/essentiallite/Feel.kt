package com.uygar.essentiallite

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Basılı tutunca menü açılırken titreşim ve kısa "pop" sesi. Ayarlardan ayrı ayrı kapatılabilir. */
object Feel {
    private const val RATE = 44_100
    private val handler = Handler(Looper.getMainLooper())

    fun pop(ctx: Context) {
        if (Prefs.get(ctx, "haptic", true)) vibrate(ctx)
        if (Prefs.get(ctx, "pop", true)) sound()
    }

    private fun vibrate(ctx: Context) {
        try {
            val v = ctx.getSystemService(Vibrator::class.java) ?: return
            if (!v.hasVibrator()) return
            val e = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            else VibrationEffect.createOneShot(30, 200)
            v.vibrate(e)
        } catch (t: Throwable) {
        }
    }

    /** ~90 ms'lik, tizden pese kayan ve hızla sönen bir ton: yumuşak bir "pop". */
    private fun sound() {
        try {
            val n = (RATE * 0.09).toInt()
            val pcm = ShortArray(n)
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / n
                val freq = 640.0 - 300.0 * t
                phase += 2 * PI * freq / RATE
                val env = exp(-t * 7.0) * (if (i < 80) i / 80.0 else 1.0) // kısa giriş, hızlı sönüş
                pcm[i] = (sin(phase) * env * 0.45 * Short.MAX_VALUE).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            handler.postDelayed({ runCatching { track.release() } }, 400)
        } catch (t: Throwable) {
        }
    }
}
