package com.github.jankoran90.showlyfin.data.maestro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plan MAESTRO — nízkoúrovňový klient protokolu **eISCP** (Integra Serial Control Protocol přes IP),
 * kterým mluví Onkyo/Pioneer receivery. Pioneer VSX-935 (`192.168.1.233:60128`).
 *
 * Formát rámce (ŽIVĚ OVĚŘENO probe skriptem `/root/claude-collab/eiscp-mvl-test.py`):
 * `"ISCP"` + uint32 BE header-size(=16) + uint32 BE data-size + uint32 BE `0x01000000`(verze+rezerva)
 * + payload `"!1" + <CMD> + "\r\n"` (ASCII). Unicast TCP, jedno spojení na příkaz (jednoduché, robustní).
 */
@Singleton
class EiscpClient @Inject constructor() {

    /**
     * Pošle eISCP příkaz na [host]. Vrátí seznam odpovědí (payloady bez prefixu `!1`), nebo `null`
     * pokud se nepodařilo vůbec spojit/poslat (= receiver nedostupný). Prázdný seznam = posláno, ale
     * žádná odpověď (typicky [expectReply] = false, nebo receiver mlčí).
     */
    suspend fun command(
        host: String,
        cmd: String,
        expectReply: Boolean = false,
        port: Int = PORT,
    ): List<String>? = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext null
        runCatching {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                sock.soTimeout = READ_TIMEOUT_MS
                sock.getOutputStream().apply { write(frame(cmd)); flush() }
                if (!expectReply) return@use emptyList()
                val buf = ByteArray(8192)
                val n = try {
                    sock.getInputStream().read(buf)
                } catch (_: SocketTimeoutException) {
                    -1
                }
                if (n <= 0) emptyList() else parse(buf, n)
            }
        }.getOrElse {
            Timber.w(it, "[MAESTRO] eISCP %s @%s selhalo", cmd, host)
            null
        }
    }

    private fun frame(cmd: String): ByteArray {
        val payload = "!1$cmd\r\n".toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN).apply {
            put("ISCP".toByteArray(Charsets.US_ASCII))
            putInt(HEADER_SIZE)     // header size
            putInt(payload.size)    // data size
            putInt(0x01000000)      // verze 0x01 + 3 rezervní bajty
            put(payload)
        }.array()
    }

    /** Rozparsuje (i víc) eISCP rámců z přijatých bajtů na jejich textové payloady (oříznuté). */
    private fun parse(data: ByteArray, len: Int): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i + HEADER_SIZE <= len) {
            if (!(data[i] == 'I'.code.toByte() && data[i + 1] == 'S'.code.toByte() &&
                    data[i + 2] == 'C'.code.toByte() && data[i + 3] == 'P'.code.toByte())
            ) {
                i++
                continue
            }
            val dataSize = ByteBuffer.wrap(data, i + 8, 4).order(ByteOrder.BIG_ENDIAN).int
            val start = i + HEADER_SIZE
            val end = (start + dataSize).coerceAtMost(len)
            if (dataSize in 1..(len - start)) {
                val msg = String(data, start, end - start, Charsets.US_ASCII)
                    .trim().trim('\u001A').trim()
                // Odpoved receiveru ma prefix jednotky !1 (napr. !1MVL6A) - odrizneme ho.
                out += if (msg.length >= 2 && msg[0] == '!') msg.substring(2) else msg
            }
            i += HEADER_SIZE + dataSize.coerceAtLeast(0)
        }
        return out
    }

    private companion object {
        const val PORT = 60128
        const val HEADER_SIZE = 16
        const val CONNECT_TIMEOUT_MS = 1500
        const val READ_TIMEOUT_MS = 1200
    }
}
