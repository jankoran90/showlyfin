package com.github.jankoran90.showlyfin.core.domain

import java.util.concurrent.ConcurrentHashMap

/**
 * MUZA (SHW-123), user 2026-08-28 21:28: „dej ten kuratorsky text i jako sdileci kartu. Kdyz dam
 * sdilet z karty filmu" + dřív „mohl by byt prepinatelny jak už ho máme s druhym popiskem".
 *
 * Karta ve výsledcích MUZA má vlastní text (SUMÁŘ technika, ale cílený na uživatelovo TÉMA) —
 * když z ní uživatel otevře detail, tenhle text má naskočit jako „Co na to diváci" (2. stránka
 * popisu) i jako text sdílecí karty, MÍSTO obecného SUMÁŘ textu, který by se jinak sám dopekl.
 *
 * Jednoduchý paměťový handoff (ne perzistence — je to jen most mezi obrazovkami v rámci jednoho
 * otevření appky): MUZA před navigací na detail [stash]ne text pod (tmdbId, isShow), `DetailViewModel.
 * load()` si ho [take]ne (a tím spotřebuje — druhé otevření stejné karty už žádný seed nenajde,
 * což je správně, protože pak už jede běžná cesta).
 */
object MuzaBlurbHandoff {
    private val map = ConcurrentHashMap<String, String>()

    private fun key(tmdbId: Long, isShow: Boolean) = "${tmdbId}_$isShow"

    fun stash(tmdbId: Long, isShow: Boolean, text: String) {
        if (tmdbId <= 0L || text.isBlank()) return
        map[key(tmdbId, isShow)] = text
    }

    /** Vyzvedne a SPOTŘEBUJE seed pro tenhle titul (null = žádný, nebo `tmdbId` neznámé). */
    fun take(tmdbId: Long?, isShow: Boolean): String? {
        val id = tmdbId?.takeIf { it > 0L } ?: return null
        return map.remove(key(id, isShow))
    }
}
