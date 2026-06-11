package com.github.jankoran90.showlyfin.data.jellyfin

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
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
        return withContext(Dispatchers.IO) {
            runCatching {
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
    }

    /**
     * Vrátí remote-control schopné Jellyfin session i s now-playing stavem (titulky/audio/hlasitost/
     * media info). Filtruje jen klienty, které hlásí `SupportsRemoteControl` (typicky přehrávače na TV).
     */
    suspend fun getSessions(baseUrl: String, token: String): List<JellyfinSessionSummary> {
        if (baseUrl.isBlank() || token.isBlank()) {
            Timber.w("[Ovladac] getSessions: prázdné creds url=%s tokenLen=%d", baseUrl, token.length)
            return emptyList()
        }
        val base = baseUrl.trimEnd('/')
        val url = "$base/Sessions?api_key=$token"
        val request = Request.Builder().url(url).get().build()
        return withContext(Dispatchers.IO) {
            runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("[Ovladac] getSessions HTTP %d", response.code)
                    return@use emptyList()
                }
                val body = response.body?.string() ?: return@use emptyList()
                val arr = JSONArray(body)
                val out = mutableListOf<JellyfinSessionSummary>()
                val rawDump = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val id = s.optString("Id").takeIf { it.isNotBlank() } ?: continue
                    val deviceName = s.optString("DeviceName").takeIf { it.isNotBlank() } ?: "?"
                    val client = s.optString("Client").takeIf { it.isNotBlank() }
                    val supportsRemote = s.optBoolean("SupportsRemoteControl", false)
                    val lastActivity = s.optString("LastActivityDate")
                    val isActive = lastActivity.isNotBlank()
                    val yellyfin = isYellyfinClient(deviceName, client)
                    // Diagnostika: ukaž VŠECHNY session s důvodem zahození (proč se Yellyfin nedrží).
                    rawDump += "$deviceName/$client[remote=$supportsRemote,yellyfin=$yellyfin]"
                    // Bereme VÝHRADNĚ Yellyfin TV klienty (pokyn usera 2026-06-11: žádný Jellyfin Web /
                    // Firefox / jiná instance — ani v Ovladači, ani v „Přehrát na TV"). Musí umět remote.
                    if (!supportsRemote || !yellyfin) continue

                    val nowPlaying = s.optJSONObject("NowPlayingItem")
                    val playState = s.optJSONObject("PlayState")
                    val streams = parseStreams(nowPlaying)
                    out += JellyfinSessionSummary(
                        sessionId = id,
                        deviceName = deviceName,
                        client = client,
                        isActive = isActive,
                        lastActivityDate = lastActivity.takeIf { it.isNotBlank() },
                        itemId = nowPlaying?.optString("Id")?.takeIf { it.isNotBlank() }?.let(::dashUuid),
                        imageTag = nowPlaying?.optJSONObject("ImageTags")?.optString("Primary")?.takeIf { it.isNotBlank() },
                        overview = nowPlaying?.optString("Overview")?.takeIf { it.isNotBlank() },
                        nowPlayingTitle = nowPlaying?.let { buildNowPlayingTitle(it) },
                        nowPlayingSubtitle = nowPlaying?.let { buildNowPlayingSubtitle(it) },
                        isPlaying = nowPlaying != null && !(playState?.optBoolean("IsPaused", false) ?: true),
                        isPaused = playState?.optBoolean("IsPaused", false) ?: false,
                        positionTicks = playState?.optLong("PositionTicks", 0L) ?: 0L,
                        runtimeTicks = nowPlaying?.optLong("RunTimeTicks", 0L) ?: 0L,
                        canSeek = playState?.optBoolean("CanSeek", false) ?: false,
                        volumeLevel = playState?.optInt("VolumeLevel", -1)?.takeIf { it >= 0 },
                        isMuted = playState?.optBoolean("IsMuted", false) ?: false,
                        currentSubtitleIndex = playState?.optInt("SubtitleStreamIndex", -1) ?: -1,
                        currentAudioIndex = playState?.optInt("AudioStreamIndex", -1) ?: -1,
                        subtitleTracks = streams.subtitles,
                        audioTracks = streams.audios,
                        mediaInfoLines = streams.infoLines,
                    )
                }
                Timber.i(
                    "[Ovladac] getSessions host=%s code=%d raw=%d kept=%d(jen Yellyfin) → kept:[%s] raw:[%s]",
                    base.substringAfter("://").substringBefore("/"), response.code, arr.length(), out.size,
                    out.joinToString { "${it.deviceName}/${it.client}[now=${it.nowPlayingTitle}]" },
                    rawDump.joinToString(),
                )
                out
            }
            }.getOrElse {
                Timber.w(it, "[Ovladac] getSessions selhalo")
                emptyList()
            }
        }
    }

    private data class ParsedStreams(
        val subtitles: List<StreamTrack>,
        val audios: List<StreamTrack>,
        val infoLines: List<String>,
    )

    /** Jeden průchod MediaStreams → titulky, audio stopy a čitelné info řádky (video/audio/kontejner). */
    private fun parseStreams(item: JSONObject?): ParsedStreams {
        val streams = item?.optJSONArray("MediaStreams")
            ?: return ParsedStreams(emptyList(), emptyList(), emptyList())
        val subs = mutableListOf<StreamTrack>()
        val audios = mutableListOf<StreamTrack>()
        val info = mutableListOf<String>()
        for (i in 0 until streams.length()) {
            val st = streams.optJSONObject(i) ?: continue
            val idx = st.optInt("Index", -1)
            when (st.optString("Type")) {
                "Subtitle" -> if (idx >= 0) subs += StreamTrack(idx, streamLabel(st, "Titulky $idx"))
                "Audio" -> {
                    if (idx >= 0) audios += StreamTrack(idx, streamLabel(st, "Audio $idx"))
                    val ch = st.optInt("Channels", 0).takeIf { it > 0 }?.let { "${it}ch" }
                    info += listOfNotNull(
                        "🔊", st.optString("DisplayTitle").takeIf { it.isNotBlank() }
                            ?: st.optString("Language").takeIf { it.isNotBlank() },
                        st.optString("Codec").takeIf { it.isNotBlank() }?.uppercase(), ch,
                    ).joinToString(" ")
                }
                "Video" -> {
                    val res = listOfNotNull(
                        st.optInt("Width", 0).takeIf { it > 0 },
                        st.optInt("Height", 0).takeIf { it > 0 },
                    ).takeIf { it.size == 2 }?.let { "${it[0]}×${it[1]}" }
                    info += listOfNotNull(
                        "🎬", st.optString("Codec").takeIf { it.isNotBlank() }?.uppercase(), res,
                        st.optString("DisplayTitle").takeIf { it.isNotBlank() },
                    ).joinToString(" ")
                }
            }
        }
        item.optString("Container").takeIf { it.isNotBlank() }?.let { info += "📦 ${it.uppercase()}" }
        return ParsedStreams(subs, audios, info)
    }

    private fun streamLabel(st: JSONObject, fallback: String): String =
        st.optString("DisplayTitle").takeIf { it.isNotBlank() }
            ?: st.optString("Title").takeIf { it.isNotBlank() }
            ?: st.optString("Language").takeIf { it.isNotBlank() }
            ?: fallback

    /** JF /Sessions vrací item Id bez pomlček; JellyfinDetail (UUID.fromString) je vyžaduje. */
    private fun dashUuid(id: String): String =
        if (id.length == 32 && !id.contains('-')) {
            "${id.substring(0, 8)}-${id.substring(8, 12)}-${id.substring(12, 16)}-" +
                "${id.substring(16, 20)}-${id.substring(20)}"
        } else {
            id
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
     * Vybere cílovou session pro „Ovladač".
     * Priorita: Wolphin/Yellyfin TV klient s běžícím přehráváním → jakákoli Wolphin/Yellyfin →
     * aktivní s now-playing → první aktivní → první.
     */
    /** Je session náš Yellyfin TV klient? (filtr proti Jellyfin Web/Firefox/jiným instancím). */
    private fun isYellyfinClient(deviceName: String, client: String?): Boolean {
        val hay = "${client.orEmpty()} $deviceName".lowercase()
        return hay.contains("yellyfin") || hay.contains("wolphin") || hay.contains("wholphin")
    }

    fun pickWatchSession(sessions: List<JellyfinSessionSummary>): JellyfinSessionSummary? {
        if (sessions.isEmpty()) return null
        // Seznam je už z getSessions filtrovaný jen na Yellyfin → preferuj běžící přehrávání.
        return sessions.firstOrNull { it.nowPlayingTitle != null }
            ?: sessions.firstOrNull { it.isActive }
            ?: sessions.first()
    }

    /** URL na cover (Primary) běžící položky — pro Coil; api_key v query je v pořádku. */
    fun imageUrl(baseUrl: String, token: String, itemId: String, tag: String?): String {
        val base = baseUrl.trimEnd('/')
        val tagPart = if (!tag.isNullOrBlank()) "tag=$tag&" else ""
        return "$base/Items/$itemId/Images/Primary?${tagPart}quality=90&maxHeight=480&api_key=$token"
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

    /** Obecný příkaz na session (GeneralCommand): SetVolume / ToggleMute / Set(Subtitle|Audio)StreamIndex. */
    suspend fun sendGeneralCommand(
        baseUrl: String,
        token: String,
        sessionId: String,
        name: String,
        arguments: Map<String, String> = emptyMap(),
    ): Boolean {
        if (baseUrl.isBlank() || token.isBlank() || sessionId.isBlank() || name.isBlank()) return false
        val base = baseUrl.trimEnd('/')
        val url = "$base/Sessions/$sessionId/Command?api_key=$token"
        val args = JSONObject().apply { arguments.forEach { (k, v) -> put(k, v) } }
        val payload = JSONObject().apply { put("Name", name); put("Arguments", args) }.toString()
        val request = Request.Builder().url(url)
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        return withContext(Dispatchers.IO) {
            val ok = runCatching {
                httpClient.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            Timber.i("[Ovladac] cmd %s args=%s -> ok=%b", name, arguments, ok)
            ok
        }
    }

    suspend fun setVolume(baseUrl: String, token: String, sessionId: String, volume: Int): Boolean =
        sendGeneralCommand(baseUrl, token, sessionId, "SetVolume",
            mapOf("Volume" to volume.coerceIn(0, 100).toString()))

    suspend fun toggleMute(baseUrl: String, token: String, sessionId: String): Boolean =
        sendGeneralCommand(baseUrl, token, sessionId, "ToggleMute")

    /** Index titulkové stopy; -1 = vypnout titulky. */
    suspend fun setSubtitleIndex(baseUrl: String, token: String, sessionId: String, index: Int): Boolean =
        sendGeneralCommand(baseUrl, token, sessionId, "SetSubtitleStreamIndex",
            mapOf("Index" to index.toString()))

    /** Index audio stopy. */
    suspend fun setAudioIndex(baseUrl: String, token: String, sessionId: String, index: Int): Boolean =
        sendGeneralCommand(baseUrl, token, sessionId, "SetAudioStreamIndex",
            mapOf("Index" to index.toString()))

    private suspend fun post(url: String): Boolean = withContext(Dispatchers.IO) {
        val body = "".toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        val ok = runCatching {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        Timber.i("[Ovladac] post %s -> ok=%b", url.substringBefore("?").substringAfterLast("/"), ok)
        ok
    }
}

data class JellyfinSessionSummary(
    val sessionId: String,
    val deviceName: String,
    val client: String?,
    val isActive: Boolean,
    val lastActivityDate: String? = null,
    val itemId: String? = null,
    val imageTag: String? = null,
    val overview: String? = null,
    val nowPlayingTitle: String? = null,
    val nowPlayingSubtitle: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val positionTicks: Long = 0L,
    val runtimeTicks: Long = 0L,
    val canSeek: Boolean = false,
    val volumeLevel: Int? = null,
    val isMuted: Boolean = false,
    val currentSubtitleIndex: Int = -1,
    val currentAudioIndex: Int = -1,
    val subtitleTracks: List<StreamTrack> = emptyList(),
    val audioTracks: List<StreamTrack> = emptyList(),
    val mediaInfoLines: List<String> = emptyList(),
)

data class StreamTrack(val index: Int, val label: String)
