package com.uygar.essentiallite

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.util.Calendar
import java.util.Locale

/** Nottan tarih/saat bulma, alarm kurma ve bildirim. */
object Reminders {
    private const val CHANNEL = "reminders"
    const val EXTRA_PATH = "path"

    private val DAYS = mapOf(
        "pazartesi" to Calendar.MONDAY, "salı" to Calendar.TUESDAY, "çarşamba" to Calendar.WEDNESDAY,
        "perşembe" to Calendar.THURSDAY, "cuma" to Calendar.FRIDAY, "cumartesi" to Calendar.SATURDAY,
        "pazar" to Calendar.SUNDAY
    )

    private val DAYS_EN = mapOf(
        "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY
    )

    /**
     * Finds a date/time in text, English and Turkish: "tomorrow 3pm", "monday", "10.11 14:30", "in 3 days",
     * "in 2 hours", "tonight", "morning" (Turkish: "yarın 15:00", "3 gün sonra", "akşam").
     * Returns the future time in ms, or null when nothing is found.
     * Metinde tarih/saat arar: "yarın 15:00", "pazartesi", "10.11 14:30", "3 gün sonra", "2 saat sonra",
     * "akşam", "sabah". Bulursa gelecekteki zamanı (ms), bulamazsa null döner.
     */
    fun parse(text: String, now: Long = System.currentTimeMillis()): Long? {
        val t = text.lowercase(Locale("tr"))
        val te = text.lowercase(Locale.ROOT)
        val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        var dateFound = false
        var hour = -1
        var minute = 0

        // Göreli süre: "N dakika/saat/gün/hafta sonra"
        Regex("""(\d{1,3})\s*(dakika|dk|saat|gün|hafta)\s*sonra""").find(t)?.let {
            val n = it.groupValues[1].toInt()
            when (it.groupValues[2]) {
                "dakika", "dk" -> return now + n * 60_000L
                "saat" -> return now + n * 3_600_000L
                "gün" -> { cal.add(Calendar.DAY_OF_YEAR, n); dateFound = true }
                "hafta" -> { cal.add(Calendar.DAY_OF_YEAR, 7 * n); dateFound = true }
            }
        }
        if (!dateFound) Regex("""\bin\s+(\d{1,3})\s*(minutes?|mins?|hours?|hrs?|days?|weeks?)\b""").find(te)?.let {
            val n = it.groupValues[1].toInt()
            val u = it.groupValues[2]
            when {
                u.startsWith("min") -> return now + n * 60_000L
                u.startsWith("h") -> return now + n * 3_600_000L
                u.startsWith("d") -> { cal.add(Calendar.DAY_OF_YEAR, n); dateFound = true }
                else -> { cal.add(Calendar.DAY_OF_YEAR, 7 * n); dateFound = true }
            }
        }
        // Açık tarih: 10.11, 10/11, 10.11.2026
        if (!dateFound) Regex("""\b(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?\b""").find(t)?.let {
            val d = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            if (d in 1..31 && m in 1..12) {
                var y = it.groupValues[3].let { s -> if (s.isEmpty()) cal.get(Calendar.YEAR) else if (s.length == 2) 2000 + s.toInt() else s.toInt() }
                cal.set(y, m - 1, d)
                if (it.groupValues[3].isEmpty() && cal.timeInMillis < now - 3_600_000L) { y += 1; cal.set(y, m - 1, d) }
                dateFound = true
            }
        }
        if (!dateFound) {
            when {
                "yarından sonra" in t || "öbür gün" in t || "ertesi gün" in t -> { cal.add(Calendar.DAY_OF_YEAR, 2); dateFound = true }
                "yarın" in t -> { cal.add(Calendar.DAY_OF_YEAR, 1); dateFound = true }
                "bugün" in t || "bu akşam" in t || "bu sabah" in t -> dateFound = true
                "haftaya" in t -> { cal.add(Calendar.DAY_OF_YEAR, 7); dateFound = true }
                "day after tomorrow" in te -> { cal.add(Calendar.DAY_OF_YEAR, 2); dateFound = true }
                "tomorrow" in te -> { cal.add(Calendar.DAY_OF_YEAR, 1); dateFound = true }
                "next week" in te -> { cal.add(Calendar.DAY_OF_YEAR, 7); dateFound = true }
                "today" in te || "tonight" in te || "this evening" in te || "this morning" in te -> dateFound = true
            }
        }
        if (!dateFound) {
            for ((name, dow) in DAYS) {
                if (Regex("""\b$name\b""").containsMatchIn(t) || Regex("""\b$name(ya|ye|da|de|ye kadar)\b""").containsMatchIn(t)) {
                    var add = (dow - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (add == 0) add = 7
                    cal.add(Calendar.DAY_OF_YEAR, add)
                    dateFound = true
                    break
                }
            }
        }
        if (!dateFound) {
            for ((name, dow) in DAYS_EN) {
                if (Regex("""\b$name\b""").containsMatchIn(te)) {
                    var add = (dow - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (add == 0) add = 7
                    cal.add(Calendar.DAY_OF_YEAR, add)
                    dateFound = true
                    break
                }
            }
        }

        // Saat: 15:00 (iki nokta) ya da, tarih parçası çıkarıldıktan sonra kalan metinde, 15.30; ayrıca "saat 15"
        val rest = Regex("""\b\d{1,2}[./]\d{1,2}(?:[./]\d{2,4})?\b""").replace(t, " ")
        Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(t)?.let {
            hour = it.groupValues[1].toInt(); minute = it.groupValues[2].toInt()
        }
        if (hour == -1) Regex("""\b([01]?\d|2[0-3])\.([0-5]\d)\b""").find(rest)?.let {
            hour = it.groupValues[1].toInt(); minute = it.groupValues[2].toInt()
        }
        if (hour == -1) Regex("""saat\s*(\d{1,2})\b""").find(t)?.let {
            val h = it.groupValues[1].toInt()
            if (h in 0..23) { hour = h; minute = 0 }
        }
        if (hour == -1) Regex("""\b(1[0-2]|0?[1-9])(?::([0-5]\d))?\s*(am|pm)\b""").find(te)?.let {
            var h = it.groupValues[1].toInt() % 12
            if (it.groupValues[3] == "pm") h += 12
            hour = h; minute = it.groupValues[2].ifEmpty { "0" }.toInt()
        }
        if (hour == -1) Regex("""\bat\s*(\d{1,2})\b""").find(te)?.let {
            val h = it.groupValues[1].toInt()
            if (h in 0..23) { hour = h; minute = 0 }
        }
        if (hour == -1) {
            when {
                "afternoon" in te -> hour = 15
                "noon" in te -> hour = 12
                "tonight" in te -> hour = 21
                "evening" in te -> hour = 19
                "night" in te -> hour = 22
                "morning" in te -> hour = 9
                "öğleden sonra" in t -> hour = 15
                "öğle" in t -> hour = 12
                "akşam" in t -> hour = 19
                "gece" in t -> hour = 22
                "sabah" in t -> hour = 9
            }
        }
        if (!dateFound && hour == -1) return null

        if (hour == -1) { hour = 9; minute = 0 }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        // Yalnızca saat verilmişse ve geçmişse yarına al.
        if (!dateFound && cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return if (cal.timeInMillis > now) cal.timeInMillis else null
    }

    private fun pending(ctx: Context, f: File): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, f.nameWithoutExtension.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).putExtra(EXTRA_PATH, f.absolutePath),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Alarmı kurar ve zamanı kayda yazar. */
    fun set(ctx: Context, f: File, at: Long) {
        Info.update(f) { it.copy(remind = at) }
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, f))
    }

