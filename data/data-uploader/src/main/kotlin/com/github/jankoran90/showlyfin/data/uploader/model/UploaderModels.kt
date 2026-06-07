package com.github.jankoran90.showlyfin.data.uploader.model

import com.google.gson.annotations.SerializedName

data class UploaderStreamQuality(
    val resolution: String? = null,
    @SerializedName("videoCodec") val videoCodec: String? = null,
    @SerializedName("audioLanguage") val audioLanguage: String? = null,
    @SerializedName("audioFormat") val audioFormat: String? = null,
    val channels: String? = null,
    val source: String? = null,
    @SerializedName("sizeGB") val sizeGB: Double? = null,
    @SerializedName("bitrateMbps") val bitrateMbps: Double? = null,
    val score: Int = 0,
    @SerializedName("rdReady") val rdReady: Boolean = false,
    @SerializedName("rdDownloadable") val rdDownloadable: Boolean = false,
    @SerializedName("rdSaved") val rdSaved: Boolean = false,   // už uložené na RD (DebridSearch)
    val hdr: Boolean = false,
    @SerializedName("csfdPct") val csfdPct: Int? = null,
    val seeders: Int? = null,
    val fps: Double? = null,
    @SerializedName("durationS") val durationS: Double? = null,
)

data class UploaderStream(
    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
    @SerializedName("infoHash") val infoHash: String? = null,
    @SerializedName("fileIdx") val fileIdx: Int = 0,
    @SerializedName("cometPath") val cometPath: String? = null,
    val addon: String? = null,
    val quality: UploaderStreamQuality = UploaderStreamQuality(),
    val fps: Double? = null,
)

data class UploaderStreamsResponse(
    val streams: List<UploaderStream> = emptyList(),
    val error: String? = null,
)

data class UploaderResolveRequest(
    @SerializedName("infoHash") val infoHash: String? = null,
    @SerializedName("fileIdx") val fileIdx: Int = 0,
    @SerializedName("cometPath") val cometPath: String? = null,
    // Plan CASCADE: fallback kontext — když primární zdroj selže (DMCA/451), backend zkusí
    // dalšího cached kandidáta stejné kvality a nejbližší velikosti.
    val imdb: String? = null,
    val mediaType: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val quality: UploaderResolveQuality? = null,
)

/** Kvalita vybraného streamu pro CASCADE fallback (preferuj stejné rozlišení + nejbližší velikost). */
data class UploaderResolveQuality(
    val resolution: String? = null,
    @SerializedName("sizeGB") val sizeGB: Double? = null,
)

/** Volitelný kontext, který appka přibalí k resolve pro auto-fallback (Plan CASCADE). */
data class UploaderResolveContext(
    val imdb: String? = null,
    val mediaType: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val resolution: String? = null,
    val sizeGB: Double? = null,
)

data class UploaderResolveResponse(
    val url: String? = null,
    val error: String? = null,
    val fallback: Boolean? = null,
    @SerializedName("fallbackName") val fallbackName: String? = null,
)

// ── RD caching progress (Fáze F) — async add + poll pro NEcachované torrenty ───

data class UploaderRdAddRequest(
    @SerializedName("infoHash") val infoHash: String? = null,
    @SerializedName("fileIdx") val fileIdx: Int = 0,
    @SerializedName("cometPath") val cometPath: String? = null,
)

data class UploaderRdAddResponse(
    @SerializedName("torrent_id") val torrentId: String = "",
    val status: String = "",
    val progress: Double = 0.0,
    @SerializedName("file_idx") val fileIdx: Int = 0,
    val error: String? = null,
)

data class UploaderRdProgressResponse(
    @SerializedName("torrent_id") val torrentId: String = "",
    val status: String = "",
    val progress: Double = 0.0,
    val speed: Long = 0,
    val seeders: Int = 0,
    val filename: String = "",
    val url: String? = null,
    val error: String? = null,
)

