package com.github.jankoran90.showlyfin.data.uploader.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * SUBSTRATE (SHW-99) F2b — wire modely delta sync API (server `substrate.sqlite3`).
 *
 * Endpointy (per profil, per doména `favorites|working-sources|ratings|recommendations|stream-presets`):
 *  - `GET  /api/profiles/{key}/{domain}/delta?since={v}` → [DeltaResponse] (jen řádky s version>since + tombstones).
 *  - `POST /api/profiles/{key}/{domain}/delta` tělo [DeltaPushBody] → [DeltaPushResponse] (LWW merge, union-safe).
 *
 * `payload` je na drátě **JSON objekt** (server ho deserializuje), NE string — proto [JsonElement]
 * (doména si ho (de)serializuje na svůj typ, např. `FavoriteItem`). Tombstone může mít `payload=null`.
 */
data class DeltaRow(
    @SerializedName("row_id") val rowId: String,
    val payload: JsonElement? = null,
    val updatedAt: Long = 0L,
    val version: Long = 0L,
    val deleted: Int = 0,
)

/** Odpověď `GET …/delta?since=v`: aktuální max [version] + změněné [rows] (vč. tombstones). */
data class DeltaResponse(
    val version: Long = 0L,
    val rows: List<DeltaRow> = emptyList(),
)

/** Odpověď `POST …/delta`: nová [version], počet [applied] + [rows] aplikované nové verze (nesou serverovou version). */
data class DeltaPushResponse(
    val version: Long = 0L,
    val applied: Int = 0,
    val rows: List<DeltaRow> = emptyList(),
)

/** Tělo `POST …/delta` — dirty řádky k pushnutí. */
data class DeltaPushBody(
    val rows: List<DeltaRow> = emptyList(),
)

/**
 * SUBSTRATE F2c KROK 2 — odpověď `POST /api/profiles/{key}/mirror/refresh` (server tahá Trakt vkus
 * do serverového mirroru). [tokenStale] = uložený access token na serveru je mrtvý (V3 zeď) → appka
 * musí pushnout čerstvý po re-loginu. [counts] = kolik řádků server natáhl.
 */
data class MirrorRefreshResponse(
    val ok: Boolean = false,
    val counts: MirrorCounts = MirrorCounts(),
    val tokenStale: Boolean = false,
    val error: String? = null,
)

data class MirrorCounts(
    val watched: Int = 0,
    val watchlist: Int = 0,
    val ratings: Int = 0,
)

/**
 * Odpověď `GET /api/profiles/{key}/mirror/{what}` — serverové ZRCADLO Trakt vkusu.
 *
 * Appka si watchlist normálně tahá přímo z Traktu; když jí vyprší token, dostane 401 a zůstala by
 * BEZ dat „kdy jsem si film přidal do Chci vidět" → Filmotéka se tiše přerovná podle data uložení
 * zdroje (user 2026-07-30). Tohle je záloha pro ten případ. [lastSuccessAt] říká, jak stará data jsou.
 */
data class MirrorReadResponse(
    val items: List<MirrorWatchlistItem> = emptyList(),
    val count: Int = 0,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
)

/** Jedna položka zrcadla „Chci vidět" (payload, jak ho ukládá serverový mirror). */
data class MirrorWatchlistItem(
    val tmdbId: Long? = null,
    val traktId: Long? = null,
    val imdb: String? = null,
    val title: String? = null,
    val year: Int? = null,
    val type: String? = null,
    /** ISO 8601 čas přidání do watchlistu (`listed_at`). */
    val listedAt: String? = null,
)
