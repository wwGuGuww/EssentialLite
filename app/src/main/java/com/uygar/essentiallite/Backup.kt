package com.uygar.essentiallite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Şifreli yedek: tüm kayıtlar (görseller, sesler, notlar, meta ve OCR dosyaları) tek bir zip'te toplanır,
 * kullanıcının parolasından türetilen anahtarla AES-256-GCM ile şifrelenir. Dosya, kullanıcının seçtiği
 * yere (Android dosya seçici → Google Drive dahil) yazılır. Parola telefonda kalır; Google içeriği okuyamaz.
 *
 * Biçim: "ELB1" (4) + tuz (16) + iv (12) + şifreli zip.
 */
object Backup {
    private const val MAGIC = "ELB1"
    private const val ITER = 210_000
    private val rnd = SecureRandom()

    private fun key(pw: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pw, salt, ITER, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(k, "AES")
    }

    /** Kayıt klasörünü şifreli olarak [out]'a yazar. Ayrıntılı hata fırlatır. */
    fun write(ctx: Context, pw: CharArray, out: java.io.OutputStream) {
        val zipBytes = ByteArrayOutputStream().also { bo ->
            ZipOutputStream(bo).use { zo ->
                Store.dir(ctx).listFiles()?.filter { it.isFile }?.forEach { f ->
                    zo.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zo) }
                    zo.closeEntry()
                }
            }
        }.toByteArray()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pw, salt), GCMParameterSpec(128, iv))
        val body = c.doFinal(zipBytes)
        out.use {
            it.write(MAGIC.toByteArray())
            it.write(salt)
            it.write(iv)
            it.write(body)
        }
    }

    /** Yedeği çözer ve henüz olmayan kayıtları ekler. Parola yanlışsa [BadPassword] fırlatır. Eklenen sayıyı döndürür. */
    fun restore(ctx: Context, pw: CharArray, input: java.io.InputStream): Int {
        val all = input.use { it.readBytes() }
        if (all.size < 4 + 16 + 12 + 16 || String(all, 0, 4) != MAGIC) throw IllegalArgumentException("Not an Essential Lite backup")
        val salt = all.copyOfRange(4, 20)
        val iv = all.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pw, salt), GCMParameterSpec(128, iv))
        val zip = try { c.doFinal(all, 32, all.size - 32) } catch (e: Exception) { throw BadPassword() }
        val dir = Store.dir(ctx)
        var added = 0
        ZipInputStream(zip.inputStream()).use { zi ->
            var e = zi.nextEntry
            while (e != null) {
                val name = File(e.name).name // yol saldırısına karşı yalnızca dosya adı
                val target = File(dir, name)
                if (!target.exists() && name.isNotBlank()) {
                    target.outputStream().use { zi.copyTo(it) }
                    added++
                }
                e = zi.nextEntry
            }
        }
        CaptureWidget.refreshAll(ctx)
        return added
    }

    class BadPassword : Exception("Wrong password")
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Yedek ekranı: parola sorar, dosya seçiciyi açar. Yedek dosyası Drive'a seçici üzerinden kaydedilir. */
class BackupActivity : BaseActivity() {
    companion object {
        private const val REQ_SAVE = 61
        private const val REQ_OPEN = 62
    }

    private var pendingPw: CharArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(v: Int) = ToolUi.dp(this, v)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, "Your records, notes and voice memos are saved into a single encrypted file. You can put the file on Drive or anywhere else. If you forget the password, the backup can't be opened.", 11f, Ui.GREY).apply {
            letterSpacing = 0.06f; setLineSpacing(0f, 1.4f)
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(Ui.text(this, "● BACK UP", 10f, Ui.RED).apply { letterSpacing = 0.2f; setPadding(0, dp(24), 0, dp(10)) })
        col.addView(outlineBtn(this, "CREATE BACKUP FILE", true, Ui.WHITE, Ui.WHITE) { askPassword(restore = false) },
            LinearLayout.LayoutParams(MATCH, dp(56)))
        col.addView(Ui.text(this, "● RESTORE BACKUP", 10f, Ui.RED).apply { letterSpacing = 0.2f; setPadding(0, dp(24), 0, dp(10)) })
        col.addView(outlineBtn(this, "PICK BACKUP FILE", false, Ui.LINE, Ui.GREY) { askPassword(restore = true) },
            LinearLayout.LayoutParams(MATCH, dp(56)))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col) }
        ToolUi.screen(this, "BACKUP", "DRIVE", scroll, topGap = 18)
    }

    private fun askPassword(restore: Boolean) {
        Sheet.input(this, if (restore) "BACKUP PASSWORD" else "SET BACKUP PASSWORD", "At least 6 characters", "") { pw ->
            if (pw.length < 6) { Store.toast(this, "AT LEAST 6 CHARACTERS"); return@input }
            pendingPw = pw.toCharArray()
            if (restore) {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQ_OPEN)
            } else {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, "essential-yedek-$stamp.elb")
                }, REQ_SAVE)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(req, res, data)
        val pw = pendingPw ?: return
        pendingPw = null
        val uri: Uri = data?.data ?: return
        if (res != RESULT_OK) return
        Thread {
            try {
                when (req) {
                    REQ_SAVE -> contentResolver.openOutputStream(uri)?.let { Backup.write(this, pw, it) }
                        ?.also { runOnUiThread { Store.toast(this, "BACKUP SAVED") } }
                    REQ_OPEN -> contentResolver.openInputStream(uri)?.let { n ->
                        val added = Backup.restore(this, pw, n)
                        runOnUiThread { Store.toast(this, "$added ITEMS RESTORED") }
                    }
                }
            } catch (e: Backup.BadPassword) {
                runOnUiThread { Store.toast(this, "WRONG PASSWORD") }
            } catch (e: Exception) {
                AppLog.e(this, "yedek", e)
                runOnUiThread { Store.toast(this, "BACKUP FAILED") }
            } finally {
                java.util.Arrays.fill(pw, '\u0000')
            }
        }.start()
    }
}
