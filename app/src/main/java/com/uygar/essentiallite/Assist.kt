package com.uygar.essentiallite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Varsayılan dijital asistan olarak seçilince (güç tuşuna uzun basış, ana ekran kaydırma vb.)
 * sistem bu oturumu başlatır. Oturum kendi penceresi açmaz; hızlı erişim panelini açar.
 */
class AssistService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistSession(this)
}

class AssistSession(private val ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val svc = KeyComboService.instance
        if (svc != null) {
            svc.showPanel()
        } else {
            // Panel erişilebilirlik servisiyle çalışır; kapalıysa kurulum sayfasına götür.
            Store.toast(ctx, "TURN ON QUICK ACCESS FIRST")
            startAssistantActivity(Intent(ctx, AccessSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        hide()
    }
}

/** Asistan olabilmek için sistemin aradığı, konuşma tanımayı hiç yapmayan boş servis. */
class AssistRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
