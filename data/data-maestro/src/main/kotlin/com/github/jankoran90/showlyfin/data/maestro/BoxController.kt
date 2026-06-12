package com.github.jankoran90.showlyfin.data.maestro

import android.content.Context
import android.util.Base64
import dadb.AdbKeyPair
import dadb.Dadb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
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
    /** Vlastní scope, ať blokující ADB práce běží odděleně a volající ji může po timeoutu opustit. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Serializace ADB operací — NIKDY víc spojení na box najednou. Paralelní wake(TV)+launch(box) +
     * retry scény dřív otevíraly víc spojení současně → na neautorizovaném boxu se hromadily
     * autorizační dialogy (loop). S mutexem běží vždy jen jedno → uživatel potvrdí jeden dialog.
     */
    private val adbMutex = Mutex()

    /** Výsledek párování boxu — pro jasnou diagnostiku v UI (proč to nejde). */
    sealed interface PairResult {
        data object Success : PairResult       // spojení + autorizace OK
        data object Unreachable : PairResult    // port 5555 neodpovídá (ladění po síti vyplé / jiná IP / jiná Wi-Fi)
        data object NotConfirmed : PairResult   // dosažitelný, ale spojení/autorizace se nedokončilo (potvrď „Vždy povolit" na TV)
    }

    /**
     * Explicitní jednorázové spárování boxu s DLOUHÝM oknem na potvrzení ([PAIR_TIMEOUT_MS]) — uživatel
     * má čas dojít k TV a kliknout „Vždy povolit". Jedno spojení (mutex), žádné retry, vrací důvod selhání.
     */
    suspend fun pair(host: String): PairResult {
        val deferred = scope.async {
            adbMutex.withLock {
                if (!isReachable(host)) return@withLock PairResult.Unreachable
                runCatching {
                    Dadb.create(host, ADB_PORT, adbKeyPair()).use { it.shell("echo showlyfin-pair") }
                    Timber.i("[MAESTRO] box @%s spárován", host)
                    PairResult.Success as PairResult
                }.getOrElse {
                    Timber.w(it, "[MAESTRO] box @%s párování nepotvrzeno", host)
                    PairResult.NotConfirmed
                }
            }
        }
        return withTimeoutOrNull(PAIR_TIMEOUT_MS) { deferred.await() } ?: run {
            deferred.cancel()
            Timber.w("[MAESTRO] box @%s párování timeout — nepotvrzeno na TV?", host)
            PairResult.NotConfirmed
        }
    }

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
        runAdb(host, "wake+launch $packageName") { dadb ->
            dadb.shell("input keyevent KEYCODE_WAKEUP")
            dadb.shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        }

    /** Jen probudí zařízení přes ADB (`KEYCODE_WAKEUP`), bez spouštění appky — pro TV. */
    suspend fun wake(host: String): Boolean =
        runAdb(host, "wake") { it.shell("input keyevent KEYCODE_WAKEUP") }

    /** Uspí box přes ADB (`KEYCODE_SLEEP`). Pro „vypnout obývák". */
    suspend fun sleep(host: String): Boolean =
        runAdb(host, "sleep") { it.shell("input keyevent KEYCODE_SLEEP") }

    /**
     * Rychlá zkouška, jestli je ADB port boxu vůbec dosažitelný. Když box spí / není v síti, TCP connect
     * spadne do [PROBE_TIMEOUT_MS] místo dlouhého OS timeoutu → nejčastější příčinu visení usekneme hned.
     */
    private fun isReachable(host: String): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, ADB_PORT), PROBE_TIMEOUT_MS) }
        true
    }.getOrDefault(false)

    /**
     * Spustí ADB operaci [op] s TVRDÝM stropem [ADB_TIMEOUT_MS]. Klíčové: blokující `Dadb.create`
     * (TCP connect + auth handshake, který u neautorizovaného klíče **čeká na schválení dialogu na boxu**)
     * běží na vlastním [scope] přes [async]; volající čeká přes zrušitelné `await()` v [withTimeoutOrNull],
     * takže i když handshake visí, UI se po timeoutu odblokuje (dřív `withTimeoutOrNull` kolem blokujícího
     * volání NEFUNGOVAL — neměl kde přerušit a dialog „Vypínám obývák" visel). Předřazená [isReachable]
     * usekne nedostupný box hned.
     */
    private suspend fun runAdb(host: String, label: String, op: (Dadb) -> Unit): Boolean {
        val deferred = scope.async {
            adbMutex.withLock {
                if (!isReachable(host)) {
                    Timber.w("[MAESTRO] box @%s nedostupný (port %d) — %s přeskočeno", host, ADB_PORT, label)
                    return@withLock false
                }
                runCatching {
                    Dadb.create(host, ADB_PORT, adbKeyPair()).use { op(it) }
                    Timber.i("[MAESTRO] box ADB @%s %s OK", host, label)
                    true
                }.getOrElse {
                    Timber.w(it, "[MAESTRO] box ADB @%s %s selhalo (autorizace/síť?)", host, label)
                    false
                }
            }
        }
        return withTimeoutOrNull(ADB_TIMEOUT_MS) { deferred.await() } ?: run {
            deferred.cancel()
            Timber.w("[MAESTRO] box ADB @%s %s timeout %d ms — nejspíš čeká na autorizaci na boxu", host, label, ADB_TIMEOUT_MS)
            false
        }
    }

    /**
     * ADB klíč pro spojení s boxem. **Společný klíč zašitý do appky** (build env → BuildConfig,
     * base64) má přednost: všechny instalace mají stejnou ADB identitu → box autorizuje JEDNOU
     * (klíč je už autorizovaný), žádné dialogy „Vždy povolit" na TV. Když embedded klíč chybí
     * (prázdné BuildConfig pole), spadne na původní chování = per-instalaci vygenerovaný klíč.
     */
    private fun adbKeyPair(): AdbKeyPair {
        val priv = File(context.filesDir, "maestro_adb_key")
        val pub = File(context.filesDir, "maestro_adb_key.pub")
        val embeddedPriv = BuildConfig.MAESTRO_ADB_KEY_PRIVATE
        val embeddedPub = BuildConfig.MAESTRO_ADB_KEY_PUBLIC
        if (embeddedPriv.isNotBlank() && embeddedPub.isNotBlank()) {
            // Materializuj sdílený klíč do filesDir (přepiš případný starý per-instalaci klíč),
            // ať `AdbKeyPair.read` čte File. Zapiš jen když se obsah liší (idempotentní).
            runCatching {
                val privBytes = Base64.decode(embeddedPriv, Base64.DEFAULT)
                val pubBytes = Base64.decode(embeddedPub, Base64.DEFAULT)
                if (!priv.exists() || !priv.readBytes().contentEquals(privBytes)) priv.writeBytes(privBytes)
                if (!pub.exists() || !pub.readBytes().contentEquals(pubBytes)) pub.writeBytes(pubBytes)
            }.onFailure { Timber.w(it, "[MAESTRO] zápis sdíleného ADB klíče selhal — fallback na lokální") }
        }
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    companion object {
        const val YELLYFIN_PACKAGE = "com.github.jankoran90.yellyfin"
        private const val ADB_PORT = 5555
        private const val ADB_TIMEOUT_MS = 8000L
        private const val PAIR_TIMEOUT_MS = 60_000L  // dlouhé okno na potvrzení „Vždy povolit" na TV
        private const val PROBE_TIMEOUT_MS = 2500
    }
}
