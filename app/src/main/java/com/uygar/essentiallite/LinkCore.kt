package com.uygar.essentiallite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Telefon ↔ PC bağlantısı (tasarım 21–23). PC'deki uygulama QR kodunda adres ve tek kullanımlık bir anahtar verir;
 * telefon o adrese bağlanır ve tüm trafik AES-256-GCM ile şifrelenir. Sunucu yoktur; her şey yerel ağda kalır.
 */
object Link {
    enum class St { OFF, CONNECTING, CONFIRM, LIVE }

    const val PREF = "essentiallite_link"

    @Volatile var st = St.OFF
    @Volatile var pcName = ""
    @Volatile var pcOs = ""
    @Volatile var ping = 0
    @Volatile var code = ""
    /** Son hata ya da durum metni (ekranlarda gösterilir). */
    @Volatile var note = ""

    val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val rnd = SecureRandom()
    private var sock: Socket? = null
    private var out: OutputStream? = null
    private var key: ByteArray = ByteArray(0)
    private var pairing = false
    private var epoch = 0
    private var app: Context? = null
    private val writeLock = Any()

    /** Gönderilen/alınan son aktarımlar: ad, boyut metni, zaman, yön (true = PC'ye). */
    class Transfer(val name: String, val meta: String, val at: Long, val toPc: Boolean)

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun feature(ctx: Context, k: String, def: Boolean) = prefs(ctx).getBoolean("f_$k", def)
    fun setFeature(ctx: Context, k: String, v: Boolean) = prefs(ctx).edit().putBoolean("f_$k", v).apply()

