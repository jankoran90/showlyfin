package com.github.jankoran90.showlyfin.data.uploader.audioteka

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderAuthInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Audioteka jako katalog Slova (user 2026-11-03): hledání, detail, série/kolekce a návrhy u knihy.
 * Data staví server (`/api/audioteka/…`, neveřejné API Audioteky + webové hledání) a sám značí,
 * co už je v naší ABS knihovně ([AtBook.inLibrary] = id položky ABS). Nákup = otevřít [AtBook.url].
 * null = dotaz selhal (síť, server); prázdný seznam = opravdu nic.
 */
@Singleton
class AudiotekaRepository @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(UploaderAuthInterceptor(prefs))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    private fun base(): String = prefs.getString("uploader_base_url", "").orEmpty().trim().trimEnd('/')
    private fun cookie(): String = prefs.getString("uploader_session_cookie", "").orEmpty()

    suspend fun search(query: String): List<AtBook>? =
        get("/api/audioteka/search?q=${enc(query)}")?.let { parseBooks(it.optJSONArray("items")) }

    suspend fun related(bookId: String): AtRelated? = get("/api/audioteka/related/${enc(bookId)}")?.let { o ->
        val b = o.optJSONObject("book") ?: return@let null
        val sections = o.optJSONArray("sections") ?: JSONArray()
        AtRelated(
            detail = parseDetail(b),
            sections = (0 until sections.length()).mapNotNull { i ->
                val s = sections.optJSONObject(i) ?: return@mapNotNull null
                AtSection(
                    kind = s.optString("kind"),
                    id = s.optString("id"),
                    title = s.optString("title"),
                    items = parseBooks(s.optJSONArray("items")),
                )
            },
        )
    }

    suspend fun series(seriesId: String): AtSeries? = get("/api/audioteka/series/${enc(seriesId)}")?.let { o ->
        AtSeries(
            id = o.optString("id"),
            title = o.optString("title"),
            description = o.str("description"),
            image = o.str("image"),
            items = parseBooks(o.optJSONArray("items")),
        )
    }

    /** Kniha z naší knihovny → kniha v Audiotéce (shoda názvu + autora na serveru). */
    suspend fun match(title: String, author: String?): AtBook? {
        val q = "title=${enc(title)}" + (author?.takeIf { it.isNotBlank() }?.let { "&author=${enc(it)}" } ?: "")
        return get("/api/audioteka/match?$q")?.optJSONObject("book")?.let { parseBook(it) }
    }

    private suspend fun get(path: String): JSONObject? = withContext(Dispatchers.IO) {
        val base = base()
        if (base.isBlank()) return@withContext null
        runCatching {
            val req = Request.Builder().url(base + path)
                .apply { if (cookie().isNotBlank()) header("Cookie", "session=${cookie()}") }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            }
        }.onFailure { Timber.w(it, "[AUDIOTEKA] dotaz %s selhal", path) }.getOrNull()
    }

    private fun parseBooks(arr: JSONArray?): List<AtBook> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parseBook) }

    private fun parseBook(o: JSONObject): AtBook? {
        val id = o.str("id") ?: return null
        val title = o.str("title") ?: return null
        return AtBook(
            id = id,
            isSeries = o.optString("type") == "series",
            title = title,
            authors = o.optJSONArray("authors").strings(),
            lectors = o.optJSONArray("lectors").strings(),
            publisher = o.str("publisher"),
            imageUrl = o.str("image"),
            price = o.str("price")?.replace(' ', ' '),
            priceRegular = o.str("price_regular")?.replace(' ', ' '),
            priceClub = o.str("price_club")?.replace(' ', ' '),
            inClub = o.optBoolean("in_club"),
            free = o.optBoolean("free"),
            minutes = o.optInt("minutes", 0).takeIf { it > 0 },
            rating = o.optDouble("rating", 0.0).takeIf { it > 0.0 }?.toFloat(),
            url = o.str("url"),
            inLibrary = o.str("in_library"),
        )
    }

    private fun parseDetail(o: JSONObject): AtBookDetail? {
        val book = parseBook(o) ?: return null
        val cats = o.optJSONArray("categories")
        return AtBookDetail(
            book = book,
            description = o.str("description"),
            sampleUrl = o.str("sample_url"),
            categories = (0 until (cats?.length() ?: 0)).mapNotNull { cats?.optJSONObject(it)?.str("name") },
        )
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

data class AtBook(
    val id: String,
    val isSeries: Boolean,
    val title: String,
    val authors: List<String>,
    val lectors: List<String>,
    val publisher: String?,
    val imageUrl: String?,
    val price: String?,
    val priceRegular: String?,
    val priceClub: String?,
    val inClub: Boolean,
    val free: Boolean,
    val minutes: Int?,
    val rating: Float?,
    val url: String?,
    /** id položky v naší ABS knihovně, když knihu už máme. */
    val inLibrary: String?,
)

data class AtBookDetail(
    val book: AtBook,
    val description: String?,
    val sampleUrl: String?,
    val categories: List<String>,
)

/** kind: series | collection | author | lector | category */
data class AtSection(val kind: String, val id: String, val title: String, val items: List<AtBook>)

data class AtRelated(val detail: AtBookDetail?, val sections: List<AtSection>)

data class AtSeries(
    val id: String,
    val title: String,
    val description: String?,
    val image: String?,
    val items: List<AtBook>,
)
