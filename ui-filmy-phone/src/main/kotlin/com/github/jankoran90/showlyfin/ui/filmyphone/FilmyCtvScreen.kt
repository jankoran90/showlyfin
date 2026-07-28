package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.github.jankoran90.showlyfin.data.uploader.model.CtvEpisode
import com.github.jankoran90.showlyfin.data.uploader.model.CtvTitle
import com.github.jankoran90.showlyfin.ui.phone.CtvTitleViewModel

/**
 * VLTAVA (SHW-110) F6 — karta titulu z ČT iVysílání na telefonu.
 *
 * Film = jedno tlačítko „Přehrát"; pořad = seznam dílů (nejnovější první, tak je vrací ČT). Odkaz na
 * video vzniká až tady na zařízení (viz [CtvTitleViewModel]) — proto tlačítko chvíli točí kolečkem.
 * Titul z ČT nemá TMDB identitu, takže nejde přes sdílený `DetailScreen` (ten stojí na tmdb/imdb).
 */
@Composable
fun FilmyCtvScreen(
    title: CtvTitle,
    onBack: () -> Unit,
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

    // Titul otevřený z Filmotéky přijde jen s identitou + názvem; zbytek (popis, obrázek, `idec`)
    // dotáhne VM. Kreslíme proto VŽDY z jeho stavu a parametr je jen výchozí hodnota.
    val head = state.title ?: title

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                if (head.thumbnail != null) {
                    AsyncImage(
                        model = head.thumbnail,
                        contentDescription = head.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(4.dp).clip(CircleShape),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Zpět",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(head.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                val meta = buildList {
                    head.year?.let { add(it.toString()) }
                    head.genres?.take(2)?.let { addAll(it) }
                    add("Česká televize")
                    minutesOf(head.duration)?.let { add(it) }
                }.joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (!head.description.isNullOrBlank()) {
                    Text(
                        head.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                state.error?.let { err ->
                    Text(
                        err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp).clickable { vm.dismissError() },
                    )
                }
                // Zobrazuj VŽDY živý stav z VM (po dohydrataci z Filmotéky zná `idec` až on, ne parametr).
                val shown = head
                Row(
                    Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Film → přehraj rovnou. Pořad se přehrává po dílech (níž).
                    val movieIdec = shown.idec.takeIf { shown.isMovie && !it.isNullOrBlank() }
                    if (movieIdec != null) {
                        Button(
                            onClick = { vm.playIdec(movieIdec, shown.title, shown.thumbnail) },
                            enabled = state.resolvingIdec == null,
                        ) {
                            if (state.resolvingIdec != null) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            }
                            Text("Přehrát", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    // VLTAVA F6b — titul z ČT do Filmotéky (uloží se jako zapamatovaný zdroj pod vlastní
                    // identitou, takže je rovnou i přehratelný). Per profil → u dětí se objeví jen to,
                    // co se přidá na dětském profilu.
                    OutlinedButton(onClick = { vm.toggleFilmoteka() }) {
                        Icon(
                            if (state.inFilmoteka) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = null,
                        )
                        Text(
                            if (state.inFilmoteka) "Ve Filmotéce" else "Do Filmotéky",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        if (state.loadingEpisodes) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (state.episodes.isNotEmpty()) {
            item {
                Text(
                    "Díly (${state.episodes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
                )
            }
            items(state.episodes, key = { it.id }) { ep ->
                CtvEpisodeRow(
                    episode = ep,
                    resolving = state.resolvingIdec == ep.id,
                    onClick = { vm.playIdec(ep.id, ep.title.ifBlank { title.title }, ep.image ?: title.thumbnail) },
                )
            }
        }
    }
}

@Composable
private fun CtvEpisodeRow(episode: CtvEpisode, resolving: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (episode.image != null) {
                AsyncImage(
                    model = episode.image,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (resolving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                episode.title.ifBlank { "Díl" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(episode.date?.take(10)?.czDate(), episode.label).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** „2026-06-17" → „17. 6. 2026" (ČT posílá ISO datum; nechceme na kartě strojový tvar). */
private fun String.czDate(): String? {
    val p = split("-")
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return "$d. $m. $y"
}

/** Stopáž v sekundách → „21 min" (ČT ji zná jen u filmů). */
private fun minutesOf(duration: Double?): String? =
    duration?.takeIf { it > 0 }?.let { "${(it / 60).toInt()} min" }
