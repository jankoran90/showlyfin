package com.github.jankoran90.showlyfin.ui.tv.ctv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import com.github.jankoran90.showlyfin.core.ui.EpisodeUi
import com.github.jankoran90.showlyfin.core.ui.TvEpisodeStrip
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
    onPlay: (url: String, label: String, posterUrl: String?, resumeKey: String) -> Unit,
    modifier: Modifier = Modifier,
    vm: CtvTitleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val play by vm.play.collectAsStateWithLifecycle()
    val watched by vm.watchedKeys.collectAsStateWithLifecycle()

    LaunchedEffect(title.sidp) { vm.load(title) }
    LaunchedEffect(play) {
        play?.let {
            vm.consumePlay()
            onPlay(it.url, it.title, it.posterUrl, it.resumeKey)
        }
    }

    // Titul otevřený z Filmotéky nese jen identitu + název, zbytek dotáhne VM → kresli z jeho stavu.
    val head = state.title ?: title

    Column(modifier.fillMaxSize().tvOverscan()) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
            if (head.thumbnail != null) {
                AsyncImage(
                    model = head.thumbnail,
                    contentDescription = head.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(320.dp).height(180.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(head.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                val meta = buildList {
                    add(if (head.isMovie) "Film" else "Pořad s díly")
                    head.year?.let { add(it.toString()) }
                    head.genres?.take(2)?.let { addAll(it) }
                    add("Česká televize")
                }.joinToString(" · ")
                Text(
                    meta, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
                if (!head.description.isNullOrBlank()) {
                    Text(
                        head.description!!, style = MaterialTheme.typography.bodyMedium,
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
                Row(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val movieIdec = head.idec.takeIf { head.isMovie && !it.isNullOrBlank() }
                    if (movieIdec != null) {
                        Button(
                            onClick = { vm.playIdec(movieIdec, head.title, head.thumbnail) },
                            enabled = state.resolvingIdec == null,
                            // Akcentní prstenec by na akcentním tlačítku splynul → kontrastní barva (viz tvFocusBorder).
                            modifier = Modifier.tvFocusBorder(color = MaterialTheme.colorScheme.onPrimary),
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
                    // VLTAVA F6b — parita s telefonem: ČT titul do Filmotéky (per profil, i u dětí).
                    OutlinedButton(
                        onClick = { vm.toggleFilmoteka() },
                        modifier = Modifier.tvFocusBorder(),
                    ) {
                        Icon(
                            if (state.inFilmoteka) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = null,
                        )
                        Text(
                            if (state.inFilmoteka) "Ve Filmotéce" else "Do Filmotéky",
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }

        // VLTAVA F5 — lišta sérií + „Označit sérii", 1:1 s epizodami seriálu z Jellyfinu. D-pad ji projede.
        val seasonLabel = state.selectedSeason ?: state.seasons.firstOrNull()?.label
        val shownEpisodes = vm.visibleEpisodes(state)
        if (state.seasons.size > 1 || (seasonLabel != null && shownEpisodes.isNotEmpty())) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.seasons.forEach { season ->
                    androidx.compose.material3.FilterChip(
                        selected = season.label == state.selectedSeason,
                        onClick = { vm.selectSeason(season.label) },
                        label = { Text("${season.label} (${season.episodes.size})") },
                        modifier = Modifier.tvFocusBorder(),
                    )
                }
                if (seasonLabel != null) {
                    val allWatched = shownEpisodes.isNotEmpty() &&
                        shownEpisodes.all { "ctv:${it.episode.id}" in watched }
                    androidx.compose.material3.FilterChip(
                        selected = false,
                        onClick = { vm.toggleSeasonWatched(seasonLabel) },
                        label = { Text(if (allWatched) "Odznačit sérii" else "Označit sérii") },
                        modifier = Modifier.tvFocusBorder(),
                    )
                }
            }
        }
        when {
            state.loadingEpisodes -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            // VODOROVNÁ lišta dílů — přesně jako u seriálů z Jellyfinu (user 2026-07-29: „na TV máme
            // horizontální lištu s díly … vezmi to 1:1"). Fokus padne na první nedokoukaný díl.
            shownEpisodes.isNotEmpty() -> Box(Modifier.fillMaxWidth().weight(1f)) {
                TvEpisodeStrip(
                    episodes = shownEpisodes.map { n ->
                        EpisodeUi(
                            key = n.episode.id,
                            seasonNumber = n.seasonNumber,
                            episodeNumber = n.episodeNumber,
                            title = n.cleanTitle,
                            imageUrl = n.episode.image,
                            meta = listOfNotNull(n.episode.date?.take(10), n.episode.label).joinToString(" · "),
                            watched = "ctv:${n.episode.id}" in watched,
                            loading = state.resolvingIdec == n.episode.id,
                        )
                    },
                    onPlay = { e ->
                        vm.playIdec(e.key, e.title.ifBlank { head.title }, e.imageUrl ?: head.thumbnail)
                    },
                    // Podržení OK na dálkáku = přepnout „zhlédnuto" (parita s telefonem).
                    onLongPress = { e -> vm.toggleEpisodeWatched(e.key) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
    // BACK řeší TvNavigator (stack) — vlastní BackHandler by mu bral přednost.
}
