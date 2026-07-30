package com.github.jankoran90.showlyfin.core.domain.resume

import kotlinx.coroutines.flow.StateFlow

/**
 * REWIND (SHW-68): paměť pozice přehrávání pro VIDEO (Jellyfin-item playback v [PlaybackScreen]).
 *
 * Showlyfin NEhlásí JF playback progress zpět na server, takže serverový `playbackPositionTicks` u videa
 * přehraného na telefonu zůstává ~0 → žádné „Pokračovat"/progres. Tenhle store dělá videu vlastní
 * resume (ekvivalent `DirectResumeStore` pro audio): pozici ukládáme periodicky, při dohrání mažeme.
 * Klíč = stabilní `resumeKey` předaný do přehrávače.
 *
 * **Proč rozhraní (2026-07-30):** implementace dřív byla jedno lokální `SharedPreferences` BEZ profilu —
 * tatáž vada, jakou měl `CtvWatchedStore`: (a) telefon a TV o pozicích nevěděly, (b) profily je sdílely
 * (dětský profil pokračoval tam, kde skončil dospělý). Implementace proto sedí nad SUBSTRATE Room
 * (`playback_state`, doména `playback-state`) v `core-db`, kam `feature-playback` nevidí.
 *
 * Sdílený přes `core-domain` → vidí ho `feature-playback` (zapisuje pozici videa) i `feature-listen`
 * (RSS řádek čte [marks] → progres + „Pokračovat" u video epizody, sdílený klíč s audiem = „poslední
 * vyhrává"). Reaktivní [marks] = seznam ukáže stav bez pollingu.
 */
interface VideoResumeStore {

    /** Pozice + (známá) délka v ms pro jednu video položku. [updatedAt] = čas posledního zápisu (epoch ms). */
    data class Mark(val posMs: Long, val durMs: Long, val updatedAt: Long = 0L)

    /** key → [Mark] aktivního profilu; reaktivní (seznamy ukáží progres/„Pokračovat" bez pollingu). */
    val marks: StateFlow<Map<String, Mark>>

    fun get(key: String): Mark?

    /**
     * Ulož pozici. Blízko konce ([FINISH_TAIL_MS]) = dokoukáno → [clear] (žádné „Pokračovat" na 99 %).
     * Pod [MIN_RESUME_MS] od začátku neukládáme.
     */
    fun save(key: String, posMs: Long, durMs: Long)

    fun clear(key: String)

    /** Push rozepsaných pozic + pull ze serveru (lifecycle: odchod z přehrávače, pauza, změna profilu). */
    fun syncNow()

    companion object {
        const val MIN_RESUME_MS = 5_000L
        const val FINISH_TAIL_MS = 20_000L
    }
}
