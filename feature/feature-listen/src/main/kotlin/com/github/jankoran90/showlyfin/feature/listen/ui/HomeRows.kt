package com.github.jankoran90.showlyfin.feature.listen.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.feature.listen.PodcastTimelineViewModel.TimelineItem
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore

/**
 * AMPHORA (SHW-124) F2 / DO1 — Domů jako rozcestník: řady pod sebou (Pokračovat · Nové díly ·
 * Další v sérii · Krátké do N min · Nově v knihovně). Prázdná řada se sama skryje. Ťuk na kartu =
 * přehrát, ⋮ / podržení = další akce (do fronty, otevřít), ťuk na název řady = celý seznam.
 * Které řady a v jakém pořadí + délka „krátkého“ → Nastavení › Zobrazení Domů ([HomeRowsPrefs]).
 */
enum class HomeRow(val label: String) {
    CONTINUE("Pokračovat"),
    NEW_EPISODES("Nové díly"),
    NEXT_IN_SERIES("Další v sérii"),
    SHORT("Krátké díly"),
    RECENT_BOOKS("Nově v knihovně"),
}

object HomeRowsPrefs {
    private const val PREFS = "slovo_home_rows"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val shortChoices = listOf(10, 20, 30, 45)

    fun order(ctx: Context): List<HomeRow> {
        val saved = p(ctx).getString("order", null)?.split(",")?.mapNotNull { n -> HomeRow.entries.firstOrNull { it.name == n } }?.distinct().orEmpty()
        return saved + HomeRow.entries.filter { it !in saved }
    }
    fun setOrder(ctx: Context, order: List<HomeRow>) = p(ctx).edit().putString("order", order.joinToString(",") { it.name }).apply()

    fun hidden(ctx: Context): Set<HomeRow> =
        p(ctx).getStringSet("hidden", emptySet()).orEmpty().mapNotNull { n -> HomeRow.entries.firstOrNull { it.name == n } }.toSet()
    fun setHidden(ctx: Context, row: HomeRow, hidden: Boolean) {
        val cur = hidden(ctx).toMutableSet().apply { if (hidden) add(row) else remove(row) }
        p(ctx).edit().putStringSet("hidden", cur.map { it.name }.toSet()).apply()
    }

    fun shortMinutes(ctx: Context): Int = p(ctx).getInt("short_min", 20)
    fun setShortMinutes(ctx: Context, v: Int) = p(ctx).edit().putInt("short_min", v).apply()

    /** Viditelné řady v pořadí (Pokračovat se nikdy neskrývá úplně — jeho karty jsou jádro Domů). */
    fun visible(ctx: Context): List<HomeRow> {
        val h = hidden(ctx) - HomeRow.CONTINUE
        return order(ctx).filter { it !in h }
    }
}

/** Jedna položka řady: díl z odběrů, nebo kniha. */
sealed interface HomeRowItem {
    val key: String
    data class Episode(val item: TimelineItem) : HomeRowItem { override val key get() = "ep:" + item.key }
    data class Book(val book: Audiobook) : HomeRowItem { override val key get() = "book:" + book.id }
}

private fun TimelineItem.notStarted(marks: Map<String, DirectResumeStore.Mark>): Boolean {
    val m = marks[key] ?: return true
    return !m.isFinished && m.posMs <= 0L
}

/** Nové nespuštěné díly z odběrů, nejnovější první. */
fun newEpisodesRow(timeline: List<TimelineItem>, marks: Map<String, DirectResumeStore.Mark>): List<HomeRowItem> =
    timeline.filter { it.notStarted(marks) }.take(ROW_LIMIT).map { HomeRowItem.Episode(it) }

/** Nespuštěné díly do [maxMinutes] (známá délka), nejnovější první. */
fun shortEpisodesRow(timeline: List<TimelineItem>, marks: Map<String, DirectResumeStore.Mark>, maxMinutes: Int): List<HomeRowItem> =
    timeline.filter { it.episode.durationSec > 0 && it.episode.durationSec <= maxMinutes * 60 && it.notStarted(marks) }
        .take(ROW_LIMIT).map { HomeRowItem.Episode(it) }

/**
 * Další díl série: v sérii je aspoň jeden díl dohraný a za posledním dohraným čeká nezačatý díl
 * (rozposlouchané jsou v Pokračovat). Číslo dílu z ABS „Série #N“.
 */
