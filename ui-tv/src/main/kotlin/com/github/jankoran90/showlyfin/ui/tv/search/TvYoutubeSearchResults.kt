package com.github.jankoran90.showlyfin.ui.tv.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastsViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.YoutubeSearchViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.asEpisode
import com.github.jankoran90.showlyfin.feature.discover.podcasts.asSource
import com.github.jankoran90.showlyfin.feature.discover.podcasts.durationLabel
import com.github.jankoran90.showlyfin.ui.tv.components.TvHomeCard
import com.github.jankoran90.showlyfin.ui.tv.podcasts.TvTranslateConfirmDialog

/**
 * AMFITEÁTR (Filmy 1.2.129) — TV Hledat se zapnutým „Hledat i na YouTube": svislý seznam řad
 * YouTube videa (OK = přehrát, podržení OK = Přeložit do češtiny) · YouTube kanály (karta kanálu) ·
 * výsledky filmů/ČT ([otherResults], aby zůstaly vidět i vedle YouTube). Karty = sdílená [TvHomeCard].
 */
@Composable
internal fun TvYoutubeSearchResults(
    state: YoutubeSearchViewModel.UiState,
    podcasts: PodcastsViewModel,
    onPlay: (PodcastsViewModel.PlayTarget) -> Unit,
    onOpenSource: (PodcastSource) -> Unit,
    modifier: Modifier = Modifier,
    otherResults: @Composable () -> Unit,
) {
    var confirmFor by remember { mutableStateOf<Pair<PodcastSource, SourceEpisode>?>(null) }
    confirmFor?.let { (src, ep) ->
        TvTranslateConfirmDialog(
            episode = ep,
            alreadyTranslated = "yt:${ep.id}" in state.translated,
            onConfirm = { podcasts.translate(src, ep); confirmFor = null },
            onDismiss = { confirmFor = null },
        )
    }
    val videoItems = remember(state.videos, state.translated) {
        state.videos.map { v ->
            HomeRowItem(
                key = "ytv|${v.id}",
                title = v.title,
                subtitle = listOfNotNull(if ("yt:${v.id}" in state.translated) "CZ" else null, v.uploader, durationLabel(v.duration))
                    .joinToString(" · "),
                landscapeUrl = v.thumbnail,
                posterUrl = v.thumbnail,
            )
        }
    }
    val channelItems = remember(state.channels) {
        state.channels.map { c -> HomeRowItem(key = "ytc|${c.ref}", title = c.title, subtitle = "YouTube kanál", landscapeUrl = c.thumbnail, posterUrl = c.thumbnail) }
    }

    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "yt-head") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
                Text("YouTube", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                if (state.loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(start = 12.dp).size(20.dp))
                val note = when {
                    state.loading && videoItems.isEmpty() -> "Hledám na YouTube…"
                    else -> state.error
                }
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        if (videoItems.isNotEmpty()) item(key = "yt-videos") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                items(videoItems, key = { it.key }) { item ->
                    val v = state.videos.first { "ytv|${it.id}" == item.key }
                    TvHomeCard(
                        item = item,
                        style = HomeCardStyle.LANDSCAPE,
                        onClick = { podcasts.playTarget(v.asSource(), v.asEpisode())?.let(onPlay) },
                        onLongClick = { confirmFor = v.asSource() to v.asEpisode() },
                    )
                }
            }
        }
        if (channelItems.isNotEmpty()) item(key = "yt-channels") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                items(channelItems, key = { it.key }) { item ->
                    val c = state.channels.first { "ytc|${it.ref}" == item.key }
                    TvHomeCard(item = item, style = HomeCardStyle.LANDSCAPE, onClick = { onOpenSource(c.asSource()) })
                }
            }
        }
        item(key = "other") { otherResults() }
    }
}
