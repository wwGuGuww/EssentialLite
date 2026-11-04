package com.uygar.essentiallite

import android.app.Application
import android.content.res.Configuration

/** Uygulama başlarken tema renklerini ayarlar; sistem koyu/açık modu değişince yeniler. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Ui.apply(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Ui.apply(this)
    }
}
