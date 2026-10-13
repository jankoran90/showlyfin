package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.feature.listen.HomeLayout
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookChapter

/**
 * Slovo 2026-10-13 — společné rozhodnutí obrazovek pořadu (ČT / RSS / YouTube), kdy místo seznamu dílů
 * ukázat knihu ze série ([SeriesBookScreen]):
 *  - ťuk na kartu série ([openSlug]) → kniha té série (zpět = seznam pořadu),
 *  - pořad otevřený jen na jednu sérii ([seriesFiltered], dětská cesta) → rovnou kniha,
 *  - pořad, který je celý jedna série, a volba „rovnou kniha" → rovnou kniha (zpět = pryč z pořadu),
 *  - zvýrazněný díl (z Domů) v sérii → otevře se kniha té série se zvýrazněnou kapitolou.
 * Vrací true, když knihu vykreslila (volající pak svůj seznam nekreslí).
 */
@Composable
internal fun <T> SeriesBookGate(
    layout: HomeLayout,
    shelfItems: List<PodcastEpisodeSeriesGrouping.EpisodeShelfItem<T>>?,
    seriesFiltered: List<T>?,
    showTitle: String,
    seriesTitleOf: (T) -> String?,
    keyOf: (T) -> String,
    chaptersOf: (List<T>) -> List<SeriesBookChapter>,
    openSlug: MutableState<String?>,
    highlightKey: String?,
    wideCover: Boolean,
    onBack: () -> Unit,
    onOpenAudioPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    /** Non-null pro ruční knihu (pravidlo) a admina → v liště knihy „Upravit knihu". */
    onEditBook: ((title: String) -> Unit)? = null,
    /** Admin: je kniha (série) schválená dětem? null = ne-admin, akce se nezobrazí. */
    kidsVisible: ((title: String) -> Boolean)? = null,
    onToggleKids: (title: String, visible: Boolean) -> Unit = { _, _ -> },
): Boolean {
    if (!layout.seriesAsBook) return false
    val groups = shelfItems.orEmpty().filterIsInstance<PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup<T>>()
    var openedForHighlight by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(highlightKey, groups) {
        if (!openedForHighlight && highlightKey != null) {
            groups.firstOrNull { g -> g.members.any { keyOf(it) == highlightKey } }?.let {
                openSlug.value = it.slug
                openedForHighlight = true
            }
        }
    }
    val openGroup = groups.firstOrNull { it.slug == openSlug.value }
    val book: Pair<String, List<T>> = when {
        openGroup != null -> openGroup.title to openGroup.members
        !seriesFiltered.isNullOrEmpty() -> (seriesTitleOf(seriesFiltered.first()) ?: showTitle) to seriesFiltered
        seriesFiltered == null && layout.singleSeriesOpensBook && shelfItems?.size == 1 && groups.size == 1 ->
            groups.first().title to groups.first().members
        else -> return false
    }
    val chapters = remember(book.second) { chaptersOf(book.second) }
    SeriesBookScreen(
        title = book.first,
        author = showTitle.takeIf { it != book.first },
        cover = chapters.firstOrNull()?.image,
        chapters = chapters,
        onBack = if (openGroup != null) ({ openSlug.value = null }) else onBack,
        onOpenAudioPlayer = onOpenAudioPlayer,
        wideCover = wideCover,
        highlightKey = highlightKey,
        modifier = modifier,
        onEdit = onEditBook?.let { edit -> { edit(book.first) } },
        kidsVisible = kidsVisible?.invoke(book.first),
        onToggleKids = { onToggleKids(book.first, it) },
    )
    return true
}

/**
 * Slovo 2026-10-13 (user „v přehrávači ťuknu na obrázek a nehodí mě do knihy Po jedné stopě") — dětská
 * pojistka otevře pořad se schválenou sérií, a když jich je víc, vezme první. Je-li zvýrazněný díl
 * v JINÉ schválené sérii téhož pořadu, otevři tu jeho. Neschválená série = beze změny (pojistka platí dál).
 */
internal fun <T> seriesFilterForHighlight(
    seriesFilter: String?,
    highlightKey: String?,
    episodes: List<T>,
    keyOf: (T) -> String,
    seriesTitleOf: (T) -> String?,
    sourceKey: String,
    approvedKeys: Set<String>,
): String? {
    if (seriesFilter == null || highlightKey == null) return seriesFilter
    val ep = episodes.firstOrNull { keyOf(it) == highlightKey } ?: return seriesFilter
    val slug = seriesTitleOf(ep)?.let(PodcastEpisodeSeriesGrouping::seriesSlug) ?: return seriesFilter
    if (slug == seriesFilter) return seriesFilter
    val approved = sourceKey in approvedKeys || approvedKeys.any { k ->
        PodcastEpisodeSeriesGrouping.parseSeriesKey(k)?.let { it.sourceKey == sourceKey && it.slug == slug } == true
    }
    return if (approved) slug else seriesFilter
}
