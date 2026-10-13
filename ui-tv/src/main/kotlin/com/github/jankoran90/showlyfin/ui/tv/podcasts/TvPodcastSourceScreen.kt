package com.github.jankoran90.showlyfin.ui.tv.podcasts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastItemKey
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastsViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.episodeItem
import com.github.jankoran90.showlyfin.feature.discover.podcasts.sourceTypeLabel
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList

/**
 * AMFITEÁTR (FLM-05) F1 — kanál YouTube / video podcast: řada „Pokračovat" (rozkoukané v tomto kanálu)
 * + „Díly" (nejnovější první). Data drží sdílený [PodcastsViewModel] sekce (activity scope) —
 * kanál se otevírá ze sekce, takže díly už jsou načtené a obrazovka naskočí bez čekání.
 */
@Composable
fun TvPodcastSourceScreen(
    source: PodcastSource,
    onPlay: (PodcastsViewModel.PlayTarget) -> Unit,
    modifier: Modifier = Modifier,
    vm: PodcastsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.onEnter() }
    // Kanál z hledání na YouTube (nesledovaný) → díly zvlášť + nabídka „Sledovat" (1.2.129).
    androidx.compose.runtime.LaunchedEffect(source.key, state.sources, state.loading) { if (!state.loading) vm.openExternal(source) }
    val followed = state.sources.any { it.key == source.key }
    var following by remember { mutableStateOf(false) }
    val followAction: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? =
        if (!followed && !state.loading && vm.canFollow()) {
            {
                androidx.compose.material3.Button(
                    onClick = { following = true; vm.follow(source) { following = false } },
                    enabled = !following,
                    modifier = Modifier.tvFocusBorder(),
                ) { Text(if (following) "Přidávám…" else "Sledovat") }
            }
        } else null
    val translated by vm.translated.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    var confirmFor by remember { mutableStateOf<com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode?>(null) }
    val episodes = state.episodes[source.key].orEmpty()
    val title = "${source.title} · ${sourceTypeLabel(source.type)}"

    confirmFor?.let { ep ->
        TvTranslateConfirmDialog(
            episode = ep,
            alreadyTranslated = "yt:${ep.id}" in translated,
            onConfirm = { vm.translate(source, ep); confirmFor = null },
            onDismiss = { confirmFor = null },
        )
    }

    val rails = remember(episodes, marks, translated, translating, watched) {
        val keyOf = { e: com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode -> e.resumeKey ?: e.id }
        val started = episodes.filter { (marks[keyOf(it)]?.posMs ?: 0L) > 0L }
            .sortedByDescending { marks[keyOf(it)]?.updatedAt ?: 0L }
        buildList {
            if (started.isNotEmpty()) add(
                TvRail(
                    id = "kanal-pokracovat", title = "Pokračovat", style = HomeCardStyle.LANDSCAPE,
                    items = started.map { episodeItem(source, it, marks[keyOf(it)], translated, translating, watched) }, configId = "kanal-pokracovat",
                ),
            )
            if (episodes.isNotEmpty()) add(
                TvRail(
                    id = "kanal-dily", title = "Díly", style = HomeCardStyle.LANDSCAPE,
                    items = episodes.map { episodeItem(source, it, marks[keyOf(it)], translated, translating, watched) }, configId = "kanal-dily",
                ),
            )
        }
    }

    if (rails.isEmpty()) {
        Column(modifier.fillMaxSize().tvOverscan(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (state.loading || source.key in state.loadingSources) "Načítám díly…" else "Tenhle kanál zatím nemá žádné díly s videem.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    TvRailList(
        rails = rails,
        sectionTitle = title,
        immersive = false,
        immersiveHeader = false,
        onFocusItem = {},
        sectionActions = followAction,
        onItemClick = { item ->
            val ref = PodcastItemKey.parse(item.key) as? PodcastItemKey.Episode ?: return@TvRailList
            val ep = vm.episodeOf(ref.sourceKey, ref.episodeId) ?: return@TvRailList
            vm.playTarget(source, ep)?.let(onPlay)
        },
        onItemLongClick = { item ->
            val ref = PodcastItemKey.parse(item.key) as? PodcastItemKey.Episode ?: return@TvRailList
            val ep = vm.episodeOf(ref.sourceKey, ref.episodeId) ?: return@TvRailList
            if (vm.canTranslate(source)) confirmFor = ep
        },
        modifier = modifier,
    )
}
