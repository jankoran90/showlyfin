package com.github.jankoran90.showlyfin.ui.tv.podcasts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList

/**
 * AMFITEÁTR (FLM-05) F1 — kanál YouTube / video podcast: řada „Pokračovat" (rozkoukané v tomto kanálu)
 * + „Díly" (nejnovější první). Data drží sdílený [TvPodcastsViewModel] sekce (activity scope) —
 * kanál se otevírá ze sekce, takže díly už jsou načtené a obrazovka naskočí bez čekání.
 */
@Composable
fun TvPodcastSourceScreen(
    source: PodcastSource,
    onPlay: (TvPodcastsViewModel.PlayTarget) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvPodcastsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.onEnter() }
    val translated by vm.translated.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
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

    val rails = remember(episodes, marks, translated, translating) {
        val keyOf = { e: com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode -> e.resumeKey ?: e.id }
        val started = episodes.filter { (marks[keyOf(it)]?.posMs ?: 0L) > 0L }
            .sortedByDescending { marks[keyOf(it)]?.updatedAt ?: 0L }
        buildList {
            if (started.isNotEmpty()) add(
                TvRail(
                    id = "kanal-pokracovat", title = "Pokračovat", style = HomeCardStyle.LANDSCAPE,
                    items = started.map { episodeItem(source, it, marks[keyOf(it)], translated, translating) }, configId = "kanal-pokracovat",
                ),
            )
            if (episodes.isNotEmpty()) add(
                TvRail(
                    id = "kanal-dily", title = "Díly", style = HomeCardStyle.LANDSCAPE,
                    items = episodes.map { episodeItem(source, it, marks[keyOf(it)], translated, translating) }, configId = "kanal-dily",
                ),
            )
        }
    }

    if (rails.isEmpty()) {
        Column(modifier.fillMaxSize().tvOverscan(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (state.loading) "Načítám díly…" else "Tenhle kanál zatím nemá žádné díly s videem.",
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
