package com.github.jankoran90.showlyfin.data.uploader.api

import com.github.jankoran90.showlyfin.data.uploader.model.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface UploaderService {
    @GET suspend fun getStreams(@Url url: String, @Header("Cookie") cookie: String): UploaderStreamsResponse
    @POST suspend fun resolveStream(@Url url: String, @Header("Cookie") cookie: String, @Body request: UploaderResolveRequest): UploaderResolveResponse
    @POST suspend fun rdAdd(@Url url: String, @Header("Cookie") cookie: String, @Body request: UploaderRdAddRequest): UploaderRdAddResponse
    @GET suspend fun rdProgress(@Url url: String, @Header("Cookie") cookie: String): UploaderRdProgressResponse
    @GET suspend fun rdSearch(@Url url: String, @Header("Cookie") cookie: String): UploaderStreamsResponse
    @POST suspend fun rdCleanup(@Url url: String, @Header("Cookie") cookie: String, @Body request: UploaderRdCleanupRequest): UploaderRdCleanupResponse
    // Plan LEDGER (SHW-43) — správa RD účtu z Nastavení
    @GET suspend fun rdList(@Url url: String, @Header("Cookie") cookie: String): UploaderRdListResponse
    @POST suspend fun rdDelete(@Url url: String, @Header("Cookie") cookie: String, @Body request: UploaderRdDeleteRequest): UploaderRdCleanupResponse
    @POST suspend fun rdPurgeOrphans(@Url url: String, @Header("Cookie") cookie: String, @Body request: UploaderRdDeleteRequest = UploaderRdDeleteRequest()): UploaderRdCleanupResponse
    @POST suspend fun rdMatch(@Url url: String, @Header("Cookie") cookie: String, @Body request: RdMatchRequest): RdMatchResponse
    @GET suspend fun getRdLibrary(@Url url: String, @Header("Cookie") cookie: String): RdLibraryResponse
    @GET suspend fun getStreamFilter(@Url url: String, @Header("Cookie") cookie: String): StreamFilterPrefs
    @PUT suspend fun putStreamFilter(@Url url: String, @Header("Cookie") cookie: String, @Body prefs: StreamFilterPrefs): Response<ResponseBody>
    @POST suspend fun capture(@Url url: String, @Header("Cookie") cookie: String, @Body request: UploaderCaptureRequest): UploaderCaptureResponse
    @POST suspend fun login(@Url url: String, @Body request: UploaderLoginRequest): Response<ResponseBody>

    // Plan PROFILES Fáze 2 — config balík per profil (raw JSON přes ResponseBody/RequestBody)
    @GET suspend fun getProfileConfig(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
    @PUT suspend fun putProfileConfig(@Url url: String, @Header("Cookie") cookie: String, @Body body: RequestBody): Response<ResponseBody>
    @PUT suspend fun putProfile(@Url url: String, @Header("Cookie") cookie: String, @Body request: ProfileMetaRequest): Response<ResponseBody>
    // Plan WARDEN W3c — šablony + profilová meta (raw JSON pole přes ResponseBody)
    @GET suspend fun getTemplates(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
    @GET suspend fun getProfilesMeta(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
    @PUT suspend fun putTemplate(@Url url: String, @Header("Cookie") cookie: String, @Body body: RequestBody): Response<ResponseBody>
    @DELETE suspend fun deleteTemplate(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
    // Plan HELM — admin parity (raw JSON přes ResponseBody)
    @GET suspend fun getJellyfinLibraries(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
    @GET suspend fun getTmdbGenres(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
    @GET suspend fun exportProfiles(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
    @POST suspend fun importProfiles(@Url url: String, @Header("Cookie") cookie: String, @Body body: RequestBody): Response<ResponseBody>

    // Titulky.com CZ titulky (Fáze E)
    @GET suspend fun getSubtitles(@Url url: String, @Header("Cookie") cookie: String): SubtitlesResponse
    @GET @Streaming suspend fun downloadSubtitle(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
    // Plan LINGUA Fáze 2 — async AI překlad EN→CS (start + poll status)
    @POST suspend fun startSubtitleTranslate(@Url url: String, @Header("Cookie") cookie: String): SubtitleTranslateJob
    @GET suspend fun getSubtitleTranslateStatus(@Url url: String, @Header("Cookie") cookie: String): SubtitleTranslateJob

    // ČSFD popis + recenze (scrape na backendu, server zvládá Anubis)
    @GET suspend fun getCsfdPlot(@Url url: String, @Header("Cookie") cookie: String): CsfdPlotResponse
    @GET suspend fun getCsfdReviews(@Url url: String, @Header("Cookie") cookie: String): CsfdReviewsResponse
    @GET suspend fun getCsfdGallery(@Url url: String, @Header("Cookie") cookie: String): CsfdGalleryResponse

    // TMM Pipeline
    @GET suspend fun getTmmSession(@Url url: String, @Header("Cookie") cookie: String): TmmSession
    @POST suspend fun tmmSearch(@Url url: String, @Header("Cookie") cookie: String, @Body request: TmmSearchRequest): TmmSearchResponse
    @POST suspend fun tmmConfirm(@Url url: String, @Header("Cookie") cookie: String, @Body request: TmmConfirmRequest): TmmConfirmResponse
    @POST suspend fun tmmProcess(@Url url: String, @Header("Cookie") cookie: String, @Body request: TmmProcessRequest): TmmProcessResponse
    @POST suspend fun tmmMove(@Url url: String, @Header("Cookie") cookie: String, @Body request: TmmMoveRequest): TmmMoveResponse

    // Libraries
    @GET suspend fun getLibraries(@Url url: String, @Header("Cookie") cookie: String): List<String>
    @GET suspend fun scanLibrary(@Url url: String, @Header("Cookie") cookie: String): LibraryScanResponse
    @PATCH suspend fun updateUserdata(@Url url: String, @Header("Cookie") cookie: String, @Body request: StorageboxUserdataRequest): Any

    // Probe + Remux
    @GET suspend fun probeStreams(@Url url: String, @Header("Cookie") cookie: String): ProbeResponse
    @POST suspend fun startRemux(@Url url: String, @Header("Cookie") cookie: String, @Body request: RemuxStartRequest): RemuxStartResponse
    @GET suspend fun getRemuxStatus(@Url url: String, @Header("Cookie") cookie: String): RemuxJob

    // Smart Pair
    @POST suspend fun startPair(@Url url: String, @Header("Cookie") cookie: String, @Body request: PairRequest): PairResponse
    @GET suspend fun getPairStatus(@Url url: String, @Header("Cookie") cookie: String): PairJob
    @POST suspend fun selectPairTracks(@Url url: String, @Header("Cookie") cookie: String, @Body request: PairMergeRequest): Response<ResponseBody>
    @DELETE suspend fun cancelPair(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>

    // TMDB detail
    @GET suspend fun getTmdbDetail(@Url url: String, @Header("Cookie") cookie: String): TmdbDetail

    // Storagebox detail operations
    @POST suspend fun storageboxSearch(@Url url: String, @Header("Cookie") cookie: String, @Body request: StorageboxSearchRequest): TmmSearchResponse
    @POST suspend fun storageboxConfirm(@Url url: String, @Header("Cookie") cookie: String, @Body request: StorageboxConfirmRequest): StorageboxConfirmResponse
    @PATCH suspend fun setArtwork(@Url url: String, @Header("Cookie") cookie: String, @Body request: StorageboxArtworkRequest): Any
    @PATCH suspend fun setCollection(@Url url: String, @Header("Cookie") cookie: String, @Body request: StorageboxCollectionRequest): Any
    @GET suspend fun getCollections(@Url url: String, @Header("Cookie") cookie: String): StorageboxCollectionsResponse
    @GET suspend fun checkLibraryPresence(@Url url: String, @Header("Cookie") cookie: String): Map<String, Boolean>
    @PATCH suspend fun setDateAdded(@Url url: String, @Header("Cookie") cookie: String, @Body request: StorageboxDateAddedRequest): Any
    @DELETE suspend fun deleteFolder(@Url url: String, @Header("Cookie") cookie: String): Any

    // Fix Audio Delay
    @POST suspend fun startFixAudioDelay(@Url url: String, @Header("Cookie") cookie: String, @Body request: FixAudioDelayRequest): FixAudioDelayStartResponse
    @GET suspend fun getFixAudioDelayStatus(@Url url: String, @Header("Cookie") cookie: String): FixAudioDelayJob
    @DELETE suspend fun deleteFixAudioDelayBackup(@Url url: String, @Header("Cookie") cookie: String): Any

    // Smart Pair Preview
    @GET @Streaming suspend fun getPairPreview(@Url url: String, @Header("Cookie") cookie: String): ResponseBody

    // Android log upload
    @POST suspend fun uploadLog(@Url url: String, @Header("Cookie") cookie: String, @Body body: RequestBody): Any

    // Remux History
    @GET suspend fun getRemuxHistory(@Url url: String, @Header("Cookie") cookie: String): RemuxHistoryResponse
    @GET suspend fun getRemuxSession(@Url url: String, @Header("Cookie") cookie: String): RemuxSession
    @DELETE suspend fun deleteRemuxSession(@Url url: String, @Header("Cookie") cookie: String): Any
    @POST suspend fun reDetectRemuxSession(@Url url: String, @Header("Cookie") cookie: String): RemuxReDetectResponse

    // TUNER (SHW-62) — YouTube podcast feed (proxy na antenna; jen metadata)
    @GET suspend fun getYtFeed(@Url url: String, @Header("Cookie") cookie: String): YtChannelFeed
    // TUNER — pre-warm resolve cache (rychlejší start přehrávání nejnovějších epizod)
    @GET suspend fun getYtResolve(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>

    // PRESET (SHW-65) — dynamický správce zdrojů Poslechu (sdílený store + hledání + RSS feed)
    @GET suspend fun listSources(@Url url: String, @Header("Cookie") cookie: String): SourcesResponse
    @POST suspend fun addSource(@Url url: String, @Header("Cookie") cookie: String, @Body request: AddSourceRequest): SourcesResponse
    @DELETE suspend fun removeSource(@Url url: String, @Header("Cookie") cookie: String): SourcesResponse
    @GET suspend fun searchSources(@Url url: String, @Header("Cookie") cookie: String): SourceSearchResponse
    @GET suspend fun getRssFeed(@Url url: String, @Header("Cookie") cookie: String): RssFeed
}
