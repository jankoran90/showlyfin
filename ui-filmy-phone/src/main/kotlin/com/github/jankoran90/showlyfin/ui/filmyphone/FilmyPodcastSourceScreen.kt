package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastsViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.episodeItem
import com.github.jankoran90.showlyfin.feature.discover.podcasts.sourceTypeLabel

/**
 * AMFITEÁTR (FLM-05) F4 — karta kanálu / pořadu v telefonu: Pokračovat (rozkoukané) + všechny díly.
 * Sdílí [PodcastsViewModel] se sekcí (stejný ViewModelStore aktivity) → díly jsou už načtené.
 */
@Composable
fun FilmyPodcastSourceScreen(
    source: PodcastSource,
    onBack: () -> Unit,
    onPlay: (PodcastsViewModel.PlayTarget) -> Unit,
    modifier: Modifier = Modifier,
    vm: PodcastsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    val translated by vm.translated.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onEnter() }
    // Kanál z hledání na YouTube není mezi zdroji → díly se načtou zvlášť; nabídni „Sledovat".
    LaunchedEffect(source.key, state.sources, state.loading) { if (!state.loading) vm.openExternal(source) }
    val followed = state.sources.any { it.key == source.key }
    var following by remember { mutableStateOf(false) }
    var confirmFor by remember { mutableStateOf<SourceEpisode?>(null) }

    val episodes = state.episodes[source.key].orEmpty()
    val keyOf = { e: SourceEpisode -> e.resumeKey ?: e.id }
    val started = remember(episodes, marks) {
        episodes.filter { (marks[keyOf(it)]?.posMs ?: 0L) > 0L }.sortedByDescending { marks[keyOf(it)]?.updatedAt ?: 0L }
    }

    confirmFor?.let { ep ->
        FilmyTranslateConfirmDialog(
            title = ep.title,
            alreadyTranslated = "yt:${ep.id}" in translated,
            onConfirm = { vm.translate(source, ep); confirmFor = null },
            onDismiss = { confirmFor = null },
        )
    }

    @Composable
    fun EpisodeRow(ep: SourceEpisode) = FilmyPodcastRow(
        item = episodeItem(source, ep, marks[keyOf(ep)], translated, translating, watched),
        onClick = { vm.playTarget(source, ep)?.let(onPlay) },
        onLongClick = if (vm.canTranslate(source)) { { confirmFor = ep } } else null,
    )

    // Karta leží mimo Scaffold sekcí → odsazení pod stavový řádek si řeší sama.
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět", tint = MaterialTheme.colorScheme.onBackground) }
            Column(Modifier.weight(1f)) {
                Text(source.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 2)
                Text(sourceTypeLabel(source.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!followed && !state.loading && vm.canFollow()) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { following = true; vm.follow(source) { following = false } },
                    enabled = !following,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(if (following) "Přidávám…" else "Sledovat") }
            }
        }
        if (episodes.isEmpty()) {
            Text(
                if (state.loading || source.key in state.loadingSources) "Načítám díly…" else "Tenhle kanál zatím nemá žádné díly s videem.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (started.isNotEmpty()) {
                item(key = "h-pokracovat") { PodcastSectionHeader("Pokračovat") }
                items(started, key = { "p-${it.id}" }) { EpisodeRow(it) }
            }
            item(key = "h-dily") { PodcastSectionHeader("Díly") }
            items(episodes, key = { "d-${it.id}" }) { EpisodeRow(it) }
        }
    }
}

@Composable
private fun PodcastSectionHeader(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
)