fun nextInSeriesRow(books: List<Audiobook>): List<HomeRowItem> =
    books.filter { !it.seriesName.isNullOrBlank() }
        .groupBy { seriesBaseName(it.seriesName!!) }
        .values.mapNotNull { group ->
            val seq = group.mapNotNull { b -> (b.seriesSequence?.toDoubleOrNull() ?: seriesSequenceOf(b.seriesName!!))?.let { it to b } }
                .sortedBy { it.first }
            val lastDone = seq.lastOrNull { it.second.isFinished }?.first ?: return@mapNotNull null
            val next = seq.firstOrNull { (n, b) -> n > lastDone && !b.isFinished } ?: return@mapNotNull null
            next.second.takeIf { it.progress <= 0.0 && it.currentTimeSec <= 0.0 }
        }
        .sortedByDescending { it.lastUpdate ?: it.addedAt ?: 0L }
        .take(ROW_LIMIT).map { HomeRowItem.Book(it) }

/** Knihy přidané za posledních [RECENT_DAYS] dní, ještě nezačaté (typicky dovoz z Audioteky). */
fun recentBooksRow(books: List<Audiobook>, now: Long = System.currentTimeMillis()): List<HomeRowItem> =
    books.filter { b ->
        val added = b.addedAt ?: return@filter false
        now - added <= RECENT_DAYS * 86_400_000L && !b.isFinished && b.progress <= 0.0 && b.currentTimeSec <= 0.0
    }.sortedByDescending { it.addedAt }.take(ROW_LIMIT).map { HomeRowItem.Book(it) }

private const val ROW_LIMIT = 20
private const val RECENT_DAYS = 60L

/** Vodorovná řada jednotných karet s nadpisem (ťuk na nadpis = celý seznam, když [onShowAll] není null). */
@Composable
fun HomeRowStrip(
    title: String,
    items: List<HomeRowItem>,
    playingKey: String?,
    onShowAll: (() -> Unit)?,
    onPlay: (HomeRowItem) -> Unit,
    actionsFor: (HomeRowItem) -> List<ListenEpisodeAction>,
) {
    var actionItem by remember { mutableStateOf<HomeRowItem?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onShowAll != null) Modifier.clickable(onClick = onShowAll) else Modifier)
                .padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (onShowAll != null) Icon(Icons.Rounded.ChevronRight, contentDescription = "Celý seznam", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), contentPadding = PaddingValues(horizontal = Spacing.xs)) {
            items(items, key = { it.key }) { item ->
                SlovoMediaCard(
                    model = rowModel(item, playing = item.key == playingKey),
                    onClick = { onPlay(item) },
                    onLongClick = { actionItem = item },
                    onMore = { actionItem = item },
                )
            }
        }
    }
    actionItem?.let { item ->
        ListenEpisodeActionSheet(
            title = when (item) {
                is HomeRowItem.Episode -> item.item.episode.title
                is HomeRowItem.Book -> item.book.title
            },
            actions = actionsFor(item),
            onDismiss = { actionItem = null },
        )
    }
}

private fun rowModel(item: HomeRowItem, playing: Boolean): SlovoMediaModel = when (item) {
    is HomeRowItem.Book -> bookModel(item.book, playing = playing)
    is HomeRowItem.Episode -> {
        val ep = item.item.episode
        val durMs = (ep.durationSec * 1000).toLong()
        SlovoMediaModel(
            title = ep.title,
            subtitle = item.item.sourceTitle,
            coverUrl = ep.imageUrl,
            meta = durMs.takeIf { it > 0 }?.let { durationLabel(it) },
            // V řadě jednotná výška karet → i video náhled jako čtverec (výřez).
            playing = playing,
            placeholder = Icons.Rounded.Podcasts,
        )
    }
}

/** Akce pro díl z řady (stejné jako v Timeline): přehrát, do fronty, otevřít pořad. */
fun episodeRowActions(
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onQueueEnd: () -> Unit,
    onOpenShow: () -> Unit,
): List<ListenEpisodeAction> = listOf(
    ListenEpisodeAction(Icons.Rounded.PlayArrow, "Přehrát", onClick = onPlay),
    ListenEpisodeAction(Icons.Rounded.QueuePlayNext, "Přehrát další", onClick = onPlayNext),
    ListenEpisodeAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Na konec fronty", onClick = onQueueEnd),
    ListenEpisodeAction(Icons.Rounded.Podcasts, "Otevřít pořad", onClick = onOpenShow),
)