    fun cancel(ctx: Context, f: File) {
        Info.update(f) { it.copy(remind = 0L) }
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, f))
    }

    /** Cihaz yeniden başlayınca ya da uygulama güncellenince bekleyen alarmları yeniden kurar. */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        Store.all(ctx).filter { it.remind > now }.forEach {
            ctx.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.remind, pending(ctx, it.file))
        }
    }

    internal fun notify(ctx: Context, f: File) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val entry = Store.all(ctx).firstOrNull { it.file.path == f.path }
        val body = entry?.title?.takeIf { it.isNotBlank() } ?: entry?.preview?.takeIf { it.isNotBlank() } ?: "Note reminder"
        val open = PendingIntent.getActivity(
            ctx, f.nameWithoutExtension.hashCode(),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("● ESSENTIAL LITE")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(snoozeAction(ctx, f, 10, "10 MIN"))
            .addAction(snoozeAction(ctx, f, 60, "1 HOUR"))
            .addAction(snoozeAction(ctx, f, -1, "TOMORROW"))
            .build()
        nm.notify(f.nameWithoutExtension.hashCode(), n)
    }

    const val ACTION_SNOOZE = "com.uygar.essentiallite.SNOOZE"
    private const val EXTRA_MIN = "min"

    /** Ertele düğmesi: [minutes] = -1 ise yarın 09:00. */
    private fun snoozeAction(ctx: Context, f: File, minutes: Int, label: String): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, (f.nameWithoutExtension + minutes).hashCode(),
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_PATH, f.absolutePath).putExtra(EXTRA_MIN, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(0, label, pi).build()
    }

    /** Ertelenmiş zaman: [minutes] dakika sonrası ya da -1 ise yarın 09:00. */
    fun snoozeTime(minutes: Int, now: Long = System.currentTimeMillis()): Long {
        if (minutes >= 0) return now + minutes * 60_000L
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val path = intent.getStringExtra(Reminders.EXTRA_PATH) ?: return
        val f = File(path)
        if (!f.exists()) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (intent.action == Reminders.ACTION_SNOOZE) {
            nm.cancel(f.nameWithoutExtension.hashCode())
            val minutes = intent.getIntExtra("min", 10)
            Reminders.set(ctx, f, Reminders.snoozeTime(minutes))
            Store.toast(ctx, "SNOOZED: ${Store.stamp(Reminders.snoozeTime(minutes))}")
            return
        }
        Reminders.notify(ctx, f)
        Info.update(f) { it.copy(remind = 0L) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                Reminders.rescheduleAll(ctx.applicationContext)
                if (Prefs.get(ctx, "batt_on", false)) BatteryWatch.schedule(ctx.applicationContext)
                AlarmStore.schedule(ctx.applicationContext)
            } finally { pending.finish() }
        }.start()
    }
}
