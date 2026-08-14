package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderService
import com.github.jankoran90.showlyfin.data.uploader.model.AudiobookUploadResponse
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
}
