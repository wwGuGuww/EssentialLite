package com.uygar.essentiallite

import android.app.Activity
import android.os.Bundle

/** Simge kısayolundan ekran görüntüsü: kendini kapatır, ardından alttaki ekranı yakalar. */
class QuickShotActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        Thread {
            Thread.sleep(450) // kısayol menüsü ve bu ekran kapansın
            val msg = try { Shot.take(app) } catch (e: Throwable) { "COULDN'T CAPTURE SCREEN" }
            Store.toast(app, msg)
        }.start()
        finish()
    }
}
