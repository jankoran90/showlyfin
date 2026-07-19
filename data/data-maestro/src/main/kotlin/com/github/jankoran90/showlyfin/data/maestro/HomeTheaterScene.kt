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
        repeat(LAUNCH_ATTEMPTS) { i ->
            if (i == 1) onProgress("Spouštím appku na TV…")
            runCatching { box.wakeAndLaunch(boxHost, launchPackage) }
                .onFailure { Timber.w(it, "[MAESTRO] wakeAndLaunch selhal (pokus %d)", i) }
            if (i < LAUNCH_ATTEMPTS - 1) delay(LAUNCH_RETRY_MS)
        }
    }

    private companion object {
        const val LAUNCH_ATTEMPTS = 3       // opakuj spuštění appky, jak naběhne síť po WoL
        const val LAUNCH_RETRY_MS = 6_000L  // ~0/6/12 s
    }
}
