package com.github.jankoran90.showlyfin.data.uploader.api

import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

internal class UploaderApi(
    private val service: UploaderService,
) : UploaderRemoteDataSource {

    override suspend fun getStreams(baseUrl: String, sessionCookie: String, mediaType: String, imdbId: String, season: Int?, episode: Int?, strict: Boolean?): List<UploaderStream> {
        val base = baseUrl.trimEnd('/')
        var url = "$base/api/stremio/streams/$mediaType/$imdbId"
        val params = buildList {
            if (season != null && episode != null) { add("season=$season"); add("episode=$episode") }
            if (strict != null) add("strict=$strict")
        }
        if (params.isNotEmpty()) url += "?" + params.joinToString("&")
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getStreams(url, cookie).streams
    }

    override suspend fun resolveStream(baseUrl: String, sessionCookie: String, infoHash: String, fileIdx: Int): String {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.resolveStream("$base/api/stremio/resolve", cookie, UploaderResolveRequest(infoHash = infoHash, fileIdx = fileIdx))
        return resp.url ?: throw IllegalStateException(resp.error ?: "RD resolve nevrátil URL")
    }

    override suspend fun resolveCometStream(baseUrl: String, sessionCookie: String, cometPath: String): String {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.resolveStream("$base/api/stremio/resolve", cookie, UploaderResolveRequest(cometPath = cometPath))
        return resp.url ?: throw IllegalStateException(resp.error ?: "RD resolve nevrátil URL")
    }

    override suspend fun rdAdd(baseUrl: String, sessionCookie: String, infoHash: String?, fileIdx: Int, cometPath: String?): UploaderRdAddResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.rdAdd("$base/api/stremio/rd/add", cookie, UploaderRdAddRequest(infoHash = infoHash, fileIdx = fileIdx, cometPath = cometPath))
    }

    override suspend fun rdProgress(baseUrl: String, sessionCookie: String, torrentId: String, fileIdx: Int): UploaderRdProgressResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.rdProgress("$base/api/stremio/rd/progress/$torrentId?fileIdx=$fileIdx", cookie)
    }

    override suspend fun rdSearch(baseUrl: String, sessionCookie: String, title: String, year: Int?): List<UploaderStream> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val q = buildString {
            append("?title=${java.net.URLEncoder.encode(title, "UTF-8")}")
            if (year != null) append("&year=$year")
        }
        return service.rdSearch("$base/api/stremio/rd/search$q", cookie).streams
    }

    override suspend fun rdMatch(baseUrl: String, sessionCookie: String, items: List<RdMatchItem>): List<Int> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.rdMatch("$base/api/stremio/rd/match", cookie, RdMatchRequest(items)).matched
    }

    override suspend fun getStreamFilter(baseUrl: String, sessionCookie: String): StreamFilterPrefs {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getStreamFilter("$base/api/stremio/prefs/stream-filter", cookie)
    }

    override suspend fun putStreamFilter(baseUrl: String, sessionCookie: String, prefs: StreamFilterPrefs) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.putStreamFilter("$base/api/stremio/prefs/stream-filter", cookie, prefs)
    }

    override suspend fun capture(baseUrl: String, sessionCookie: String, request: UploaderCaptureRequest): UploaderCaptureResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.capture("$base/api/stremio/capture", cookie, request)
    }

    override suspend fun login(baseUrl: String, password: String): String {
        val base = baseUrl.trimEnd('/')
        val response = service.login("$base/api/login", UploaderLoginRequest(password))
        if (!response.isSuccessful) throw HttpException(response)
        val setCookie = response.headers()["Set-Cookie"]
            ?: throw IllegalStateException("Login failed: missing Set-Cookie header")
        return setCookie.substringAfter("session=").substringBefore(";")
    }

    override suspend fun getSdillejStreams(baseUrl: String, sessionCookie: String, mediaType: String, imdbId: String, title: String, titleCs: String, year: Int?, season: Int?, episode: Int?): List<UploaderStream> {
        val base = baseUrl.trimEnd('/')
        val params = buildString {
            append("?title=${java.net.URLEncoder.encode(title, "UTF-8")}")
            if (titleCs.isNotBlank()) append("&title_cs=${java.net.URLEncoder.encode(titleCs, "UTF-8")}")
            if (year != null) append("&year=$year")
            if (season != null && episode != null) append("&season=$season&episode=$episode")
        }
        val url = "$base/api/sdilej/search/$mediaType/$imdbId$params"
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getStreams(url, cookie).streams
    }

    override suspend fun captureSdillej(baseUrl: String, sessionCookie: String, request: UploaderCaptureRequest): UploaderCaptureResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.capture("$base/api/sdilej/capture", cookie, request)
    }

    // TMM Pipeline

    override suspend fun getTmmSession(baseUrl: String, sessionCookie: String, sid: String): TmmSession {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getTmmSession("$base/api/tmm/session/$sid", cookie)
    }

    override suspend fun tmmSearch(baseUrl: String, sessionCookie: String, sid: String, fid: String, query: String, year: Int?): List<TmmCandidate> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.tmmSearch("$base/api/tmm/session/$sid/file/$fid/search", cookie, TmmSearchRequest(query, year)).candidates
    }

    override suspend fun tmmConfirm(baseUrl: String, sessionCookie: String, sid: String, fid: String, tmdbId: Int): TmmMatch {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.tmmConfirm("$base/api/tmm/session/$sid/file/$fid/confirm", cookie, TmmConfirmRequest(tmdbId)).match
            ?: throw IllegalStateException("Confirm response missing match")
    }

    override suspend fun tmmProcess(baseUrl: String, sessionCookie: String, sid: String): TmmProcessResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.tmmProcess("$base/api/tmm/session/$sid/process", cookie, TmmProcessRequest())
    }

    override suspend fun tmmMove(baseUrl: String, sessionCookie: String, sid: String, library: String): TmmMoveResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.tmmMove("$base/api/tmm/session/$sid/move", cookie, TmmMoveRequest(library))
    }

    // Libraries

    override suspend fun getLibraries(baseUrl: String, sessionCookie: String): List<String> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getLibraries("$base/api/libraries", cookie)
    }

    override suspend fun scanLibrary(baseUrl: String, sessionCookie: String, lib: String): List<LibraryItem> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val libEnc = java.net.URLEncoder.encode(lib, "UTF-8")
        return service.scanLibrary("$base/api/storagebox/scan?lib=$libEnc", cookie).items
    }

    override suspend fun updateUserdata(baseUrl: String, sessionCookie: String, library: String, folder: String, watched: Boolean?, favorite: Boolean?, jfItemId: String?) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.updateUserdata("$base/api/storagebox/userdata", cookie, StorageboxUserdataRequest(library, folder, watched, favorite, jfItemId))
    }

    // Probe + Remux

    override suspend fun probeStreams(baseUrl: String, sessionCookie: String, library: String, folder: String): ProbeResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val libEnc = java.net.URLEncoder.encode(library, "UTF-8")
        val folderEnc = java.net.URLEncoder.encode(folder, "UTF-8")
        return service.probeStreams("$base/api/storagebox/probe?library=$libEnc&folder=$folderEnc", cookie)
    }

    override suspend fun startRemux(baseUrl: String, sessionCookie: String, library: String, folder: String, keepIndices: List<Int>, totalDurMs: Long): String {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.startRemux("$base/api/storagebox/remux", cookie, RemuxStartRequest(library, folder, keepIndices, totalDurMs)).jobId
    }

    override suspend fun getRemuxStatus(baseUrl: String, sessionCookie: String, jobId: String): RemuxJob {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getRemuxStatus("$base/api/storagebox/remux/$jobId", cookie)
    }

    // Smart Pair

    override suspend fun startPair(baseUrl: String, sessionCookie: String, videoFid: String, audioFid: String, sid: String): PairResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.startPair("$base/api/pair", cookie, PairRequest(videoFid, audioFid, sid))
    }

    override suspend fun getPairStatus(baseUrl: String, sessionCookie: String, jobId: String): PairJob {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getPairStatus("$base/api/pair/$jobId", cookie)
    }

    override suspend fun selectPairTracks(baseUrl: String, sessionCookie: String, jobId: String, videoIndices: List<Int>, audioIndices: List<Int>, overrideOffsetS: Double?, applyOverride: Boolean, applyAtempo: Boolean) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.selectPairTracks("$base/api/pair/$jobId/select-tracks", cookie, PairMergeRequest(videoIndices, audioIndices, overrideOffsetS, applyOverride, applyAtempo))
    }

    override suspend fun cancelPair(baseUrl: String, sessionCookie: String, jobId: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.cancelPair("$base/api/pair/$jobId", cookie)
    }

    override suspend fun getPairPreviewBytes(baseUrl: String, sessionCookie: String, jobId: String, t: Double, dur: Int, offsetS: Double): okhttp3.ResponseBody {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getPairPreview("$base/api/pair/$jobId/preview?t=$t&dur=$dur&offset_s=$offsetS", cookie)
    }

    // Android log upload

    override suspend fun uploadLog(baseUrl: String, sessionCookie: String, logBytes: ByteArray) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val body = logBytes.toRequestBody("text/plain; charset=utf-8".toMediaType())
        service.uploadLog("$base/api/logs", cookie, body)
    }

    // Remux History

    override suspend fun getRemuxHistory(baseUrl: String, sessionCookie: String): List<RemuxSession> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getRemuxHistory("$base/api/remux/history", cookie).sessions
    }

    override suspend fun getRemuxSessionDetail(baseUrl: String, sessionCookie: String, rsid: String): RemuxSession {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getRemuxSession("$base/api/remux/session/$rsid", cookie)
    }

    override suspend fun deleteRemuxSession(baseUrl: String, sessionCookie: String, rsid: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.deleteRemuxSession("$base/api/remux/session/$rsid", cookie)
    }

    override suspend fun reDetectRemuxSession(baseUrl: String, sessionCookie: String, rsid: String): RemuxReDetectResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.reDetectRemuxSession("$base/api/remux/session/$rsid/re-detect", cookie)
    }

    // TMDB detail

    override suspend fun getTmdbDetail(baseUrl: String, sessionCookie: String, tmdbId: Int, mediaType: String): TmdbDetail {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val endpoint = if (mediaType == "tv") "tv" else "movie"
        return service.getTmdbDetail("$base/api/tmdb/$endpoint/$tmdbId", cookie)
    }

    // Storagebox detail operations

    override suspend fun storageboxSearch(baseUrl: String, sessionCookie: String, library: String, folder: String, query: String, mediaType: String): List<TmmCandidate> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.storageboxSearch("$base/api/storagebox/search", cookie, StorageboxSearchRequest(library, folder, query, mediaType = mediaType)).candidates
    }

    override suspend fun storageboxConfirm(baseUrl: String, sessionCookie: String, library: String, folder: String, tmdbId: Int, mediaType: String, posterUrl: String?, backdropUrl: String?, logoUrl: String?, jfItemId: String?): StorageboxConfirmResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.storageboxConfirm("$base/api/storagebox/confirm", cookie, StorageboxConfirmRequest(library, folder, tmdbId, mediaType, posterUrl, backdropUrl, logoUrl, jfItemId))
    }

    override suspend fun setArtwork(baseUrl: String, sessionCookie: String, library: String, folder: String, type: String, url: String, jfItemId: String?) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.setArtwork("$base/api/storagebox/artwork", cookie, StorageboxArtworkRequest(library, folder, type, url, jfItemId))
    }

    override suspend fun setCollection(baseUrl: String, sessionCookie: String, library: String, folder: String, collection: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.setCollection("$base/api/storagebox/set-collection", cookie, StorageboxCollectionRequest(library, folder, collection))
    }

    override suspend fun getCollections(baseUrl: String, sessionCookie: String, library: String): List<String> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val libEnc = java.net.URLEncoder.encode(library, "UTF-8")
        return service.getCollections("$base/api/storagebox/collections?library=$libEnc", cookie).collections
    }

    override suspend fun setDateAdded(baseUrl: String, sessionCookie: String, library: String, folder: String, date: String, jfItemId: String?) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.setDateAdded("$base/api/storagebox/dateadded", cookie, StorageboxDateAddedRequest(library, folder, date, jfItemId))
    }

    override suspend fun deleteFolder(baseUrl: String, sessionCookie: String, library: String, folder: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val libEnc = java.net.URLEncoder.encode(library, "UTF-8")
        val nameEnc = java.net.URLEncoder.encode(folder, "UTF-8")
        service.deleteFolder("$base/api/storagebox/folder?library=$libEnc&name=$nameEnc", cookie)
    }

    // Fix Audio Delay

    override suspend fun startFixAudioDelay(baseUrl: String, sessionCookie: String, library: String, folder: String, offsetS: Double): String {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.startFixAudioDelay("$base/api/storagebox/fix-audio-delay", cookie, FixAudioDelayRequest(library, folder, offsetS)).jobId
    }

    override suspend fun getFixAudioDelayStatus(baseUrl: String, sessionCookie: String, jobId: String): FixAudioDelayJob {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getFixAudioDelayStatus("$base/api/storagebox/fix-audio-delay/$jobId", cookie)
    }

    override suspend fun deleteFixAudioDelayBackup(baseUrl: String, sessionCookie: String, jobId: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        service.deleteFixAudioDelayBackup("$base/api/storagebox/fix-audio-delay/$jobId/backup", cookie)
    }

    // Library presence

    override suspend fun checkLibraryPresence(baseUrl: String, sessionCookie: String, imdbIds: List<String>): Map<String, Boolean> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val ids = java.net.URLEncoder.encode(imdbIds.joinToString(","), "UTF-8")
        return service.checkLibraryPresence("$base/api/library/check?imdb_ids=$ids", cookie)
    }
}
