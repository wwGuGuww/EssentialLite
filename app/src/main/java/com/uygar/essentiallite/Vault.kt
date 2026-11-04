package com.uygar.essentiallite

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Gizli bölümün şifreli deposu. Dosyalar files/vault klasöründe, Android Keystore anahtarıyla (AES-256 GCM)
 * şifrelenmiş olarak tutulur; ana listede, aramada, widget'ta ya da OCR'da görünmez.
 * Dosya adı: <zaman>.<uzantı>.v  (ör. 1793800000000.txt.v)
 */
object Vault {
    data class Item(val file: File, val kind: String, val ext: String, val time: Long)

    private const val HASH = "vault_hash"
    private const val SALT = "vault_salt"
    private val IMAGES = setOf("png", "jpg", "jpeg", "webp")

    private fun dir(ctx: Context) = File(ctx.filesDir, "vault").apply { mkdirs() }

    private fun enc(ctx: Context, f: File): EncryptedFile {
        val app = ctx.applicationContext
        val master = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedFile.Builder(app, f, master, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
    }

    // --- Şifre ---

    /** Bölüm bir kez açıldıysa true; "GİZLE" satırı yalnızca o zaman görünür. */
    fun configured(ctx: Context) = Prefs.get(ctx, "vault_on", false) || Settings.secretGet(ctx, HASH) != null

    fun markReady(ctx: Context) = Prefs.set(ctx, "vault_on", true)

    /** Parmak izi/ekran kilidi yoksa kullanılan 4 haneli yedek şifre belirlenmiş mi? */
    fun hasPin(ctx: Context) = Settings.secretGet(ctx, HASH) != null

    private fun hash(pw: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pw.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun setPassword(ctx: Context, pw: String): Boolean {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Settings.secretPut(ctx, SALT, Base64.encodeToString(salt, Base64.NO_WRAP)) &&
            Settings.secretPut(ctx, HASH, hash(pw, salt))
    }

    fun verify(ctx: Context, pw: String): Boolean {
        val h = Settings.secretGet(ctx, HASH) ?: return false
        val salt = Base64.decode(Settings.secretGet(ctx, SALT) ?: return false, Base64.NO_WRAP)
        return hash(pw, salt) == h
    }

    /** Arşivi tamamen siler: şifre ve tüm kayıtlar. Geri alınamaz. */
    fun wipe(ctx: Context) {
        dir(ctx).deleteRecursively()
        Settings.secretRemove(ctx, HASH)
        Settings.secretRemove(ctx, SALT)
        Prefs.set(ctx, "vault_on", false)
    }

    // --- İçerik ---

    fun write(ctx: Context, bytes: ByteArray, ext: String, time: Long = System.currentTimeMillis()): File {
        var t = time
        var f = File(dir(ctx), "$t.$ext.v")
        while (f.exists()) { t++; f = File(dir(ctx), "$t.$ext.v") }
        enc(ctx, f).openFileOutput().use { it.write(bytes) }
        return f
    }

    fun read(ctx: Context, f: File): ByteArray = enc(ctx, f).openFileInput().use { it.readBytes() }

    fun list(ctx: Context): List<Item> =
        (dir(ctx).listFiles() ?: emptyArray()).filter { it.name.endsWith(".v") }.mapNotNull { f ->
            val parts = f.name.removeSuffix(".v").split('.')
            if (parts.size != 2) return@mapNotNull null
            val ext = parts[1].lowercase()
            val kind = when {
                ext == "txt" -> "NOT"
                ext in IMAGES -> "GÖRSEL"
                ext == "m4a" -> "SES"
                else -> return@mapNotNull null
            }
            Item(f, kind, ext, parts[0].toLongOrNull() ?: f.lastModified())
        }.sortedByDescending { it.time }

    fun delete(f: File) {
        f.delete()
    }

    /** Ana listedeki bir kaydı şifreleyip bölüme taşır, orijinali siler. Başarılıysa true. */
    fun moveIn(ctx: Context, e: Entry): Boolean {
        return try {
        // Görseli ya da bağlı notu olan kayıt taşınırsa o ekler kaybolurdu; bu yüzden taşınmaz.
        if (Store.attachments(e.file).isNotEmpty() || (e.kind != "NOT" && e.text.isNotBlank())) return false
        val ext = e.file.extension.lowercase()
        write(ctx, e.file.readBytes(), ext, e.time)
        Store.delete(e.file)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Bölümdeki bir kaydı çözüp ana listeye geri koyar. */
    fun moveOut(ctx: Context, item: Item): Boolean = try {
        val target = File(Store.dir(ctx), "${item.time}.${item.ext}")
        val out = if (target.exists()) Store.newFile(ctx, item.ext) else target
        out.writeBytes(read(ctx, item.file))
        out.setLastModified(item.time)
        delete(item.file)
        true
    } catch (t: Throwable) {
        false
    }
}