// ── RD card-level match (Fáze F++) — filtr „jen co je na RD" v Objevit/Chci vidět ─

data class RdMatchItem(
    val title: String,
    val year: Int? = null,
)

data class RdMatchRequest(
    val items: List<RdMatchItem> = emptyList(),
)

data class RdMatchResponse(
    val matched: List<Int> = emptyList(),
)

// ── Sekce „Na RD" (Fáze D) — uložené filmy na RD účtu, TMDB-matchnuté ──────────

data class RdLibraryItem(
    @SerializedName("tmdb_id") val tmdbId: Int? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
    val title: String = "",
    val year: Int? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    val type: String = "movie",
)

data class RdLibraryResponse(
    val items: List<RdLibraryItem> = emptyList(),
    val unmatched: List<String> = emptyList(),
)

// ── ČSFD (scrape na BACKENDU — server zvládá Anubis anti-bot, zařízení ne kvůli ──
//     cookie-propagation bugu po pass-challenge) ───────────────────────────────────
/** Odpověď /api/csfd/plot — český popis filmu z ČSFD. */
data class CsfdPlotResponse(val plot: String? = null)
/** Jedna ČSFD recenze (/api/csfd/reviews). rating je 0–100 % (stars×20). */
data class CsfdReviewItem(
    val username: String = "",
    val rating: Int? = null,
    val text: String = "",
    val date: String = "",
)
data class CsfdReviewsResponse(val reviews: List<CsfdReviewItem> = emptyList())

// ── Stremio / Comet stream filter (Nastavení) ─────────────────────────────────

data class StreamSizeRange(val min: Double = 2.0, val max: Double = 8.0)

data class StreamFilterPrefs(
    val allowedResolutions: List<String> = listOf("4K", "1080p"),
    val videoCodecs: List<String> = emptyList(),
    val audioFormats: List<String> = emptyList(),
    val channels: List<String> = emptyList(),
    val audioLanguages: List<String> = emptyList(),
    val minSizeGB: Double = 1.0,
    val maxSizeGB: Double = 20.0,
    val maxResults: Int = 20,
    val guaranteeCzSk: Boolean = true,
    val strict: Boolean = true,
    val fallbackOrder: List<String> = listOf("cached", "czsk", "res4k", "sizeSweet", "hevcSdr"),
    val sizeSweetSpot: StreamSizeRange = StreamSizeRange(),
    val rdFirstMode: String = "both",   // RD-first / DebridSearch: off | hash | search | both
)

data class UploaderCaptureRequest(
    val stream: UploaderStream,
    @SerializedName("imdb_id") val imdbId: String,
    val title: String,
    val year: Int?,
    val type: String,
    val tmm: Boolean = true,
    val season: Int? = null,
    val episode: Int? = null,
)

data class UploaderCaptureResponse(
    @SerializedName("file_id") val fileId: String,
    @SerializedName("session_id") val sessionId: String,
    val mode: String,
    val filename: String,
)

data class UploaderLoginRequest(val password: String)

// ── TMM Pipeline models ──────────────────────────────────────────────────────

data class TmmCandidate(
    @SerializedName("tmdb_id") val tmdbId: Int,
    val title: String = "",
    @SerializedName("original_title") val originalTitle: String = "",
    val year: Int? = null,
    val overview: String = "",
    @SerializedName("poster_url") val posterUrl: String? = null,
    val confidence: Int = 0,
)

data class TmmMatch(
    @SerializedName("tmdb_id") val tmdbId: Int,
    val title: String = "",
    val year: Int? = null,
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    val genres: List<String> = emptyList(),
)

data class TmmFile(
    val filename: String = "",
    val status: String = "",
    @SerializedName("download_pct") val downloadPct: Double = 0.0,
    @SerializedName("download_speed_mb") val downloadSpeedMb: Double = 0.0,
    val candidates: List<TmmCandidate>? = null,
    @SerializedName("confirmed_match") val confirmedMatch: TmmMatch? = null,
    val error: String? = null,
)

