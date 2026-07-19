package com.github.jankoran90.showlyfin.feature.playback.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * RELAY-CORE (2026-07-19): robustní HTTP vrstva pro přehrávání RealDebrid/CDN streamů.
 *
 * **Proč:** dosud běžel [androidx.media3.datasource.DefaultHttpDataSource] (HttpURLConnection) bez
 * poolingu a s výchozí [DefaultLoadErrorHandlingPolicy] = jen 3 pokusy, backoff do 5 s. Efemérní RD
 * odkaz, který na pár vteřin „zchladne" (RD dorozbaluje / CDN kolísá), přehrávač po ~5 s vzdá →
 * nekonečné BUFFERING nebo IO chyba → zbytečný skok na jiný zdroj. showlyfin problém neměl, protože
 * TV hrál přes zralý JF přehrávač na boxu (server-side buffering), Filmy hraje LOKÁLNĚ přímo z RD.
 *
 * **Řešení (dvě páky):**
 *  1. [streamingOkHttpClient] — OkHttp s `retryOnConnectionFailure` + connection poolem (drží teplé
 *     TLS spojení na RD, každý mid-stream reconnect neplatí nový handshake) + logging interceptorem,
 *     který zaznamená Range/odpověď (DŮKAZ, jestli RD při reconnectu vrací 206 s korektním Content-Range,
 *     nebo 200 = full body → posunuté bajty → `contentIsMalformed` pád).
 *  2. [RelayLoadErrorHandlingPolicy] — diferencovaná retry politika: dočasné IO (timeout/reset/5xx)
 *     TRPĚLIVĚ (víc pokusů, delší backoff → přejede přes kolísání RD reconnectem téhož bajtu, bez
 *     re-resolve); mrtvý odkaz (404/410/416/403) NAOPAK fail-fast → hned padne → RELAY re-resolve
 *     čerstvého odkazu v [com.github.jankoran90.showlyfin.feature.detail.DetailViewModel].
 */
@OptIn(UnstableApi::class)
object RobustStreamHttp {

    private const val TAG = "RELAY-HTTP"

    /** UA jako v původním inline přehrávači (některé CDN filtrují neznámé UA). */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * OkHttp klient laděný na dlouhé streamy z flaky CDN:
     *  - `retryOnConnectionFailure(true)` — transparentní retry při selhání navázání spojení.
     *  - žádný `callTimeout` (0) — dlouhé legitimní čtení chunku nesmí spadnout; stall hlídá `readTimeout`.
     *  - `connectionPool` s dlouhým keep-alive — reconnecty na TÝŽ RD host recyklují teplé spojení.
     *  - `followRedirects` — RD vrací 302 na CDN uzel.
     */
    private val streamingOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .addInterceptor(RangeLoggingInterceptor)
            .build()
    }

    /**
     * Data source factory pro ExoPlayer: [OkHttpDataSource] pro http(s) obalený do [DefaultDataSource]
     * kvůli podpoře `file://` (offline stažené filmy) i `content://`. Vrací se do
     * [androidx.media3.exoplayer.source.DefaultMediaSourceFactory].
     */
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val http: HttpDataSource.Factory = OkHttpDataSource.Factory(streamingOkHttpClient)
            .setUserAgent(USER_AGENT)
        return DefaultDataSource.Factory(context, http)
    }

    /** Diferencovaná retry politika (viz [RelayLoadErrorHandlingPolicy]). */
    fun loadErrorHandlingPolicy(): LoadErrorHandlingPolicy = RelayLoadErrorHandlingPolicy()

    /**
     * Zaloguje u KAŽDÉHO HTTP požadavku na stream jeho `Range` + odpověď (kód, `Content-Range`,
     * `Content-Length`, `Accept-Ranges`). To je jediný přímý důkaz, co RD při mid-stream reconnectu
     * reálně vrací: 206 s korektním rozsahem = OK; 200 (full body) na Range požadavek s nenulovým
     * offsetem = zdroj Range ignoruje → ExoPlayer dostane bajty od 0 → posun → `contentIsMalformed`.
     */
    private object RangeLoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val range = req.header("Range")
            val started = System.nanoTime()
            val resp = try {
                chain.proceed(req)
            } catch (e: IOException) {
                Timber.tag(TAG).w("REQ %s range=%s → IO %s: %s",
                    hostPath(req.url.toString()), range ?: "-", e.javaClass.simpleName, e.message)
                throw e
            }
            val ms = (System.nanoTime() - started) / 1_000_000
            val contentRange = resp.header("Content-Range")
            val suspiciousFullBody = range != null && !range.trimStart().startsWith("bytes=0-") &&
                resp.code == 200
            val flag = if (suspiciousFullBody) "  ⚠️RANGE-IGNORED(200-na-offset)" else ""
            val line = "REQ ${hostPath(req.url.toString())} range=${range ?: "-"} → ${resp.code} in ${ms}ms " +
                "cr=${contentRange ?: "-"} len=${resp.header("Content-Length") ?: "-"} " +
                "ar=${resp.header("Accept-Ranges") ?: "-"}$flag"
            Timber.tag(TAG).i(line)
            // V release jde Timber jen do BufferTree (ne logcat) → duplikuj do android.util.Log, ať jde Range
            // proof číst živě přes adb logcat z boxu. WARN u podezřelé odpovědi (RANGE-IGNORED) ať vyskočí.
            if (suspiciousFullBody) android.util.Log.w(TAG, line) else android.util.Log.i(TAG, line)
            return resp
        }

        /** Zkrátí URL na host+cestu bez query (RD tokeny/podpisy pryč z logu). */
        private fun hostPath(url: String): String =
            url.substringBefore('?').removePrefix("https://").removePrefix("http://").take(80)
    }

    /**
     * Retry politika streamu. Rozšiřuje [DefaultLoadErrorHandlingPolicy]:
     *  - [getMinimumLoadableRetryCount] = 6 pro všechny typy dat (default 3) → víc reconnect pokusů.
     *  - [getRetryDelayMsFor] = fail-fast na fatální HTTP kódy efemérního odkazu (404/410/416/403 →
     *    `C.TIME_UNSET` = nefatálně-neopakovat → RELAY re-resolve), jinak trpělivý lineární backoff
     *    do 8 s (default cap 5 s) → přejede přes RD cold spell reconnectem téhož bajtu.
     */
    private class RelayLoadErrorHandlingPolicy :
        DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 6) {

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val ex = loadErrorInfo.exception
            val code = (ex as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            // Mrtvý efemérní odkaz — retry nemá smysl (vrátí to samé). Padni hned → RELAY re-resolve.
            if (code != null && code in DEAD_LINK_CODES) {
                val m = "HTTP $code (mrtvý odkaz) → fail-fast, RELAY re-resolve (pokus #${loadErrorInfo.errorCount})"
                Timber.tag(TAG).w(m); android.util.Log.w(TAG, m)
                return C.TIME_UNSET
            }
            // Dočasné IO (timeout/reset/5xx/503): trpělivý lineární backoff, ať RD stihne „zteplat".
            val delay = min(loadErrorInfo.errorCount.toLong() * 2000L, 8000L)
            val m = "retry #${loadErrorInfo.errorCount} za ${delay}ms (${ex.javaClass.simpleName})"
            Timber.tag(TAG).i(m); android.util.Log.i(TAG, m)
            return delay
        }

        companion object {
            /** HTTP kódy = mrtvý/expirovaný odkaz → nemá smysl opakovat, rovnou re-resolve. */
            private val DEAD_LINK_CODES = setOf(403, 404, 410, 416)
        }
    }
}