    fun recent(ctx: Context): List<Transfer> = try {
        val a = JSONArray(prefs(ctx).getString("recent", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Transfer(o.getString("n"), o.getString("m"), o.getLong("t"), o.getBoolean("o")) }
    } catch (e: Exception) { emptyList() }

    private fun addRecent(ctx: Context, t: Transfer) {
        val list = (listOf(t) + recent(ctx)).take(6)
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("n", it.name).put("m", it.meta).put("t", it.at).put("o", it.toPc)) }
        prefs(ctx).edit().putString("recent", a.toString()).apply()
    }

    fun app(): Context? = app

    fun changed() { main.post { listeners.forEach { runCatching { it() } } } }

    val live get() = st == St.LIVE
    fun saved(ctx: Context) = prefs(ctx).getString("host", null) != null

    // ---------- Şifreleme ----------

    private fun b64u(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun fromB64u(s: String) = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun confirmCode(k: ByteArray): String {
        val h = MessageDigest.getInstance("SHA-256").digest(k + "confirm".toByteArray())
        val n = ((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
        return (n % 1000000).toString().padStart(6, '0')
    }

    private fun seal(obj: JSONObject): ByteArray {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val body = iv + c.doFinal(obj.toString().toByteArray())
        val len = body.size
        return byteArrayOf((len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()) + body
    }

    private fun open(body: ByteArray): JSONObject {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, body.copyOfRange(0, 12)))
        return JSONObject(String(c.doFinal(body, 12, body.size - 12)))
    }

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Ağ yazımı ana iş parçacığında yapılamaz; tek bir yazıcı iş parçacığı sırayla gönderir. */
    fun send(o: JSONObject) {
        val s = out ?: return
        val my = epoch
        val frame = try { seal(o) } catch (e: Exception) { return }
        writer.execute {
            try {
                synchronized(writeLock) { s.write(frame); s.flush() }
            } catch (e: Exception) { dropped(my) }
        }
    }

    fun send(vararg kv: Pair<String, Any?>) = send(JSONObject().also { j -> kv.forEach { j.put(it.first, it.second) } })

    // ---------- Bağlantı ----------

    /** QR'dan okunan "el://IP:PORT/ANAHTAR" metnini çözer; geçersizse null. */
    fun parse(text: String): Triple<String, Int, ByteArray>? {
        val m = Regex("""^el://([0-9a-zA-Z.\-]+):(\d{1,5})/([A-Za-z0-9_\-]{43})$""").find(text.trim()) ?: return null
        return Triple(m.groupValues[1], m.groupValues[2].toInt(), fromB64u(m.groupValues[3]))
    }

    fun connect(ctx: Context, host: String, port: Int, k: ByteArray, isPairing: Boolean) {
        app = ctx.applicationContext
        disconnect(ctx, forget = false, quiet = true)
        key = k
        pairing = isPairing
        st = St.CONNECTING
        note = ""
        val my = ++epoch
        changed()
        Thread {
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), 4000)
                if (my != epoch) { s.close(); return@Thread }
                sock = s
                out = s.getOutputStream()
                send("t" to "hello", "name" to deviceName(), "bat" to battery(ctx), "net" to "Wi-Fi")
                val inp = DataInputStream(s.getInputStream())
                while (my == epoch) {
                    val len = inp.readInt()
                    if (len < 28 || len > 64 * 1024 * 1024) throw java.io.IOException("kötü çerçeve")
                    val body = ByteArray(len)
                    inp.readFully(body)
                    handle(ctx.applicationContext, open(body), my)
                }
            } catch (e: Exception) {
                if (my == epoch) {
                    note = if (st == St.CONNECTING) "PC'YE ULAŞILAMADI" else ""
                    dropped(my)
                }
            }
        }.start()
    }

    private fun dropped(my: Int) {
        if (my != epoch) return
        val ctx = app
        runCatching { sock?.close() }
        sock = null; out = null
        val wasLive = st == St.LIVE
        st = St.OFF
        ping = 0
        LinkControl.stopAll()
        changed()
        // Kayıtlı bir PC varsa ve kullanıcı kesmediyse yeniden denenir.
        if (ctx != null && saved(ctx) && prefs(ctx).getBoolean("auto", false)) {
            main.postDelayed({
                if (my == epoch && st == St.OFF) {
                    val p = prefs(ctx)
                    reconnectSaved(ctx)
                }
            }, if (wasLive) 2000 else 6000)
        } else LinkService.stop(ctx)
    }

    fun reconnectSaved(ctx: Context): Boolean {
        val p = prefs(ctx)
        val host = p.getString("host", null) ?: return false
        val k = p.getString("key", null) ?: return false
        LinkService.start(ctx)
        connect(ctx, host, p.getInt("port", 7879), fromB64u(k), false)
        return true
    }

    /** [forget] ise kayıtlı eşleşme de silinir (PC bir daha otomatik bağlanmaz). */
    fun disconnect(ctx: Context, forget: Boolean, quiet: Boolean = false) {
        epoch++
        runCatching { send("t" to "bye") }
        runCatching { sock?.close() }
        sock = null; out = null
        st = St.OFF
        ping = 0
        LinkControl.stopAll()
        if (forget) prefs(ctx).edit().remove("host").remove("key").remove("port").putBoolean("auto", false).apply()
        else if (!quiet) prefs(ctx).edit().putBoolean("auto", false).apply()
        if (!quiet) LinkService.stop(ctx)
        changed()
    }

    /** Eşleştirme onaylandı: bağlantı kalıcı hale gelir. */
    fun accept(ctx: Context, host: String, port: Int) {
        prefs(ctx).edit().putString("host", host).putInt("port", port).putString("key", b64u(key)).putString("pc", pcName).putBoolean("auto", true).apply()
        send("t" to "ok")
        pairing = false
        st = St.LIVE
        LinkService.start(ctx)
        changed()
    }

    private fun deviceName(): String = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim()

    fun battery(ctx: Context): Int {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // ---------- Gelen iletiler ----------

    private fun handle(ctx: Context, m: JSONObject, my: Int) {
        when (m.optString("t")) {
            "hello" -> {
                pcName = m.optString("pc", "PC")
                pcOs = m.optString("os", "WINDOWS")
                code = confirmCode(key)
                st = if (pairing) St.CONFIRM else St.LIVE
                if (st == St.LIVE) { LinkService.start(ctx); startPings() }
                changed()
            }
            "ok" -> if (!pairing) { st = St.LIVE; startPings(); changed() }
            "ping" -> send("t" to "pong", "ts" to m.optLong("ts"))
            "pong" -> { ping = (System.currentTimeMillis() - m.optLong("ts")).toInt().coerceAtLeast(1); changed() }
            "bye" -> { epoch++; dropped(epoch); }
            "clip" -> if (feature(ctx, "clip", true)) main.post {
                runCatching {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipFromPc = m.optString("text")
                    cm.setPrimaryClip(ClipData.newPlainText("PC", clipFromPc))
                }
            }
            "fstart" -> if (feature(ctx, "files", true)) incomingStart(ctx, m)
            "fchunk" -> incomingChunk(m)
            "fend" -> incomingEnd(ctx, m)
            "reply" -> LinkNotif.reply(m.optString("id"), m.optString("text"))
            "dismiss" -> LinkNotif.dismiss(m.optString("id"))
            "touch", "key", "text" -> LinkControl.input(ctx, m)
            "control" -> if (!m.optBoolean("on")) LinkControl.stopAll()
        }
    }

    @Volatile var clipFromPc: String? = null

    private var pingThread: Thread? = null
    private fun startPings() {
        if (pingThread?.isAlive == true) return
        val my = epoch
        pingThread = Thread {
            while (my == epoch && st == St.LIVE) {
                send("t" to "ping", "ts" to System.currentTimeMillis())
                try { Thread.sleep(2500) } catch (e: InterruptedException) { return@Thread }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    // ---------- Dosya alma (PC → telefon) ----------

    private class In(val name: String, val uri: Uri, val os: OutputStream, var size: Long = 0)
    private val incoming = HashMap<String, In>()

    private fun incomingStart(ctx: Context, m: JSONObject) {
        val name = m.optString("name", "dosya").replace(Regex("""[\\/:*?"<>|]"""), "_")
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/EssentialLite")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return
        val os = ctx.contentResolver.openOutputStream(uri) ?: return
        synchronized(incoming) { incoming[m.optString("id")] = In(name, uri, os) }
    }

    private fun incomingChunk(m: JSONObject) {
        val f = synchronized(incoming) { incoming[m.optString("id")] } ?: return
        val b = Base64.decode(m.optString("d"), Base64.DEFAULT)
        f.os.write(b); f.size += b.size
    }

    private fun incomingEnd(ctx: Context, m: JSONObject) {
        val f = synchronized(incoming) { incoming.remove(m.optString("id")) } ?: return
        runCatching { f.os.close() }
        ctx.contentResolver.update(f.uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        addRecent(ctx, Transfer(f.name, sizeText(f.size) + " · İNDİRİLENLER/ESSENTIALLITE", System.currentTimeMillis(), false))
        LinkService.notify(ctx, "DOSYA GELDİ", f.name)
        changed()
    }

    fun sizeText(n: Long): String = when {
        n >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", n / 1048576.0).replace('.', ',')
        n >= 1024 -> "${n / 1024} KB"
        else -> "$n B"
    }

    // ---------- Dosya gönderme (telefon → PC) ----------

    /** Dosyaları PC'ye yollar; [progress] 0..100 ile arka plan iş parçacığından çağrılır. */
    fun sendFiles(ctx: Context, files: List<File>, progress: (Int, String) -> Unit, done: () -> Unit) {
        val total = files.sumOf { it.length() }.coerceAtLeast(1)
        Thread {
            var sent = 0L
            for (f in files) {
                if (!live) break
                val id = java.lang.Long.toHexString(System.nanoTime())
                send("t" to "fstart", "id" to id, "name" to f.name, "size" to f.length())
                f.inputStream().use { ins ->
                    val buf = ByteArray(48 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        send("t" to "fchunk", "id" to id, "d" to Base64.encodeToString(buf, 0, n, Base64.NO_WRAP))
                        sent += n
                        progress((sent * 100 / total).toInt().coerceAtMost(99), f.name)
                    }
                }
                send("t" to "fend", "id" to id)
                addRecent(ctx, Transfer(f.name, sizeText(f.length()) + " · " + f.extension.uppercase(), System.currentTimeMillis(), true))
            }
            progress(100, "")
            main.post { done(); changed() }
        }.start()
    }

    fun sendClip(text: String) = send("t" to "clip", "text" to text)
}

/** Bağlantı sürerken uygulamayı canlı tutan ön plan servisi. */
class LinkService : Service() {
    companion object {
        private const val CH = "link"
        private const val ID = 7301

        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, LinkService::class.java)) }
        }

        fun stop(ctx: Context?) {
            ctx ?: return
            runCatching { ctx.stopService(Intent(ctx, LinkService::class.java)) }
        }

        fun channel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CH) == null)
                nm.createNotificationChannel(NotificationChannel(CH, "PC bağlantısı", NotificationManager.IMPORTANCE_LOW))
        }

        fun notify(ctx: Context, title: String, text: String) {
            channel(ctx)
            val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, LinkActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, CH).setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).build()
            runCatching { ctx.getSystemService(NotificationManager::class.java).notify(System.nanoTime().toInt(), n) }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        // Telefonda kopyalanan metin PC'nin panosuna da gider (Android arka planda panoya erişimi kısıtlar; uygulama açıkken kesin çalışır).
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            if (!Link.live || !Link.feature(this, "clip", true)) return@OnPrimaryClipChangedListener
            val t = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull()
            if (!t.isNullOrBlank() && t != Link.clipFromPc && t.length < 200_000) Link.sendClip(t)
        }.also { cm.addPrimaryClipChangedListener(it) }
    }

    override fun onDestroy() {
        clipListener?.let { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(it) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channel(this)
        val pi = PendingIntent.getActivity(this, 0, Intent(this, LinkActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getBroadcast(this, 1, Intent(this, LinkStopReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("PC BAĞLANTISI")
            .setContentText(if (Link.pcName.isNotEmpty()) "${Link.pcName} · şifreli" else "Bağlanıyor…")
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "KES", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, n)
        return START_STICKY
    }
}

class LinkStopReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) { Link.disconnect(ctx, forget = false) }
}

/** Telefon bildirimlerini PC'ye aktarır; PC'den gelen yanıtı bildirimdeki satır içi yanıt eylemiyle gönderir. */
class LinkNotif : NotificationListenerService() {
    companion object {
        @Volatile var inst: LinkNotif? = null
        private val replies = java.util.concurrent.ConcurrentHashMap<String, Notification.Action>()

        fun enabled(ctx: Context): Boolean =
            android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
                ?.contains(ctx.packageName) == true

        fun reply(id: String, text: String) {
            val svc = inst ?: return
            val a = replies[id] ?: return
            val ri = a.remoteInputs?.firstOrNull() ?: return
            val intent = Intent()
            val b = Bundle().also { it.putCharSequence(ri.resultKey, text) }
            RemoteInput.addResultsToIntent(a.remoteInputs, intent, b)
            runCatching { a.actionIntent.send(svc, 0, intent) }
            runCatching { svc.cancelNotification(id) }
        }

        fun dismiss(id: String) { runCatching { inst?.cancelNotification(id) } }
    }

    override fun onListenerConnected() { inst = this }
    override fun onListenerDisconnected() { if (inst === this) inst = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Link.live || !Link.feature(this, "notif", true)) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val act = n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        if (act != null) replies[sbn.key] = act else replies.remove(sbn.key)
        Link.send("t" to "notif", "id" to sbn.key, "app" to label.uppercase(java.util.Locale("tr")), "who" to title, "text" to text,
            "time" to Store.clock(sbn.postTime), "canReply" to (act != null))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        replies.remove(sbn.key)
        if (Link.live) Link.send("t" to "notif-gone", "id" to sbn.key)
    }
}
