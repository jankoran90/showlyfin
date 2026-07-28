package com.github.jankoran90.showlyfin.data.uploader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * VLTAVA (SHW-110): odkaz na video z ČT iVysílání si vytáhne **ZAŘÍZENÍ**, ne náš server.
 *
 * Playlist API České televize je geoblokované: týž dotaz vrací z našeho serveru (Hetzner DE)
 * `403 UNSUPPORTED_GEOLOCATION`, ale z domácí české sítě `200` s kompletními daty (ověřeno 2026-07-27).
 * Televize i telefon doma na české IP jsou tedy ten správný „resolver" — a jako bonus odpadá byte-proxy
 * přes server, video teče z CDN napřímo.
 *
 * Zdroj se ukládá jako `ctv:<idec>` (nikdy hotová adresa) — CDN odkaz má krátkou platnost a je vázaný
 * na IP toho, kdo si o něj řekl, takže se resolvuje VŽDY ZNOVU při přehrání. Uložený zdroj tím nezestárne.
 */
@Singleton
class CtvStreamResolver @Inject constructor(
    @Named("okHttpBase") private val http: OkHttpClient,
) {

    /** Proč se nedá přehrát — ať uživatel dostane pravdivou hlášku místo černé obrazovky. */
    sealed interface Result {
        /** Hotová adresa DASH manifestu (`.mpd` / token URL s přesměrováním). */
        data class Ok(val url: String) : Result
        /** ČT odmítla nešifrovanou variantu — titul je chráněný Widevine (zkusíme DRM stream). */
        data object DrmRequired : Result
        /** Mimo ČR / IP bez licence. */
        data object OutsideCz : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Nejdřív zkusí čistý stream (vlastní produkce ČT), a když ČT odpoví „vyžaduje DRM", požádá znovu
     * o šifrovanou variantu — tu přehrajeme přes Widevine (licenční server řeší přehrávač podle značky
     * `encryption=wv` v URL, stejně jako webový přehrávač ČT).
     */
    suspend fun resolve(idec: String): Result {
        val plain = request(idec, drm = false)
        return if (plain is Result.DrmRequired) request(idec, drm = true) else plain
    }

    private suspend fun request(idec: String, drm: Boolean): Result = withContext(Dispatchers.IO) {
        if (idec.isBlank()) return@withContext Result.Failed("prázdné idec")
        val url = "$PLAYLIST$idec?canPlayDrm=$drm&streamType=dash"
        val req = Request.Builder().url(url)
            .header("Origin", "https://www.ceskatelevize.cz")
            .header("Referer", "https://www.ceskatelevize.cz/")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use Result.Failed("prázdná odpověď (${resp.code})")
                val json = JSONObject(body)
                // Chybová větev nese DŮVOD (licence/geo) — je to HTTP 403 s tělem, ne prázdná odpověď.
                json.optJSONObject("message")?.optJSONObject("status")?.optString("name").orEmpty().let { st ->
                    when {
                        st.contains("DRM", true) -> return@use Result.DrmRequired
                        st.contains("GEOLOCATION", true) -> return@use Result.OutsideCz
                    }
                }
                val stream = json.optJSONArray("streams")?.optJSONObject(0)
                val play = stream?.optString("url").orEmpty()
                if (play.isBlank()) Result.Failed("ČT nevrátila stream (${resp.code})")
                else Result.Ok(play)
            }
        }.getOrElse { e ->
            Timber.w(e, "[VLTAVA] ČT resolve selhal pro %s", idec)
            Result.Failed(e.message ?: "chyba spojení")
        }
    }

    private companion object {
        const val PLAYLIST =
            "https://api.ceskatelevize.cz/video/v1/playlist-vod/v1/stream-data/media/external/"
    }
}

/** `ctv:<idec>` → `idec`; null u čehokoli jiného (jediné místo, kde se ten tvar rozebírá). */
fun ctvIdecOrNull(url: String?): String? =
    url?.takeIf { it.startsWith(CTV_SCHEME) }?.removePrefix(CTV_SCHEME)?.takeIf { it.isNotBlank() }

/** `ctvid:<sidp>` → `sidp` = IDENTITA ČT titulu ve Filmotéce (VLTAVA F6b); null u čehokoli jiného. */
fun ctvSidpOrNull(identity: String?): String? =
    identity?.takeIf { it.startsWith(CTV_ID_SCHEME) }?.removePrefix(CTV_ID_SCHEME)?.takeIf { it.isNotBlank() }

/** Adresa VIDEA jednoho dílu/filmu ČT — resolvuje ji zařízení (`CtvStreamResolver`). */
const val CTV_SCHEME = "ctv:"

/**
 * VLTAVA F6b — IDENTITA ČT titulu (pořadu i filmu) v uložených zdrojích. Schválně JINÝ prefix než
 * [CTV_SCHEME]: tohle je `sidp` pořadu (trvalý), ne `idec` konkrétního videa. Sedí do pole `imdb`
 * ve [WorkingSource], protože ČT titul žádné imdb/tmdb nemá — a klíčování zůstane jednotné.
 */
const val CTV_ID_SCHEME = "ctvid:"

/**
 * VLTAVA F6b — „stream" ČT POŘADU s díly: přehrát se nedá jeden odkaz, karta musí nabídnout díly.
 * Uložený zdroj ho drží proto, aby pořad mohl být plnohodnotným členem Filmotéky.
 */
const val CTV_SHOW_SCHEME = "ctvshow:"

/** `ctvshow:<sidp>` → `sidp`; null u čehokoli jiného. */
fun ctvShowSidpOrNull(url: String?): String? =
    url?.takeIf { it.startsWith(CTV_SHOW_SCHEME) }?.removePrefix(CTV_SHOW_SCHEME)?.takeIf { it.isNotBlank() }
