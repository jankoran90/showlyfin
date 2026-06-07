package com.github.jankoran90.showlyfin.data.csfd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
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

    // Anubis auth cookie store (name → value). Sdílené napříč requesty jedné instance.
    // Pozn.: scrape jede přes HttpURLConnection (ne OkHttp) — OkHttp 5.x na zařízení k ČSFD
    // selhával (Anubis challenge fetch vracel null za ~110 ms), zatímco HttpURLConnection
    // (stejný stack jako funkční Wikidata lookup) projde. Parity s funkčním yeshowly scrapem.
    private val cookieJar = mutableMapOf<String, String>()

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
            val html = fetchWithAnubis("$BASE_URL/film/$csfdId/")
            if (html == null) { Timber.w("[CsfdScraper] scrapePlot $csfdId: fetchWithAnubis=null"); return@withContext null }
            val doc = Jsoup.parse(html, BASE_URL)
            val plotEl = doc.selectFirst(".plot-full") ?: doc.selectFirst(".plot-preview")
            val plot = plotEl?.select("p")?.text()?.trim()?.takeIf { it.isNotBlank() }
            Timber.d("[CsfdScraper] scrapePlot $csfdId: html=${html.length}ch anubis=${html.contains("anubis_challenge")} plotEl=${plotEl != null} plot=${plot?.take(50)}")
            plot
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] scrapePlot failed for csfdId=$csfdId")
            null
        }
    }

    suspend fun scrapeRating(csfdId: Long): Int? = withContext(Dispatchers.IO) {
        try {
            val html = fetchWithAnubis("$BASE_URL/film/$csfdId/") ?: return@withContext null
            val doc = Jsoup.parse(html, BASE_URL)
            val ratingEl = doc.selectFirst(".film-rating-average")
                ?: doc.selectFirst(".rating-average")
                ?: doc.selectFirst("[class*=rating-average]")
            val text = ratingEl?.text()?.trim() ?: return@withContext null
            Regex("(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] scrapeRating failed for csfdId=$csfdId")
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
        val html = fetchHtml(url) ?: run { Timber.w("[CsfdScraper] anubis: fetchHtml(1)=null $url"); return null }
        if (!html.contains("anubis_challenge")) { Timber.d("[CsfdScraper] anubis: no challenge, ${html.length}ch $url"); return html }

        val match = Regex("""id="anubis_challenge"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.trim() ?: run { Timber.w("[CsfdScraper] anubis: challenge regex MISS ${html.length}ch"); return null }

        val cd = JSONObject(match)
        val rules = cd.getJSONObject("rules")
        val challenge = cd.getJSONObject("challenge")
        val (hash, nonce) = solveAnubisPoW(challenge.getString("randomData"), rules.getInt("difficulty"))
        Timber.d("[CsfdScraper] anubis: solved difficulty=${rules.getInt("difficulty")} nonce=$nonce")
        val redir = URLEncoder.encode(url.removePrefix(BASE_URL), "UTF-8")
        val passUrl = "$BASE_URL/.within.website/x/cmd/anubis/api/pass-challenge" +
            "?id=${URLEncoder.encode(challenge.getString("id"), "UTF-8")}" +
            "&response=$hash&nonce=$nonce&redir=$redir&elapsedTime=1"
        fetchHtml(passUrl)
        val result = fetchHtml(url)
        Timber.d("[CsfdScraper] anubis: after pass cookies=${cookieJar.keys} result=${result?.length}ch stillChallenge=${result?.contains("anubis_challenge")}")
        return result
    }

    private fun fetchHtml(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "cs-CZ,cs;q=0.9")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                if (cookieJar.isNotEmpty()) {
                    setRequestProperty("Cookie", cookieJar.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            val status = conn.responseCode
            Timber.d("[CsfdScraper] fetchHtml HTTP $status $url")
            // Anubis challenge i pass-challenge mohou přijít s ne-2xx statusem — čteme tělo i tak
            // (error stream), ať fetchWithAnubis challenge uvidí a vyřeší.
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            // Ulož Set-Cookie (Anubis auth cookie) pro následný re-fetch.
            conn.headerFields.forEach { (key, values) ->
                if (key != null && key.equals("Set-Cookie", ignoreCase = true)) {
                    values.forEach { sc ->
                        val pair = sc.substringBefore(";").split("=", limit = 2)
                        if (pair.size == 2 && pair[0].isNotBlank()) cookieJar[pair[0].trim()] = pair[1].trim()
                    }
                }
            }
            stream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            Timber.w(e, "[CsfdScraper] fetchHtml failed: $url")
            null
        } finally {
            conn?.disconnect()
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
