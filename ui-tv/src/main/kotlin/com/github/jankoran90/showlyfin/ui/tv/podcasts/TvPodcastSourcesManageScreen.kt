package com.github.jankoran90.showlyfin.ui.tv.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastSourceManagerViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.sourceTypeLabel

/**
 * AMFITEÁTR F5 — TV „Spravovat zdroje": hledání (YouTube, ČT, podcasty) → Přidat, moje zdroje → Děti ano/ne,
 * Odebrat. Vše D-padem; seznam je společný se Slovem a telefonem (server `/api/sources`).
 */
@Composable
fun TvPodcastSourcesManageScreen(
    onBack: () -> Unit,
    onOpenSource: (PodcastSource) -> Unit,
    vm: PodcastSourceManagerViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    var removeFor by remember { mutableStateOf<PodcastSource?>(null) }
    LaunchedEffect(s.message) {
        s.message?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }
    androidx.activity.compose.BackHandler(onBack = onBack)
    removeFor?.let { src ->
        AlertDialog(
            onDismissRequest = { removeFor = null },
            title = { Text("Odebrat zdroj?") },
            text = { Text("„${src.title}“ zmizí z Podcastů ve Filmech i ve Slovu. Rozkoukané pozice zůstanou.") },
            confirmButton = { TextButton(onClick = { vm.remove(src); removeFor = null }, modifier = Modifier.tvFocusBorder()) { Text("Odebrat") } },
            dismissButton = { TextButton(onClick = { removeFor = null }, modifier = Modifier.tvFocusBorder()) { Text("Zrušit") } },
        )
    }

    Column(Modifier.fillMaxSize().tvOverscan(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Spravovat zdroje", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Hledat kanál YouTube, pořad ČT nebo podcast") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(0.7f).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) focusManager.moveFocus(FocusDirection.Down) else false
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PodcastSourceManagerViewModel.TypeFilter.entries.forEach { f ->
                FilterChip(selected = s.typeFilter == f, onClick = { vm.setTypeFilter(f) }, label = { Text(f.label) }, modifier = Modifier.tvFocusable())
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.query.trim().length >= 2) {
                item(key = "h-vysledky") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Header("Výsledky")
                        if (s.searching) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(start = 12.dp).size(20.dp))
                    }
                }
                if (s.searched && s.results.isEmpty() && !s.searching) item(key = "nic") { Note("Nic nenalezeno. Zkus jiný název nebo typ.") }
                items(s.results, key = { "r-${it.type}:${it.ref}" }) { r ->
                    val added = vm.isAdded(r)
                    Row(
                        Modifier.fillMaxWidth(0.85f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SourceInfo(
                            thumbnail = r.thumbnail, title = r.title,
                            subtitle = listOfNotNull(sourceTypeLabel(r.type), r.subtitle?.takeIf { it.isNotBlank() }).joinToString(" · "),
                            onClick = { onOpenSource(PodcastSource(id = "hledani:${r.type}:${r.ref}", type = r.type, ref = r.ref, title = r.title, thumbnail = r.thumbnail)) },
                            modifier = Modifier.weight(1f),
                        )
                        if (added) Text("Přidáno", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        else if (s.isAdult) Button(onClick = { vm.add(r) }, modifier = Modifier.tvFocusBorder()) { Text("Přidat") }
                    }
                }
            }
            item(key = "h-moje") { Header("Moje zdroje (${s.sources.size})") }
            if (s.sources.isEmpty()) item(key = "prazdne") { Note("Zatím žádné. Najdi kanál nebo pořad nahoře a přidej ho.") }
            items(s.sources, key = { "s-${it.key}" }) { src ->
                Row(
                    Modifier.fillMaxWidth(0.85f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SourceInfo(
                        thumbnail = src.thumbnail, title = src.title,
                        subtitle = sourceTypeLabel(src.type) + if (src.premium) " · rodinný" else "",
                        onClick = { onOpenSource(src) },
                        modifier = Modifier.weight(1f),
                    )
                    if (s.isAdult && s.hasKidsProfile) {
                        val forKids = src.key in s.kidsKeys
                        OutlinedButton(onClick = { vm.setVisibleForKids(src, !forKids) }, modifier = Modifier.tvFocusBorder()) {
                            Text(if (forKids) "Děti: ano" else "Děti: ne")
                        }
                    }
                    if (s.isAdult && !src.premium) {
                        OutlinedButton(onClick = { removeFor = src }, modifier = Modifier.tvFocusBorder()) { Text("Odebrat") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceInfo(thumbnail: String?, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.tvFocusBorder(shape = MaterialTheme.shapes.medium).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(model = thumbnail, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun Note(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