data class TmmSession(
    @SerializedName("session_id") val sessionId: String,
    val files: Map<String, TmmFile> = emptyMap(),
)

data class TmmSearchResponse(val candidates: List<TmmCandidate> = emptyList())

data class TmmFileResult(val ok: Boolean = false, val dest: String? = null, val error: String? = null)

data class TmmConfirmResponse(val status: String = "", val match: TmmMatch? = null)

data class TmmProcessResponse(val results: Map<String, TmmFileResult> = emptyMap())

data class TmmMoveResponse(
    val results: Map<String, TmmFileResult> = emptyMap(),
    @SerializedName("jellyfin_scan") val jellyfinScan: Boolean = false,
)

// ── Request models ───────────────────────────────────────────────────────────

data class TmmSearchRequest(val query: String, val year: Int? = null)
data class TmmConfirmRequest(@SerializedName("tmdb_id") val tmdbId: Int)
data class TmmProcessRequest(@SerializedName("date_added") val dateAdded: String? = null)
data class TmmMoveRequest(val library: String)

// ── Library browser models ───────────────────────────────────────────────────

data class LibraryItem(
    val name: String = "",
    val title: String = "",
    val year: String? = null,
    @SerializedName("tmdb_id") val tmdbId: Int? = null,
    val resolution: String = "",
    val audio: String = "",
    @SerializedName("file_size") val fileSizeBytes: Long = 0L,
    val watched: Boolean = false,
    val favorite: Boolean = false,
    @SerializedName("jf_item_id") val jfItemId: String? = null,
    @SerializedName("artwork_poster_url") val artworkPosterUrl: String? = null,
    @SerializedName("has_nfo") val hasNfo: Boolean = false,
    @SerializedName("has_poster") val hasPoster: Boolean = false,
    @SerializedName("has_fanart") val hasFanart: Boolean = false,
    @SerializedName("has_logo") val hasLogo: Boolean = false,
    val complete: Boolean = false,
    val overview: String = "",
    val director: String? = null,
    val genres: List<String> = emptyList(),
    @SerializedName("media_type") val mediaType: String = "movie",
    @SerializedName("date_added_nfo") val dateAddedNfo: String? = null,
    @SerializedName("added_at") val addedAt: Long = 0L,
    @SerializedName("artwork_backdrop_url") val artworkBackdropUrl: String? = null,
    @SerializedName("artwork_logo_url") val artworkLogoUrl: String? = null,
    @SerializedName("user_collection") val userCollection: String? = null,
)

data class LibraryScanResponse(val library: String = "", val items: List<LibraryItem> = emptyList(), val count: Int = 0)

data class StorageboxUserdataRequest(
    val library: String,
    val folder: String,
    val watched: Boolean? = null,
    val favorite: Boolean? = null,
    @SerializedName("jf_item_id") val jfItemId: String? = null,
)

// ── TMDB detail models ───────────────────────────────────────────────────────

data class TmdbCastMember(
    val name: String = "",
    val character: String = "",
    @SerializedName("profile_url") val profileUrl: String? = null,
)

data class TmdbArtworkOption(
    val url: String = "",
    @SerializedName("url_orig") val urlOrig: String? = null,
    val thumb: String = "",
    val votes: Float = 0f,
    val lang: String = "",
)

data class TmdbDetail(
    @SerializedName("tmdb_id") val tmdbId: Int = 0,
    val title: String = "",
    @SerializedName("original_title") val originalTitle: String = "",
    val year: Int? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    val overview: String = "",
    val tagline: String = "",
    val runtime: String? = null,
    val certification: String? = null,
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val production: String? = null,
    val countries: List<String> = emptyList(),
    val collection: String? = null,
    val keywords: List<String> = emptyList(),
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("vote_count") val voteCount: Int? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
    val cast: List<TmdbCastMember> = emptyList(),
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("poster_options") val posterOptions: List<TmdbArtworkOption> = emptyList(),
    @SerializedName("backdrop_options") val backdropOptions: List<TmdbArtworkOption> = emptyList(),
    @SerializedName("logo_options") val logoOptions: List<TmdbArtworkOption> = emptyList(),
)

