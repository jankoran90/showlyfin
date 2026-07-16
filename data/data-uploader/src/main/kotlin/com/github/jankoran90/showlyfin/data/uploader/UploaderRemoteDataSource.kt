package com.github.jankoran90.showlyfin.data.uploader

import com.github.jankoran90.showlyfin.data.uploader.model.*

interface UploaderRemoteDataSource {
    suspend fun getStreams(baseUrl: String, sessionCookie: String, mediaType: String, imdbId: String, season: Int? = null, episode: Int? = null, strict: Boolean? = null): List<UploaderStream>
    // Plan CASCADE Fáze 3: probnuté zdroje (reálný addMagnet test) — jen smysluplné (instant/downloadable), mrtvé+infringing pryč.
    suspend fun getProbedStreams(baseUrl: String, sessionCookie: String, mediaType: String, imdbId: String, season: Int? = null, episode: Int? = null): List<UploaderStream>
    suspend fun resolveStream(baseUrl: String, sessionCookie: String, infoHash: String, fileIdx: Int = 0, ctx: UploaderResolveContext? = null): String
    suspend fun resolveCometStream(baseUrl: String, sessionCookie: String, cometPath: String, ctx: UploaderResolveContext? = null): String
    suspend fun rdAdd(baseUrl: String, sessionCookie: String, infoHash: String?, fileIdx: Int, cometPath: String?): UploaderRdAddResponse
    suspend fun rdProgress(baseUrl: String, sessionCookie: String, torrentId: String, fileIdx: Int): UploaderRdProgressResponse
    suspend fun rdSearch(baseUrl: String, sessionCookie: String, title: String, year: Int?): List<UploaderStream>
    // Plan WINNOW (SHW-41, item 2): bezpečný úklid RD — smaž torrenty z [hashes] kromě [keepHash]. Vrací počet smazaných.
    suspend fun rdCleanup(baseUrl: String, sessionCookie: String, keepHash: String?, hashes: List<String>): Int
    // Plan LEDGER (SHW-43): správa RD účtu z Nastavení — seznam všeho na RD + ruční/hromadné mazání.
    suspend fun rdList(baseUrl: String, sessionCookie: String, force: Boolean = false): List<UploaderRdSavedItem>
    suspend fun rdDelete(baseUrl: String, sessionCookie: String, hashes: List<String>): Int
    suspend fun rdPurgeOrphans(baseUrl: String, sessionCookie: String): Int
    suspend fun rdMatch(baseUrl: String, sessionCookie: String, items: List<RdMatchItem>): List<Int>
    suspend fun getRdLibrary(baseUrl: String, sessionCookie: String): RdLibraryResponse
    suspend fun getStreamFilter(baseUrl: String, sessionCookie: String): StreamFilterPrefs
    suspend fun putStreamFilter(baseUrl: String, sessionCookie: String, prefs: StreamFilterPrefs)
    suspend fun capture(baseUrl: String, sessionCookie: String, request: UploaderCaptureRequest): UploaderCaptureResponse
    suspend fun login(baseUrl: String, password: String): String
    // Plan PROFILES Fáze 2 — config balík per profil (raw JSON; key = jellyfinUserId)
    suspend fun getProfileConfig(baseUrl: String, sessionCookie: String, key: String): String?
    suspend fun putProfileConfig(baseUrl: String, sessionCookie: String, key: String, json: String)
    // COMPASS follow-up — Oblíbené per profil (raw JSON tělo {"favorites":[…]}; key = jellyfinUserId)
    suspend fun getProfileFavorites(baseUrl: String, sessionCookie: String, key: String): String?
    suspend fun putProfileFavorites(baseUrl: String, sessionCookie: String, key: String, json: String)
    // BESPOKE F2 — akumulovaná doporučení „Pro tebe" per profil (raw JSON {"recommendations":[…]})
    suspend fun getProfileRecommendations(baseUrl: String, sessionCookie: String, key: String): String?
    suspend fun putProfileRecommendations(baseUrl: String, sessionCookie: String, key: String, json: String)
    // BESPOKE F3 — vlastní hodnocení filmů 1–10 hvězd per profil (raw JSON {"ratings":[…]})
    suspend fun getProfileRatings(baseUrl: String, sessionCookie: String, key: String): String?
    suspend fun putProfileRatings(baseUrl: String, sessionCookie: String, key: String, json: String)
    // SIEVE follow-up — Zapamatované zdroje per profil (raw JSON {"sources":[…]})
    suspend fun getProfileWorkingSources(baseUrl: String, sessionCookie: String, key: String): String?
    suspend fun putProfileWorkingSources(baseUrl: String, sessionCookie: String, key: String, json: String)
    // AUTEUR (SHW-91) — kurátorský mozek: taste payload (raw JSON) → doporučení (raw JSON), nebo null při chybě.
    suspend fun curatorRecommend(baseUrl: String, sessionCookie: String, json: String): String?
    // LAPIDARY (SHW-96) — vzácné klenoty. cacheOne = watchlist/favorite trigger (fire-and-forget, backend běží na pozadí,
    // po nacachování zapíše auto-WorkingSource do profilu). gemsCatalog = obsah sekce (raw JSON {"items":[…]}), null při chybě.
    suspend fun gemsCacheOne(baseUrl: String, sessionCookie: String, imdb: String, tmdb: Long, profile: String, policy: String, title: String, year: Int?)
    suspend fun gemsCatalog(baseUrl: String, sessionCookie: String, country: String, status: String = "all", sort: String? = null): String?
    suspend fun putProfile(baseUrl: String, sessionCookie: String, key: String, name: String, isAdmin: Boolean, jellyfinUserId: String, templateUuid: String? = null, loginPinHash: String? = null)
    // Plan WARDEN W3c — raw JSON: pole šablon (/api/templates) + pole profilových meta (/api/profiles).
    suspend fun getTemplates(baseUrl: String, sessionCookie: String): String?
    suspend fun getProfilesMeta(baseUrl: String, sessionCookie: String): String?
    // Plan HELM — admin parity (in-app administrace profilů): JF knihovny, TMDB žánry, export/import.
    suspend fun getJellyfinLibraries(baseUrl: String, sessionCookie: String, userId: String): String?
    suspend fun getTmdbGenres(baseUrl: String, sessionCookie: String): String?
    suspend fun exportProfiles(baseUrl: String, sessionCookie: String): String?
    suspend fun importProfiles(baseUrl: String, sessionCookie: String, json: String): Boolean
    // Plan WARDEN W3c (část 2) — write-through authoring šablon na backend.
    suspend fun putTemplate(baseUrl: String, sessionCookie: String, uuid: String, name: String, ageRating: String?, configJson: String)
    suspend fun deleteTemplate(baseUrl: String, sessionCookie: String, uuid: String)
    suspend fun getSdillejStreams(baseUrl: String, sessionCookie: String, mediaType: String, imdbId: String, title: String, titleCs: String, year: Int? = null, season: Int? = null, episode: Int? = null, origTitle: String = ""): List<UploaderStream>
    suspend fun captureSdillej(baseUrl: String, sessionCookie: String, request: UploaderCaptureRequest): UploaderCaptureResponse

