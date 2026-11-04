package com.uygar.essentiallite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.Calendar

/** Hatırlatıcı tarih/saat ayrıştırıcısı. "Şu an" 4 Kasım 2026 10:00 (Çarşamba) alınır. */
class RemindersTest {

    private val now: Long = cal(2026, 11, 4, 10, 0)

    private fun cal(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, m - 1, d, h, min, 0)
        }.timeInMillis

    @Test fun yarin_saatli() = assertEquals(cal(2026, 11, 5, 15, 0), Reminders.parse("Yarın 15:00 toplantı", now))

    @Test fun pazartesi_sabah_dokuz() = assertEquals(cal(2026, 11, 9, 9, 0), Reminders.parse("pazartesi", now))

    @Test fun gun_sonra() = assertEquals(cal(2026, 11, 7, 9, 0), Reminders.parse("3 gün sonra", now))

    @Test fun saat_sonra() = assertEquals(now + 2 * 3_600_000L, Reminders.parse("2 saat sonra ara", now))

    @Test fun acik_tarih_ve_saat() = assertEquals(cal(2026, 11, 10, 14, 30), Reminders.parse("10.11 14:30 fatura", now))

    @Test fun sadece_saat_bugun() = assertEquals(cal(2026, 11, 4, 15, 0), Reminders.parse("15:00 toplantı", now))

    @Test fun saat_kelimesi() = assertEquals(cal(2026, 11, 4, 15, 0), Reminders.parse("saat 15 de gel", now))

    @Test fun tarih_yoksa_null() = assertNull(Reminders.parse("alışveriş listesi: süt, yumurta", now))
}

/** Arama: Türkçe büyük/küçük harf (İ/ı) ve metin araması. */
class StoreFilterTest {

    private fun entry(text: String) = Entry(File("x.txt"), "NOT", 0L, text)

    @Test fun buyuk_i_ile_aranir() {
        val r = Store.filter(listOf(entry("İstanbul toplantısı")), Store.ALL, "İSTANBUL")
        assertEquals(1, r.size)
    }

    @Test fun kucuk_i_ile_aranir() {
        val r = Store.filter(listOf(entry("İstanbul toplantısı")), Store.ALL, "istanbul")
        assertEquals(1, r.size)
    }

    @Test fun eslesmeyen_bos() {
        val r = Store.filter(listOf(entry("süt yumurta")), Store.ALL, "fatura")
        assertEquals(0, r.size)
    }
}
