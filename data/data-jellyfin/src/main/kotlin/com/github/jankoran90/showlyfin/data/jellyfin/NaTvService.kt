package com.github.jankoran90.showlyfin.data.jellyfin

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaTvService @Inject constructor(
    private val httpClient: OkHttpClient,
) {

    suspend fun findJellyfinItemId(baseUrl: String, token: String, imdbId: String?, tmdbId: Long?): String? {
        if (baseUrl.isBlank() || token.isBlank()) return null
        if (imdbId.isNullOrBlank() && (tmdbId == null || tmdbId <= 0L)) return null
        val base = baseUrl.trimEnd('/')
        val url = "$base/Items?HasAnyProviderId=Imdb,Tmdb&Fields=ProviderIds&IncludeItemTypes=Movie,Series&Recursive=true&Limit=2000&api_key=$token"
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                val items = json.optJSONArray("Items") ?: return@use null
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val providers = it.optJSONObject("ProviderIds") ?: continue
                    val itemImdb = providers.optString("Imdb")
                    val itemTmdb = providers.optString("Tmdb")
                    val matchImdb = !imdbId.isNullOrBlank() && itemImdb.equals(imdbId, ignoreCase = true)
                    val matchTmdb = tmdbId != null && tmdbId > 0L && itemTmdb == tmdbId.toString()
                    if (matchImdb || matchTmdb) return@use it.optString("Id")
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Vrátí remote-control schopné Jellyfin session i s now-playing stavem.
     * Filtruje jen klienty, které hlásí `SupportsRemoteControl` (typicky přehrávače na TV).
     */
    suspend fun getSessions(baseUrl: String, token: String): List<JellyfinSessionSummary> {
        if (baseUrl.isBlank() || token.isBlank()) return emptyList()
        val base = baseUrl.trimEnd('/')
        val url = "$base/Sessions?api_key=$token"
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string() ?: return@use emptyList()
                val arr = JSONArray(body)
                val out = mutableListOf<JellyfinSessionSummary>()
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val id = s.optString("Id").takeIf { it.isNotBlank() } ?: continue
                    val deviceName = s.optString("DeviceName").takeIf { it.isNotBlank() } ?: "?"
                    val client = s.optString("Client").takeIf { it.isNotBlank() }
                    val supportsRemote = s.optBoolean("SupportsRemoteControl", false)
                    val lastActivity = s.optString("LastActivityDate")
                    val isActive = lastActivity.isNotBlank()
                    if (!supportsRemote) continue

                    val nowPlaying = s.optJSONObject("NowPlayingItem")
                    val playState = s.optJSONObject("PlayState")
                    out += JellyfinSessionSummary(
                        sessionId = id,
                        deviceName = deviceName,
                        client = client,
                        isActive = isActive,
                        lastActivityDate = lastActivity.takeIf { it.isNotBlank() },
                        nowPlayingTitle = nowPlaying?.let { buildNowPlayingTitle(it) },
                        nowPlayingSubtitle = nowPlaying?.let { buildNowPlayingSubtitle(it) },
                        isPlaying = nowPlaying != null && !(playState?.optBoolean("IsPaused", false) ?: true),
                        isPaused = playState?.optBoolean("IsPaused", false) ?: false,
                        positionTicks = playState?.optLong("PositionTicks", 0L) ?: 0L,
                        runtimeTicks = nowPlaying?.optLong("RunTimeTicks", 0L) ?: 0L,
                        canSeek = playState?.optBoolean("CanSeek", false) ?: false,
                    )
                }
                out
            }
        }.getOrElse { emptyList() }
    }

    private fun buildNowPlayingTitle(item: JSONObject): String? {
        val type = item.optString("Type")
        // U epizod ukaž seriál jako hlavní titulek (epizoda je v podtitulku).
        return if (type == "Episode") {
            item.optString("SeriesName").takeIf { it.isNotBlank() }
                ?: item.optString("Name").takeIf { it.isNotBlank() }
        } else {
            item.optString("Name").takeIf { it.isNotBlank() }
        }
    }

    private fun buildNowPlayingSubtitle(item: JSONObject): String? {
        if (item.optString("Type") != "Episode") return null
        val season = item.optInt("ParentIndexNumber", -1)
        val episode = item.optInt("IndexNumber", -1)
        val epName = item.optString("Name").takeIf { it.isNotBlank() }
        val code = when {
            season >= 0 && episode >= 0 -> "S%02dE%02d".format(season, episode)
            episode >= 0 -> "E%02d".format(episode)
            else -> null
        }
        return listOfNotNull(code, epName).joinToString(" · ").takeIf { it.isNotBlank() }
    }

    /**
     * Vybere cílovou session pro „Sleduj" widget.
     * Priorita: Wolphin/Yellyfin TV klient s běžícím přehráváním → jakákoli Wolphin/Yellyfin →
     * aktivní s now-playing → první aktivní → první.
     */
    fun pickWatchSession(sessions: List<JellyfinSessionSummary>): JellyfinSessionSummary? {
        if (sessions.isEmpty()) return null
        fun isWolphin(s: JellyfinSessionSummary): Boolean {
            val hay = "${s.client.orEmpty()} ${s.deviceName}".lowercase()
            return hay.contains("wolphin") || hay.contains("wholphin") || hay.contains("yellyfin")
        }
        return sessions.firstOrNull { isWolphin(it) && it.nowPlayingTitle != null }
            ?: sessions.firstOrNull { isWolphin(it) }
            ?: sessions.firstOrNull { it.nowPlayingTitle != null }
            ?: sessions.firstOrNull { it.isActive }
            ?: sessions.first()
    }

    suspend fun sendPlayCommand(baseUrl: String, token: String, sessionId: String, itemId: String): Boolean {
        if (baseUrl.isBlank() || token.isBlank() || sessionId.isBlank() || itemId.isBlank()) return false
        val base = baseUrl.trimEnd('/')
        val url = "$base/Sessions/$sessionId/Playing?playCommand=PlayNow&itemIds=$itemId&startPositionTicks=0&api_key=$token"
        return post(url)
    }

    /** Playstate příkaz na běžící session: PlayPause / Pause / Unpause / Stop / NextTrack / PreviousTrack. */
    suspend fun sendPlaystateCommand(baseUrl: String, token: String, sessionId: String, command: String): Boolean {
        if (baseUrl.isBlank() || token.isBlank() || sessionId.isBlank() || command.isBlank()) return false
        val base = baseUrl.trimEnd('/')
        val url = "$base/Sessions/$sessionId/Playing/$command?api_key=$token"
        return post(url)
    }

    /** Seek na absolutní pozici v ticks (1 ms = 10000 ticks). */
    suspend fun sendSeek(baseUrl: String, token: String, sessionId: String, positionTicks: Long): Boolean {
        if (baseUrl.isBlank() || token.isBlank() || sessionId.isBlank()) return false
        val pos = positionTicks.coerceAtLeast(0L)
        val base = baseUrl.trimEnd('/')
        val url = "$base/Sessions/$sessionId/Playing/Seek?seekPositionTicks=$pos&api_key=$token"
        return post(url)
    }

    private fun post(url: String): Boolean {
        val body = "".toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        return runCatching {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

data class JellyfinSessionSummary(
    val sessionId: String,
    val deviceName: String,
    val client: String?,
    val isActive: Boolean,
    val lastActivityDate: String? = null,
    val nowPlayingTitle: String? = null,
    val nowPlayingSubtitle: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val positionTicks: Long = 0L,
    val runtimeTicks: Long = 0L,
    val canSeek: Boolean = false,
)
