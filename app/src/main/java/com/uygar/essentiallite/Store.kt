package com.uygar.essentiallite

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Listede bir satır: ana kayıt, varsa bağlı not metni ve hangi uygulamadan yakalandığı. */
data class Entry(
    val file: File,
    val kind: String,
    val time: Long,
    val text: String,
    val source: String? = null,
    val sourceLabel: String? = null,
    val pinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val title: String = "",
    val remind: Long = 0L,
    /** Ekran görüntüsündeki okunmuş metin (OCR); yalnızca aramada kullanılır. */
    val ocr: String = ""
) {
    val preview: String
        get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60) ?: ""
}

/** Tüm kayıtların tutulduğu yer: uygulamanın özel klasörü. */
object Store {
    const val ALL = "TÜMÜ"
    private val IMAGES = setOf("png", "jpg", "jpeg", "webp")
    /** Oynatılabilen ses uzantıları; dışarıdan açılan dosyalar da bu listeye göre SES olur. */
    val AUDIO = setOf("m4a", "mp3", "wav", "ogg", "aac", "3gp", "amr", "opus")
    private val MEDIA = IMAGES + AUDIO
    private val textCache = ConcurrentHashMap<String, Pair<Long, String>>()
    private val labelCache = ConcurrentHashMap<String, String>()

    fun dir(ctx: Context): File = File(ctx.filesDir, "captures").apply { mkdirs() }

    fun newFile(ctx: Context, ext: String): File =
        File(dir(ctx), "${System.currentTimeMillis()}.$ext")

    fun kindOf(f: File): String = when (f.extension.lowercase()) {
        "png" -> "EKRAN"
        "jpg", "jpeg", "webp" -> "GÖRSEL"
        "txt" -> "NOT"
        in AUDIO -> "SES"
        else -> "?"
    }

    fun isImage(f: File) = f.extension.lowercase() in IMAGES

    fun stamp(t: Long): String = SimpleDateFormat("dd.MM HH:mm", Locale("tr")).format(Date(t))

    fun dateOf(t: Long): String = SimpleDateFormat("dd.MM", Locale("tr")).format(Date(t))

