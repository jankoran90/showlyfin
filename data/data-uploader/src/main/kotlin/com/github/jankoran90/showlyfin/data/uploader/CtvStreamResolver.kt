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
        /** ČT titul chrání Widevine DRM — dnes neumíme (viz Known gaps). */
        data object DrmRequired : Result
        /** Mimo ČR / IP bez licence. */
        data object OutsideCz : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun resolve(idec: String): Result = withContext(Dispatchers.IO) {
        if (idec.isBlank()) return@withContext Result.Failed("prázdné idec")
        val url = "$PLAYLIST$idec?canPlayDrm=false&streamType=dash"
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

const val CTV_SCHEME = "ctv:"
