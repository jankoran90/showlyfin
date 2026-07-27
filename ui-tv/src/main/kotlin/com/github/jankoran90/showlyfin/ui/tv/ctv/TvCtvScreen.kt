package com.github.jankoran90.showlyfin.ui.tv.ctv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.data.uploader.model.CtvEpisode
import com.github.jankoran90.showlyfin.data.uploader.model.CtvTitle
import com.github.jankoran90.showlyfin.ui.phone.CtvTitleViewModel

/**
 * VLTAVA (SHW-110) F6 — karta titulu z ČT iVysílání na TV (parita s telefonním `FilmyCtvScreen`,
 * sdílený mozek [CtvTitleViewModel]). Film = tlačítko „Přehrát", pořad = D-pad seznam dílů.
 *
 * Odkaz na video vzniká na ZAŘÍZENÍ (playlist API ČT je geoblokované na náš server), takže ťuk
 * chvíli točí kolečkem a teprve pak skočí do přehrávače.
 */
@Composable
fun TvCtvScreen(
    title: CtvTitle,
    onPlay: (url: String, label: String, posterUrl: String?) -> Unit,
    modifier: Modifier = Modifier,
    vm: CtvTitleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val play by vm.play.collectAsStateWithLifecycle()

    LaunchedEffect(title.sidp) { vm.load(title) }
    LaunchedEffect(play) {
        play?.let {
            vm.consumePlay()
            onPlay(it.url, it.title, it.posterUrl)
        }
    }

    Column(modifier.fillMaxSize().tvOverscan()) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
            if (title.thumbnail != null) {
                AsyncImage(
                    model = title.thumbnail,
                    contentDescription = title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(320.dp).height(180.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                val meta = buildList {
                    add(if (title.isMovie) "Film" else "Pořad s díly")
                    title.year?.let { add(it.toString()) }
                    title.genres?.take(2)?.let { addAll(it) }
                    add("Česká televize")
                }.joinToString(" · ")
                Text(
                    meta, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
                if (!title.description.isNullOrBlank()) {
                    Text(
                        title.description!!, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 4,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                state.error?.let { err ->
                    Text(
                        err, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                val movieIdec = title.idec.takeIf { title.isMovie && !it.isNullOrBlank() }
                if (movieIdec != null) {
                    Button(
                        onClick = { vm.playIdec(movieIdec, title.title, title.thumbnail) },
                        enabled = state.resolvingIdec == null,
                        // Akcentní prstenec by na akcentním tlačítku splynul → kontrastní barva (viz tvFocusBorder).
                        modifier = Modifier.padding(top = 16.dp)
                            .tvFocusBorder(color = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        if (state.resolvingIdec != null) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        }
                        Text("Přehrát", modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }

        when {
            state.loadingEpisodes -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.episodes.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(state.episodes, key = { it.id }) { ep ->
                    TvCtvEpisodeRow(
                        episode = ep,
                        resolving = state.resolvingIdec == ep.id,
                        onClick = { vm.playIdec(ep.id, ep.title.ifBlank { title.title }, ep.image ?: title.thumbnail) },
                    )
                }
            }
        }
    }
    // BACK řeší TvNavigator (stack) — vlastní BackHandler by mu bral přednost.
}

@Composable
private fun TvCtvEpisodeRow(episode: CtvEpisode, resolving: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tvFocusBorder(shape = RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(160.dp).height(90.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (episode.image != null) {
                AsyncImage(
                    model = episode.image, contentDescription = episode.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            if (resolving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                episode.title.ifBlank { "Díl" }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(episode.date?.take(10), episode.label).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
