package com.github.jankoran90.showlyfin.data.maestro

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plan MAESTRO — ovládání Android TV boxu (Xiaomi) z telefonu pro scénu „spustit film z vypnuté TV".
 * Dvě cesty probuzení (ověřeno #48): **Wake-on-LAN** magic packet (deep-sleep, ~15 s) + **ADB**
 * `KEYCODE_WAKEUP` (lehký spánek) → pak `monkey` spustí Yellyfin, aby naběhla JF session.
 *
 * ADB-over-network: box musí mít zapnuté ladění po síti (port 5555) a JEDNOU autorizovat klíč
 * telefonu (dialog na TV). Klíč persistujeme v `filesDir`, ať se dialog neptá pokaždé.
 */
@Singleton
class BoxController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Probudí box přes Wake-on-LAN (magic packet na broadcast). [mac] = `AA:BB:CC:DD:EE:FF`. */
    suspend fun wakeViaWol(mac: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val macBytes = mac.split(':', '-').map { it.trim().toInt(16).toByte() }
            require(macBytes.size == 6) { "neplatná MAC: $mac" }
            // 6× 0xFF + 16× opakovaná MAC.
            val packet = ByteArray(6 + 16 * 6) { 0xFF.toByte() }
            for (i in 0 until 16) {
                for (j in 0 until 6) packet[6 + i * 6 + j] = macBytes[j]
            }
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), 9))
            }
            Timber.i("[MAESTRO] WoL odeslán na %s", mac)
            true
        }.getOrElse {
            Timber.w(it, "[MAESTRO] WoL %s selhalo", mac)
            false
        }
    }

    /**
     * Přes ADB probudí box (`KEYCODE_WAKEUP`) a spustí balíček [packageName] (Yellyfin).
     * Vrátí true, pokud se příkazy podařilo odeslat.
     */
    suspend fun wakeAndLaunch(host: String, packageName: String = YELLYFIN_PACKAGE): Boolean =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(ADB_TIMEOUT_MS) {
                runCatching {
                    Dadb.create(host, ADB_PORT, adbKeyPair()).use { dadb ->
                        dadb.shell("input keyevent KEYCODE_WAKEUP")
                        dadb.shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
                    }
                    Timber.i("[MAESTRO] box ADB @%s wake+launch %s OK", host, packageName)
                    true
                }.getOrElse {
                    Timber.w(it, "[MAESTRO] box ADB @%s selhalo (autorizace/síť?)", host)
                    false
                }
            } ?: run {
                Timber.w("[MAESTRO] box ADB @%s timeout", host)
                false
            }
        }

    /** Jen probudí zařízení přes ADB (`KEYCODE_WAKEUP`), bez spouštění appky — pro TV. */
    suspend fun wake(host: String): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(ADB_TIMEOUT_MS) {
            runCatching {
                Dadb.create(host, ADB_PORT, adbKeyPair()).use { it.shell("input keyevent KEYCODE_WAKEUP") }
                Timber.i("[MAESTRO] ADB @%s wake OK", host)
                true
            }.getOrElse {
                Timber.w(it, "[MAESTRO] ADB @%s wake selhalo", host)
                false
            }
        } ?: false
    }

    /** Uspí box přes ADB (`KEYCODE_SLEEP`). Pro „vypnout obývák". */
    suspend fun sleep(host: String): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(ADB_TIMEOUT_MS) {
            runCatching {
                Dadb.create(host, ADB_PORT, adbKeyPair()).use { it.shell("input keyevent KEYCODE_SLEEP") }
                Timber.i("[MAESTRO] box ADB @%s sleep OK", host)
                true
            }.getOrElse {
                Timber.w(it, "[MAESTRO] box ADB @%s sleep selhalo", host)
                false
            }
        } ?: false
    }

    /** Persistovaný ADB klíč telefonu (jinak by box vyžadoval autorizaci při každém spojení). */
    private fun adbKeyPair(): AdbKeyPair {
        val priv = File(context.filesDir, "maestro_adb_key")
        val pub = File(context.filesDir, "maestro_adb_key.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    companion object {
        const val YELLYFIN_PACKAGE = "com.github.jankoran90.yellyfin"
        private const val ADB_PORT = 5555
        private const val ADB_TIMEOUT_MS = 8000L
    }
}
