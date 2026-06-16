package com.github.jankoran90.showlyfin.data.uploader.api

import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.*
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.net.URLEncoder

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

    private fun UploaderResolveContext?.toQuality(): UploaderResolveQuality? =
        this?.let { if (it.resolution == null && it.sizeGB == null) null else UploaderResolveQuality(it.resolution, it.sizeGB) }

    // Plan WINNOW (item 1): HTTP 451 z backendu = DMCA → přehoď na doménovou výjimku, ať feature
    // vrstva pozná „blokováno" bez závislosti na retrofitu.
    private suspend fun <T> mapBlocked(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        if (e.code() == 451) throw StreamBlockedException() else throw e
    }

    override suspend fun getProbedStreams(baseUrl: String, sessionCookie: String, mediaType: String, imdbId: String, season: Int?, episode: Int?): List<UploaderStream> {
        val base = baseUrl.trimEnd('/')
        var url = "$base/api/stremio/streams_probe/$mediaType/$imdbId"
        if (season != null && episode != null) url += "?season=$season&episode=$episode"
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getStreams(url, cookie).streams
    }

    override suspend fun resolveStream(baseUrl: String, sessionCookie: String, infoHash: String, fileIdx: Int, ctx: UploaderResolveContext?): String {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = mapBlocked { service.resolveStream("$base/api/stremio/resolve", cookie, UploaderResolveRequest(
            infoHash = infoHash, fileIdx = fileIdx,
            imdb = ctx?.imdb, mediaType = ctx?.mediaType, season = ctx?.season, episode = ctx?.episode, quality = ctx.toQuality(),
        )) }
        return resp.url ?: throw IllegalStateException(resp.error ?: "RD resolve nevrátil URL")
    }

    override suspend fun resolveCometStream(baseUrl: String, sessionCookie: String, cometPath: String, ctx: UploaderResolveContext?): String {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = mapBlocked { service.resolveStream("$base/api/stremio/resolve", cookie, UploaderResolveRequest(
            cometPath = cometPath,
            imdb = ctx?.imdb, mediaType = ctx?.mediaType, season = ctx?.season, episode = ctx?.episode, quality = ctx.toQuality(),
        )) }
        return resp.url ?: throw IllegalStateException(resp.error ?: "RD resolve nevrátil URL")
    }

    override suspend fun rdAdd(baseUrl: String, sessionCookie: String, infoHash: String?, fileIdx: Int, cometPath: String?): UploaderRdAddResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return mapBlocked { service.rdAdd("$base/api/stremio/rd/add", cookie, UploaderRdAddRequest(infoHash = infoHash, fileIdx = fileIdx, cometPath = cometPath)) }
    }

    override suspend fun rdCleanup(baseUrl: String, sessionCookie: String, keepHash: String?, hashes: List<String>): Int {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.rdCleanup("$base/api/stremio/rd/cleanup", cookie, UploaderRdCleanupRequest(keep = keepHash, hashes = hashes)).deleted
    }

    override suspend fun rdList(baseUrl: String, sessionCookie: String, force: Boolean): List<UploaderRdSavedItem> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.rdList("$base/api/stremio/rd/list?force=$force", cookie).items
    }

    override suspend fun rdDelete(baseUrl: String, sessionCookie: String, hashes: List<String>): Int {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.rdDelete("$base/api/stremio/rd/delete", cookie, UploaderRdDeleteRequest(hashes = hashes)).deleted
    }

    override suspend fun rdPurgeOrphans(baseUrl: String, sessionCookie: String): Int {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.rdPurgeOrphans("$base/api/stremio/rd/purge-orphans", cookie).deleted
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

    override suspend fun getCsfdPlot(baseUrl: String, sessionCookie: String, csfdId: Long): CsfdPlotResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getCsfdPlot("$base/api/csfd/plot?csfd_id=$csfdId", cookie)
    }

    override suspend fun getCsfdReviews(baseUrl: String, sessionCookie: String, csfdId: Long): List<CsfdReviewItem> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getCsfdReviews("$base/api/csfd/reviews?csfd_id=$csfdId", cookie).reviews
    }

    override suspend fun getCsfdGallery(baseUrl: String, sessionCookie: String, csfdId: Long): List<String> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getCsfdGallery("$base/api/csfd/gallery?csfd_id=$csfdId", cookie).urls
    }

    override suspend fun rdMatch(baseUrl: String, sessionCookie: String, items: List<RdMatchItem>): List<Int> {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.rdMatch("$base/api/stremio/rd/match", cookie, RdMatchRequest(items)).matched
    }

    override suspend fun getRdLibrary(baseUrl: String, sessionCookie: String): RdLibraryResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getRdLibrary("$base/api/stremio/rd/library", cookie)
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

    // Plan PROFILES Fáze 2 — config balík per profil (raw JSON)

    override suspend fun getProfileConfig(baseUrl: String, sessionCookie: String, key: String): String? {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.getProfileConfig("$base/api/profiles/${enc(key)}/config", cookie)
        return if (resp.isSuccessful) resp.body()?.string() else null
    }

    override suspend fun putProfileConfig(baseUrl: String, sessionCookie: String, key: String, json: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val resp = service.putProfileConfig("$base/api/profiles/${enc(key)}/config", cookie, body)
        if (!resp.isSuccessful) throw HttpException(resp)
    }

    override suspend fun putProfile(baseUrl: String, sessionCookie: String, key: String, name: String, isAdmin: Boolean, jellyfinUserId: String, templateUuid: String?, loginPinHash: String?) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.putProfile("$base/api/profiles/${enc(key)}", cookie, ProfileMetaRequest(name, isAdmin, jellyfinUserId, templateUuid, loginPinHash))
        if (!resp.isSuccessful) throw HttpException(resp)
    }

    override suspend fun putTemplate(baseUrl: String, sessionCookie: String, uuid: String, name: String, ageRating: String?, configJson: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        // config musí jít jako JSON objekt (backend ho Fernet-šifruje), ne string → vlož raw configJson.
        val obj = JsonObject().apply {
            addProperty("name", name)
            if (ageRating != null) addProperty("ageRating", ageRating) else add("ageRating", JsonNull.INSTANCE)
            add("config", runCatching { JsonParser.parseString(configJson) }.getOrDefault(JsonObject()))
        }
        val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val resp = service.putTemplate("$base/api/templates/${enc(uuid)}", cookie, body)
        if (!resp.isSuccessful) throw HttpException(resp)
    }

    override suspend fun deleteTemplate(baseUrl: String, sessionCookie: String, uuid: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.deleteTemplate("$base/api/templates/${enc(uuid)}", cookie)
        if (!resp.isSuccessful) throw HttpException(resp)
    }

    // Plan WARDEN W3c — raw JSON pole (parsuje gateway přes Gson JsonParser)

    override suspend fun getTemplates(baseUrl: String, sessionCookie: String): String? {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.getTemplates("$base/api/templates", cookie)
        return if (resp.isSuccessful) resp.body()?.string() else null
    }

    override suspend fun getProfilesMeta(baseUrl: String, sessionCookie: String): String? {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.getProfilesMeta("$base/api/profiles", cookie)
        return if (resp.isSuccessful) resp.body()?.string() else null
    }

    // Plan HELM — admin parity (raw JSON parsuje gateway přes Gson)

    override suspend fun getJellyfinLibraries(baseUrl: String, sessionCookie: String, userId: String): String? {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val url = "$base/api/jellyfin/libraries" + if (userId.isNotBlank()) "?user_id=${enc(userId)}" else ""
        val resp = service.getJellyfinLibraries(url, cookie)
        return if (resp.isSuccessful) resp.body()?.string() else null
    }

    override suspend fun getTmdbGenres(baseUrl: String, sessionCookie: String): String? {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.getTmdbGenres("$base/api/tmdb/genres", cookie)
        return if (resp.isSuccessful) resp.body()?.string() else null
    }

    override suspend fun exportProfiles(baseUrl: String, sessionCookie: String): String? {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val resp = service.exportProfiles("$base/api/profiles/export", cookie)
        return if (resp.isSuccessful) resp.body()?.string() else null
    }

    override suspend fun importProfiles(baseUrl: String, sessionCookie: String, json: String): Boolean {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val resp = service.importProfiles("$base/api/profiles/import", cookie, body)
        return resp.isSuccessful
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

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    override suspend fun getSubtitles(
        baseUrl: String, sessionCookie: String, imdbId: String,
        title: String, origTitle: String, year: Int?,
        season: Int?, episode: Int?, release: String?, fps: Double?,
    ): SubtitlesResponse {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val params = buildList {
            add("lang=cs")
            if (title.isNotBlank()) add("title=${enc(title)}")
            if (origTitle.isNotBlank()) add("origTitle=${enc(origTitle)}")
            if (year != null) add("year=$year")
            if (season != null && episode != null) { add("season=$season"); add("episode=$episode") }
            if (!release.isNullOrBlank()) add("release=${enc(release)}")
            if (fps != null && fps > 0.0) add("fps=$fps")
        }
        // Prázdné imdb (cast z doporučení, kde se imdb dohledá z TMDB až později) → placeholder, ať
        // route `/api/subtitles/{imdb}` matchne; backend pak hledá podle title/origTitle/year (any_imdb=false).
        val imdbSeg = imdbId.takeIf { it.isNotBlank() } ?: "_"
        val url = "$base/api/subtitles/$imdbSeg?" + params.joinToString("&")
        return service.getSubtitles(url, cookie)
    }

    override suspend fun downloadSubtitle(
        baseUrl: String, sessionCookie: String, titulkyId: String,
        season: Int?, episode: Int?, runtime: Int?,
    ): SubtitleDownload {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val params = buildList {
            if (season != null && episode != null) { add("season=$season"); add("episode=$episode") }
            if (runtime != null && runtime > 0) add("runtime=$runtime")
        }
        var url = "$base/api/subtitles/download/$titulkyId"
        if (params.isNotEmpty()) url += "?" + params.joinToString("&")
        val resp = service.downloadSubtitle(url, cookie)
        if (!resp.isSuccessful) throw HttpException(resp)
        val body = resp.body() ?: throw IllegalStateException("Stažení titulků nevrátilo data")
        val lastTs = resp.headers()["X-Sub-LastTs"]?.toIntOrNull() ?: 0
        val runtimeOk = resp.headers()["X-Sub-RuntimeOk"] ?: "-"
        return SubtitleDownload(bytes = body.bytes(), lastTsSec = lastTs, runtimeOk = runtimeOk)
    }

    override suspend fun startSubtitleTranslate(
        baseUrl: String, sessionCookie: String, imdbId: String, season: Int?, episode: Int?,
    ): SubtitleTranslateJob {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val params = buildList {
            if (season != null && episode != null) { add("season=$season"); add("episode=$episode") }
        }
        var url = "$base/api/subtitles/translate/$imdbId"
        if (params.isNotEmpty()) url += "?" + params.joinToString("&")
        return service.startSubtitleTranslate(url, cookie)
    }

    override suspend fun getSubtitleTranslateStatus(
        baseUrl: String, sessionCookie: String, jobId: String,
    ): SubtitleTranslateJob {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        return service.getSubtitleTranslateStatus("$base/api/subtitles/translate/status/$jobId", cookie)
    }

    // TUNER (SHW-62) — YouTube podcast streaming
    override suspend fun getYtFeed(baseUrl: String, sessionCookie: String, channel: String, limit: Int): YtChannelFeed {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        val ch = URLEncoder.encode(channel, "UTF-8")
        return service.getYtFeed("$base/api/yt/feed?channel=$ch&limit=$limit", cookie)
    }

    override fun ytStreamUrl(baseUrl: String, sessionCookie: String, videoId: String, kind: String): String {
        val base = baseUrl.trimEnd('/')
        val key = URLEncoder.encode(sessionCookie, "UTF-8")
        return "$base/api/yt/stream/$videoId?kind=$kind&key=$key"
    }

    override suspend fun warmYt(baseUrl: String, sessionCookie: String, videoId: String, kind: String) {
        val base = baseUrl.trimEnd('/')
        val cookie = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""
        runCatching { service.getYtResolve("$base/api/yt/resolve?video_id=$videoId&kind=$kind", cookie) }
    }

    // PRESET (SHW-65) — správce zdrojů Poslechu (sdílený store + hledání + RSS feed)
    private fun cookieOf(sessionCookie: String) = if (sessionCookie.isNotBlank()) "session=$sessionCookie" else ""

    override suspend fun listSources(baseUrl: String, sessionCookie: String): List<PodcastSource> =
        service.listSources("${baseUrl.trimEnd('/')}/api/sources", cookieOf(sessionCookie)).sources

    override suspend fun addSource(baseUrl: String, sessionCookie: String, type: String, ref: String, title: String, thumbnail: String?): List<PodcastSource> =
        service.addSource("${baseUrl.trimEnd('/')}/api/sources", cookieOf(sessionCookie), AddSourceRequest(type, ref, title, thumbnail)).sources

    override suspend fun removeSource(baseUrl: String, sessionCookie: String, id: String): List<PodcastSource> =
        service.removeSource("${baseUrl.trimEnd('/')}/api/sources/${URLEncoder.encode(id, "UTF-8")}", cookieOf(sessionCookie)).sources

    override suspend fun searchSources(baseUrl: String, sessionCookie: String, query: String, type: String, limit: Int): List<SourceSearchResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        return service.searchSources("${baseUrl.trimEnd('/')}/api/sources/search?q=$q&type=$type&limit=$limit", cookieOf(sessionCookie)).results
    }

    override suspend fun getRssFeed(baseUrl: String, sessionCookie: String, feedUrl: String, limit: Int): RssFeed {
        val u = URLEncoder.encode(feedUrl, "UTF-8")
        return service.getRssFeed("${baseUrl.trimEnd('/')}/api/rss/feed?url=$u&limit=$limit", cookieOf(sessionCookie))
    }
}