    // TMM Pipeline
    suspend fun getTmmSession(baseUrl: String, sessionCookie: String, sid: String): TmmSession
    suspend fun tmmSearch(baseUrl: String, sessionCookie: String, sid: String, fid: String, query: String, year: Int?): List<TmmCandidate>
    suspend fun tmmConfirm(baseUrl: String, sessionCookie: String, sid: String, fid: String, tmdbId: Int): TmmMatch
    suspend fun tmmProcess(baseUrl: String, sessionCookie: String, sid: String): TmmProcessResponse
    suspend fun tmmMove(baseUrl: String, sessionCookie: String, sid: String, library: String): TmmMoveResponse

    // Libraries
    suspend fun getLibraries(baseUrl: String, sessionCookie: String): List<String>
    suspend fun scanLibrary(baseUrl: String, sessionCookie: String, lib: String): List<LibraryItem>
    suspend fun updateUserdata(baseUrl: String, sessionCookie: String, library: String, folder: String, watched: Boolean?, favorite: Boolean?, jfItemId: String?)

    // Probe + Remux
    suspend fun probeStreams(baseUrl: String, sessionCookie: String, library: String, folder: String): ProbeResponse
    suspend fun startRemux(baseUrl: String, sessionCookie: String, library: String, folder: String, keepIndices: List<Int>, totalDurMs: Long): String
    suspend fun getRemuxStatus(baseUrl: String, sessionCookie: String, jobId: String): RemuxJob

