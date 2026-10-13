package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.YtSearchChannel
import com.github.jankoran90.showlyfin.data.uploader.model.YtSearchVideo
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastsViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.YoutubeSearchViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.asEpisode
import com.github.jankoran90.showlyfin.feature.discover.podcasts.asSource
import com.github.jankoran90.showlyfin.feature.discover.podcasts.durationLabel

/**
 * AMFITEÁTR (Filmy 1.2.129) — výsledky „Hledat i na YouTube" nad výsledky filmů: řada videí (16:9, klik =
 * přehrát, dlouhý stisk = Přeložit do češtiny) a řada kanálů (klik = karta kanálu, odtud Sledovat).
 */
@Composable
internal fun FilmyYoutubeSearchResults(
    state: YoutubeSearchViewModel.UiState,
    podcasts: PodcastsViewModel,
    onPlay: (PodcastsViewModel.PlayTarget) -> Unit,
    onOpenSource: (PodcastSource) -> Unit,
) {
    if (!state.on || state.query.length < 2) return
    var confirmFor by remember { mutableStateOf<YtSearchVideo?>(null) }
    confirmFor?.let { v ->
        FilmyTranslateConfirmDialog(
            title = v.title,
            alreadyTranslated = "yt:${v.id}" in state.translated,
            onConfirm = { podcasts.translate(v.asSource(), v.asEpisode()); confirmFor = null },
            onDismiss = { confirmFor = null },
        )
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YouTube", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            if (state.loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(start = 8.dp).size(16.dp))
            }
        }
        if (state.videos.isEmpty() && state.channels.isEmpty()) {
            Text(
                if (state.loading) "Hledám na YouTube…" else state.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return@Column
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (state.videos.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.videos, key = { "v-${it.id}" }) { v ->
                    VideoCard(
                        video = v,
                        czech = "yt:${v.id}" in state.translated,
                        onClick = { podcasts.playTarget(v.asSource(), v.asEpisode())?.let(onPlay) },
                        onLongClick = { confirmFor = v },
                    )
                }
            }
        }
        if (state.channels.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(state.channels, key = { "c-${it.ref}" }) { c ->
                    ChannelChip(channel = c, onClick = { onOpenSource(c.asSource()) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(video: YtSearchVideo, czech: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.width(VideoWidth).clip(MaterialTheme.shapes.small).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(model = video.thumbnail, contentDescription = video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val badge = listOfNotNull(if (czech) "CZ" else null, durationLabel(video.duration)).joinToString(" · ")
            if (badge.isNotEmpty()) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Text(video.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(video.uploader, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChannelChip(channel: YtSearchChannel, onClick: () -> Unit) {
    Column(
        Modifier.width(ChannelWidth).clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = channel.thumbnail, contentDescription = channel.title, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(channel.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

private val VideoWidth = 220.dp
private val ChannelWidth = 84.dp
