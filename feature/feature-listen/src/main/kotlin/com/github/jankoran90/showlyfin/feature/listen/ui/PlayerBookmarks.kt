package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.bookmarks.Bookmark
import com.github.jankoran90.showlyfin.feature.listen.bookmarks.formatBookmarkTime

/**
 * AMPHORA (SHW-124) F5 / PR2 — záložky v UI: značky nad časovou osou, potvrzení po uložení s nabídkou
 * poznámky, řádek záložky (přehrávač, detail knihy, obrazovka Záložky) a dialog poznámky.
 */

/** Tečky záložek nad časovou osou (stejné odsazení jako dráha posuvníku). */
@Composable
internal fun BookmarkMarks(bookmarks: List<Bookmark>, durationMs: Long, modifier: Modifier = Modifier) {
    if (bookmarks.isEmpty() || durationMs <= 0L) return
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.fillMaxWidth().height(6.dp).padding(horizontal = 10.dp)) {
        val r = size.height / 2f
        bookmarks.forEach { b ->
            val x = (b.posMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
            drawCircle(color, radius = r, center = Offset(x, r))
        }
    }
}

/** Potvrzení „Záložka 12:40 uložena“ s tlačítkem Poznámka. */
@Composable
internal fun BookmarkSavedBanner(bookmark: Bookmark, onNote: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Row(Modifier.padding(start = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                "Záložka ${formatBookmarkTime(bookmark.posMs)} uložena",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.s),
            )
            TextButton(onClick = onNote) { Text("Poznámka") }
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

/** Dialog poznámky k záložce (prázdná = bez poznámky). */
@Composable
internal fun BookmarkNoteDialog(initial: String, time: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Poznámka k záložce $time") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(500) },
                placeholder = { Text("Co se tu stalo, proč se sem vrátit…") },
                minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Uložit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}

/**
 * Řádek záložky: čas, kapitola / název, poznámka. Ťuk = přehrát odtud, ⋮ (i dlouhý stisk) = poznámka
 * nebo smazání. [showItem] = ukázat i název knihy / dílu (seznam napříč položkami).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookmarkRow(
    bookmark: Bookmark,
    onPlay: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showItem: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth()
            .combinedClickable(onClick = onPlay, onLongClick = { menu = true })
            .padding(start = Spacing.s, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatBookmarkTime(bookmark.posMs),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = Spacing.m),
        )
        Column(Modifier.weight(1f)) {
            val head = if (showItem) bookmark.title else bookmark.chapter
            if (!head.isNullOrBlank()) {
                Text(head, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (bookmark.note.isNotBlank()) {
                Text(
                    "„${bookmark.note}“",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            } else if (head.isNullOrBlank()) {
                Text("Bez poznámky", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Možnosti záložky") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Přehrát odtud") }, onClick = { menu = false; onPlay() })
                DropdownMenuItem(
                    text = { Text(if (bookmark.note.isBlank()) "Přidat poznámku" else "Upravit poznámku") },
                    onClick = { menu = false; onEditNote() },
                )
                DropdownMenuItem(text = { Text("Smazat záložku") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Záložky právě hrané položky v přehrávači (místo kapitol / fronty). Ťuk = skok na místo. */
@Composable
internal fun InlineBookmarkList(
    bookmarks: List<Bookmark>,
    onSeek: (Bookmark) -> Unit,
    onEditNote: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        items(bookmarks, key = { it.id }) { b ->
            BookmarkRow(b, onPlay = { onSeek(b) }, onEditNote = { onEditNote(b) }, onDelete = { onDelete(b) })
        }
    }
}

/** Seznam záložek v přehrávači i s dialogem poznámky (ťuk = skok, ⋮ = poznámka / smazání). */
@Composable
internal fun PlayerBookmarkList(
    bookmarks: List<Bookmark>,
    onSeek: (Bookmark) -> Unit,
    onSaveNote: (Bookmark, String) -> Unit,
    onDelete: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    InlineBookmarkList(bookmarks, onSeek = onSeek, onEditNote = { editing = it }, onDelete = onDelete, modifier = modifier)
    editing?.let { b ->
        BookmarkNoteDialog(
            initial = b.note, time = formatBookmarkTime(b.posMs),
            onSave = { onSaveNote(b, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

/** Přepínač seznamu pod ovládáním: Kapitoly / Fronta ↔ Záložky (N). */
@Composable
internal fun PlayerListToggle(otherLabel: String?, bookmarkCount: Int, showBookmarks: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (otherLabel != null) {
            androidx.compose.material3.FilterChip(
                selected = !showBookmarks, onClick = { onChange(false) }, label = { Text(otherLabel) },
            )
        }
        androidx.compose.material3.FilterChip(
            selected = showBookmarks || otherLabel == null, onClick = { onChange(true) },
            label = { Text("Záložky ($bookmarkCount)") }, leadingIcon = { BookmarkChipIcon() },
        )
    }
}

/** Ikona velikosti pilulek přehrávače. */
@Composable
internal fun BookmarkChipIcon() {
    Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(Spacing.l))
}
