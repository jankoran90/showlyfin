package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel

/**
 * CELLULOID (SHW-98) Fáze 2 M2.2 — telefonní domov appky „Filmy".
 *
 * Reuse datového mozku TV domova ([TvHomeViewModel] + [LibraryRowsViewModel] — obojí ve feature vrstvě,
 * bez TV závislosti), s telefonním renderem [FilmyHomeTabbed] (transpozice TV: řada = tab, obsah =
 * svislý seznam řádků). Sestavení řad kopíruje logiku `TvHomeScreen` (JF knihovny přes libraryState,
 * ostatní přes `states`), bez TV editoru/immersive. Řádek klik → detail (M2.3; zatím no-op ze shellu).
 */
@Composable
fun FilmyHomeScreen(
    onMenu: () -> Unit,
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

    var showTraktLogin by remember { mutableStateOf(false) }

    when {
        // Domov = TRANSPOZICE TV: taby (řady) splynulé s lištou (☰ + taby) + swipe + svislý seznam řádků.
        rails.isNotEmpty() -> FilmyHomeTabbed(rails = rails, onItemClick = ::clickItem, onMenu = onMenu, modifier = modifier)
        // Ještě se načítá / prázdno → titulková lišta (☰ + „Domů") + obsah pod ní.
        else -> Column(modifier.fillMaxSize()) {
            FilmySectionBar(title = "Domů", onMenu = onMenu)
            if (states.isEmpty() || states.values.any { it.loading }) {
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                // Načtení doběhlo a nic není → profil nemá Trakt/JF obsah (typicky nepřihlášen). Věčný spinner = ne.
                FilmyHomeEmpty(Modifier.weight(1f), onTraktLogin = { showTraktLogin = true })
            }
        }
    }

    if (showTraktLogin) {
        FilmyTraktLoginDialog(onDismiss = { showTraktLogin = false })
    }
}

/** Prázdný domov — místo věčného spinneru srozumitelná výzva + přímé Trakt přihlášení. */
@Composable
private fun FilmyHomeEmpty(modifier: Modifier = Modifier, onTraktLogin: () -> Unit) {
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
                text = "Přihlas se k Traktu, ať se objeví filmy podle tvého vkusu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onTraktLogin) { Text("Přihlásit se k Traktu") }
        }
    }
}

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