    // Smart Pair
    suspend fun startPair(baseUrl: String, sessionCookie: String, videoFid: String, audioFid: String, sid: String): PairResponse
    suspend fun getPairStatus(baseUrl: String, sessionCookie: String, jobId: String): PairJob
    suspend fun selectPairTracks(baseUrl: String, sessionCookie: String, jobId: String, videoIndices: List<Int>, audioIndices: List<Int>, overrideOffsetS: Double? = null, applyOverride: Boolean = false, applyAtempo: Boolean = false)
    suspend fun cancelPair(baseUrl: String, sessionCookie: String, jobId: String)
    suspend fun getPairPreviewBytes(baseUrl: String, sessionCookie: String, jobId: String, t: Double, dur: Int, offsetS: Double): okhttp3.ResponseBody

    // Android log upload
    suspend fun uploadLog(baseUrl: String, sessionCookie: String, logBytes: ByteArray)

    // AIRWAVE II Fáze C — nahraj aktuální snapshot stažených filmů/epizod pod profile_key (jellyfinUserId).
    suspend fun reportDownloads(baseUrl: String, sessionCookie: String, profileKey: String, jsonBytes: ByteArray)

    // Remux History
    suspend fun getRemuxHistory(baseUrl: String, sessionCookie: String): List<RemuxSession>
    suspend fun getRemuxSessionDetail(baseUrl: String, sessionCookie: String, rsid: String): RemuxSession
    suspend fun deleteRemuxSession(baseUrl: String, sessionCookie: String, rsid: String)
    suspend fun reDetectRemuxSession(baseUrl: String, sessionCookie: String, rsid: String): RemuxReDetectResponse

    // TMDB detail
    suspend fun getTmdbDetail(baseUrl: String, sessionCookie: String, tmdbId: Int, mediaType: String): TmdbDetail

    // Storagebox detail operations
    suspend fun storageboxSearch(baseUrl: String, sessionCookie: String, library: String, folder: String, query: String, mediaType: String): List<TmmCandidate>
    suspend fun storageboxConfirm(baseUrl: String, sessionCookie: String, library: String, folder: String, tmdbId: Int, mediaType: String, posterUrl: String?, backdropUrl: String?, logoUrl: String?, jfItemId: String?): StorageboxConfirmResponse
    suspend fun setArtwork(baseUrl: String, sessionCookie: String, library: String, folder: String, type: String, url: String, jfItemId: String?)
    suspend fun setCollection(baseUrl: String, sessionCookie: String, library: String, folder: String, collection: String)
    suspend fun getCollections(baseUrl: String, sessionCookie: String, library: String): List<String>
    suspend fun setDateAdded(baseUrl: String, sessionCookie: String, library: String, folder: String, date: String, jfItemId: String?)
    suspend fun deleteFolder(baseUrl: String, sessionCookie: String, library: String, folder: String)

    // Fix Audio Delay
    suspend fun startFixAudioDelay(baseUrl: String, sessionCookie: String, library: String, folder: String, offsetS: Double): String
    suspend fun getFixAudioDelayStatus(baseUrl: String, sessionCookie: String, jobId: String): FixAudioDelayJob
    suspend fun deleteFixAudioDelayBackup(baseUrl: String, sessionCookie: String, jobId: String)

    // Library presence
    suspend fun checkLibraryPresence(baseUrl: String, sessionCookie: String, imdbIds: List<String>): Map<String, Boolean>

    // Titulky.com CZ titulky (Fáze E)
    suspend fun getSubtitles(
        baseUrl: String, sessionCookie: String, imdbId: String,
        title: String, origTitle: String, year: Int?,
        season: Int? = null, episode: Int? = null, release: String? = null, fps: Double? = null,
    ): SubtitlesResponse
    suspend fun downloadSubtitle(
        baseUrl: String, sessionCookie: String, titulkyId: String,
        season: Int? = null, episode: Int? = null, runtime: Int? = null,
    ): SubtitleDownload
    // Plan LINGUA Fáze 2 — async AI překlad EN→CS (poslední záloha když 0 CZ titulků)
    suspend fun startSubtitleTranslate(
        baseUrl: String, sessionCookie: String, imdbId: String,
        season: Int? = null, episode: Int? = null,
    ): SubtitleTranslateJob
    suspend fun getSubtitleTranslateStatus(
        baseUrl: String, sessionCookie: String, jobId: String,
    ): SubtitleTranslateJob

