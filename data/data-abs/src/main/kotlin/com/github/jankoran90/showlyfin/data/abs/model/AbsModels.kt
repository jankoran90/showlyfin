package com.github.jankoran90.showlyfin.data.abs.model

import com.google.gson.annotations.SerializedName

// ---- Login ----
data class AbsLoginRequest(
    val username: String,
    val password: String,
)

data class AbsLoginResponse(
    val user: AbsUser?,
    val userDefaultLibraryId: String? = null,
)

data class AbsUser(
    val id: String?,
    val username: String?,
    // Legacy ABS vrací long-lived `token`; ABS 2.26+ vrací accessToken/refreshToken
    // (s hlavičkou x-return-tokens). Bereme `token`, fallback accessToken.
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
) {
    val bearerToken: String? get() = token ?: accessToken
}

// ---- Libraries ----
data class AbsLibrariesResponse(
    val libraries: List<AbsLibrary> = emptyList(),
)

data class AbsLibrary(
    val id: String,
    val name: String,
    val mediaType: String, // "book" | "podcast"
)

// ---- Library items ----
data class AbsLibraryItemsResponse(
    val results: List<AbsLibraryItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
)

data class AbsLibraryItem(
    val id: String,
    val libraryId: String? = null,
    val mediaType: String? = null,
    val media: AbsMedia? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
)

data class AbsMedia(
    val metadata: AbsMetadata? = null,
    val coverPath: String? = null,
    val duration: Double? = null,
    val numChapters: Int? = null,
    val numTracks: Int? = null,
    val numEpisodes: Int? = null,            // podcast: počet epizod
    val autoDownloadEpisodes: Boolean? = null, // podcast: ABS server auto-download nových epizod
    val chapters: List<AbsChapter>? = null,
    val tracks: List<AbsAudioTrack>? = null,
    val episodes: List<AbsPodcastEpisode>? = null, // podcast: epizody (expanded)
)

/** Tělo PATCH /api/items/{id}/media — zapnutí/vypnutí ABS server auto-downloadu epizod. */
data class AbsMediaUpdate(val autoDownloadEpisodes: Boolean)

// ---- RSS feed: dostupné epizody k stažení na server ----
// ABS 2.x: POST /api/podcasts/feed {rssFeed} → {podcast:{episodes}};
//          POST /api/podcasts/{id}/download-episodes  (tělo = HOLÉ pole feed epizod)

/** Tělo POST /api/podcasts/feed — naparsuje RSS feed a vrátí všechny epizody. */
data class AbsPodcastFeedRequest(
    val rssFeed: String,
)

/** Odpověď POST /api/podcasts/feed. */
data class AbsPodcastFeedResponse(
    val podcast: AbsFeedPodcast? = null,
)

data class AbsFeedPodcast(
    val episodes: List<com.google.gson.JsonObject> = emptyList(),
)

data class AbsMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val authorName: String? = null,
    val author: String? = null,              // podcast metadata používá `author`
    val narratorName: String? = null,
    val seriesName: String? = null,
    val description: String? = null,
    val publishedYear: String? = null,
    val genres: List<String>? = null,
    val language: String? = null,
    val feedUrl: String? = null,            // podcast: URL RSS feedu (pro „Prohledat epizody")
) {
    /** Sjednocený autor: audioknihy `authorName`, podcasty `author`. */
    val authorDisplay: String? get() = authorName?.takeIf { it.isNotBlank() } ?: author?.takeIf { it.isNotBlank() }
}

// ---- Podcast epizody ----
data class AbsPodcastEpisode(
    val id: String,
    val index: Int? = null,
    val season: String? = null,
    val episode: String? = null,
    val episodeType: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val pubDate: String? = null,
    val publishedAt: Long? = null,           // ms
    val duration: Double? = null,            // některé ABS verze; jinak v audioFile/audioTrack
    val audioFile: AbsAudioFile? = null,
    val audioTrack: AbsAudioTrack? = null,
    val enclosure: AbsEnclosure? = null,     // původní RSS enclosure (porovnání s feedem)
) {
    /** Délka epizody v sekundách z nejspolehlivějšího zdroje. */
    val durationSec: Double get() = audioTrack?.duration ?: audioFile?.duration ?: duration ?: 0.0
}

data class AbsEnclosure(
    val url: String? = null,
)

data class AbsAudioFile(
    val ino: String? = null,
    val duration: Double? = null,
    val mimeType: String? = null,
    val codec: String? = null,
    val metadata: AbsFileMetadata? = null,
)

data class AbsFileMetadata(
    val filename: String? = null,
)

/** Tělo PATCH /api/me/progress/{itemId}/{episodeId} pro označení přehráno/nepřehráno. */
data class AbsProgressUpdate(
    val isFinished: Boolean,
)

data class AbsChapter(
    val id: Int,
    val start: Double,
    val end: Double,
    val title: String? = null,
)

// ---- Me / progress ----
data class AbsMeResponse(
    val id: String? = null,
    val username: String? = null,
    val mediaProgress: List<AbsMediaProgress> = emptyList(),
)

data class AbsMediaProgress(
    val libraryItemId: String? = null,
    val episodeId: String? = null,
    val duration: Double? = null,
    val progress: Double? = null,       // 0.0 - 1.0
    val currentTime: Double? = null,    // sekundy
    val isFinished: Boolean = false,
    val lastUpdate: Long? = null,
)

// ---- Play session ----
data class AbsPlayRequest(
    val deviceInfo: AbsDeviceInfo,
    val forceDirectPlay: Boolean = true,
    val forceTranscode: Boolean = false,
    val mediaPlayer: String = "exoplayer",
)

data class AbsDeviceInfo(
    val clientName: String = "Showlyfin",
    val deviceId: String,
)

data class AbsPlaySession(
    val id: String,
    val libraryItemId: String? = null,
    val displayTitle: String? = null,
    val displayAuthor: String? = null,
    val coverPath: String? = null,
    val duration: Double? = null,
    val currentTime: Double? = null,    // uložená pozice ze serveru
    val chapters: List<AbsChapter> = emptyList(),
    val audioTracks: List<AbsAudioTrack> = emptyList(),
)

data class AbsAudioTrack(
    val index: Int? = null,
    val ino: String? = null,
    val contentUrl: String? = null,     // relativní, např. /api/items/{id}/file/{ino}
    val startOffset: Double? = null,    // posun tracku v rámci celé knihy (s)
    val duration: Double? = null,
    val mimeType: String? = null,
    val codec: String? = null,
    val title: String? = null,
)

data class AbsSyncRequest(
    val currentTime: Double,
    val timeListening: Double = 0.0,
    val duration: Double? = null,
)
