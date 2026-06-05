package com.github.jankoran90.showlyfin.data.csfd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import timber.log.Timber
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

data class CsfdReviewRaw(
    val username: String,
    val rating: Int?,
    val text: String,
    val date: String,
)

@Singleton
class CsfdScraper @Inject constructor() {

    companion object {
        private const val BASE_URL = "https://www.csfd.cz"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }

    private val cookieStore = mutableMapOf<String, List<Cookie>>()
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val existing = cookieStore[url.host] ?: emptyList()
                cookieStore[url.host] = (existing + cookies).distinctBy { it.name }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host] ?: emptyList()
        })
        .build()

    suspend fun searchByTitle(title: String, year: Int): Long? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/hledat/?q=${URLEncoder.encode(title, "UTF-8")}&filter=film"
            val html = fetchWithAnubis(url) ?: return@withContext null
            val doc = Jsoup.parse(html, BASE_URL)
            for (article in doc.select("article[class*=article-poster]")) {
                val href = article.select("a[href*=/film/]").first()?.attr("href") ?: continue
                val idMatch = Regex("/film/(\\d+)").find(href)?.groupValues?.get(1)?.toLongOrNull() ?: continue
                if (year > 0) {
                    val yearMatch = Regex("\\((\\d{4})\\)").find(article.text())?.groupValues?.get(1)?.toIntOrNull()
                    if (yearMatch != null && abs(yearMatch - year) > 1) continue
                }
                return@withContext idMatch
            }
            null
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] searchByTitle failed for title=$title")
            null
        }
    }

    suspend fun scrapeReviews(csfdId: Long): List<CsfdReviewRaw> = withContext(Dispatchers.IO) {
        try {
            val html = fetchWithAnubis("$BASE_URL/film/$csfdId/recenze/") ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, BASE_URL)
            val reviews = mutableListOf<CsfdReviewRaw>()
            for (item in doc.select("article.article-review")) {
                val username = item.select("a.user-title-name").text().trim()
                if (username.isBlank()) continue
                val rating = item.select("span[class*=stars-]").firstOrNull()?.let { el ->
                    Regex("stars-(\\d+)").find(el.className())?.groupValues?.get(1)?.toIntOrNull()?.times(20)
                }
                val text = item.select("span[data-film-review-content]").text().trim()
                    .ifBlank { item.select("span.comment").text().trim() }
                if (text.isBlank()) continue
                val date = item.select("time").first()?.text()?.trim() ?: ""
                reviews.add(CsfdReviewRaw(username, rating, text, date))
            }
            reviews
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] scrapeReviews failed for csfdId=$csfdId")
            emptyList()
        }
    }

    suspend fun scrapePlot(csfdId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val html = fetchWithAnubis("$BASE_URL/film/$csfdId/") ?: return@withContext null
            val doc = Jsoup.parse(html, BASE_URL)
            val plotEl = doc.selectFirst(".plot-full") ?: doc.selectFirst(".plot-preview")
            plotEl?.select("p")?.text()?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] scrapePlot failed for csfdId=$csfdId")
            null
        }
    }

    suspend fun scrapeTitle(csfdId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val html = fetchWithAnubis("$BASE_URL/film/$csfdId/") ?: return@withContext null
            val doc = Jsoup.parse(html, BASE_URL)
            doc.selectFirst(".film-header-name h1")?.text()?.trim()
                ?: doc.selectFirst(".film-header h1")?.text()?.trim()
                ?: doc.selectFirst(".film-header-name .name")?.text()?.trim()
                ?: doc.selectFirst("h1[itemprop=name]")?.text()?.trim()
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] scrapeTitle failed for csfdId=$csfdId")
            null
        }
    }

    suspend fun scrapeGallery(csfdId: Long): List<String> = withContext(Dispatchers.IO) {
        try {
            val html = fetchWithAnubis("$BASE_URL/film/$csfdId/galerie/") ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, BASE_URL)
            val urls = mutableListOf<String>()
            val section = doc.getElementById("snippet--photos") ?: doc.body()
            for (img in section.select("img[src]")) {
                val src = img.attr("src").trim()
                if (src.isBlank() || src.contains(".svg") || src.contains("placeholder") || src.contains("blank")) continue
                if (!src.contains("pmgstatic.com") && !src.contains("/photos/") && !src.contains("/images/")) continue
                val fullUrl = when {
                    src.startsWith("//") -> "https:$src"
                    src.startsWith("http") -> src
                    else -> "$BASE_URL$src"
                }
                urls.add(fullUrl)
                if (urls.size >= 30) break
            }
            urls
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] scrapeGallery failed for csfdId=$csfdId")
            emptyList()
        }
    }

    private fun fetchWithAnubis(url: String): String? {
        val html = fetchHtml(url) ?: return null
        if (!html.contains("anubis_challenge")) return html

        val match = Regex("""id="anubis_challenge"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.trim() ?: return null

        val cd = JSONObject(match)
        val rules = cd.getJSONObject("rules")
        val challenge = cd.getJSONObject("challenge")
        val (hash, nonce) = solveAnubisPoW(challenge.getString("randomData"), rules.getInt("difficulty"))
        val redir = URLEncoder.encode(url.removePrefix(BASE_URL), "UTF-8")
        val passUrl = "$BASE_URL/.within.website/x/cmd/anubis/api/pass-challenge" +
            "?id=${URLEncoder.encode(challenge.getString("id"), "UTF-8")}" +
            "&response=$hash&nonce=$nonce&redir=$redir&elapsedTime=1"
        fetchHtml(passUrl)
        return fetchHtml(url)
    }

    private fun fetchHtml(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "cs-CZ,cs;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] fetchHtml failed: $url")
            null
        }
    }

    private fun solveAnubisPoW(randomData: String, difficulty: Int): Pair<String, Int> {
        val md = MessageDigest.getInstance("SHA-256")
        val fullBytes = difficulty / 2
        val needHalfNibble = difficulty % 2 != 0
        var nonce = 0
        while (true) {
            md.reset()
            val hash = md.digest("$randomData$nonce".toByteArray(Charsets.UTF_8))
            var ok = (0 until fullBytes).all { hash[it] == 0.toByte() }
            if (ok && needHalfNibble && (hash[fullBytes].toInt() and 0xFF) shr 4 != 0) ok = false
            if (ok) {
                val hex = hash.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                return Pair(hex, nonce)
            }
            nonce++
        }
    }
}
