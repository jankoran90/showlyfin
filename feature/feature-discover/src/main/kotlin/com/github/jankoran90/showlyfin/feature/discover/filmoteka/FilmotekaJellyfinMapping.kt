package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.ZoneOffset

/**
 * ATRIUM (SHW-118) — čisté mapování Jellyfin DTO → [MediaItem] a dedup klíče. Vytaženo z
 * [FilmotekaBaseLoader] 2026-08-24: loader byl na 610 řádcích (tvrdý strop 600) a kolekce do něj
 * potřebovaly přidat další stav. Tyhle funkce nesahají na žádný stav loaderu (jen na vstupní DTO
 * + session údaje v parametrech), takže jsou přesunuté 1:1 beze změny chování.
 */

/**
 * FOYER (SHW-107, user 2026-07-26 „některé filmy nemají načtené obrázky coveru"): plakát z Jellyfinu se nese
 * s položkou jako FALLBACK. Enricher plní [MediaItem.posterUrl] z TMDB; když titul na TMDB nesedí
 * (nebo tam plakát není), zůstala karta prázdná, přestože Jellyfin obrázek MÁ. Vyplněný `posterUrl`
 * si enricher nepřepisuje jen tak — a i kdyby, TMDB plakát je hezčí, tak je to výhra oběma směry.
 */
internal fun BaseItemDto.toFilmotekaMediaItem(serverUrl: String, token: String): MediaItem? {
    // FOYER (SHW-107, DŮKAZ 2026-07-26): Jellyfin vrací BOX_SET (kolekce) i na dotaz `includeItemTypes=
    // Movie,Series` (ověřeno proti serveru: „Harry Potter (kolekce)" Tmdb=1241 mezi 16 položkami knihovny
    // Rodinné filmy). Bez téhle pojistky se kolekce mapovala na FILM s TMDB id KOLEKCE → enrich stáhl
    // úplně jiný film → karta s cizím obsahem, u které není co přehrát (přesně userův report). Filmy
    // uvnitř kolekce v seznamu ZŮSTÁVAJÍ (rekurzivní dotaz je vrací zvlášť) — nic se neztratí.
    if (type != BaseItemKind.MOVIE && type != BaseItemKind.SERIES) return null
    val tmdb = providerIds?.get("Tmdb")?.toLongOrNull()
    val imdb = providerIds?.get("Imdb")?.takeIf { it.isNotBlank() }
    if (tmdb == null && imdb == null) return null
    val isShow = type == BaseItemKind.SERIES
    return MediaItem(
        traktId = 0L,
        tmdbId = tmdb,
        imdbId = imdb,
        title = name ?: "",
        year = productionYear,
        overview = null,
        rating = null,
        genres = genres?.takeIf { it.isNotEmpty() },
        type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
        fallbackPosterUrl = jellyfinPosterUrl(serverUrl, token),
        addedAtMs = dateCreated?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
    )
}

/** Plakát z Jellyfinu (Primary tag → cache-busting; bez tagu se obrázek stejně vrátí, jen bez cache klíče). */
internal fun BaseItemDto.jellyfinPosterUrl(serverUrl: String, token: String): String? {
    val tag = imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY) ?: return null
    return "$serverUrl/Items/$id/Images/Primary?tag=$tag&fillWidth=400&quality=90&api_key=$token"
}

/** Široká grafika z Jellyfinu (Backdrop, fallback Thumb) — hlavička obsahu kolekce. */
internal fun BaseItemDto.jellyfinBackdropUrl(serverUrl: String, token: String): String? {
    val tag = imageTags?.get(org.jellyfin.sdk.model.api.ImageType.BACKDROP)
        ?: imageTags?.get(org.jellyfin.sdk.model.api.ImageType.THUMB)
        ?: backdropImageTags?.firstOrNull()
        ?: return null
    return "$serverUrl/Items/$id/Images/Backdrop?tag=$tag&fillWidth=1280&quality=90&api_key=$token"
}

/** Identita titulu napříč zdroji (JF / working-source / Trakt): TMDB má přednost, IMDb je fallback. */
internal fun filmotekaDedupKey(item: MediaItem): String? = when {
    item.tmdbId != null -> "tmdb:${item.tmdbId}"
    !item.imdbId.isNullOrBlank() -> "imdb:${item.imdbId}"
    else -> null
}

/** Minimální položka jen s identitou — enricher jí dopočítá zbytek. */
internal fun filmotekaStub(
    tmdbId: Long,
    imdbId: String?,
    title: String,
    year: Int?,
    isShow: Boolean,
    addedAtMs: Long? = null,
) = MediaItem(
    traktId = 0L,
    tmdbId = tmdbId,
    imdbId = imdbId,
    title = title,
    year = year,
    overview = null,
    rating = null,
    genres = null,
    type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
    addedAtMs = addedAtMs,
)

/** Filmové/seriálové/smíšené knihovny (vzor LibraryRowsViewModel.isMediaLibrary); RealDebrid vynech. */
internal fun BaseItemDto.isFilmotekaLibrary(): Boolean {
    val ct = collectionType?.name?.uppercase()
    val allowed = ct == null || ct == "MOVIES" || ct == "TVSHOWS" || ct == "MIXED"
    if (!allowed) return false
    val n = name?.lowercase() ?: return true
    return !n.contains("realdebrid") && !n.contains("real-debrid")
}
