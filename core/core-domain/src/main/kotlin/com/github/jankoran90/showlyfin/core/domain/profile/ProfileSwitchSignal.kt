package com.github.jankoran90.showlyfin.core.domain.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PROFIL (user 2026-07-28 „při přepnutí chci, aby se opravdu appka znovunačetla a opravdu došlo
 * ke 100% načtení dat z vybraného profilu") — jednoduchý globální signál „profil se přepnul".
 *
 * Proč vůbec: přepnutí profilu mění ÚPLNĚ VŠECHNO (Jellyfin creds, Trakt token, Oblíbené, uložené
 * zdroje, nastavení Filmotéky, věkový strop). Reaktivní cesty to většinou stihnou, ale obrazovka,
 * která si data drží v `remember`/lokálním stavu (nebo ViewModel, který už jednou načetl), zůstane
 * viset na obsahu předchozího profilu. Skořápky si na tenhle signál vynutí **re-create Activity** =
 * všechny ViewModely i lokální stavy vzniknou znovu, tedy nad daty NOVÉHO profilu.
 *
 * Vědomě je to `object` bez DI: signál posílá `ProfileRepository` (core-data) a poslouchají ho
 * skořápky (ui-*), mezi kterými není přímá závislost. Stejný vzor jako `ListenNavSignal`.
 */
object ProfileSwitchSignal {

    private val _switches = MutableStateFlow(0L)

    /** Roste s každým dokončeným přepnutím profilu. 0 = od startu appky se nepřepínalo. */
    val switches: StateFlow<Long> = _switches.asStateFlow()

    /** Poslední profil, o kterém jsme dali vědět — druhá pojistka proti smyčce (viz níž). */
    @Volatile private var lastProfileId: Long = 0L

    /**
     * Zavolá [com.github.jankoran90.showlyfin.core.data.ProfileRepository] po dokončení přepnutí.
     *
     * 🔴 POJISTKA: tentýž profil signál NIKDY nezopakuje. `setActive` běží i při startu appky
     * (obnova posledního profilu) — bezpodmínečné hlášení = re-create → start → hlášení → **smyčka**
     * (user 2026-07-28: „bliká co půl vteřiny endlessly"). Guard je i ve volajícím; tady je záměrně
     * podruhé, protože cena chyby je nepoužitelná appka.
     */
    fun notifySwitched(profileId: Long) {
        if (profileId == lastProfileId) return
        lastProfileId = profileId
        _switches.value = _switches.value + 1
    }
}