// ── Storagebox detail operation models ───────────────────────────────────────

data class StorageboxSearchRequest(
    val library: String,
    val folder: String,
    val query: String,
    val year: Int? = null,
    @SerializedName("media_type") val mediaType: String = "movie",
)

data class StorageboxConfirmRequest(
    val library: String,
    val folder: String,
    @SerializedName("tmdb_id") val tmdbId: Int,
    @SerializedName("media_type") val mediaType: String = "movie",
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("jf_item_id") val jfItemId: String? = null,
)

data class StorageboxConfirmResponse(
    val ok: Boolean = false,
    val title: String = "",
    val year: String? = null,
    @SerializedName("has_nfo") val hasNfo: Boolean = false,
    @SerializedName("has_poster") val hasPoster: Boolean = false,
    @SerializedName("has_fanart") val hasFanart: Boolean = false,
    @SerializedName("has_logo") val hasLogo: Boolean = false,
    val complete: Boolean = false,
    val match: TmdbDetail? = null,
)

data class StorageboxArtworkRequest(
    val library: String, val folder: String, val type: String, val url: String,
    @SerializedName("jf_item_id") val jfItemId: String? = null,
)

data class StorageboxCollectionRequest(val library: String, val folder: String, val collection: String)
data class StorageboxCollectionsResponse(val collections: List<String> = emptyList())
data class StorageboxDateAddedRequest(
    val library: String, val folder: String, val date: String,
    @SerializedName("jf_item_id") val jfItemId: String? = null,
)

// ── Probe + Remux models ─────────────────────────────────────────────────────

data class ProbeStream(val index: Int = 0, val type: String = "", val codec: String = "", val lang: String = "", val title: String = "", val channels: Int = 0)
data class ProbeResponse(val streams: List<ProbeStream> = emptyList(), @SerializedName("total_dur_ms") val totalDurMs: Long = 0L)

data class RemuxJob(val id: String = "", val status: String = "", val pct: Double = 0.0, val error: String? = null)
data class RemuxStartRequest(val library: String, val folder: String, @SerializedName("keep_indices") val keepIndices: List<Int>, @SerializedName("total_dur_ms") val totalDurMs: Long)
data class RemuxStartResponse(@SerializedName("job_id") val jobId: String)

data class PairRequest(@SerializedName("video_fid") val videoFid: String, @SerializedName("audio_fid") val audioFid: String, val sid: String)
data class PairResponse(@SerializedName("job_id") val jobId: String, val rsid: String? = null, val status: String = "")

data class PairSyncPoint(@SerializedName("ts_a") val tsA: Double = 0.0, @SerializedName("offset_s") val offsetS: Double = 0.0, val confidence: Double = 0.0)
data class PairSyncResult(
    @SerializedName("final_offset_s") val finalOffsetS: Double? = null,
    val agree: Boolean = false, val method: String = "",
    @SerializedName("chroma_confidence") val chromaConfidence: Double = 0.0,
    @SerializedName("n_agree") val nAgree: Int = 0, @SerializedName("n_total") val nTotal: Int = 0,
    val points: List<PairSyncPoint>? = null,
)

data class PairJob(
    val id: String = "", val status: String = "", val pct: Double = 0.0, val error: String? = null,
    @SerializedName("video_tracks") val videoTracks: List<ProbeStream>? = null,
    @SerializedName("audio_tracks") val audioTracks: List<ProbeStream>? = null,
    @SerializedName("sync_result") val syncResult: PairSyncResult? = null,
    @SerializedName("sync_points") val syncPoints: List<PairSyncPoint>? = null,
    @SerializedName("fps_orig") val fpsOrig: Double = 0.0, @SerializedName("fps_source") val fpsSource: Double = 0.0,
)

