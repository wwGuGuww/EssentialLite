package com.uygar.essentiallite

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.File

/**
 * Dosya yöneticisi ya da başka bir uygulamadan "bununla aç" ile gelen ses dosyasını
 * özel klasöre kopyalar ve oynatıcıda açar. Kaynak dosyaya dokunulmaz.
 */
class OpenAudioActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent?.data
        if (uri == null) {
            finish()
            return
        }
        Thread {
            val out = runCatching { importAudio(uri) }
                .onFailure { AppLog.e(this, "dış ses açılamadı", it) }
                .getOrNull()
            runOnUiThread {
                if (out == null) {
                    Store.toast(this, "SES AÇILAMADI")
                } else {
                    startActivity(Intent(this, PlayerActivity::class.java).putExtra("path", out.absolutePath))
                }
                finish()
            }
        }.start()
    }

    /**
     * Ses dosyasını geçici olarak kopyalar. Aynı içerik zaten kayıtlıysa yeni kopya açılmaz,
     * mevcut kayıt döndürülür. Böylece aynı ses kaç kez açılırsa açılsın tek kayıt kalır.
     */
    private fun importAudio(uri: Uri): File? {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val ext = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in Store.AUDIO } ?: "m4a"
        val stream = contentResolver.openInputStream(uri) ?: return null
        val tmp = File(cacheDir, "import-${System.nanoTime()}.$ext")
        stream.use { input -> tmp.outputStream().use { input.copyTo(it) } }

        val hash = sha256(tmp)
        val existing = Store.dir(this).listFiles()?.firstOrNull {
            it.isFile && it.extension.lowercase() in Store.AUDIO && it.length() == tmp.length() && sha256(it) == hash
        }
        if (existing != null) {
            tmp.delete()
            return existing
        }
        val f = Store.newFile(this, ext)
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
        return f
    }

    private fun sha256(f: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
