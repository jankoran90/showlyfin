package com.github.jankoran90.showlyfin.feature.listen.search

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.normalizeForSearch
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtBook
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import com.github.jankoran90.showlyfin.feature.listen.PodcastSearchViewModel
import com.github.jankoran90.showlyfin.feature.listen.SourceManagerViewModel
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AtLoad
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AudiotekaSearchViewModel

/**
 * AMPHORA (SHW-124) F3 / HL1 — JEDNO hledání pro všechno: jedno pole řídí lokální filtr (moje knihy,
 * moje pořady) a tři serverová hledání, která už existovala (díly napříč zdroji, Audioteka, nové
 * pořady k odběru). Výsledky po skupinách; prázdné pole ukáže nedávná hledání. Hledání v jednom
 * zdroji (lupa na kartě pořadu) zůstává [com.github.jankoran90.showlyfin.feature.listen.ui.PodcastSearchScreen].
 * ViewModely mají vlastní klíč, aby nesdílely dotaz s Katalogem, Zdroji ani hledáním ve zdroji.
 */
@Composable
fun UnifiedSearchScreen(
    books: List<Audiobook>,
    sources: List<PodcastSource>,
    onOpenBook: (String) -> Unit,
    onOpenSource: (PodcastSource) -> Unit,
    onOpenNewShow: (SourceSearchResult) -> Unit,
    onOpenAudioteka: (AtBook) -> Unit,
    onPlayVideo: (url: String, title: String, posterUrl: String?) -> Unit,
    modifier: Modifier = Modifier,
    /** Dětský profil: bez videa, Audioteky a nových pořadů (kiosek). */
    isKidsProfile: Boolean = false,
    episodesVm: PodcastSearchViewModel = hiltViewModel(key = "unified-search-episodes"),
    audiotekaVm: AudiotekaSearchViewModel = hiltViewModel(key = "unified-search-audioteka"),
    showsVm: SourceManagerViewModel = hiltViewModel(key = "unified-search-shows"),
) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val epState by episodesVm.state.collectAsStateWithLifecycle()
    val atResult by audiotekaVm.result.collectAsStateWithLifecycle()
    val showsState by showsVm.state.collectAsStateWithLifecycle()
    val showAudioteka = !isKidsProfile && SearchPrefs.showAudioteka(ctx)
    val showNewShows = !isKidsProfile && SearchPrefs.showNewShows(ctx)
    var recent by remember { mutableStateOf(SearchPrefs.recent(ctx)) }
    val query = epState.query

    LaunchedEffect(Unit) { episodesVm.setScope(null) }
    LaunchedEffect(showsState.message) {
        showsState.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); showsVm.consumeMessage() }
    }

    fun setQuery(q: String) {
        episodesVm.setQuery(q)
        if (showAudioteka) audiotekaVm.setQuery(q)
        if (showNewShows) showsVm.onQueryChange(q)
    }
    fun saveRecent() { recent = SearchPrefs.addRecent(ctx, query) }

    val q = query.trim()
    val norm = q.normalizeForSearch()
    val active = q.length >= 2
    val myBooks = remember(books, norm) { if (active) matchBooks(books, norm) else emptyList() }
    val mySources = remember(sources, norm) {
        if (active) sources.filter { episodesVm.isSourceVisible(it) && it.title.normalizeForSearch().contains(norm) } else emptyList()
    }
    val newShows = if (showNewShows && active) showsState.results.filterNot(showsVm::isAdded) else emptyList()
    val atBooks = (atResult as? AtLoad.Ready)?.data.orEmpty()
    var episodesExpanded by rememberSaveable(q) { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = ::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
            placeholder = { Text(if (isKidsProfile) "Hledat v pořadech a knihách" else "Knihy, pořady, díly, Audioteka…") },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { setQuery("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Vymazat")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { saveRecent(); keyboard?.hide() }),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
            ),
        )

        val episodesLoading = epState.loading || !epState.searched
        val atLoading = showAudioteka && atResult == AtLoad.Loading
        val nothing = myBooks.isEmpty() && mySources.isEmpty() && epState.results.isEmpty() &&
            atBooks.isEmpty() && newShows.isEmpty() && !episodesLoading && !atLoading && !showsState.searching
        val epLimit = SearchPrefs.episodesShown(ctx)
        val shownEpisodes = if (episodesExpanded) epState.results else epState.results.take(epLimit)
        when {
            !active -> RecentSearches(
                recent = recent,
                onPick = { setQuery(it); recent = SearchPrefs.addRecent(ctx, it) },
                onRemove = { recent = SearchPrefs.removeRecent(ctx, it) },
                onClear = { SearchPrefs.clearRecent(ctx); recent = emptyList() },
                hint = if (q.isEmpty()) "Hledej v knihách, pořadech i dílech (i v popisu)" +
                    (if (showAudioteka) ", v Audiotéce" else "") + (if (showNewShows) " a v nových pořadech." else ".")
                else "Napiš aspoň dvě písmena.",
            )
            nothing -> Box(Modifier.fillMaxSize().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
                Text(
                    "Nic nenalezeno pro „$q“.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.miniPlayerReserve)) {
                if (myBooks.isNotEmpty()) {
                    item(key = "h-books") { GroupHeader("Moje knihy", myBooks.size) }
                    items(myBooks.take(MAX_LOCAL), key = { "b" + it.id }) { b ->
                        BookResultRow(b) { saveRecent(); onOpenBook(b.id) }
                    }
                }
                if (mySources.isNotEmpty()) {
                    item(key = "h-sources") { GroupHeader("Moje pořady", mySources.size) }
                    items(mySources.take(MAX_LOCAL), key = { "s" + it.key }) { s ->
                        SourceResultRow(s) { saveRecent(); onOpenSource(s) }
                    }
                }
                if (episodesLoading || epState.results.isNotEmpty()) {
                    item(key = "h-episodes") {
                        GroupHeader("Díly", epState.results.size.takeIf { !episodesLoading }, loading = episodesLoading)
                    }
                    if (episodesExpanded && epState.results.size > 1) item(key = "ep-sort") {
                        EpisodeSortChips(epState.sort, episodesVm::setSort)
                    }
                    items(shownEpisodes, key = { "e" + (it.resumeKey ?: it.id) }) { ep ->
                        com.github.jankoran90.showlyfin.feature.listen.ui.EpisodeSearchResult(
                            ep, episodesVm, audioOnly = isKidsProfile, onPlayVideo = onPlayVideo, onBeforeAction = ::saveRecent,
                        )
                    }
                    if (epState.results.size > epLimit) item(key = "ep-more") {
                        ShowAllButton(
                            if (episodesExpanded) "Méně dílů" else "Všechny díly (${epState.results.size})",
                        ) { episodesExpanded = !episodesExpanded }
                    }
                }
                if (showAudioteka && (atLoading || atBooks.isNotEmpty())) {
                    item(key = "h-audioteka") { GroupHeader("Audioteka", atBooks.size.takeIf { !atLoading }, loading = atLoading) }
                    item(key = "audioteka-row") { AudiotekaResultRow(atBooks) { saveRecent(); onOpenAudioteka(it) } }
                }
                if (showNewShows && (showsState.searching || newShows.isNotEmpty())) {
                    item(key = "h-new") {
                        GroupHeader("Nové pořady", newShows.size.takeIf { !showsState.searching }, loading = showsState.searching)
                    }
                    items(newShows.take(MAX_LOCAL), key = { "n" + it.type + it.ref }) { r ->
                        NewShowRow(r, onOpen = { saveRecent(); onOpenNewShow(r) }, onFollow = { saveRecent(); showsVm.add(r) })
                    }
                }
            }
        }
    }
}

private const val MAX_LOCAL = 20

/** Knihy podle názvu, autora, interpreta i série; shoda na začátku názvu první. */
private fun matchBooks(books: List<Audiobook>, norm: String): List<Audiobook> = books
    .filter { b ->
        listOfNotNull(b.title, b.author, b.narrator, b.seriesName).any { it.normalizeForSearch().contains(norm) }
    }
    .sortedWith(
        compareByDescending<Audiobook> { it.title.normalizeForSearch().startsWith(norm) }
            .thenByDescending { it.title.normalizeForSearch().contains(norm) }
            .thenByDescending { it.lastUpdate ?: 0L },
    )
