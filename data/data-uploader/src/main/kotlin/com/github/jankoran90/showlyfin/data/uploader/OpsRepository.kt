package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.api.OpsService
import com.github.jankoran90.showlyfin.data.uploader.model.OpsHeartbeatBody
import com.github.jankoran90.showlyfin.data.uploader.model.OpsOverviewResponse
import com.github.jankoran90.showlyfin.data.uploader.model.OpsSourcesResponse
import com.github.jankoran90.showlyfin.data.uploader.model.OpsSweepResponse
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * PROVOZ (SHW-114) — přístup k serverovým endpointům sekce „Provoz".
 *
 * Čtení je záměrně `runCatching` → null: Provoz je INFORMAČNÍ obrazovka, výpadek serveru ji smí nechat
 * prázdnou s hláškou, ale nesmí shodit appku ani přehrávání. **Tep z přehrávače totéž** — kdyby selhání
 * hlášení zabilo přehrávač, byla by diagnostika horší než neznalost.
 */
@Singleton
class OpsRepository @Inject constructor(
    private val service: OpsService,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private fun base(): String = prefs.getString("uploader_base_url", "").orEmpty().trimEnd('/')

    private fun cookie(): String = prefs.getString("uploader_session_cookie", "").orEmpty()
        .let { if (it.isNotBlank()) "session=$it" else "" }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Přehled: co hraje, jak to jede, poslední události. `profile` zúží frontu a log. */
    suspend fun overview(profile: String): OpsOverviewResponse? {
        val b = base().ifBlank { return null }
        return runCatching { service.overview("$b/api/ops/overview?profile=${enc(profile)}", cookie()) }
            .onFailure { Timber.w(it, "[PROVOZ] přehled se nepodařilo načíst") }
            .getOrNull()
    }

    /** Stav zdrojů profilu: kdo má ověřený zdroj, komu chybí, co se hledá. */
    suspend fun sources(profile: String): OpsSourcesResponse? {
        val b = base().ifBlank { return null }
        return runCatching { service.sources("$b/api/ops/sources?profile=${enc(profile)}", cookie()) }
            .onFailure { Timber.w(it, "[PROVOZ] stav zdrojů se nepodařilo načíst") }
            .getOrNull()
    }

    /** „Dohledat chybějící" — jedno kolo hlídače teď. */
    suspend fun sweep(profile: String): OpsSweepResponse? {
        val b = base().ifBlank { return null }
        return runCatching { service.sweep("$b/api/ops/sources/sweep?profile=${enc(profile)}", cookie()) }
            .onFailure { Timber.w(it, "[PROVOZ] dohledání chybějících selhalo") }
            .getOrNull()
    }

    /** „Zkontrolovat zdraví" uložených zdrojů (běží na serveru na pozadí). */
    suspend fun verifyHealth(profile: String): Boolean {
        val b = base().ifBlank { return false }
        return runCatching {
            service.verify("$b/api/ops/sources/verify?profile=${enc(profile)}", cookie()).isSuccessful
        }.getOrDefault(false)
    }

    /** Odeber zapamatovaný zdroj (i ten, který si uživatel kdysi potvrdil — je to jeho příkaz). */
    suspend fun removeSource(profile: String, tmdb: Long, imdb: String, epKey: String? = null): Boolean {
        val b = base().ifBlank { return false }
        val ep = epKey?.takeIf { it.isNotBlank() }?.let { "&epKey=${enc(it)}" }.orEmpty()
        val url = "$b/api/ops/sources?profile=${enc(profile)}&tmdb=$tmdb&imdb=${enc(imdb)}$ep"
        return runCatching { service.removeSource(url, cookie()).isSuccessful }
            .onFailure { Timber.w(it, "[PROVOZ] odebrání zdroje selhalo") }
            .getOrDefault(false)
    }

    /** Jak má automat pro profil hledat: `child` (česky, sdilej napřed) / `original`. */
    suspend fun setPolicy(profile: String, policy: String): Boolean {
        val b = base().ifBlank { return false }
        val url = "$b/api/ops/policy?profile=${enc(profile)}&policy=${enc(policy)}"
        return runCatching { service.setPolicy(url, cookie()).isSuccessful }.getOrDefault(false)
    }

    /** Tep přehrávače. Tichý — selhání hlášení nesmí mít vliv na přehrávání. */
    suspend fun heartbeat(deviceId: String, body: OpsHeartbeatBody) {
        val b = base().ifBlank { return }
        runCatching { service.heartbeat("$b/api/ops/playing?device=${enc(deviceId)}", cookie(), body) }
            .onFailure { Timber.d("[PROVOZ] tep neodeslán: %s", it.message) }
    }

    /** Konec přehrávání. Nepovinné — bez tepu záznam na serveru stejně vyprší. */
    suspend fun playbackStopped(deviceId: String, reason: String = "") {
        val b = base().ifBlank { return }
        val url = "$b/api/ops/playing/stop?device=${enc(deviceId)}&reason=${enc(reason)}"
        runCatching { service.stop(url, cookie()) }
    }
}
