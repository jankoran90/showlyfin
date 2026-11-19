package com.github.jankoran90.showlyfin.feature.listen.kids

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.feature.listen.KidsSourceAccess
import com.github.jankoran90.showlyfin.feature.listen.SeriesResolver
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRuleStore
import com.github.jankoran90.showlyfin.feature.listen.player.removeFromQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tester 2026-11-19 (1.0.107/108): díl, který rodič Dětem odebral, zůstal v mini-přehrávači a frontě a šel dál
 * přehrát streamem. Hlídač sleduje config dětského profilu a při ODEBRÁNÍ klíče (díl / série / celý pořad) vyřadí
 * z fronty i z přehrávače díly, které předtím povolené byly a teď už nejsou.
 *
 * Záměrně reaguje jen na změnu configu za běhu (předchozí → nový), ne na samotný stav: prázdný / ještě
 * nenačtený config po startu by jinak vyhodil i povolené díly. Knihy řeší samostahování (mazání staženého).
 */
@Singleton
class KidsQueueGuard @Inject constructor(
    private val profiles: ProfileRepository,
    private val connection: AudiobookPlayerConnection,
    private val sourcesRepo: PodcastSourcesRepository,
    private val ruleStore: SeriesRuleStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Poslední config každého dětského profilu (i přes mezihru v rodičovském profilu — revize 2026-11-19:
            // rodič odebere díl ve svém profilu, po přepnutí zpět na dítě se porovná s tím, co dítě mělo naposled).
            val lastByProfile = HashMap<Long, Set<String>>()
            profiles.activeProfileWithConfig.collect { (active, cfg) ->
                val kidsId = active?.takeIf { !it.isAdmin }?.id ?: return@collect
                val now = cfg.visibleForKidsSourceKeys
                // Prázdný config = nejspíš ještě nenačtený (přechod mezi profily) → nic nevyhazovat ani nepamatovat.
                if (now.isEmpty()) return@collect
                val before = lastByProfile.put(kidsId, now) ?: return@collect
                if ((before - now).isEmpty()) return@collect
                ejectNoLongerAllowed(before, now)
            }
        }
    }

    private fun ejectNoLongerAllowed(oldKeys: Set<String>, newKeys: Set<String>) {
        val st = connection.state.value
        val candidates = buildList {
            addAll(connection.queue.value)
            connection.currentEpisode?.let { cur -> if (none { it.episodeId == cur.episodeId }) add(cur) }
        }
        val gone = candidates.filter { q -> allowed(q, oldKeys) == true && allowed(q, newKeys) == false }
        if (gone.isEmpty()) return
        Timber.i("[kids-queue] odebráno rodičem → vyřazuji z fronty/přehrávače: ${gone.map { it.episodeId }}")
        // Nejdřív ostatní, načtený díl nakonec (removeFromQueue u něj případně přeskočí na další povolený).
        val currentId = st.currentEpisodeId
        gone.sortedBy { it.episodeId == currentId }.forEach { connection.removeFromQueue(it.episodeId) }
    }

    /** true/false = díl je / není povolený; null = nejde rozhodnout (ABS, neznámý zdroj) → nesahat. */
    private fun allowed(q: QueuedEpisode, keys: Set<String>): Boolean? {
        val type = when {
            q.episodeId.startsWith("rss:") -> "rss"
            q.episodeId.startsWith("yt:") -> "youtube"
            q.episodeId.startsWith("ctv:") -> "ctv"
            else -> return null
        }
        // itemId direct dílu = ref zdroje (starší fronty z Domů: název pořadu).
        val src = sourcesRepo.sources.value.filter { it.type == type }
            .firstOrNull { it.ref == q.itemId || it.title.equals(q.itemId, ignoreCase = true) }
        val sourceKey = src?.key ?: "$type:${q.itemId}"
        if (KidsSourceAccess.isWholeSourceApproved(keys, sourceKey)) return true
        if (KidsSourceAccess.isEpisodeApproved(keys, sourceKey, KidsSourceAccess.episodeIdOfResumeKey(q.episodeId))) return true
        val slug = SeriesResolver.seriesSlug(ruleStore, sourceKey, src?.title ?: q.podcastTitle, q.title, null)
        if (slug != null) return KidsSourceAccess.isSeriesApproved(keys, sourceKey, slug)
        // Série jen z metadat dílu (fronta je nenese) → při schválené sérii pořadu nejde rozhodnout, nesahat (revize).
        return if (KidsSourceAccess.approvedSeries(keys, sourceKey).isNotEmpty()) null else false
    }
}
