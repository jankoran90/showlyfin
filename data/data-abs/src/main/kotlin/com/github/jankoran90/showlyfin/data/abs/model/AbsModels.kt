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
    val chapters: List<AbsChapter>? = null,
    val tracks: List<AbsAudioTrack>? = null,
)

data class AbsMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val authorName: String? = null,
    val narratorName: String? = null,
    val seriesName: String? = null,
    val description: String? = null,
    val publishedYear: String? = null,
    val genres: List<String>? = null,
    val language: String? = null,
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
