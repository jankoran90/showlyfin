package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.Context
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TWINE (SHW-74 / plán F7): lokální (per-zařízení) PROPOJENÍ zdrojů Poslechu, které jsou TÝŽ pořad
 * vydávaný zvlášť (audio jako RSS + video na YouTube — např. Boomertalk). Linkovací vrstva NAD
 * sdíleným serverovým store [com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository]:
 * nic nemaže, podkladové zdroje zůstávají, jdou kdykoli odlinkovat (delikátní, nedestruktivní).
 *
 * Skupina [LinkGroup] = množina členských klíčů `type:ref` + zvolený nadpis/obálka. V knihovně se
 * slinkované zdroje zobrazí jako JEDNA karta a otevřou sloučenou obrazovku se spárovanými epizodami.
 * Stejný princip jako [FavoriteSourcesStore] (SharedPreferences + JSON + reaktivní StateFlow).
 */
@Singleton
class PodcastLinkStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** Propojený pořad: členské zdroje (`type:ref`) + zobrazovaný nadpis/obálka (default z 1. členu). */
    data class LinkGroup(
        val id: String,
        val members: List<String>,
        val title: String? = null,
        val thumbnail: String? = null,
    )

    private val prefs = context.getSharedPreferences("podcast_links", Context.MODE_PRIVATE)

    private val _links = MutableStateFlow(load())
    /** Reaktivní seznam propojení → knihovna i Timeline se hned přepočítají. */
    val links: StateFlow<List<LinkGroup>> = _links.asStateFlow()

    /** Stabilní klíč zdroje (shoda s [FavoriteSourcesStore.key]). */
    fun key(type: String, ref: String) = "$type:$ref"
    fun key(source: PodcastSource) = key(source.type, source.ref)

    /** Skupina, do které zdroj patří (nebo null). */
    fun groupForKey(memberKey: String): LinkGroup? = _links.value.firstOrNull { memberKey in it.members }
    fun groupForSource(source: PodcastSource): LinkGroup? = groupForKey(key(source))

    /** Snapshot klíčů, které už jsou v nějaké skupině (≥2 členy) → kandidáti na linkování. */
    fun linkedKeys(): Set<String> = _links.value.filter { it.members.size >= 2 }.flatMap { it.members }.toSet()

    /**
     * Propojí dva zdroje jako týž pořad. Když je některý už ve skupině, druhý se do ní přidá; když jsou
     * v různých skupinách, skupiny se sloučí; jinak vznikne nová. Nadpis/obálka = z [a] (audio/RSS bývá
     * „mateřský"), fallback z [b].
     */
    fun link(a: PodcastSource, b: PodcastSource) {
        val ka = key(a)
        val kb = key(b)
        if (ka == kb) return
        _links.update { list ->
            val ga = list.firstOrNull { ka in it.members }
            val gb = list.firstOrNull { kb in it.members }
            val title = a.title.ifBlank { b.title }
            val thumb = a.thumbnail ?: b.thumbnail
            when {
                ga == null && gb == null ->
                    list + LinkGroup(id = newId(), members = listOf(ka, kb), title = title, thumbnail = thumb)
                ga != null && gb == null ->
                    list.map { if (it.id == ga.id) it.copy(members = (it.members + kb).distinct()) else it }
                ga == null && gb != null ->
                    list.map { if (it.id == gb.id) it.copy(members = (it.members + ka).distinct()) else it }
                ga != null && gb != null && ga.id != gb.id ->
                    // Sloučení dvou skupin → ponech ga, vlož členy gb, gb zahoď.
                    list.mapNotNull {
                        when (it.id) {
                            ga.id -> it.copy(members = (it.members + gb.members).distinct())
                            gb.id -> null
                            else -> it
                        }
                    }
                else -> list   // už ve stejné skupině
            }
        }
        persist()
    }

    /** Zruší celé propojení (zdroje se vrátí jako samostatné karty). */
    fun unlink(groupId: String) {
        _links.update { list -> list.filterNot { it.id == groupId } }
        persist()
    }

    /** Odebere jediný zdroj z propojení; když zbyde <2 členy, skupina zanikne. */
    fun removeMember(memberKey: String) {
        _links.update { list ->
            list.mapNotNull {
                if (memberKey !in it.members) it
                else it.copy(members = it.members - memberKey).takeIf { g -> g.members.size >= 2 }
            }
        }
        persist()
    }

    private fun newId(): String = "lg_" + System.currentTimeMillis().toString(36)

    private fun persist() {
        val arr = JSONArray()
        _links.value.forEach { g ->
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("members", JSONArray(g.members))
                    .apply {
                        g.title?.let { put("title", it) }
                        g.thumbnail?.let { put("thumbnail", it) }
                    },
            )
        }
        prefs.edit().putString(KEY_JSON, arr.toString()).apply()
    }

    private fun load(): List<LinkGroup> {
        val json = prefs.getString(KEY_JSON, "").orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val membersArr = o.optJSONArray("members") ?: JSONArray()
                    val members = buildList { for (j in 0 until membersArr.length()) add(membersArr.getString(j)) }
                    if (members.size >= 2) {
                        add(
                            LinkGroup(
                                id = o.optString("id").ifBlank { "lg_$i" },
                                members = members,
                                title = o.optString("title").ifBlank { null },
                                thumbnail = o.optString("thumbnail").ifBlank { null },
                            ),
                        )
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val KEY_JSON = "links"
    }
}
