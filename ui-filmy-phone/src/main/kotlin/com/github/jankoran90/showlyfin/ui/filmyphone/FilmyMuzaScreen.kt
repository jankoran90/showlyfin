package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.data.uploader.MuzaRepository

/**
 * MUZA (SHW-123) — „Podle tématu": napíšeš čemu se chceš věnovat, appka navrhne+ověří+okurátoruje
 * tituly (technika SUMÁŘ: ČSFD+Trakt ohlasy) a ukáže je jako karty s vysvětlením. Historie témat
 * dole na úvodní obrazovce, každé jde znovu otevřít nebo na něj navázat dalším kolem.
 */
@Composable
fun FilmyMuzaScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: FilmyMuzaViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(onMenu = onMenu) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.activeTopic != null) {
                    IconButton(onClick = { vm.closeActiveTopic() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět na témata")
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = state.activeTopic?.query ?: "Podle tématu",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            }
        }

        val topic = state.activeTopic
        if (topic == null) {
            MuzaHome(state, vm)
        } else {
            MuzaTopicResults(topic, state.searching, state.error, onOpenDetail, vm)
        }
    }
}

@Composable
private fun MuzaHome(state: FilmyMuzaViewModel.UiState, vm: FilmyMuzaViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "O čem to má být?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Popiš téma nebo přání vlastními slovy — najdu tituly, ověřím je a napíšu k nim krátký text z divácké a kritické recepce.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("např. filmy o horolezectví") },
                singleLine = true,
                enabled = !state.searching,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.search() }, enabled = !state.searching && state.query.isNotBlank()) {
                if (state.searching) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Icon(Icons.Rounded.Send, contentDescription = "Hledat")
            }
        }
        state.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        when {
            state.historyLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.history.isEmpty() -> FilmyEmpty(
                icon = Icons.Rounded.AutoAwesome,
                title = "Zatím žádná témata",
                text = "Napiš první téma nahoře — třeba \"nejlepší válečné drama posledních 10 let\".",
            )
            else -> {
                Text(
                    "Historie témat",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.history, key = { it.id }) { t -> MuzaHistoryRow(t) { vm.openTopic(t) } }
                }
            }
        }
    }
}

@Composable
private fun MuzaHistoryRow(t: MuzaRepository.TopicSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.query, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                if (t.status == "running") "Hledá se…" else "${t.count} " + pluralTitulu(t.count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (t.status == "running") CircularProgressIndicator(modifier = Modifier.size(16.dp))
    }
}

private fun pluralTitulu(n: Int): String = when {
    n == 1 -> "titul"
    n in 2..4 -> "tituly"
    else -> "titulů"
}

@Composable
private fun MuzaTopicResults(
    topic: MuzaRepository.TopicDetail,
    searching: Boolean,
    error: String?,
    onOpenDetail: (MediaItem) -> Unit,
    vm: FilmyMuzaViewModel,
) {
    Column(Modifier.fillMaxSize()) {
        when {
            topic.results.isEmpty() && searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Hledám a ověřuju tituly, chvíli to potrvá…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            topic.results.isEmpty() -> FilmyEmpty(
                icon = Icons.Rounded.AutoAwesome,
                title = "Nic se neověřilo",
                text = "Zkus téma popsat jinak nebo konkrétněji.",
            )
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(topic.results, key = { "${it.tmdbId}_${it.isShow}" }) { r ->
                    MuzaResultCard(r, onClick = { onOpenDetail(vm.toMediaItem(r)) })
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (searching) {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp).size(24.dp))
                        } else {
                            TextButton(onClick = { vm.continueActiveTopic() }) { Text("Najít další tituly") }
                        }
                    }
                }
            }
        }
        error?.let {
            Text(
                it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun MuzaResultCard(r: MuzaRepository.TopicResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier.height(140.dp).width(94.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (r.posterUrl != null) {
                AsyncImage(
                    model = r.posterUrl, contentDescription = r.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = if (r.isShow) Icons.Rounded.Tv else Icons.Rounded.Movie,
                    contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${r.title}${if (r.year > 0) " (${r.year})" else ""}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                r.blurb ?: "Popis se nepodařilo napsat — u titulu chybí dostatek ohlasů.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
            )
        }
    }
}