data class PairMergeRequest(
    @SerializedName("video_indices") val videoIndices: List<Int>,
    @SerializedName("audio_indices") val audioIndices: List<Int>,
    @SerializedName("override_offset_s") val overrideOffsetS: Double? = null,
    @SerializedName("apply_override") val applyOverride: Boolean = false,
    @SerializedName("apply_atempo") val applyAtempo: Boolean = false,
)

// ── Remux History models ─────────────────────────────────────────────────────

data class RemuxMergeConfig(
    @SerializedName("video_indices") val videoIndices: List<Int> = emptyList(),
    @SerializedName("audio_indices") val audioIndices: List<Int> = emptyList(),
    @SerializedName("offset_s") val offsetS: Double = 0.0,
    @SerializedName("apply_atempo") val applyAtempo: Boolean = false,
    @SerializedName("apply_override") val applyOverride: Boolean = false,
)

data class RemuxSession(
    val id: String = "", @SerializedName("created_at") val createdAt: String = "",
    val title: String = "", @SerializedName("imdb_id") val imdbId: String = "",
    @SerializedName("tmm_sid") val tmmSid: String = "", @SerializedName("video_fid") val videoFid: String = "",
    @SerializedName("audio_fid") val audioFid: String = "", @SerializedName("pair_job_id") val pairJobId: String = "",
    val status: String = "", @SerializedName("sync_result") val syncResult: PairSyncResult? = null,
    @SerializedName("fps_orig") val fpsOrig: Double? = null, @SerializedName("fps_source") val fpsSource: Double? = null,
    @SerializedName("merge_config") val mergeConfig: RemuxMergeConfig? = null,
    @SerializedName("merged_path") val mergedPath: String? = null, val error: String? = null,
)

data class RemuxHistoryResponse(val sessions: List<RemuxSession> = emptyList())
data class RemuxReDetectResponse(@SerializedName("job_id") val jobId: String, val status: String = "")

// ── Fix Audio Delay models ────────────────────────────────────────────────────

data class FixAudioDelayRequest(val library: String, val folder: String, @SerializedName("offset_s") val offsetS: Double)
data class FixAudioDelayStartResponse(@SerializedName("job_id") val jobId: String)
data class FixAudioDelayJob(
    val id: String = "", val status: String = "", val pct: Double = 0.0, val error: String? = null,
    @SerializedName("backup_path") val backupPath: String? = null, @SerializedName("offset_s") val offsetS: Double = 0.0,
)

// ── Titulky.com CZ titulky (Plan QUASAR Fáze E) ───────────────────────────────

data class SubtitleCandidate(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("release") val release: String = "",
    @SerializedName("fps") val fps: Double = 0.0,
    @SerializedName("year") val year: Int = 0,
    @SerializedName("lang") val lang: String = "cs",
    @SerializedName("imdbMatch") val imdbMatch: Boolean = false,
    @SerializedName("downloads") val downloads: Int = 0,
    @SerializedName("score") val score: Double = 0.0,
)

data class SubtitlesResponse(
    @SerializedName("subtitles") val subtitles: List<SubtitleCandidate> = emptyList(),
    @SerializedName("best") val best: Int = -1,
)

/** Stažený .srt (UTF-8) + ověření délky proti filmu (z hlaviček backendu). Ne-síťový holder. */
data class SubtitleDownload(
    val bytes: ByteArray,
    val lastTsSec: Int = 0,
    val runtimeOk: String = "-",   // "1" sedí / "0" nesedí / "-" neznámo
)

/** Parametry hledání CZ titulků (titulky.com). In-memory přenos Detail → Playback. */
data class SubtitleQuery(
    val imdb: String,
    val title: String,          // český název (preferovaný pro hledání)
    val origTitle: String,      // originální/anglický název
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val release: String? = null, // release zvoleného streamu (pro match)
    val fps: Double? = null,
    val runtime: Int? = null,
)
