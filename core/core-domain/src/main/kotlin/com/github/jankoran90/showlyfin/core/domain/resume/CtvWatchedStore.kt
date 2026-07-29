package com.github.jankoran90.showlyfin.core.domain.resume

import kotlinx.coroutines.flow.StateFlow

/**
 * VLTAVA (SHW-110) F6c — DOKOUKANÉ díly ČT (klíč `ctv:<idec>`, viz `CTV_SCHEME`).
 *
 * Proč vlastní paměť: díl z ČT nemá Jellyfin id ani imdb, takže „zhlédnuto" nemá kam nahlásit
 * ([WatchedReporter] umí Jellyfin a Trakt). A z [VideoResumeStore] to poznat NEJDE — dokoukaný díl
 * má pozici smazanou stejně jako díl, který se nikdy nespustil. Bez tohohle rozlišení by řada
 * „Další díly" u pořadu napořád nabízela ten první (user 2026-07-28: „u ČT chci jít vždy od
 * nejstarších, stejně jako u seriálu od S01E01").
 *
 * **Rozhraní, ne implementace:** stav žije v `substrate.db` (per profil, cross-device delta sync —
 * `CtvWatchedStoreImpl` v `core-db`), ale píší do něj i moduly, které Room nevidí (`feature-playback`).
 * Původní verze byla jedno lokální `SharedPreferences` pro celou appku → druhý přístroj o dokoukaných
 * dílech nevěděl a všechny profily je sdílely.
 */
interface CtvWatchedStore {

    /** Množina `ctv:<idec>` dokoukaných dílů AKTIVNÍHO profilu; reaktivní (řada „Další díly" se posune sama). */
    val watched: StateFlow<Set<String>>

    /**
     * Je už načteno z úložiště? Dokud ne, je [watched] prázdné jen proto, že se teprve hydratuje —
     * konzumenti (řada „Další díly") na to nesmí reagovat přestavbou, jinak se každý start dělá 2×.
     */
    val hydrated: StateFlow<Boolean>

    fun isWatched(key: String): Boolean

    fun markWatched(key: String)

    /**
     * Hromadné označení („vše až sem", „označit sérii") — JEDEN zápis, JEDEN sync, JEDNA emise.
     * Po dílech to dělat nelze: každý díl by jinak spustil vlastní kolo zápis→server→přestavba řad
     * (u série o 26 dílech 26 lavin) a domov by se minuty přepočítával.
     */
    fun markWatched(keys: Collection<String>)

    /** Ruční „nezhlédnuto" (kdyby si to user chtěl přehrát znovu od začátku seriálu). */
    fun clear(key: String)

    /** Hromadné odznačení — viz [markWatched] se seznamem. */
    fun clear(keys: Collection<String>)

    /** Push neodeslaných změn + pull ze serveru. Volá se sám při změně profilu; jinak na lifecycle. */
    fun syncNow()
}
