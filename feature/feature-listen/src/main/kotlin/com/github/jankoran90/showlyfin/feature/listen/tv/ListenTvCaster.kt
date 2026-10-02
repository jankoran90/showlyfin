package com.github.jankoran90.showlyfin.feature.listen.tv

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.jellyfin.CastResult
import com.github.jankoran90.showlyfin.data.jellyfin.CastTargetPrefs
import com.github.jankoran90.showlyfin.data.jellyfin.FerrySubtitle
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.isNearEnd
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Cíl tlačítka „Na TV" ve Slovu (Nastavení → Poslech → „Posílání videa na TV").
 * FILMY = Filmy appka na TV (FILMYCAST, serverová fronta pod profilem) — default od 2026-10-02.
 * JELLYFIN = běžící Jellyfin/yellyfin session na TV (FERRY cast, potřebuje přihlášení k Jellyfinu).
 */
enum class ListenTvTarget(val key: String) {
    FILMY("filmy"),
    JELLYFIN("jellyfin");

    companion object {
        const val PREF_KEY = "slovo_tv_cast_target"
        fun from(prefs: SharedPreferences): ListenTvTarget =
            entries.firstOrNull { it.key == prefs.getString(PREF_KEY, null) } ?: FILMY
    }
}

/**
 * BUG (2026-10-02, user „skoro žádné video nejde přehrát na TV ze Slova" + „chceme TV jako cast nebo
 * Filmy appku, Jellyfin nepotřebujeme"): všech 5 cest „Na TV" (YouTube kanál, ČT, RSS, sloučený podcast,
 * video verze RSS) šlo VÝHRADNĚ přes Jellyfin session → bez Jellyfin loginu / bez otevřeného Jellyfin
 * klienta na TV hláška „nikdo nehraje / chybí přihlášení". Navíc YouTube 720p/max URL nese oddělovač
 * video+audio proudu ([com.github.jankoran90.showlyfin.data.uploader.YOUTUBE_DASH_URL_DELIMITER]), který
 * rozdělí jen náš přehrávač — Jellyfin klient na TV z něj dostal nehratelnou adresu. Filmy na TV jede na
 * stejném přehrávači jako telefon, takže oddělovač zvládne.
 */
@Singleton
class ListenTvCaster @Inject constructor(
    private val naTv: NaTvService,
    private val workingSourceStore: WorkingSourceStore,
    private val directResume: DirectResumeStore,
    private val videoResume: VideoResumeStore,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {

    /**
     * Pošle video na TV podle zvoleného cíle a vrátí hlášku pro uživatele (Toast).
     * [resumeKey] = klíč sdílené pozice epizody (`yt:`/`rss:`/`ctv:`) → TV naváže, kde telefon skončil.
     * [subtitles] umí jen Jellyfin cesta (Filmy TV příkaz nese jen dohledávací dotaz, ne URL stopy).
     */
    suspend fun castVideo(
        videoUrl: String,
        title: String,
        posterUrl: String? = null,
        resumeKey: String? = null,
        subtitles: List<FerrySubtitle> = emptyList(),
    ): String = when (ListenTvTarget.from(prefs)) {
        ListenTvTarget.FILMY -> castToFilmy(videoUrl, title, posterUrl, resumeKey)
        ListenTvTarget.JELLYFIN -> castToJellyfin(videoUrl, title, subtitles)
    }

    private suspend fun castToFilmy(videoUrl: String, title: String, posterUrl: String?, resumeKey: String?): String {
        if (videoUrl.isBlank()) return "Tohle video nejde poslat na TV."
        val ok = runCatching {
            workingSourceStore.castToTv(
                imdb = null, tmdb = null, title = title, year = null,
                sourceUrl = videoUrl, positionMs = resumePositionMs(resumeKey),
                posterUrl = posterUrl, subtitleQuery = null,
            )
        }.onFailure { Timber.w(it, "[TVCAST] Filmy TV příkaz selhal") }.getOrDefault(false)
        Timber.i("[TVCAST] Filmy TV: %s ok=%s", title, ok)
        return if (ok) {
            "Posláno do Filmy na TV: $title. Když Filmy na TV neběží, otevři ji do 2 minut — pustí se sama."
        } else {
            "Nepodařilo se poslat na TV (server nedostupný nebo chybí profil)."
        }
    }

    private suspend fun castToJellyfin(videoUrl: String, title: String, subtitles: List<FerrySubtitle>): String {
        val jfUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val jfToken = prefs.getString("jellyfin_token", "").orEmpty()
        val base = prefs.getString("uploader_base_url", "").orEmpty()
        val cookie = prefs.getString("uploader_session_cookie", "").orEmpty()
        val reportUrl = if (base.isNotBlank() && cookie.isNotBlank()) {
            "${base.trimEnd('/')}/api/ferry/state?key=${java.net.URLEncoder.encode(cookie, "UTF-8")}"
        } else null
        val result = naTv.castFerry(jfUrl, jfToken, videoUrl, title, subtitles, reportUrl, preferredDeviceId = CastTargetPrefs.defaultDeviceId(prefs))
        Timber.i("[TVCAST] Jellyfin: %s result=%s", title, result)
        return when (result) {
            CastResult.SENT -> "Spuštěno na TV: $title"
            CastResult.NO_SESSION -> "Na TV nikdo nehraje — otevři Jellyfin na televizi a zkus znovu."
            CastResult.NO_CREDS -> "Chybí přihlášení k Jellyfinu (Nastavení → Jellyfin)."
            CastResult.FAILED -> "Nepodařilo se spustit na TV."
        }
    }

    /** Sdílená pozice epizody (audio i video jsou jedna pozice); dohraná = od začátku. */
    private fun resumePositionMs(key: String?): Long {
        if (key.isNullOrBlank()) return 0L
        val v = videoResume.get(key)?.takeUnless { it.isNearEnd() }?.posMs ?: 0L
        val a = directResume.get(key)?.takeUnless { it.isFinished }?.posMs ?: 0L
        return maxOf(v, a)
    }
}
