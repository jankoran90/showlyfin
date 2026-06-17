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

/** Výsledek hledání zdroje podle názvu (`/api/sources/search`). */
data class SourceSearchResult(
    val type: String,                 // "youtube" | "rss"
    val ref: String,
    val title: String,
    val subtitle: String? = null,     // "YouTube kanál" / autor podcastu
    val thumbnail: String? = null,
)

data class SourceSearchResponse(
    val results: List<SourceSearchResult> = emptyList(),
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
