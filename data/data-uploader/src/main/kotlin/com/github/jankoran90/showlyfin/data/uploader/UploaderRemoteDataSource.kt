package com.github.jankoran90.showlyfin.data.uploader

import com.github.jankoran90.showlyfin.data.uploader.model.*

interface UploaderRemoteDataSource {
    suspend fun getStreams(baseUrl: String, sessionCookie: String, mediaType: String, imdbId: String, season: Int? = null, episode: Int? = null, strict: Boolean? = null): List<UploaderStream>
    suspend fun resolveStream(baseUrl: String, sessionCookie: String, infoHash: String, fileIdx: Int = 0): String
    suspend fun resolveCometStream(baseUrl: String, sessionCookie: String, cometPath: String): String
    suspend fun rdAdd(baseUrl: String, sessionCookie: String, infoHash: String?, fileIdx: Int, cometPath: String?): UploaderRdAddResponse
    suspend fun rdProgress(baseUrl: String, sessionCookie: String, torrentId: String, fileIdx: Int): UploaderRdProgressResponse
    suspend fun rdSearch(baseUrl: String, sessionCookie: String, title: String, year: Int?): List<UploaderStream>
    suspend fun rdMatch(baseUrl: String, sessionCookie: String, items: List<RdMatchItem>): List<Int>
    suspend fun getStreamFilter(baseUrl: String, sessionCookie: String): StreamFilterPrefs
    suspend fun putStreamFilter(baseUrl: String, sessionCookie: String, prefs: StreamFilterPrefs)
    suspend fun capture(baseUrl: String, sessionCookie: String, request: UploaderCaptureRequest): UploaderCaptureResponse
    suspend fun login(baseUrl: String, password: String): String
    suspend fun getSdillejStreams(baseUrl: String, sessionCookie: String, mediaType: String, imdbId: String, title: String, titleCs: String, year: Int? = null, season: Int? = null, episode: Int? = null): List<UploaderStream>
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
}