    // ČSFD popis + recenze přes backend (server zvládá Anubis anti-bot; csfdId se páruje on-device přes Wikidata)
    suspend fun getCsfdPlot(baseUrl: String, sessionCookie: String, csfdId: Long): CsfdPlotResponse
    suspend fun getCsfdReviews(baseUrl: String, sessionCookie: String, csfdId: Long): List<CsfdReviewItem>
    suspend fun getCsfdGallery(baseUrl: String, sessionCookie: String, csfdId: Long): List<String>

    // TUNER (SHW-62) — YouTube podcast (streaming): feed + samonosné stream URL (?key=, jako sdilej/titulky)
    suspend fun getYtFeed(baseUrl: String, sessionCookie: String, channel: String, limit: Int = 30): YtChannelFeed
    /** Přímá přehrávací URL přes backend byte-proxy (googlevideo je IP-locked na server). kind = "video"|"audio".
     *  CLARITY: quality jen pro video. Pro audio + progresivní 360p video. */
    fun ytStreamUrl(baseUrl: String, sessionCookie: String, videoId: String, kind: String, quality: String = "720"): String

    /** CLARITY: přehrávací URL VIDEA dle kvality. 360 = progresivní byte-proxy; 720/max = HLS proxy
     *  (itag 95/96 = video+audio, ExoPlayer hraje nativně, segmenty přes /api/yt/seg). Funguje i pro TV. */
    fun ytVideoUrl(baseUrl: String, sessionCookie: String, videoId: String, quality: String = "720"): String
    /** Pre-warm resolve cache (best-effort) — rychlejší start přehrávání. */
    suspend fun warmYt(baseUrl: String, sessionCookie: String, videoId: String, kind: String, quality: String = "720")

    // KAVKA (SHW-76) — ČT iVysílání podcast (DASH stream přes byte-proxy; o2tv CDN je IP-locked na server)
    suspend fun getCtvFeed(baseUrl: String, sessionCookie: String, show: String, limit: Int = 100): CtvShowFeed
    /** Poslechová (audio-only) varianta DASH manifestu dílu — `?audio=1`, ExoPlayer hraje jen aac stopu. */
    fun ctvAudioUrl(baseUrl: String, sessionCookie: String, idec: String): String
    /** Plný DASH manifest dílu (video) — ExoPlayer ABR (default nejvyšší dostupná, až 1080p). I pro cast na TV. */
    fun ctvVideoUrl(baseUrl: String, sessionCookie: String, idec: String): String
    /** Pre-warm resolve cache ČT dílu (best-effort). */
    suspend fun warmCtv(baseUrl: String, sessionCookie: String, idec: String)

    // PRESET (SHW-65) — dynamický správce zdrojů Poslechu: sdílený store + hledání podle názvu + RSS epizody
    suspend fun listSources(baseUrl: String, sessionCookie: String): List<PodcastSource>
    suspend fun addSource(baseUrl: String, sessionCookie: String, type: String, ref: String, title: String, thumbnail: String?): List<PodcastSource>
    suspend fun removeSource(baseUrl: String, sessionCookie: String, id: String): List<PodcastSource>
    suspend fun searchSources(baseUrl: String, sessionCookie: String, query: String, type: String = "all", limit: Int = 8): List<SourceSearchResult>
    /** RSS podcast feed → epizody (přímé audio enclosure URL — ExoPlayer hraje rovnou, nic se neukládá). */
    suspend fun getRssFeed(baseUrl: String, sessionCookie: String, feedUrl: String, limit: Int = 50): RssFeed
    /** AGORA (F5) — kandidáti VIDEO verze audio epizody na YouTube (q = název podcastu + název epizody). */
    suspend fun findEpisodeVideo(baseUrl: String, sessionCookie: String, query: String, limit: Int = 6): List<EpisodeVideo>

    // AGORA (objevovací modul) — procházení zdrojů dle země/režimu/kategorie + seznam kategorií
    suspend fun browseSources(
        baseUrl: String, sessionCookie: String, country: String, mode: String,
        category: String? = null, exclude: List<String>? = null, page: Int = 1, pageSize: Int = 30,
    ): SourceBrowseResponse
    suspend fun getCategories(baseUrl: String, sessionCookie: String, country: String): CategoriesResponse
}
