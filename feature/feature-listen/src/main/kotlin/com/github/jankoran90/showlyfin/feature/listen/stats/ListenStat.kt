package com.github.jankoran90.showlyfin.feature.listen.stats

/**
 * AMPHORA (SHW-124) F8 / CH2 — jeden řádek statistik poslechu: kolik sekund se v daný den na
 * jednom zařízení opravdu přehrálo z jednoho pořadu nebo knihy. Identita [id] =
 * `RRRR-MM-DD|<zařízení>|<klíč>` → každý řádek píše jen jeho zařízení (bez konfliktů mezi telefony).
 * Synchronizuje se doménou `listen-stats` (server: identita `id`, čas `updatedAt`).
 */
data class ListenStat(
    val id: String,
    /** Místní den „RRRR-MM-DD“. */
    val day: String,
    val device: String,
    /** `show:<id>` (pořad) / `book:<id>` (kniha) / `ep:<id>` (díl bez známého pořadu). */
    val key: String,
    /** [KIND_BOOK] / [KIND_EPISODE]. */
    val kind: String,
    val title: String,
    val coverUrl: String? = null,
    /** Opravdu přehraný čas (hodiny na zdi, ne obsah × rychlost). */
    val seconds: Long = 0L,
    /** Ušetřeno rychlostí: obsah přehraný navíc oproti 1× (záporné při zpomalení). */
    val savedSec: Long = 0L,
    /** Počet dohraných dílů / knih toho dne. */
    val finished: Int = 0,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val KIND_BOOK = "book"
        const val KIND_EPISODE = "episode"
    }
}

/** Co právě hraje (z přehrávače): klíč, druh a název pro statistiky. */
data class ListenItem(
    val key: String,
    val kind: String,
    val title: String,
    val coverUrl: String?,
    /** Konkrétní díl / kniha — „dohráno“ se počítá jen jednou. */
    val unitId: String,
)
