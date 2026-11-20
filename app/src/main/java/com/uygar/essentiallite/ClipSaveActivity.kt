package com.uygar.essentiallite

import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle

/** Panodaki metni tek dokunuşla not olarak kaydeder (kısayoldan). Link ise sayfa başlığı da eklenir. */
class ClipSaveActivity : Activity() {
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // Android 10+ panoyu yalnızca odaktaki uygulamaya verir; bu yüzden odak gelince okunur.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done) return
        done = true
        val cm = getSystemService(ClipboardManager::class.java)
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Store.toast(this, "CLIPBOARD EMPTY")
        } else {
            val f = Store.newFile(this, "txt")
            f.writeText(text)
            LinkTitle.attach(this, f, text)
            val app = applicationContext
            Thread { CaptureWidget.refreshAll(app) }.start()
            Store.toast(this, "CLIPBOARD SAVED")
        }
        finish()
    }
}
