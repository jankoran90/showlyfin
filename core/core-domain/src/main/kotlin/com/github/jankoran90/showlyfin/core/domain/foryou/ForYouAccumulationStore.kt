package com.github.jankoran90.showlyfin.core.domain.foryou

import android.content.Context
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BESPOKE (SHW-95) F2 / Track 2 — perzistentní AKUMULACE sekce „Pro tebe". Kurátor (`CuratorLoader.forYou`)
 * vrací při každém načtení jen aktuální „snímek" (~60 titulů dle vkusu); tento store ho MERGuje s dřívějšími,
 * takže doporučené filmy se v sekci hromadí místo aby při obměně vkusu/profilu mizely.
 *
 * Vlastní prefs soubor (`tv_for_you_accum`) mimo `trakt_prefs` → izolace (nepodléhá odhlášení Traktu) a snadný
 * reset. Vzor: [com.github.jankoran90.showlyfin.core.domain.home.HomeLayoutStore] (kotlinx JSON → String,
 * per-element tolerantní decode). [MediaItem] se ukládá přes serializovatelné [StoredItem] (doména sama není
 * `@Serializable`), rekonstrukce plná (žádná ztráta polí karty).
 *
 * **Per-profil izolace:** klíč nese profil (`foryou_p<id>`) — každý profil má vlastní rostoucí seznam,
 * profily se nemíchají. core-domain nesmí vidět `ProfileRepository` (obrácená závislost), proto profil
 * dodává volající (TvForYouViewModel) jako `profileKey`.
 */
@Singleton
class ForYouAccumulationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tv_for_you_accum", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private fun keyFor(profileKey: String): String = "foryou_$profileKey"

    /** Dedup klíč: tmdbId+type (kurátor plní tmdbId), fallback traktId. */
    private fun keyOf(m: MediaItem): String =
        m.tmdbId?.takeIf { it > 0L }?.let { "${m.type.name}_$it" } ?: "trakt_${m.traktId}"

    /** Načti akumulovaný seznam profilu (přežívá restart appky). Prázdný / poškozený = prázdný seznam. */
    fun load(profileKey: String): List<MediaItem> {
        val raw = prefs.getString(keyFor(profileKey), null)
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                runCatching { json.decodeFromJsonElement<StoredItem>(el).toDomain() }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Slouči čerstvý výsledek kurátora s uloženým akumulovaným seznamem, ulož a vrať výsledek.
     * - Dedup podle [keyOf]; nové/neznámé se PŘIDAJÍ na konec, dřívější zůstávají (stabilní pořadí).
     * - Strop [CAP]; při přetečení se ořízne NEJSTARŠÍ (začátek seznamu).
     * - Hygiena: prázdný `fresh` (kurátor vypnutý / studený vkus / backend nedostupný) NEMAŽE akumulované
     *   — jen se nic nepřidá a vrátí se stávající.
     */
    fun accumulate(profileKey: String, fresh: List<MediaItem>): List<MediaItem> {
        val existing = load(profileKey)
        if (fresh.isEmpty()) return existing

        val result = ArrayList(existing)
        val seen = HashSet<String>(existing.size + fresh.size)
        existing.forEach { seen.add(keyOf(it)) }
        for (item in fresh) {
            if (seen.add(keyOf(item))) result.add(item)
        }
        val capped = if (result.size > CAP) result.takeLast(CAP) else result
        // Ulož jen když se něco reálně změnilo (nové položky nebo ořez) — šetří zápisy.
        if (capped.size != existing.size || capped !== existing) {
            persist(profileKey, capped)
        }
        return capped
    }

    private fun persist(profileKey: String, items: List<MediaItem>) {
        val payload = json.encodeToString(items.map { it.toStored() })
        prefs.edit().putString(keyFor(profileKey), payload).apply()
    }

    // ── Serializace MediaItem (doména není @Serializable) ─────────────────────────
    @Serializable
    private data class StoredItem(
        val traktId: Long = 0L,
        val tmdbId: Long? = null,
        val imdbId: String? = null,
        val title: String = "",
        val year: Int? = null,
        val overview: String? = null,
        val rating: Float? = null,
        val genres: List<String>? = null,
        val type: String = "MOVIE",
        val posterPath: String? = null,
        val backdropPath: String? = null,
        val titleCz: String? = null,
        val overviewCz: String? = null,
        val certificationAge: Int? = null,
        val originCountries: List<String>? = null,
        val originalTitle: String? = null,
    )

    private fun MediaItem.toStored(): StoredItem = StoredItem(
        traktId = traktId,
        tmdbId = tmdbId,
        imdbId = imdbId,
        title = title,
        year = year,
        overview = overview,
        rating = rating,
        genres = genres,
        type = type.name,
        posterPath = posterPath,
        backdropPath = backdropPath,
        titleCz = titleCz,
        overviewCz = overviewCz,
        certificationAge = certificationAge,
        originCountries = originCountries,
        originalTitle = originalTitle,
    )

    private fun StoredItem.toDomain(): MediaItem = MediaItem(
        traktId = traktId,
        tmdbId = tmdbId,
        imdbId = imdbId,
        title = title,
        year = year,
        overview = overview,
        rating = rating,
        genres = genres,
        type = if (type == MediaType.SHOW.name) MediaType.SHOW else MediaType.MOVIE,
        posterPath = posterPath,
        backdropPath = backdropPath,
        titleCz = titleCz,
        overviewCz = overviewCz,
        certificationAge = certificationAge,
        originCountries = originCountries,
        originalTitle = originalTitle,
    )

    private companion object {
        /** Strop akumulovaného seznamu; přetečení ořízne nejstarší (začátek). */
        const val CAP = 200
    }
}
