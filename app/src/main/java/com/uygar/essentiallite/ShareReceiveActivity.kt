package com.uygar.essentiallite

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Paylaş menüsünden gelen metin ve görselleri kaydeder. Kaynak uygulama, çağıran uygulamanın
 * referrer bilgisinden alınır (ör. android-app://com.android.chrome).
 */
class ShareReceiveActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = referrer?.host
        try {
            when (intent?.action) {
                Intent.ACTION_SEND -> handleSend(source)
            }
        } catch (e: Throwable) {
            Store.toast(this, "KAYDEDİLEMEDİ")
        }
        finish()
    }

    private fun handleSend(source: String?) {
        val stream: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (stream != null) {
            val mime = contentResolver.getType(stream) ?: "image/png"
            val ext = when {
                mime.contains("jpeg") -> "jpg"
                mime.contains("webp") -> "webp"
                else -> "png"
            }
            val target = Store.newFile(this, ext)
            contentResolver.openInputStream(stream)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            Store.saveMeta(target, source)
            Store.toast(this, "GÖRSEL KAYDEDİLDİ")
            val app0 = applicationContext
            Thread { Ocr.afterSave(app0, target) }.start()
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val target = Store.newFile(this, "txt")
                target.writeText(text)
                LinkTitle.attach(this, target, text)
                Store.saveMeta(target, source)
                Store.toast(this, "METİN KAYDEDİLDİ")
            }
        }
        val app = applicationContext
        Thread { CaptureWidget.refreshAll(app) }.start()
    }
}
