package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderService
import com.github.jankoran90.showlyfin.data.uploader.model.AudiobookUploadResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * DROPSHIP F2 — nahrání audioknihy na uploader backend (`POST /api/audiobook/upload`).
 *
 * URL a session cookie čteme ze sdílených `traktPreferences` (klíče `uploader_*`) — stejný vzor jako
 * [PodcastSourcesRepository] a [OpsRepository]. Volající (VM) dodá už hotové [MultipartBody.Part],
 * protože konstrukce z `Uri` potřebuje `ContentResolver` (Android Context) — to patří do VM, ne sem.
 *
 * `auto_match` posíláme jako text ("true"/"false"), FastAPI ho z Form() koercuje na bool.
 */
@Singleton
class AudiobookUploadRepository @Inject constructor(
    private val service: UploaderService,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private fun base(): String = prefs.getString("uploader_base_url", "").orEmpty().trimEnd('/')

    private fun cookie(): String = prefs.getString("uploader_session_cookie", "").orEmpty()
        .let { if (it.isNotBlank()) "session=$it" else "" }

    /** Je uploader nakonfigurovaný (base URL i cookie)? */
    val isUploaderConfigured: Boolean
        get() = prefs.getString("uploader_base_url", "").orEmpty().isNotBlank() &&
            prefs.getString("uploader_session_cookie", "").orEmpty().isNotBlank()

    /**
     * Nahraje audioknihu. [parts] = audio/archiv soubory, [libraryId] = cílová ABS knihovna,
     * [title]/[author] = volitelná metadata (null = backend detekuje z názvu), [autoMatch] = spustit
     * Audible enrich. Vrací response nebo vyhazuje výjimku (HTTP/síť) — VM ji přeloží do state.error.
     */
    suspend fun upload(
        parts: List<MultipartBody.Part>,
        libraryId: String,
        title: String?,
        author: String?,
        autoMatch: Boolean,
        coverPart: MultipartBody.Part? = null,
    ): AudiobookUploadResponse {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/upload"
        val libraryPart = libraryId.toRequestBodyForm()
        val titlePart = title?.takeIf { it.isNotBlank() }?.toRequestBodyForm()
        val authorPart = author?.takeIf { it.isNotBlank() }?.toRequestBodyForm()
        val autoMatchPart = autoMatch.toString().toRequestBodyForm()

        val resp = service.uploadAudiobook(url, cookie(), parts, libraryPart, titlePart, authorPart, autoMatchPart, coverPart)
        if (!resp.isSuccessful) {
            val msg = runCatching { resp.errorBody()?.string() }.getOrNull()?.take(200).orEmpty()
            throw HttpException(resp).also { Timber.w(it, "[DROPSHIP] upload HTTP ${resp.code()}: $msg") }
        }
        return resp.body() ?: error("Server nevrátil odpověď.")
    }

    private fun String.toRequestBodyForm(): RequestBody =
        this.toRequestBody(MultipartBody.FORM)

    /**
     * SLOVO-UPLOAD-NET krok 1/4 — založí chunkovanou upload session, vrátí session_id.
     * [addedBy] = profileUuid nahrávajícího profilu (PROFIL 2026-08-16) — backend si ho uloží k
     * hotové audioknize, appka podle něj pak řídí per-profil viditelnost + sdílení.
     */
    suspend fun startUploadSession(libraryId: String, title: String?, author: String?, autoMatch: Boolean, addedBy: String?): String {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/upload/start"
        val resp = service.startUploadSession(
            url, cookie(),
            libraryId.toRequestBodyForm(),
            title?.takeIf { it.isNotBlank() }?.toRequestBodyForm(),
            author?.takeIf { it.isNotBlank() }?.toRequestBodyForm(),
            autoMatch.toString().toRequestBodyForm(),
            addedBy?.takeIf { it.isNotBlank() }?.toRequestBodyForm(),
        )
        if (!resp.isSuccessful) {
            throw HttpException(resp).also { Timber.w(it, "[DROPSHIP] upload/start HTTP ${resp.code()}") }
        }
        return resp.body()?.sessionId ?: error("Server nevrátil session_id.")
    }

    /** SLOVO-UPLOAD-NET krok 2/4 — pošle jeden kousek souboru (bezpečné opakovat při chybě). */
    suspend fun uploadChunk(
        sessionId: String,
        fileIndex: Int,
        chunkIndex: Int,
        totalChunks: Int,
        filename: String,
        bytes: ByteArray,
    ) {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/upload/chunk"
        val chunkPart = MultipartBody.Part.createFormData(
            "chunk", "chunk", bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
        )
        val resp = service.uploadChunk(
            url, cookie(),
            sessionId.toRequestBodyForm(),
            fileIndex.toString().toRequestBodyForm(),
            chunkIndex.toString().toRequestBodyForm(),
            totalChunks.toString().toRequestBodyForm(),
            filename.toRequestBodyForm(),
            chunkPart,
        )
        if (!resp.isSuccessful) {
            throw HttpException(resp).also { Timber.w(it, "[DROPSHIP] upload/chunk $fileIndex/$chunkIndex HTTP ${resp.code()}") }
        }
    }

    /** SLOVO-UPLOAD-NET krok 3/4 — po odeslání všech kousků souboru je nechá server slepit. */
    suspend fun finishUploadFile(sessionId: String, fileIndex: Int) {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/upload/finish_file"
        val resp = service.finishUploadFile(url, cookie(), sessionId.toRequestBodyForm(), fileIndex.toString().toRequestBodyForm())
        if (!resp.isSuccessful) {
            throw HttpException(resp).also { Timber.w(it, "[DROPSHIP] upload/finish_file $fileIndex HTTP ${resp.code()}") }
        }
    }

    /** SLOVO-UPLOAD-NET krok 4/4 — spustí ingest pipeline (stejná odpověď jako jednorázový upload). */
    suspend fun finalizeUpload(sessionId: String, coverPart: MultipartBody.Part?): AudiobookUploadResponse {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/upload/finalize"
        val resp = service.finalizeUpload(url, cookie(), sessionId.toRequestBodyForm(), coverPart)
        if (!resp.isSuccessful) {
            val msg = runCatching { resp.errorBody()?.string() }.getOrNull()?.take(200).orEmpty()
            throw HttpException(resp).also { Timber.w(it, "[DROPSHIP] upload/finalize HTTP ${resp.code()}: $msg") }
        }
        return resp.body() ?: error("Server nevrátil odpověď.")
    }

    /** Zruší rozdělanou session (chyba/zrušení uživatelem) — uklidí dočasné soubory na serveru. */
    suspend fun cancelUploadSession(sessionId: String) {
        runCatching {
            val b = base()
            if (b.isBlank()) return
            service.cancelUploadSession("$b/api/audiobook/upload/cancel", cookie(), sessionId.toRequestBodyForm())
        }.onFailure { Timber.w(it, "[DROPSHIP] upload/cancel selhal (neškodné, TTL úklid to dořeší)") }
    }

    /** DROPSHIP F2c — dohledání CZ metadata+cover pro existující ABS item (z editace knihy). */
    suspend fun match(itemId: String, title: String, author: String?): com.github.jankoran90.showlyfin.data.uploader.model.AudiobookMatchResponse {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/match"
        val authorPart = author?.takeIf { it.isNotBlank() }?.toRequestBodyForm()
        val resp = service.matchAudiobook(url, cookie(), itemId.toRequestBodyForm(), title.toRequestBodyForm(), authorPart)
        if (!resp.isSuccessful) {
            val msg = runCatching { resp.errorBody()?.string() }.getOrNull()?.take(200).orEmpty()
            throw HttpException(resp).also { Timber.w(it, "[DROPSHIP] match HTTP ${resp.code()}: $msg") }
        }
        return resp.body() ?: error("Server nevrátil odpověď.")
    }

    /** EXCISE (2026-08-19) — soft-smazání (archivace na serveru, ne fyzické mazání). */
    suspend fun deleteAudiobook(itemId: String, requestedBy: String?): com.github.jankoran90.showlyfin.data.uploader.model.AudiobookDeleteResponse {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/delete"
        val byPart = requestedBy?.takeIf { it.isNotBlank() }?.toRequestBodyForm()
        val resp = service.deleteAudiobook(url, cookie(), itemId.toRequestBodyForm(), byPart)
        if (!resp.isSuccessful) {
            val msg = runCatching { resp.errorBody()?.string() }.getOrNull()?.take(200).orEmpty()
            throw HttpException(resp).also { Timber.w(it, "[EXCISE] delete HTTP ${resp.code()}: $msg") }
        }
        return resp.body() ?: error("Server nevrátil odpověď.")
    }

    /** EXCISE (2026-08-19) — víc kandidátů obálky (CZ+Audible) k ručnímu výběru, nic se nepatchuje. */
    suspend fun searchCovers(title: String, author: String?): List<com.github.jankoran90.showlyfin.data.uploader.model.AudiobookCoverCandidate> {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/cover_search"
        val authorPart = author?.takeIf { it.isNotBlank() }?.toRequestBodyForm()
        val resp = service.searchAudiobookCovers(url, cookie(), title.toRequestBodyForm(), authorPart)
        if (!resp.isSuccessful) {
            val msg = runCatching { resp.errorBody()?.string() }.getOrNull()?.take(200).orEmpty()
            throw HttpException(resp).also { Timber.w(it, "[EXCISE] cover_search HTTP ${resp.code()}: $msg") }
        }
        return resp.body()?.candidates ?: emptyList()
    }

    /** EXCISE (2026-08-19) — aplikuje jednu z obálek vrácených [searchCovers]. */
    suspend fun setCover(itemId: String, coverUrl: String) {
        val b = base().ifBlank { error("Uploader server není nastaven v Nastavení.") }
        val url = "$b/api/audiobook/set_cover"
        val resp = service.setAudiobookCover(url, cookie(), itemId.toRequestBodyForm(), coverUrl.toRequestBodyForm())
        if (!resp.isSuccessful) {
            val msg = runCatching { resp.errorBody()?.string() }.getOrNull()?.take(200).orEmpty()
            throw HttpException(resp).also { Timber.w(it, "[EXCISE] set_cover HTTP ${resp.code()}: $msg") }
        }
    }
}