/** Akce pro knihu z řady: přehrát, otevřít detail. */
fun bookRowActions(onPlay: () -> Unit, onOpen: () -> Unit): List<ListenEpisodeAction> = listOf(
    ListenEpisodeAction(Icons.Rounded.PlayArrow, "Přehrát", onClick = onPlay),
    ListenEpisodeAction(Icons.AutoMirrored.Rounded.MenuBook, "Otevřít knihu", onClick = onOpen),
)

/** Stav řad Domů: pořadí, data a akce (data z Activity-scoped VM Timeline + Knihovny, pozice z přehrávače). */
class HomeRowsState(
    val order: List<HomeRow>,
    val data: Map<HomeRow, List<HomeRowItem>>,
    private val shortMinutes: Int,
    private val timelineVm: com.github.jankoran90.showlyfin.feature.listen.PodcastTimelineViewModel?,
    private val playerVm: com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel,
) {
    /** Neprázdné řady kromě Pokračovat. */
    val extra: List<HomeRow> get() = order.filter { it != HomeRow.CONTINUE && data[it].orEmpty().isNotEmpty() }

    fun title(row: HomeRow): String = if (row == HomeRow.SHORT) "Krátké do $shortMinutes min" else row.label

    fun playingKey(s: com.github.jankoran90.showlyfin.feature.listen.player.PlayerState): String? = when {
        !s.isActive -> null
        s.currentEpisodeId != null -> "ep:" + s.currentEpisodeId
        else -> s.currentItemId?.let { "book:$it" }
    }

    fun play(item: HomeRowItem) {
        when (item) {
            is HomeRowItem.Episode -> timelineVm?.play(item.item)
            is HomeRowItem.Book -> playerVm.open(item.book.id, fromStart = false)
        }
    }

    fun actionsFor(
        item: HomeRowItem,
        onOpenBook: (String) -> Unit,
        onOpenSourceEpisode: (String, String, String, String) -> Unit,
    ): List<ListenEpisodeAction> = when (item) {
        is HomeRowItem.Episode -> {
            val t = item.item
            episodeRowActions(
                onPlay = { play(item) },
                onPlayNext = { timelineVm?.enqueue(t, atFront = true) },
                onQueueEnd = { timelineVm?.enqueue(t, atFront = false) },
                onOpenShow = { onOpenSourceEpisode(t.sourceType, t.sourceRef, t.sourceTitle, t.key) },
            )
        }
        is HomeRowItem.Book -> bookRowActions(onPlay = { play(item) }, onOpen = { onOpenBook(item.book.id) })
    }
}

@Composable
fun rememberHomeRowsState(
    enabled: Boolean,
    playerVm: com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel,
): HomeRowsState {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Pořadí a délka se čtou při vstupu na Domů (po návratu z Nastavení se projeví).
    val order = remember(enabled) { if (enabled) HomeRowsPrefs.visible(ctx) else listOf(HomeRow.CONTINUE) }
    val shortMin = remember(enabled) { HomeRowsPrefs.shortMinutes(ctx) }
    val needsTimeline = HomeRow.NEW_EPISODES in order || HomeRow.SHORT in order
    val needsBooks = HomeRow.NEXT_IN_SERIES in order || HomeRow.RECENT_BOOKS in order
    val timelineVm: com.github.jankoran90.showlyfin.feature.listen.PodcastTimelineViewModel? =
        if (needsTimeline) androidx.hilt.navigation.compose.hiltViewModel() else null
    val listenVm: com.github.jankoran90.showlyfin.feature.listen.ListenViewModel? =
        if (needsBooks) androidx.hilt.navigation.compose.hiltViewModel() else null
    val tl = timelineVm?.state?.let { it.collectAsStateWithLifecycle().value }
    val books = listenVm?.uiState?.let { it.collectAsStateWithLifecycle().value.books }.orEmpty()
    val marks by playerVm.directMarks.collectAsStateWithLifecycle()
    val timeline = remember(tl?.buckets) { tl?.buckets?.flatMap { it.items }.orEmpty() }
    val data = remember(order, timeline, books, marks, shortMin) {
        order.associateWith { row ->
            when (row) {
                HomeRow.CONTINUE -> emptyList()
                HomeRow.NEW_EPISODES -> newEpisodesRow(timeline, marks)
                HomeRow.SHORT -> shortEpisodesRow(timeline, marks, shortMin)
                HomeRow.NEXT_IN_SERIES -> nextInSeriesRow(books)
                HomeRow.RECENT_BOOKS -> recentBooksRow(books)
            }
        }
    }
    return HomeRowsState(order, data, shortMin, timelineVm, playerVm)
}
