package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.Context
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPHEMERON (2026-09-04, user „když hledám díl na kartě konkrétního zdroje a kliknu na něj, tak
 * musíme vidět, že ten díl patří do tohoto zdroje — ať se tam připojí i po doposlouchání nebo
 * zrušení pozice, aby bylo možné ho znovu vyhledat, ale už na té kartě podcastu"): trvalá paměť
 * „tahle epizoda z SCOPED hledání ([PodcastSearchViewModel.scopeSource]) patří ke zdroji X" — appka
 * to ví JISTĚ v okamžiku hledání/přehrání (žádné dohledávání channel_id/handle jako u obecného
 * Domů fallbacku, viz `resolveYoutubeEpisode`), stačí si to zapamatovat NATRVALO (ne jen dokud běží
 * resume mark), ať jde epizoda najít na kartě zdroje i po odposlouchání/zrušení pozice.
 *
 * Lokální per-profil úložiště (SharedPreferences + JSON, stejný styl jako legacy `DirectResumeStore`
 * blob před SUBSTRATE) — na rozdíl od pozice poslechu tahle vazba nepotřebuje cross-device sync,
 * jde o osobní „našel jsem tenhle díl, ať ho vidím na kartě", ne o rozehranou pozici.
 */
@Singleton
class AttachedEpisodeStore @Inject constructor(
    @ApplicationContext context: Context,
    private val profileRepository: ProfileRepository,
) {
    private val prefs = context.getSharedPreferences("attached_episodes", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bySource = MutableStateFlow<Map<String, List<SourceEpisode>>>(emptyMap())
    /** `"$type:$ref"` zdroje → epizody manuálně připojené přes scoped hledání, nejnovější první. */
    val bySource: StateFlow<Map<String, List<SourceEpisode>>> = _bySource.asStateFlow()

    @Volatile private var activeProfileKey: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    init {
        scope.launch {
            profileRepository.activeProfile
                .map { it?.profileUuid?.takeIf { u -> u.isNotBlank() } }
                .distinctUntilChanged()
                .collect { uuid ->
                    activeProfileKey = uuid
                    _bySource.value = load(uuid)
                }
        }
    }

    fun episodesFor(sourceKey: String): List<SourceEpisode> = _bySource.value[sourceKey].orEmpty()

    /** Idempotentní — druhé kliknutí na tutéž epizodu ji jen posune na první místo (nejnovější první). */
    fun attach(sourceKey: String, episode: SourceEpisode) {
        if (sourceKey.isBlank()) return
        val epKey = episode.resumeKey ?: episode.id
        val current = _bySource.value[sourceKey].orEmpty().filterNot { (it.resumeKey ?: it.id) == epKey }
        val updated = (listOf(episode) + current).take(MAX_PER_SOURCE)
        _bySource.value = _bySource.value + (sourceKey to updated)
        persist()
    }

    private fun persist() {
        val key = activeProfileKey ?: return
        val snapshot = _bySource.value
        scope.launch {
            runCatching {
                val json = JSONObject()
                snapshot.forEach { (srcKey, eps) -> json.put(srcKey, JSONArray(eps.map { toJson(it) })) }
                prefs.edit().putString(prefsKey(key), json.toString()).apply()
            }.onFailure { Timber.w(it, "[EPHEMERON] uložení připojených epizod selhalo") }
        }
    }

    private fun load(uuid: String?): Map<String, List<SourceEpisode>> {
        val key = uuid ?: return emptyMap()
        val raw = prefs.getString(prefsKey(key), null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { srcKey ->
                val arr = obj.getJSONArray(srcKey)
                (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
            }
        }.onFailure { Timber.w(it, "[EPHEMERON] načtení připojených epizod selhalo") }.getOrDefault(emptyMap())
    }

    private fun prefsKey(uuid: String) = "attached_$uuid"

    private fun toJson(ep: SourceEpisode) = JSONObject().apply {
        put("id", ep.id)
        put("title", ep.title)
        putOpt("subtitle", ep.subtitle)
        put("streamUrl", ep.streamUrl)
        putOpt("imageUrl", ep.imageUrl)
        putOpt("date", ep.date)
        putOpt("resumeKey", ep.resumeKey)
        putOpt("description", ep.description)
        put("durationSec", ep.durationSec)
        if (ep.viewCount != null) put("viewCount", ep.viewCount)
        putOpt("sourceKey", ep.sourceKey)
        putOpt("jfItemId", ep.jfItemId)
    }

    private fun fromJson(o: JSONObject) = SourceEpisode(
        id = o.getString("id"),
        title = o.optString("title", ""),
        subtitle = o.optString("subtitle").takeIf { o.has("subtitle") },
        streamUrl = o.optString("streamUrl", ""),
        imageUrl = o.optString("imageUrl").takeIf { o.has("imageUrl") },
        date = o.optString("date").takeIf { o.has("date") },
        resumeKey = o.optString("resumeKey").takeIf { o.has("resumeKey") },
        description = o.optString("description").takeIf { o.has("description") },
        durationSec = o.optDouble("durationSec", 0.0),
        viewCount = if (o.has("viewCount")) o.optLong("viewCount") else null,
        sourceKey = o.optString("sourceKey").takeIf { o.has("sourceKey") },
        jfItemId = o.optString("jfItemId").takeIf { o.has("jfItemId") },
    )

    companion object {
        /** Ochrana proti neomezenému růstu — realisticky uživatel takhle ručně připojí pár desítek epizod. */
        private const val MAX_PER_SOURCE = 100
    }
}
