package com.focuslock.services

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.focuslock.data.AppDatabase
import com.focuslock.data.AppSettingsEntity
import com.focuslock.data.ChallengeEntity
import com.focuslock.data.record
import com.focuslock.domain.Protection
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * VPN local apenas para DNS: só o tráfego para o DNS virtual 10.111.0.1 entra no túnel.
 * Consultas de domínios bloqueados recebem NXDOMAIN; as demais são encaminhadas ao DNS 8.8.8.8.
 * Não intercepta nem descriptografa HTTPS.
 */
class FocusVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val pool = Executors.newFixedThreadPool(4)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val lastHit = HashMap<String, Long>()
    @Volatile private var domains: Set<String> = emptySet()
    @Volatile private var challenge: ChallengeEntity? = null
    @Volatile private var settings: AppSettingsEntity = AppSettingsEntity()

    companion object {
        const val ACTION_STOP = "com.focuslock.STOP_VPN"
        val running = MutableStateFlow(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tun == null) start()
        return START_STICKY
    }

    private fun start() {
        val db = AppDatabase.get(this)
        scope.launch { db.blockedDomainDao().all().collect { list -> domains = list.map { it.domain }.toSet() } }
        scope.launch { db.challengeDao().active().collect { challenge = it } }
        scope.launch { db.settingsDao().observe().collect { settings = it ?: AppSettingsEntity() } }
        val fd = Builder()
            .setSession("FocusLock")
            .addAddress("10.111.0.2", 32)
            .addDnsServer("10.111.0.1")
            .addRoute("10.111.0.1", 32)
            .setMtu(1500)
            .establish() ?: run { stopSelf(); return }
        tun = fd
        running.value = true
        worker = Thread { loop(fd) }.also { it.start() }
    }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) handle(buf.copyOf(n), output)
            }
        } catch (e: Exception) {
            // túnel fechado
        }
    }

    private fun handle(p: ByteArray, out: FileOutputStream) {
        if (p.size < 40 || (p[0].toInt() shr 4) != 4 || p[9].toInt() != 17) return
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p.size < ihl + 8 + 12) return
        val dport = ((p[ihl + 2].toInt() and 0xFF) shl 8) or (p[ihl + 3].toInt() and 0xFF)
        if (dport != 53) return
        val dns = p.copyOfRange(ihl + 8, p.size)
        val q = readQuestion(dns) ?: return
        if (isBlocked(q.first)) {
            record(q.first)
            write(out, reply(p, ihl, nxdomain(dns, q.second)))
            return
        }
        pool.execute {
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 4000
                    s.send(DatagramPacket(dns, dns.size, InetAddress.getByName("8.8.8.8"), 53))
                    val rb = ByteArray(4096)
                    val rp = DatagramPacket(rb, rb.size)
                    s.receive(rp)
                    write(out, reply(p, ihl, rb.copyOf(rp.length)))
                }
            } catch (e: Exception) {
                // sem resposta: o aplicativo tenta de novo
            }
        }
    }

    private fun isBlocked(host: String): Boolean {
        val st = settings
        if (!st.protectSites || !Protection.isActive(challenge)) return false
        return Protection.domainBlocked(host, domains, st.adultFilter, st.sensitivity)
    }

    private fun record(host: String) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val last = lastHit[host] ?: 0L
            if (now - last < 60_000) return
            lastHit[host] = now
        }
        scope.launch { AppDatabase.get(this@FocusVpnService).blockLogDao().record() }
    }

    private fun readQuestion(d: ByteArray): Pair<String, Int>? {
        var i = 12
        val sb = StringBuilder()
        while (i < d.size) {
            val len = d[i].toInt() and 0xFF
            if (len == 0) return if (i + 5 <= d.size) sb.toString() to (i + 5) else null
            if ((len and 0xC0) != 0 || i + 1 + len > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(d, i + 1, len, Charsets.US_ASCII))
            i += len + 1
        }
        return null
    }

    private fun nxdomain(d: ByteArray, end: Int): ByteArray {
        val r = d.copyOf(end)
        r[2] = (0x80 or (d[2].toInt() and 0x01)).toByte()
        r[3] = 0x83.toByte()
        for (i in 6..11) r[i] = 0
        return r
    }

    private fun reply(req: ByteArray, ihl: Int, payload: ByteArray): ByteArray {
        val total = ihl + 8 + payload.size
        val r = ByteArray(total)
        System.arraycopy(req, 0, r, 0, ihl)
        for (i in 0..3) { r[12 + i] = req[16 + i]; r[16 + i] = req[12 + i] }
        r[2] = (total shr 8).toByte(); r[3] = total.toByte()
        r[8] = 64
        r[10] = 0; r[11] = 0
        r[ihl] = req[ihl + 2]; r[ihl + 1] = req[ihl + 3]
        r[ihl + 2] = req[ihl]; r[ihl + 3] = req[ihl + 1]
        val ul = 8 + payload.size
        r[ihl + 4] = (ul shr 8).toByte(); r[ihl + 5] = ul.toByte()
        r[ihl + 6] = 0; r[ihl + 7] = 0
        System.arraycopy(payload, 0, r, ihl + 8, payload.size)
        var sum = 0
        var i = 0
        while (i < ihl) { sum += ((r[i].toInt() and 0xFF) shl 8) or (r[i + 1].toInt() and 0xFF); i += 2 }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val c = sum.inv() and 0xFFFF
        r[10] = (c shr 8).toByte(); r[11] = c.toByte()
        return r
    }

    private fun write(out: FileOutputStream, b: ByteArray) {
        synchronized(lock) {
            try { out.write(b) } catch (e: Exception) { /* túnel fechado */ }
        }
    }

    private fun stopVpn() {
        running.value = false
        try { tun?.close() } catch (e: Exception) { /* já fechado */ }
        tun = null
        worker = null
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        pool.shutdownNow()
        super.onDestroy()
    }
}
