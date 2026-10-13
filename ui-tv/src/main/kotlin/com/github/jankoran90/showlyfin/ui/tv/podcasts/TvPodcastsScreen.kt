package com.github.jankoran90.showlyfin.ui.tv.podcasts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList

/**
 * AMFITEÁTR (FLM-05) F1 — sekce Podcasty: řady Pokračovat / Nové díly / Kanály nad zdroji Slova.
 * Karty = sdílený [TvRailList] (stejný fokus, scroll a immersive jako ostatní sekce TV).
 */
@Composable
fun TvPodcastsScreen(
    onOpenSource: (PodcastSource) -> Unit,
    onPlay: (TvPodcastsViewModel.PlayTarget) -> Unit,
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvPodcastsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.onEnter() }
    val translated by vm.translated.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    var confirmFor by remember { mutableStateOf<Pair<PodcastSource, SourceEpisode>?>(null) }

    val prefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE)
    val rowPrefs = remember { TvPodcastPrefs.rows(prefs) }
    val rails = remember(state, marks, translated, translating, rowPrefs) {
        podcastRails(state, marks, translated, translating, rowPrefs)
    }

    confirmFor?.let { (src, ep) ->
        TvTranslateConfirmDialog(
            episode = ep,
            alreadyTranslated = "yt:${ep.id}" in translated,
            onConfirm = { vm.translate(src, ep); confirmFor = null },
            onDismiss = { confirmFor = null },
        )
    }

    if (rails.isEmpty()) {
        Column(modifier.fillMaxSize().tvOverscan(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Podcasty", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            val msg = when {
                state.loading -> "Načítám podcasty…"
                state.error != null -> state.error!!
                else -> "Zatím tu nejsou žádné podcasty s videem. Kanály YouTube, pořady ČT a video podcasty " +
                    "přidáš v appce Slovo — tady se objeví samy."
            }
            Text(msg, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
        return
    }

    TvRailList(
        rails = rails,
        sectionTitle = "Podcasty",
        immersive = immersive,
        immersiveHeader = immersiveHeader,
        onFocusItem = onFocusItem,
        onItemClick = { item ->
            when (val ref = PodcastItemKey.parse(item.key)) {
                is PodcastItemKey.Source -> vm.sourceOf(ref.sourceKey)?.let(onOpenSource)
                is PodcastItemKey.Episode -> {
                    val src = vm.sourceOf(ref.sourceKey)
                    val ep = vm.episodeOf(ref.sourceKey, ref.episodeId)
                    if (src != null && ep != null) vm.playTarget(src, ep)?.let(onPlay)
                }
                null -> Unit
            }
        },
        // F2: podržení OK na dílu YouTube = „Přeložit do češtiny" (s potvrzením).
        onItemLongClick = { item ->
            val ref = PodcastItemKey.parse(item.key) as? PodcastItemKey.Episode ?: return@TvRailList
            val src = vm.sourceOf(ref.sourceKey) ?: return@TvRailList
            val ep = vm.episodeOf(ref.sourceKey, ref.episodeId) ?: return@TvRailList
            if (vm.canTranslate(src)) confirmFor = src to ep
        },
        modifier = modifier,
    )
}

/** Klíč karty → co reprezentuje (karta nese jen string, akce se dohledá ve VM). */
internal sealed interface PodcastItemKey {
    data class Source(val sourceKey: String) : PodcastItemKey
    data class Episode(val sourceKey: String, val episodeId: String) : PodcastItemKey

    companion object {
        fun source(src: PodcastSource) = "src|${src.key}"
        fun episode(src: PodcastSource, ep: SourceEpisode) = "ep|${src.key}|${ep.id}"

        fun parse(key: String): PodcastItemKey? {
            val parts = key.split('|')
            return when {
                parts.size == 2 && parts[0] == "src" -> Source(parts[1])
                parts.size >= 3 && parts[0] == "ep" -> Episode(parts[1], parts.drop(2).joinToString("|"))
                else -> null
            }
        }
    }
}

private fun podcastRails(
    state: TvPodcastsViewModel.UiState,
    marks: Map<String, VideoResumeStore.Mark>,
    translated: Set<String>,
    translating: Set<String>,
    rowPrefs: TvPodcastPrefs.Rows,
): List<TvRail> {
    if (state.sources.isEmpty()) return emptyList()
    val all = state.sources.flatMap { src -> state.episodes[src.key].orEmpty().map { src to it } }
    val keyOf = { ep: SourceEpisode -> ep.resumeKey ?: ep.id }
    val inProgress = all
        .mapNotNull { (src, ep) -> marks[keyOf(ep)]?.takeIf { it.posMs > 0 }?.let { Triple(src, ep, it) } }
        .sortedByDescending { it.third.updatedAt }
    val inProgressKeys = inProgress.map { keyOf(it.second) }.toSet()
    val newest = all
        .filter { (_, ep) -> keyOf(ep) !in inProgressKeys }
        .sortedByDescending { (_, ep) -> TvPodcastsViewModel.dateSortKey(ep.date) }
        .take(rowPrefs.newLimit)

    return buildList {
        if (rowPrefs.continueRow && inProgress.isNotEmpty()) add(
            TvRail(
                id = "podcasty-pokracovat", title = "Pokračovat", style = HomeCardStyle.LANDSCAPE,
                items = inProgress.map { (src, ep, mark) -> episodeItem(src, ep, mark, translated, translating) }, configId = "podcasty-pokracovat",
            ),
        )
        if (rowPrefs.newRow && newest.isNotEmpty()) add(
            TvRail(
                id = "podcasty-nove", title = "Nové díly", style = HomeCardStyle.LANDSCAPE,
                items = newest.map { (src, ep) -> episodeItem(src, ep, marks[keyOf(ep)], translated, translating) }, configId = "podcasty-nove",
            ),
        )
        // Řada kanálů je jediná cesta k celému seznamu dílů → když jsou ostatní řady vypnuté nebo
        // prázdné, ukaž ji i přes vypnutý přepínač (sekce nesmí zůstat prázdná).
        if (rowPrefs.channelsRow || isEmpty()) add(
            TvRail(
                id = "podcasty-kanaly", title = "Kanály a pořady", style = HomeCardStyle.LANDSCAPE,
                items = state.sources.map { src -> sourceItem(src, state.episodes[src.key].orEmpty()) },
                configId = "podcasty-kanaly",
            ),
        )
    }
}

internal fun episodeItem(
    src: PodcastSource,
    ep: SourceEpisode,
    mark: VideoResumeStore.Mark?,
    translated: Set<String> = emptySet(),
    translating: Set<String> = emptySet(),
): HomeRowItem =
    HomeRowItem(
        key = PodcastItemKey.episode(src, ep),
        title = ep.title,
        // F2: „CZ" = hotový český překlad, „Překládá se" = běží právě teď (na serveru).
        subtitle = listOfNotNull(
            when ("yt:${ep.id}") { in translated -> "CZ"; in translating -> "Překládá se"; else -> null },
            src.title, formatDate(ep.date),
        ).joinToString(" · "),
        landscapeUrl = ep.imageUrl ?: src.thumbnail,
        posterUrl = ep.imageUrl ?: src.thumbnail,
        progressPct = mark?.takeIf { it.durMs > 0 }?.let { (it.posMs * 100 / it.durMs).toInt().coerceIn(1, 99) },
    )

private fun sourceItem(src: PodcastSource, eps: List<SourceEpisode>): HomeRowItem =
    HomeRowItem(
        key = PodcastItemKey.source(src),
        title = src.title,
        subtitle = sourceTypeLabel(src.type) + (if (eps.isNotEmpty()) " · ${eps.size} dílů" else ""),
        landscapeUrl = src.thumbnail ?: eps.firstOrNull()?.imageUrl,
        posterUrl = src.thumbnail ?: eps.firstOrNull()?.imageUrl,
    )

internal fun sourceTypeLabel(type: String): String = when (type) {
    "youtube" -> "YouTube"
    "ctv" -> "Česká televize"
    else -> "Video podcast"
}

/** `yyyyMMdd` → „12. 10. 2026" (neznámý tvar = nic). */
internal fun formatDate(date: String?): String? {
    val k = TvPodcastsViewModel.dateSortKey(date)
    if (k.length != 8) return null
    return "${k.substring(6, 8).trimStart('0')}. ${k.substring(4, 6).trimStart('0')}. ${k.substring(0, 4)}"
}

