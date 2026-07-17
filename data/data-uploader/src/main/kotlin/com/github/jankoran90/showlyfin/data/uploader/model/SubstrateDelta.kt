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
