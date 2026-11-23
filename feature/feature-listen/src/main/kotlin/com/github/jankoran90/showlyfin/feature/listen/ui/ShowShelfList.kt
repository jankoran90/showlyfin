package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User 2026-11-23: Domů › Pořady jako seznam (stejně jako knihy) — jednotný řádek [SlovoMediaRow] s ⋮,
 * velikost řádku podle volby v liště ([RowSize]).
 */
@Composable
internal fun ShowShelfList(
    cards: List<LibraryCard>,
    rowSize: RowSize,
    newestByKey: Map<String, Long>,
    onClick: (LibraryCard) -> Unit,
    onActions: (LibraryCard) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.miniPlayerReserve)) {
        items(cards, key = { it.itemKey }) { card ->
            val newest = card.hideKeys.maxOfOrNull { newestByKey[it] ?: 0L }?.takeIf { it > 0L }
            SlovoMediaRow(
                model = SlovoMediaModel(
                    title = card.sortTitle,
                    subtitle = card.typeLabel(),
                    coverUrl = card.coverUrl(),
                    meta = newest?.let { "díl ${SimpleDateFormat("d. M.", Locale("cs")).format(Date(it))}" },
                    placeholder = Icons.Rounded.Podcasts,
                ),
                onClick = { onClick(card) },
                onLongClick = { onActions(card) },
                coverSize = rowSize.cover,
                trailing = {
                    IconButton(onClick = { onActions(card) }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Akce") }
                },
            )
        }
    }
}

private fun LibraryCard.typeLabel(): String = when (this) {
    is LibraryCard.Merged -> "Audio + video"
    is LibraryCard.Abs -> "Podcast"
    is LibraryCard.Plain -> when {
        source.premium -> "Prémiový podcast"
        source.type == "youtube" -> "YouTube kanál"
        source.type == "ctv" -> "ČT iVysílání"
        else -> "Podcast"
    }
}

private fun LibraryCard.coverUrl(): String? = when (this) {
    is LibraryCard.Merged -> thumbnail
    is LibraryCard.Abs -> podcast.coverUrl
    is LibraryCard.Plain -> source.thumbnail
}

/** Kdy byl pořad přidán (sloučený = nejnovější člen); ABS podcast údaj nemá → 0. */
internal fun LibraryCard.addedAt(byKey: Map<String, PodcastSource>): Long = when (this) {
    is LibraryCard.Plain -> source.addedAt ?: 0L
    is LibraryCard.Merged -> members.maxOfOrNull { byKey[it]?.addedAt ?: 0L } ?: 0L
    is LibraryCard.Abs -> 0L
}
