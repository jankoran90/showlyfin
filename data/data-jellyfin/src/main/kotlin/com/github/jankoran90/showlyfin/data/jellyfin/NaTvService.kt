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
                    "[Ovladac] getSessions host=%s code=%d raw=%d kept=%d → %s",
                    base.substringAfter("://").substringBefore("/"), response.code, arr.length(), out.size,
                    out.joinToString { "${it.deviceName}/${it.client}[now=${it.nowPlayingTitle}]" },
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
     * Vybere cílovou session pro „Ovladač" / FERRY cast.
     * Priorita: **náš `Yellyfin` klient PRVNÍ** (jediný má FERRY přijímač + DPAD injektor) → teprve pak
     * upstream Wolphin/Wholphin → now-playing → aktivní → první. Bez té priority cast občas trefil
     * Wholphin (starší/upstream appka bez FERRY), který payload `FERRY1:…` jen zobrazil jako text.
     */
    fun pickWatchSession(sessions: List<JellyfinSessionSummary>): JellyfinSessionSummary? {
        if (sessions.isEmpty()) return null
        fun isYellyfin(s: JellyfinSessionSummary): Boolean =
            "${s.client.orEmpty()} ${s.deviceName}".lowercase().contains("yellyfin")
        fun isWolphin(s: JellyfinSessionSummary): Boolean {
            val hay = "${s.client.orEmpty()} ${s.deviceName}".lowercase()
            return hay.contains("wolphin") || hay.contains("wholphin") || hay.contains("yellyfin")
        }
        return sessions.firstOrNull { isYellyfin(it) && it.nowPlayingTitle != null }
            ?: sessions.firstOrNull { isYellyfin(it) }
            ?: sessions.firstOrNull { isWolphin(it) && it.nowPlayingTitle != null }
            ?: sessions.firstOrNull { isWolphin(it) }
            ?: sessions.firstOrNull { it.nowPlayingTitle != null }
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

    /**
     * Plan FERRY (SHW-37): pošle běžící yellyfin session na TV příkaz „přehraj externí URL + titulky".
     * Payload `FERRY1:{json}` protlačíme přes `SendString` GeneralCommand (server přeposílá libovolný
     * řetězec; yellyfin `RemoteControlReceiver` ho rozpozná a otevře interní MPV). Cílovou session
     * vybere [pickWatchSession] (Wolphin/Yellyfin); když žádná neběží → [CastResult.NO_SESSION].
     */
    suspend fun castFerry(
        baseUrl: String,
        token: String,
        videoUrl: String,
        title: String,
        subtitles: List<FerrySubtitle> = emptyList(),
        reportUrl: String? = null,
    ): CastResult {
        if (baseUrl.isBlank() || token.isBlank()) return CastResult.NO_CREDS
        if (videoUrl.isBlank()) return CastResult.FAILED
        val target = pickWatchSession(getSessions(baseUrl, token)) ?: return CastResult.NO_SESSION
        val payload = buildFerryPayload(videoUrl, title, subtitles, reportUrl)
        val ok = sendGeneralCommand(baseUrl, token, target.sessionId, "SendString", mapOf("String" to payload))
        Timber.i("[FERRY] cast → %s subs=%d ok=%b", target.deviceName, subtitles.size, ok)
        return if (ok) CastResult.SENT else CastResult.FAILED
    }

    /** Sestaví `FERRY1:{url,title,subs:[{u,l,n}],report}` — schéma parsuje yellyfin `parseFerryPayload`. */
    private fun buildFerryPayload(
        url: String,
        title: String,
        subtitles: List<FerrySubtitle>,
        reportUrl: String? = null,
    ): String {
        val subs = JSONArray()
        subtitles.forEach { s ->
            if (s.url.isNotBlank()) {
                subs.put(JSONObject().apply {
                    put("u", s.url)
                    s.language?.takeIf { it.isNotBlank() }?.let { put("l", it) }
                    s.label?.takeIf { it.isNotBlank() }?.let { put("n", it) }
                })
            }
        }
        val json = JSONObject().apply {
            put("url", url)
            put("title", title)
            put("subs", subs)
            reportUrl?.takeIf { it.isNotBlank() }?.let { put("report", it) }
        }
        return "FERRY1:$json"
    }

    /**
     * BATON: přečte pozici externího streamu hlášenou boxem (`/api/ferry/state`). null = nehraje/stale.
     * URL si staví volající (showlyfin) z uploader base+key — stejná jako kam box reportuje.
     */
    suspend fun getFerryState(progressUrl: String): FerryState? = withContext(Dispatchers.IO) {
        if (progressUrl.isBlank()) return@withContext null
        runCatching {
            val request = Request.Builder().url(progressUrl).get().build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string().orEmpty())
                if (!json.optBoolean("active", false)) return@use null
                // CONSOLE: box hlásí i seznam stop (titulky/audio) + aktuální výběr → Ovladač je vystaví.
                val tracks = json.optJSONObject("tracks")
                val subs = parseFerryTracks(tracks?.optJSONArray("text"))
                val auds = parseFerryTracks(tracks?.optJSONArray("audio"))
                FerryState(
                    title = json.optString("title"),
                    positionMs = json.optLong("positionMs", 0L),
                    durationMs = json.optLong("durationMs", 0L),
                    paused = json.optBoolean("paused", false),
                    subtitleTracks = subs.tracks,
                    audioTracks = auds.tracks,
                    currentSubtitleIndex = subs.selected,
                    currentAudioIndex = auds.selected,
                )
            }
        }.getOrNull()
    }

    private data class ParsedFerryTracks(val tracks: List<StreamTrack>, val selected: Int)

    /** CONSOLE: pole stop {i,label,sel} → [StreamTrack] (index = ordinál) + index právě vybrané (-1 = žádná). */
    private fun parseFerryTracks(arr: JSONArray?): ParsedFerryTracks {
        if (arr == null) return ParsedFerryTracks(emptyList(), -1)
        val out = mutableListOf<StreamTrack>()
        var selected = -1
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val idx = t.optInt("i", i)
            out += StreamTrack(idx, t.optString("label").takeIf { it.isNotBlank() } ?: "Stopa $idx")
            if (t.optBoolean("sel", false)) selected = idx
        }
        return ParsedFerryTracks(out, selected)
    }

    /**
     * Plan CONSOLE (SHW-39): pošle běžícímu externímu přehrávači nastavení obrazu/titulků (poměr stran,
     * styl titulků) přes SEND_STRING payload `FERRYCFG1:{json}`. Jen vyplněná pole se aplikují.
     */
    suspend fun castFerryConfig(
        baseUrl: String,
        token: String,
        resizeMode: String? = null,
        subFontSizeSp: Int? = null,
        subColorArgb: Int? = null,
        subBottomMarginPct: Int? = null,
    ): Boolean {
        if (baseUrl.isBlank() || token.isBlank()) return false
        val target = pickWatchSession(getSessions(baseUrl, token)) ?: return false
        val json = JSONObject().apply {
            resizeMode?.takeIf { it.isNotBlank() }?.let { put("resizeMode", it) }
            subFontSizeSp?.let { put("subFontSizeSp", it) }
            subColorArgb?.let { put("subColorArgb", it) }
            subBottomMarginPct?.let { put("subBottomMarginPct", it) }
        }
        return sendGeneralCommand(baseUrl, token, target.sessionId, "SendString", mapOf("String" to "FERRYCFG1:$json"))
    }

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

/** BATON: stav externího streamu hlášený boxem (pro posuvník v Ovladači). */
data class FerryState(
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val paused: Boolean,
    /** CONSOLE: stopy titulků/audio externího přehrávače (ordinál = index pro Set*StreamIndex). */
    val subtitleTracks: List<StreamTrack> = emptyList(),
    val audioTracks: List<StreamTrack> = emptyList(),
    val currentSubtitleIndex: Int = -1,
    val currentAudioIndex: Int = -1,
)

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

/** Plan FERRY: jeden titulkový kandidát poslaný na TV (box-dostupná SRT URL). */
data class FerrySubtitle(
    val url: String,
    val language: String? = null,
    val label: String? = null,
)

/** Výsledek odeslání FERRY příkazu na TV — pro hlášku v UI. */
enum class CastResult { SENT, NO_SESSION, NO_CREDS, FAILED }
