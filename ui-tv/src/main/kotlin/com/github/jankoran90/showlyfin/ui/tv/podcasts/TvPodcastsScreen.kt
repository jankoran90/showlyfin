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
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastItemKey
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastPrefs
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastShelves
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastsViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.podcastShelves
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
    onPlay: (PodcastsViewModel.PlayTarget) -> Unit,
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    vm: PodcastsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.onEnter() }
    val translated by vm.translated.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    var confirmFor by remember { mutableStateOf<Pair<PodcastSource, SourceEpisode>?>(null) }

    val prefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE)
    val rowPrefs = remember { PodcastPrefs.rows(prefs) }
    val rails = remember(state, marks, translated, translating, rowPrefs) {
        podcastRails(podcastShelves(state, marks, translated, translating, rowPrefs))
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

private fun podcastRails(shelves: PodcastShelves): List<TvRail> = buildList {
    if (shelves.continueItems.isNotEmpty()) add(
        TvRail(id = "podcasty-pokracovat", title = "Pokračovat", style = HomeCardStyle.LANDSCAPE,
            items = shelves.continueItems, configId = "podcasty-pokracovat"),
    )
    if (shelves.newItems.isNotEmpty()) add(
        TvRail(id = "podcasty-nove", title = "Nové díly", style = HomeCardStyle.LANDSCAPE,
            items = shelves.newItems, configId = "podcasty-nove"),
    )
    if (shelves.channelItems.isNotEmpty()) add(
        TvRail(id = "podcasty-kanaly", title = "Kanály a pořady", style = HomeCardStyle.LANDSCAPE,
            items = shelves.channelItems, configId = "podcasty-kanaly"),
    )
}
