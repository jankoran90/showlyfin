package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.PlaybackMode
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * User (2026-09-29, „zkrať dobu načítání čehokoliv") — poslední známý obsah Domů per profil. Cold start
 * ho ukáže OKAMŽITĚ (místo spinneru na celou dobu ABS + feedů všech zdrojů), čerstvá data doběhnou na
 * pozadí a snímek přepíšou. Jen pro ZOBRAZENÍ: YouTube/ČT stream URL nese session klíč, který může být
 * zastaralý → [HomeViewModel.playEpisode] si URL staví znovu s aktuálním klíčem.
 */
internal object HomeSnapshotStore {
    private fun key(profileUuid: String?) = "slovo_home_snapshot_${profileUuid ?: "none"}"

    fun save(prefs: SharedPreferences, profileUuid: String?, items: List<HomeViewModel.ContinueItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            when (item) {
                is HomeViewModel.ContinueItem.Book -> arr.put(JSONObject().put("t", "b").put("b", item.book.toJson()))
                is HomeViewModel.ContinueItem.Episode -> arr.put(
                    JSONObject().put("t", "e")
                        .put("st", item.sourceType).put("sr", item.sourceRef).put("stt", item.sourceTitle)
                        .put("p", item.progress.toDouble()).put("u", item.updatedAt).put("m", item.mode.name)
                        .put("pos", item.posMs).put("dur", item.durMs).put("ep", item.episode.toJson()),
                )
            }
        }
        prefs.edit().putString(key(profileUuid), arr.toString()).apply()
    }

    fun load(prefs: SharedPreferences, profileUuid: String?): List<HomeViewModel.ContinueItem> = runCatching {
        val raw = prefs.getString(key(profileUuid), null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            when (o.optString("t")) {
                "b" -> HomeViewModel.ContinueItem.Book(o.getJSONObject("b").toBook())
                "e" -> HomeViewModel.ContinueItem.Episode(
                    sourceType = o.getString("st"),
                    sourceRef = o.getString("sr"),
                    sourceTitle = o.getString("stt"),
                    episode = o.getJSONObject("ep").toEpisode(),
                    progress = o.optDouble("p", 0.0).toFloat(),
                    updatedAt = o.optLong("u"),
                    mode = runCatching { PlaybackMode.valueOf(o.optString("m")) }.getOrDefault(PlaybackMode.AUDIO),
                    posMs = o.optLong("pos"),
                    durMs = o.optLong("dur"),
                )
                else -> null
            }
        }
    }.onFailure { Timber.w(it, "[HOME] snímek Domů nečitelný") }.getOrDefault(emptyList())

    private fun Audiobook.toJson() = JSONObject()
        .put("id", id).put("title", title).putOpt("author", author).putOpt("narrator", narrator)
        .putOpt("series", seriesName).putOpt("seq", seriesSequence).putOpt("cover", coverUrl)
        .put("dur", durationSec).put("prog", progress).put("cur", currentTimeSec).put("fin", isFinished)
        .putOpt("lu", lastUpdate).putOpt("pid", progressId).put("lib", libraryId)

    private fun JSONObject.toBook() = Audiobook(
        id = getString("id"),
        title = getString("title"),
        author = optStr("author"),
        narrator = optStr("narrator"),
        seriesName = optStr("series"),
        seriesSequence = optStr("seq"),
        coverUrl = optStr("cover"),
        durationSec = optDouble("dur", 0.0),
        progress = optDouble("prog", 0.0),
        currentTimeSec = optDouble("cur", 0.0),
        isFinished = optBoolean("fin"),
        lastUpdate = if (has("lu")) optLong("lu") else null,
        progressId = optStr("pid"),
        libraryId = optString("lib"),
    )

    private fun SourceEpisode.toJson() = JSONObject()
        .put("id", id).put("title", title).putOpt("sub", subtitle).put("stream", streamUrl)
        .putOpt("img", imageUrl).putOpt("date", date).putOpt("rk", resumeKey).putOpt("desc", description)
        .put("dur", durationSec).putOpt("sk", sourceKey).putOpt("jf", jfItemId)

    private fun JSONObject.toEpisode() = SourceEpisode(
        id = getString("id"),
        title = getString("title"),
        subtitle = optStr("sub"),
        streamUrl = optString("stream"),
        imageUrl = optStr("img"),
        date = optStr("date"),
        resumeKey = optStr("rk"),
        description = optStr("desc"),
        durationSec = optDouble("dur", 0.0),
        sourceKey = optStr("sk"),
        jfItemId = optStr("jf"),
    )

    private fun JSONObject.optStr(name: String): String? = if (isNull(name)) null else optString(name)
}
