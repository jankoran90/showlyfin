package com.github.jankoran90.showlyfin.data.uploader.model

import com.google.gson.annotations.SerializedName

/**
 * PRESET (SHW-65): dynamický správce ZDROJŮ Poslechu (YouTube kanály + RSS podcasty).
 * Seznam zdrojů je SDÍLENÝ na serveru → přidám na jednom telefonu, vidí celá rodina.
 * Streaming-first: nic se nestahuje na server (na rozdíl od ABS) → sedí do filozofie nezávislosti.
 */

/** Uložený zdroj ve sdíleném serverovém store (`/api/sources`). */
data class PodcastSource(
    val id: String,
    val type: String,                 // "youtube" | "rss"
    val ref: String,                  // youtube: channel_id/@handle ; rss: feed_url
    val title: String,
    val thumbnail: String? = null,
    @SerializedName("added_at") val addedAt: Long? = null,
    /** EXODUS (SHW-67): prémiový zdroj rodiny (NaVýbornou přes herolink) — pin nahoru, odznak, nemazat. */
    val premium: Boolean = false,
)

/** Odpověď store endpointů (`/api/sources` GET/POST/DELETE). */
data class SourcesResponse(
    val sources: List<PodcastSource> = emptyList(),
    val added: Boolean? = null,
    val source: PodcastSource? = null,
)

/** Tělo POST `/api/sources` — přidání zdroje (po vybrání z hledání). */
data class AddSourceRequest(
    val type: String,
    val ref: String,
    val title: String,
    val thumbnail: String? = null,
)

/**
 * Výsledek hledání / objevovací karta zdroje (`/api/sources/search` i `/api/sources/browse`).
 * AGORA: backend obohacuje kartu o [summary] (popis), [episodeCount] (počet epizod) a [category]
 * (název kategorie) — reuse stejného tvaru pro hledání i objevování.
 */
data class SourceSearchResult(
    val type: String,                 // "youtube" | "rss"
    val ref: String,
    val title: String,
    val subtitle: String? = null,     // "YouTube kanál" / autor podcastu (KDO PROVÁZÍ)
    val thumbnail: String? = null,
    val summary: String? = null,                                    // krátký popis pořadu
    @SerializedName("episode_count") val episodeCount: Int? = null, // počet epizod
    val category: String? = null,                                   // název kategorie
)

data class SourceSearchResponse(
    val results: List<SourceSearchResult> = emptyList(),
)

/**
 * AGORA (objevovací modul): kategorie podcastů pro danou zemi (`/api/sources/categories`).
 * CZ kategorie ≠ Apple žánr — kategorie patří vždy ke konkrétní zemi.
 */
data class SourceCategory(
    val id: Int,
    val name: String,
    val slug: String? = null,
    val color: String? = null,
)

data class CategoriesResponse(
    val categories: List<SourceCategory> = emptyList(),
    val country: String = "cz",
)

/** Odpověď objevovacího procházení (`/api/sources/browse`) — karta = [SourceSearchResult]. */
data class SourceBrowseResponse(
    val results: List<SourceSearchResult> = emptyList(),
    val country: String = "cz",
    val mode: String = "active",
)

/** RSS podcast feed → epizody (`/api/rss/feed`). audio_url = přímá enclosure URL (ExoPlayer hraje rovnou). */
data class RssFeed(
    val title: String? = null,
    val image: String? = null,
    val episodes: List<RssEpisode> = emptyList(),
)

data class RssEpisode(
    val id: String,
    val title: String = "",
    @SerializedName("audio_url") val audioUrl: String,
    val date: String? = null,
    val description: String? = null,
    val duration: String? = null,     // itunes:duration ("HH:MM:SS" / "MM:SS" / sekundy)
    val image: String? = null,
    /** EXODUS (SHW-67) E2: video epizody v JF knihovně (NaVýbornou) — tlačítko Video / cast na TV. */
    @SerializedName("jf_item_id") val jfItemId: String? = null,
)

/**
 * AGORA (SHW, F5): kandidát VIDEO verze audio epizody nalezený na YouTube (`/api/sources/episode-video`).
 * U epizod BEZ vlastního video v JF knihovně (tj. ne NaVýbornou) lze dohledat video verzi na YouTube
 * a přehrát ji / poslat na TV stejnou cestou jako YouTube kanál (proxy `/api/yt/stream/{id}?kind=video`).
 */
data class EpisodeVideo(
    val id: String,                   // yt_video_id
    val title: String = "",
    val uploader: String = "",
    val duration: Int = 0,            // sekundy
)

data class EpisodeVideoResponse(
    val results: List<EpisodeVideo> = emptyList(),
)

/**
 * CRUISE (SHW-70): sjednocená přehratelná epizoda libovolného zdroje (YouTube/RSS/NaVýbornou) s PŘÍMOU
 * stream URL. Vrací [PodcastSourcesRepository.loadEpisodes] — datová vrstva vlastní stavbu URL (YT proxy
 * vs RSS enclosure), spotřebitel (Android Auto browse strom) jen postaví MediaItem.
 */
data class SourceEpisode(
    val id: String,
    val title: String,
    val subtitle: String? = null,     // název zdroje (autor/kanál)
    val streamUrl: String,
    val imageUrl: String? = null,
    val date: String? = null,
    /** Klíč resume SJEDNOCENÝ s in-app přehrávačem (`yt:<id>` / `rss:<id>`) → pozice se sdílí
     *  mezi appkou a Android Auto + položka jde do AA „Pokračovat" (CRUISE). */
    val resumeKey: String? = null,
)
