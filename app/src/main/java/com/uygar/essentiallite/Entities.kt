package com.uygar.essentiallite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract

/**
 * Ekran görüntüsünün OCR metninde telefon, IBAN, tarih, takip numarası, adres ve e-posta gibi şeyleri bulur.
 * Hepsi telefonda, regex ile çalışır; ağ gerektirmez. Dokununca ilgili uygulama açılır ya da metin kopyalanır.
 */
object Entities {
    enum class Kind(val label: String) { PHONE("CALL"), IBAN("COPY"), DATE("CALENDAR"), TRACK("COPY"), URL("OPEN"), MAIL("EMAIL"), PLACE("MAP") }

    data class Found(val kind: Kind, val text: String, val chip: String)

    // Telefon yalnızca +90 ya da 0 ile başlarsa sayılır; böylece takip numaraları telefon sanılmaz.
    private val phone = Regex("""(?<![\d])(?:\+90\s?|0)5\d{2}[\s-]?\d{3}[\s-]?\d{2}[\s-]?\d{2}(?![\d])""")
    private val iban = Regex("""TR\s?\d{2}(?:\s?\d{4}){5}\s?\d{2}""", RegexOption.IGNORE_CASE)
    private val date = Regex("""\b(\d{1,2})[./](\d{1,2})[./](20\d{2})\b""")
    private val track = Regex("""\b[A-Z]{2}\d{9}[A-Z]{2}\b|\b\d{10,16}\b""")
    private val url = Regex("""(?:https?://|www\.)[^\s]+""", RegexOption.IGNORE_CASE)
    private val mail = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
    private val place = Regex("""(?i)\b(?:mah\.?|mahalle|cad\.?|caddesi|sok\.?|sokak|bulvar[ıi]?|no:\s?\d+|street|avenue|boulevard|blvd)\b""")

    fun find(text: String): List<Found> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<Found>()
        phone.findAll(text).map { it.value.trim() }.distinct().take(2).forEach {
            out += Found(Kind.PHONE, it.replace(Regex("""[^\d+]"""), ""), "CALL · ${it.take(14)}")
        }
        iban.findAll(text).map { it.value.replace(" ", "") }.distinct().take(2).forEach {
            out += Found(Kind.IBAN, it, "COPY IBAN")
        }
        date.findAll(text).distinct().take(2).forEach { m ->
            out += Found(Kind.DATE, m.value, "ADD TO CALENDAR · ${m.value}")
        }
        // Takip numarası: yalnızca "kargo/takip/sipariş" geçen metinlerde ara; rastgele uzun sayıları yakalamaz.
        if (Regex("""(?i)kargo|takip|sipariş|gönderi|tracking|shipment|parcel|delivery|order""").containsMatchIn(text)) {
            track.findAll(text).map { it.value }.distinct().take(2).forEach {
                out += Found(Kind.TRACK, it, "TRACKING · COPY")
            }
        }
        url.findAll(text).map { it.value.trimEnd('.', ',') }.distinct().take(2).forEach {
            out += Found(Kind.URL, it, "OPEN · ${it.take(22)}")
        }
        mail.findAll(text).map { it.value }.distinct().take(1).forEach {
            out += Found(Kind.MAIL, it, "EMAIL · $it")
        }
        if (place.containsMatchIn(text)) {
            val line = text.lineSequence().firstOrNull { place.containsMatchIn(it) }?.trim().orEmpty()
            if (line.isNotEmpty()) out += Found(Kind.PLACE, line, "OPEN IN MAPS")
        }
        return out.take(6)
    }

    /** Bulunan öğeyle ilgili işlemi yapar. Hiçbir işlem gizli kasaya ya da kayıtlara dokunmaz. */
    fun act(ctx: Context, f: Found) {
        when (f.kind) {
            Kind.PHONE -> ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${f.text}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Kind.MAIL -> ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${f.text}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Kind.URL -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (f.text.startsWith("http")) f.text else "https://${f.text}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Kind.PLACE -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(f.text))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Kind.IBAN, Kind.TRACK -> copy(ctx, f.text)
            Kind.DATE -> calendar(ctx, f.text)
        }
    }

    private fun copy(ctx: Context, s: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("EL", s))
        Store.toast(ctx, "COPIED")
    }

    private fun calendar(ctx: Context, d: String) {
        val m = date.find(d) ?: return
        val (dd, mm, yy) = m.destructured
        val cal = java.util.Calendar.getInstance().apply {
            set(yy.toInt(), mm.toInt() - 1, dd.toInt(), 9, 0, 0)
        }
        ctx.startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
            .putExtra(CalendarContract.Events.TITLE, "Essential Lite note")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
