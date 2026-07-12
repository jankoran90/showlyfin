package com.github.jankoran90.showlyfin.feature.discover.home

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig

/**
 * TENFOOT — TV DOMOV REDESIGN. Sjednocená položka jedné řady domova (nadmnožina Trakt/TMDB i Jellyfin).
 * Klik: [mediaItem] != null → bohatý Trakt/TMDB detail; jinak [jellyfinId] → Jellyfin karta (seriál/film).
 */
data class HomeRowItem(
    /** Unikátní klíč v rámci řady (Compose `key`). */
    val key: String,
    val title: String,
    /** Např. „S1E4 · Název" u epizody (Pokračovat / Další díly). */
    val subtitle: String? = null,
    val year: Int? = null,
    /** Přímá URL plakátu 2:3 (poster / cover styl). */
    val posterUrl: String? = null,
    /** Přímá URL fanartu 16:9 (landscape styl); null → fallback na [posterUrl]. */
    val landscapeUrl: String? = null,
    val progressPct: Int? = null,
    val watched: Boolean = false,
    val mediaItem: MediaItem? = null,
    val jellyfinId: String? = null,
)

/** Stav jedné řady na domově (lazy načítaná). */
data class HomeRowState(
    val config: HomeRowConfig,
    val items: List<HomeRowItem> = emptyList(),
    val loading: Boolean = true,
)
