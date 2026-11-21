package com.github.jankoran90.showlyfin.feature.listen.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.tokens.CoverSize
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.ui.BookmarkNoteDialog
import com.github.jankoran90.showlyfin.feature.listen.ui.BookmarkRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** AMPHORA (SHW-124) F5 / PR2 — záložky profilu pro obrazovku Záložky a detail knihy. */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val store: BookmarkStore,
    private val connection: AudiobookPlayerConnection,
) : ViewModel() {
    val bookmarks = store.bookmarks

    fun refresh() = store.syncNow()
    fun updateNote(id: String, note: String) = store.updateNote(id, note)
    fun delete(id: String) = store.delete(id)

    /** Díl s přímou adresou spustí rovnou odtud; false = kniha / ABS díl (otevře je přehrávač s pozicí). */
    fun playDirect(b: Bookmark): Boolean {
        val ep = b.toDirectEpisode() ?: return false
        connection.playEpisodeAt(ep, b.posMs)
        return true
    }
}

/**
 * Přehrání záložky: díl RSS/YouTube/ČT hned + otevře přehrávač, kniha a ABS díl přes přehrávač
 * s pozicí ([onOpenAt] = itemId, episodeId nebo null, start v s).
 */
class BookmarkOpeners(
    val onOpenAt: (itemId: String, episodeId: String?, startSec: Double) -> Unit,
    val onOpenPlayer: () -> Unit,
)

@Composable
private fun rememberPlay(vm: BookmarksViewModel, openers: BookmarkOpeners): (Bookmark) -> Unit = remember(vm, openers) {
    { b ->
        if (vm.playDirect(b)) openers.onOpenPlayer()
        else openers.onOpenAt(b.itemId, b.episodeId.takeIf { !b.isBook }, b.posMs / 1000.0)
    }
}

/** Obrazovka Záložky (nabídka Víc): po knihách a dílech, nejnovější nahoře. */
@Composable
fun BookmarksScreen(openers: BookmarkOpeners, modifier: Modifier = Modifier, viewModel: BookmarksViewModel = hiltViewModel()) {
    val all by viewModel.bookmarks.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val play = rememberPlay(viewModel, openers)
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    if (all.isEmpty()) {
        Box(modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
            Text(
                "Zatím žádné záložky.\nV přehrávači ťukni na Záložka, nebo dvakrát stiskni tlačítko na sluchátkách.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val groups = remember(all) { all.groupBy { it.itemKey }.values.map { g -> g.sortedBy { it.posMs } } }
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        groups.forEach { g ->
            val head = g.first()
            item(key = "h:" + head.itemKey) { GroupHeader(head, g.size) }
            items(g, key = { it.id }) { b ->
                BookmarkRow(
                    b, onPlay = { play(b) }, onEditNote = { editing = b }, onDelete = { viewModel.delete(b.id) },
                    modifier = Modifier.padding(horizontal = Spacing.s),
                )
            }
        }
        item { Box(Modifier.padding(bottom = Spacing.miniPlayerReserve)) }
    }
    NoteDialog(editing, viewModel) { editing = null }
}

/** Záložky jedné knihy v jejím detailu (nad kapitolami); bez záložek se nic neukáže. */
@Composable
fun ItemBookmarksSection(itemKey: String, openers: BookmarkOpeners, viewModel: BookmarksViewModel = hiltViewModel()) {
    val all by viewModel.bookmarks.collectAsStateWithLifecycle()
    val mine = remember(all, itemKey) { all.filter { it.itemKey == itemKey }.sortedBy { it.posMs } }
    if (mine.isEmpty()) return
    val play = rememberPlay(viewModel, openers)
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Záložky (${mine.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = Spacing.s),
        )
        mine.forEach { b -> BookmarkRow(b, onPlay = { play(b) }, onEditNote = { editing = b }, onDelete = { viewModel.delete(b.id) }) }
    }
    NoteDialog(editing, viewModel) { editing = null }
}

@Composable
private fun NoteDialog(editing: Bookmark?, viewModel: BookmarksViewModel, onDone: () -> Unit) {
    val b = editing ?: return
    BookmarkNoteDialog(
        initial = b.note, time = formatBookmarkTime(b.posMs),
        onSave = { viewModel.updateNote(b.id, it); onDone() },
        onDismiss = onDone,
    )
}

@Composable
private fun GroupHeader(b: Bookmark, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = b.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(CoverSize.thumb).clip(MaterialTheme.shapes.small),
        )
        Column(Modifier.weight(1f).padding(start = Spacing.m)) {
            Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(b.subtitle?.takeIf { it.isNotBlank() }, if (count == 1) "1 záložka" else if (count in 2..4) "$count záložky" else "$count záložek")
            Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
