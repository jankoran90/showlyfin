package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastSourceManagerViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.sourceTypeLabel

/**
 * AMFITEÁTR F5 — „Spravovat zdroje" v Podcastech telefonu: nahoře hledání (YouTube kanály, pořady ČT, podcasty)
 * → přidat, dole moje zdroje → schválit dětem / odebrat. Seznam je společný se Slovem (server `/api/sources`).
 */
@Composable
fun FilmyPodcastSourcesManageScreen(
    onBack: () -> Unit,
    onOpenSource: (PodcastSource) -> Unit,
    vm: PodcastSourceManagerViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var removeFor by remember { mutableStateOf<PodcastSource?>(null) }
    LaunchedEffect(s.message) {
        s.message?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }
    removeFor?.let { src ->
        AlertDialog(
            onDismissRequest = { removeFor = null },
            title = { Text("Odebrat zdroj?") },
            text = { Text("„${src.title}“ zmizí z Podcastů ve Filmech i ve Slovu. Rozkoukané pozice zůstanou.") },
            confirmButton = { TextButton(onClick = { vm.remove(src); removeFor = null }) { Text("Odebrat") } },
            dismissButton = { TextButton(onClick = { removeFor = null }) { Text("Zrušit") } },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět", tint = MaterialTheme.colorScheme.onBackground)
            }
            TextField(
                value = s.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Hledat kanál, pořad ČT, podcast…") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PodcastSourceManagerViewModel.TypeFilter.entries.forEach { f ->
                FilterChip(selected = s.typeFilter == f, onClick = { vm.setTypeFilter(f) }, label = { Text(f.label) })
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (s.query.trim().length >= 2) {
                item(key = "h-vysledky") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ManageHeader("Výsledky")
                        if (s.searching) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    }
                }
                if (s.searched && s.results.isEmpty() && !s.searching) {
                    item(key = "nic") { ManageNote("Nic nenalezeno. Zkus jiný název nebo typ.") }
                }
                items(s.results, key = { "r-${it.type}:${it.ref}" }) { r ->
                    val added = vm.isAdded(r)
                    SourceRow(
                        thumbnail = r.thumbnail,
                        title = r.title,
                        subtitle = listOfNotNull(sourceTypeLabel(r.type), r.subtitle?.takeIf { it.isNotBlank() }, r.episodeCount?.let { "$it dílů" })
                            .joinToString(" · "),
                        onClick = { onOpenSource(PodcastSource(id = "hledani:${r.type}:${r.ref}", type = r.type, ref = r.ref, title = r.title, thumbnail = r.thumbnail)) },
                        trailing = {
                            if (added) Icon(Icons.Rounded.Check, contentDescription = "Přidáno", tint = MaterialTheme.colorScheme.primary)
                            else if (s.isAdult) IconButton(onClick = { vm.add(r) }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Přidat", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
            item(key = "h-moje") { ManageHeader("Moje zdroje (${s.sources.size})") }
            if (s.sources.isEmpty()) item(key = "prazdne") { ManageNote("Zatím žádné. Najdi kanál nebo pořad nahoře a přidej ho.") }
            items(s.sources, key = { "s-${it.key}" }) { src ->
                SourceRow(
                    thumbnail = src.thumbnail,
                    title = src.title,
                    subtitle = sourceTypeLabel(src.type) + if (src.premium) " · rodinný" else "",
                    onClick = { onOpenSource(src) },
                    trailing = {
                        if (s.isAdult && s.hasKidsProfile) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Switch(checked = src.key in s.kidsKeys, onCheckedChange = { vm.setVisibleForKids(src, it) })
                                Text("Děti", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (s.isAdult && !src.premium) IconButton(onClick = { removeFor = src }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Odebrat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    thumbnail: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(model = thumbnail, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    }
}

@Composable
private fun ManageHeader(text: String) = Text(
    text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
)

@Composable
private fun ManageNote(text: String) = Text(
    text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)
