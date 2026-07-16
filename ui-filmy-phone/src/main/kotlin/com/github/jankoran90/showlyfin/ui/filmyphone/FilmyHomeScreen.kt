package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams.boolParam
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType
import com.github.jankoran90.showlyfin.core.domain.home.LibrarySummary
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowItem
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel

/**
 * CELLULOID (SHW-98) Fáze 2 M2.2 — telefonní domov appky „Filmy".
 *
 * Reuse datového mozku TV domova ([TvHomeViewModel] + [LibraryRowsViewModel] — obojí ve feature vrstvě,
 * bez TV závislosti), jen s telefonním renderem ([FilmyRailList]). Sestavení řad kopíruje logiku
 * `TvHomeScreen` (JF knihovny přes libraryState, ostatní přes `states`), bez TV editoru/immersive
 * (immersive header telefonně = M2.2b). Karta klik → detail (M2.3; zatím no-op ze shellu).
 */
@Composable
fun FilmyHomeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    homeVm: TvHomeViewModel = hiltViewModel(),
    libraryVm: LibraryRowsViewModel = hiltViewModel(),
) {
    val rowConfigs by homeVm.rowConfigs.collectAsStateWithLifecycle()
    val states by homeVm.states.collectAsStateWithLifecycle()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val activeProfileId by homeVm.activeProfileId.collectAsStateWithLifecycle()

    // JF knihovní řady přenačti při přepnutí profilu (jiný profil = jiné knihovny). Vzor TvHomeScreen.
    LaunchedEffect(activeProfileId) { libraryVm.load() }
    LaunchedEffect(libraryState.rows) {
        val libs = libraryState.rows.map { LibrarySummary(it.libraryId, it.libraryName, it.collectionType) }
        if (libs.isNotEmpty()) homeVm.syncLibraries(libs)
    }
    // Lazy načtení jen ZAPNUTÝCH ne-JF řad (JF jedou přes LibraryRowsViewModel).
    LaunchedEffect(rowConfigs) {
        rowConfigs.filter {
            it.enabled &&
                it.source != HomeRowSourceType.JELLYFIN_LIBRARY &&
                it.source != HomeRowSourceType.JELLYFIN_LIBRARIES
        }.forEach { homeVm.ensureRowLoaded(it) }
    }

    val rails: List<FilmyRail> = buildList {
        rowConfigs.filter { it.enabled }.forEach { cfg ->
            when (cfg.source) {
                HomeRowSourceType.JELLYFIN_LIBRARY -> {
                    val libId = cfg.params[HomeRowParams.LIBRARY_ID]
                    val libRow = libraryState.rows.firstOrNull { it.libraryId == libId }
                    val items = libRow?.items?.map { it.toHomeRowItem() }?.applyConfig(cfg).orEmpty()
                    if (items.isNotEmpty()) {
                        add(FilmyRail(cfg.id, cfg.title.ifBlank { libRow?.libraryName.orEmpty() }, cfg.cardStyle, items, cfg.showTitles))
                    }
                }
                HomeRowSourceType.JELLYFIN_LIBRARIES -> Unit // deprecated meta
                else -> {
                    val st = states[cfg.id]
                    if (st != null && st.items.isNotEmpty()) {
                        add(FilmyRail(cfg.id, cfg.resolvedTitle(), cfg.cardStyle, st.items, cfg.showTitles))
                    }
                }
            }
        }
    }

    fun clickItem(item: HomeRowItem) {
        val mi = item.mediaItem
        val jf = item.jellyfinId
        when {
            mi != null -> onOpenDetail(mi)
            jf != null -> onOpenJellyfinDetail(jf)
        }
    }

    when {
        rails.isNotEmpty() -> FilmyRailList(rails = rails, onItemClick = ::clickItem, modifier = modifier)
        // Ještě se načítá: profil/config nedorazil, nebo aspoň jedna řada běží.
        states.isEmpty() || states.values.any { it.loading } ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        // Načtení doběhlo a nic není → profil nemá Trakt/JF obsah (typicky nepřihlášen). Věčný spinner = ne.
        else -> FilmyHomeEmpty(modifier)
    }
}

/** Prázdný domov — místo věčného spinneru srozumitelná výzva (nejčastěji: profil není přihlášen). */
@Composable
private fun FilmyHomeEmpty(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "Zatím tu nic není",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Přihlas se k Traktu nebo Jellyfinu v Nastavení, ať se objeví filmy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** LibraryRowItem (JF knihovna) → HomeRowItem pro jednotný render (telefonní verze `ui-tv::toHomeRowItem`). */
private fun LibraryRowItem.toHomeRowItem(): HomeRowItem = HomeRowItem(
    key = jellyfinId,
    title = name,
    year = year,
    posterUrl = imageUrl,
    landscapeUrl = landscapeUrl,
    progressPct = progressPct,
    watched = watched,
    mediaItem = mediaItem,
    jellyfinId = jellyfinId,
)

/** Klientské operace pro řadu knihovny (skryj zhlédnuté + řazení + limit). Kopie z `TvHomeScreen`. */
private fun List<HomeRowItem>.applyConfig(cfg: HomeRowConfig): List<HomeRowItem> {
    var r = this
    if (cfg.params.boolParam(HomeRowParams.HIDE_WATCHED)) r = r.filter { !it.watched }
    r = when (cfg.sort) {
        HomeRowSort.YEAR_DESC -> r.sortedByDescending { it.year ?: 0 }
        HomeRowSort.ALPHA -> r.sortedBy { it.title.lowercase() }
        HomeRowSort.RATING -> r.sortedByDescending { it.mediaItem?.rating ?: -1f }
        HomeRowSort.RANDOM -> r.shuffled()
        HomeRowSort.RECENT, HomeRowSort.DEFAULT -> r
    }
    return r.take(cfg.limit.coerceIn(1, 60))
}