    fun clock(t: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))

    /** Aynı günü gruplamak için anahtar (yyyyMMdd). */
    fun dayKey(t: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(t))

    private fun readText(f: File): String {
        val mod = f.lastModified()
        textCache[f.absolutePath]?.let { if (it.first == mod) return it.second }
        val t = try { f.readText() } catch (e: Exception) { "" }
        textCache[f.absolutePath] = mod to t
        return t
    }

    /**
     * Yeniden eskiye tüm kayıtlar. Aynı zaman damgalı .txt, bir ekran/ses/görsel kaydının
     * notudur: ayrı satır olarak listelenmez, ana kaydın metni olur. .meta kaynağı tutar.
     */
    fun all(ctx: Context): List<Entry> {
        val files = dir(ctx).listFiles()?.toList() ?: emptyList()
        val mediaStems = files.filter { it.extension.lowercase() in MEDIA }.map { it.nameWithoutExtension }.toSet()
        val sidecars = files.filter { it.extension == "txt" && it.nameWithoutExtension in mediaStems }.toSet()
        val sidecarByStem = sidecars.associateBy { it.nameWithoutExtension }
        return files
            .filter { it.extension.lowercase() in MEDIA || (it.extension == "txt" && it !in sidecars) }
            .map { f ->
                val text = if (f.extension == "txt") readText(f)
                else sidecarByStem[f.nameWithoutExtension]?.let { readText(it) }.orEmpty()
                val src = sourceOf(f)
                val info = Info.read(f)
                Entry(
                    f, kindOf(f), f.lastModified(), text, src, src?.let { appLabel(ctx, it) },
                    info.pinned, info.tags, info.title, info.remind, Info.readOcr(f)
                )
            }
            .sortedByDescending { it.time }
    }

    fun latest(ctx: Context): Entry? = all(ctx).firstOrNull()

    /** Tür filtresi (ALL = hepsi) ve metin araması: not metni, tür, tarih ve kaynak uygulama. */
    fun filter(entries: List<Entry>, kind: String, query: String): List<Entry> {
        val loc = Locale("tr")
        val q = query.trim().lowercase(loc)
        val qr = query.trim().lowercase(Locale.ROOT)
        return entries.filter { e ->
            (kind == ALL || e.kind == kind || (kind == "EKRAN" && e.kind == "GÖRSEL")) &&
                (q.isEmpty() ||
                    e.text.lowercase(loc).contains(q) ||
                    e.kind.lowercase(loc).contains(q) ||
                    stamp(e.time).contains(q) ||
                    (e.sourceLabel?.lowercase(Locale.ROOT)?.contains(qr) == true) ||
                    (e.source?.lowercase(Locale.ROOT)?.contains(qr) == true) ||
                    e.text.lowercase(Locale.ROOT).contains(qr) ||
                    e.title.lowercase(loc).contains(q) || e.title.lowercase(Locale.ROOT).contains(qr) ||
                    e.tags.any { it.contains(q) || it.lowercase(Locale.ROOT).contains(qr) } ||
                    e.ocr.lowercase(loc).contains(q) || e.ocr.lowercase(Locale.ROOT).contains(qr))
        }
    }

    // --- Kaynak uygulama (.meta) ---

    private fun metaFile(f: File) = File(f.parentFile, "${f.nameWithoutExtension}.meta")

    /** Kaydın hangi uygulamadan geldiğini (paket adı) saklar. */
    fun saveMeta(f: File, source: String?) {
        if (!source.isNullOrBlank()) metaFile(f).writeText(source)
    }

    fun sourceOf(f: File): String? =
        metaFile(f).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    fun copyMeta(from: File, to: File) = saveMeta(to, sourceOf(from))

    private fun appLabel(ctx: Context, pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    // --- Bağlı notlar ---

    /** Ekran/ses/görsel kaydının notunu (aynı adlı .txt) yazar. Boş metin notu siler. */
    fun saveNote(media: File, body: String) {
        val note = File(media.parentFile, "${media.nameWithoutExtension}.txt")
        textCache.remove(note.absolutePath)
        if (body.isBlank()) note.delete() else note.writeText(body)
    }

    /** Ekran/ses/görsel kaydının bağlı notunun metni (yoksa boş). */
    fun noteOf(media: File): String {
        val note = File(media.parentFile, "${media.nameWithoutExtension}.txt")
        return if (note.exists()) note.readText() else ""
    }

    // --- Not ekleri (görseller) ---

    /** Notun görsellerinin durduğu klasör: <damga>.att */
    fun attDir(note: File) = File(note.parentFile, "${note.nameWithoutExtension}.att")

    fun attachments(note: File): List<File> =
        attDir(note).listFiles()?.filter { it.isFile && it.extension.lowercase() in IMAGES }?.sortedBy { it.name } ?: emptyList()

    /** Görseli notun ek klasörüne kopyalar ve kopyalanan dosyayı döndürür. */
    fun addAttachment(note: File, input: java.io.InputStream, ext: String): File {
        val dir = attDir(note).apply { mkdirs() }
        var f = File(dir, "${System.currentTimeMillis()}.$ext")
        while (f.exists()) f = File(dir, "${System.nanoTime()}.$ext")
        f.outputStream().use { input.copyTo(it) }
        return f
    }

    /** Dosyayı siler; medya dosyasıysa bağlı notunu da, her durumda kaynak bilgisini ve eklerini de siler. */
    fun delete(f: File) {
        textCache.remove(f.absolutePath)
        f.delete()
        attDir(f).deleteRecursively()
        metaFile(f).delete()
        Info.deleteAll(f)
        if (f.extension.lowercase() in MEDIA) File(f.parentFile, "${f.nameWithoutExtension}.txt").delete()
    }

    /** Tüm kayıtları, eklerini, bilgi dosyalarını, arşivi, günlüğü ve API anahtarını siler. Geri alınamaz. */
    fun wipeAll(ctx: Context) {
        dir(ctx).listFiles().orEmpty().forEach { it.deleteRecursively() }
        Vault.wipe(ctx)
        AppLog.clear(ctx)
        Prefs.setRecent(ctx, emptyList())
        Settings.saveApiKey(ctx, "")
    }

    fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }
}
