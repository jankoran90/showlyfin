package com.github.jankoran90.showlyfin.data.maestro

import android.content.SharedPreferences
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MAESTRO / D-c (user 2026-07-19) — probuzení domácí AV sestavy před přehráním na TV.
 *
 * Sdílená orchestrace „zapnout obývák a spustit appku" pro Filmy „Přehrát na TV" (a časem i showlyfin
 * `NaTvCoordinator`, který má zatím vlastní kopii se svým Jellyfin-session pollem). Filmy model: cast příkaz
 * se zařadí do backendové fronty a TV shell ho vyzvedne AŽ když appka běží v popředí → wake MUSÍ spustit
 * Filmy appku na boxu do popředí, jinak příkaz nikdo nevyzvedne.
 *
 * Household defaulty + pref klíče `avr_*` jsou shodné s ui-phone `HomeSystemDefaults` (per-app SharedPreferences,
 * proto duplicitně zde — Filmy je samostatná appka s vlastními prefs a nevidí ui-phone helpery).
 */
data class HomeTheaterConfig(
    val enabled: Boolean,
    val avrHost: String?,
    val avrDefaultVolume: Int?,
    val tvHost: String?,
    val boxHost: String?,
    val boxMac: String?,
) {
    /** Sestava je nakonfigurovaná ke spuštění scény (aspoň AVR nebo box). */
    val configured: Boolean get() = enabled && (!avrHost.isNullOrBlank() || !boxHost.isNullOrBlank())

    companion object {
        const val DEF_ENABLED = true
        const val DEF_AVR_HOST = "192.168.1.233"     // Pioneer VSX-935 (eISCP :60128)
        const val DEF_BOX_HOST = "192.168.1.184"     // Xiaomi TV Box (ADB :5555)
        const val DEF_BOX_MAC = "80:9d:65:fd:68:04"  // Xiaomi box WoL
        const val DEF_TV_HOST = "192.168.1.102"      // TCL TV (ADB :5555)

        private fun SharedPreferences.hostOr(key: String, def: String): String =
            getString(key, "").orEmpty().trim().ifBlank { def }

        /** Sestav config z prefs (uložená hodnota má přednost, jinak household default). */
        fun from(prefs: SharedPreferences): HomeTheaterConfig = HomeTheaterConfig(
            enabled = prefs.getBoolean("avr_enabled", DEF_ENABLED),
            avrHost = prefs.hostOr("avr_host", DEF_AVR_HOST),
            avrDefaultVolume = prefs.getString("avr_default_volume", "").orEmpty().trim().toIntOrNull()?.takeIf { it > 0 },
            tvHost = prefs.hostOr("avr_tv_host", DEF_TV_HOST),
            boxHost = prefs.hostOr("avr_box_host", DEF_BOX_HOST),
            boxMac = prefs.hostOr("avr_box_mac", DEF_BOX_MAC),
        )
    }
}

@Singleton
class HomeTheaterScene @Inject constructor(
    private val avr: AvrController,
    private val box: BoxController,
) {
    /**
     * Probudí sestavu a spustí [launchPackage] na boxu do popředí (aby jeho cast poller vyzvedl čekající příkaz):
     * AVR ze standby (+ volitelně výchozí hlasitost; vstup STRM BOX si AVR přepne sám přes CEC), televizi napřímo,
     * box přes Wake-on-LAN, pak opakovaně spustí appku, jak po WoL naběhne síť. Best-effort, chyby jen loguje.
     * [onProgress] = průběžné hlášky pro volitelné UI. Volá se fire-and-forget PARALELNĚ se zařazením cast příkazu.
     */
    suspend fun wakeAndLaunch(cfg: HomeTheaterConfig, launchPackage: String, onProgress: (String) -> Unit = {}) {
        onProgress("Zapínám obývák…")
        cfg.avrHost?.takeIf { it.isNotBlank() }?.let { host ->
            runCatching { avr.powerOn(host) }.onFailure { Timber.w(it, "[MAESTRO] AVR power-on selhal") }
            cfg.avrDefaultVolume?.let { vol ->
                delay(800)
                runCatching { avr.setVolume(host, vol) }.onFailure { Timber.w(it, "[MAESTRO] AVR hlasitost selhala") }
            }
        }
        cfg.tvHost?.takeIf { it.isNotBlank() }?.let { runCatching { box.wake(it) } }
        cfg.boxMac?.takeIf { it.isNotBlank() }?.let { runCatching { box.wakeViaWol(it) } }
        val boxHost = cfg.boxHost?.takeIf { it.isNotBlank() } ?: return
        // Studený start boxu (WoL → boot → ADB) trvá desítky sekund; opakuj spuštění, dokud nevyjde (první úspěch
        // stačí — box už byl vzhůru, appka je v popředí). Cast příkaz na serveru žije 120 s.
        for (i in 0 until LAUNCH_ATTEMPTS) {
            if (i == 1) onProgress("Spouštím appku na TV…")
            val ok = runCatching { box.wakeAndLaunch(boxHost, launchPackage) }
                .onFailure { Timber.w(it, "[MAESTRO] wakeAndLaunch selhal (pokus %d)", i) }
                .getOrDefault(false)
            if (ok) {
                // Box mohl být právě ze standby a první `monkey` se v rozbíhajícím se systému ztratí — jedno
                // potvrzovací spuštění po chvíli (idempotentní: Filmy jen vytáhne do popředí).
                delay(CONFIRM_LAUNCH_MS)
                runCatching { box.wakeAndLaunch(boxHost, launchPackage) }
                return
            }
            if (i < LAUNCH_ATTEMPTS - 1) delay(LAUNCH_RETRY_MS)
        }
    }

    private companion object {
        const val LAUNCH_ATTEMPTS = 9       // opakuj spuštění appky, jak naběhne box a síť po WoL
        const val CONFIRM_LAUNCH_MS = 6_000L
        const val LAUNCH_RETRY_MS = 8_000L  // ~0/8/…/64 s (v rámci 120 s TTL cast příkazu)
    }
}
