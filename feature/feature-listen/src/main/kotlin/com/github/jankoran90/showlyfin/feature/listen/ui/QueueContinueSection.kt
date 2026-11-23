package com.github.jankoran90.showlyfin.feature.listen.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.HomeViewModel.ContinueItem

/**
 * User 2026-11-23 („a co fronta, co ji propojovat s rozposlouchanými?“): pod frontou sekce Rozposlouchané
 * (stejná data jako Přehled › Pokračovat, bez toho, co už je ve frontě nebo hraje). Ťuk = pokračovat,
 * tah doprava = díl přehrát další (kniha do fronty nejde), tah doleva = ukončit poslech (s potvrzením),
 * podržení / ⋮ = nabídka.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun LazyListScope.continueSection(
    items: List<ContinueItem>,
    onPlay: (ContinueItem) -> Unit,
    onActions: (ContinueItem) -> Unit,
    onPlayNext: (ContinueItem.Episode) -> Unit,
    onAskEnd: (ContinueItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "continue_header") {
        Text(
            "Rozposlouchané",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.l, top = Spacing.l, bottom = Spacing.xs),
        )
    }
    items(items, key = { "continue_" + it.continueKey() }) { item ->
        val state = rememberSwipeToDismissBoxState(
            confirmValueChange = { v ->
                when (v) {
                    SwipeToDismissBoxValue.StartToEnd -> (item as? ContinueItem.Episode)?.let(onPlayNext)
                    SwipeToDismissBoxValue.EndToStart -> onAskEnd(item)
                    else -> Unit
                }
                false // položka se vždy vrátí; zmizí až po skutečné akci (fronta / ukončení)
            },
        )
        SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = item is ContinueItem.Episode,
            backgroundContent = { ContinueSwipeBackground(state.dismissDirection) },
        ) {
            ContinueRow(item, onPlay, onActions, solid = state.dismissDirection != SwipeToDismissBoxValue.Settled)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
private fun ContinueRow(item: ContinueItem, onPlay: (ContinueItem) -> Unit, onActions: (ContinueItem) -> Unit, solid: Boolean) {
    val model = when (item) {
        is ContinueItem.Book -> bookModel(item.book)
        is ContinueItem.Episode -> SlovoMediaModel(
            title = item.episode.title,
            subtitle = item.sourceTitle,
            coverUrl = item.episode.imageUrl,
            progress = item.progress.takeIf { it > 0f },
            meta = remainingLabel(item.posMs, item.durMs),
            placeholder = Icons.Rounded.Headphones,
        )
    }
    SlovoMediaRow(
        model = model,
        onClick = { onPlay(item) },
        onLongClick = { onActions(item) },
        modifier = Modifier.background(if (solid) MaterialTheme.colorScheme.background else Color.Transparent),
        trailing = {
            IconButton(onClick = { onActions(item) }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Akce") }
        },
    )
}

@androidx.compose.runtime.Composable
private fun ContinueSwipeBackground(direction: SwipeToDismissBoxValue) {
    val (icon, label, align) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Triple(Icons.AutoMirrored.Rounded.PlaylistPlay, "Přehrát další", Alignment.CenterStart)
        SwipeToDismissBoxValue.EndToStart -> Triple(Icons.Rounded.Close, "Ukončit poslech", Alignment.CenterEnd)
        else -> { Box(Modifier.fillMaxSize()); return }
    }
    val color = if (direction == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onColor = if (direction == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Box(Modifier.fillMaxSize().background(color).padding(horizontal = Spacing.xl), contentAlignment = align) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = onColor)
            Text(label, color = onColor, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

/** Klíč položky — kniha podle id, díl podle resume klíče (stejný jako ve frontě). */
internal fun ContinueItem.continueKey(): String = when (this) {
    is ContinueItem.Book -> "book:" + book.id
    is ContinueItem.Episode -> "ep:" + (episode.resumeKey ?: episode.id)
}

/** Nabídka položky Rozposlouchaných. */
internal fun continueActions(
    item: ContinueItem,
    onPlay: () -> Unit,
    onEnqueue: (atFront: Boolean) -> Unit,
    onOpen: () -> Unit,
    onEnd: () -> Unit,
): List<ListenEpisodeAction> = buildList {
    add(ListenEpisodeAction(Icons.Rounded.Headphones, "Pokračovat", onClick = onPlay))
    if (item is ContinueItem.Episode) {
        add(ListenEpisodeAction(Icons.AutoMirrored.Rounded.PlaylistPlay, "Přehrát další") { onEnqueue(true) })
        add(ListenEpisodeAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Na konec fronty") { onEnqueue(false) })
    }
    add(ListenEpisodeAction(Icons.Rounded.OpenInNew, if (item is ContinueItem.Book) "Otevřít knihu" else "Otevřít pořad", onClick = onOpen))
    add(ListenEpisodeAction(Icons.Rounded.Close, "Ukončit poslech", confirmMessage = endMessage(item), onClick = onEnd))
}

internal fun endMessage(item: ContinueItem): String =
    if (item is ContinueItem.Book) "Kniha zmizí z Rozposlouchaných a uložená pozice se smaže."
    else "Díl zmizí z Rozposlouchaných a uložená pozice se smaže."

/** Volba „Rozposlouchané ve Frontě“ (Nastavení › Přehrávač), výchozí zapnuto. */
object QueuePrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("slovo_queue", Context.MODE_PRIVATE)
    fun showContinue(ctx: Context): Boolean = p(ctx).getBoolean("show_continue", true)
    fun setShowContinue(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("show_continue", v).apply()
}
