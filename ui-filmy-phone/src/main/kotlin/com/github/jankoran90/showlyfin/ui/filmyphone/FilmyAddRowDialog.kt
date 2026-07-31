package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.LibrarySurface

/**
 * PŮDORYS (SHW-112) — výběr ZDROJE nové řady domova na telefonu. Touch obdoba `TvAddRowPicker`:
 * dvoufázový (typ zdroje → u knihovních konkrétní Jellyfin knihovna), stejná nabídka zdrojů, aby
 * si telefon a TV nesestavovaly domov jinak. Zdroje bez dat (dlaždice/žánry/studia) se nenabízejí.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilmyAddRowDialog(
    onPick: (HomeRowConfig) -> Unit,
    onDismiss: () -> Unit,
    libraryVm: LibraryRowsViewModel = hiltViewModel(),
) {
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { libraryVm.load(LibrarySurface.HOME) }

    // null = fáze výběru typu; ne-null = fáze výběru knihovny pro daný zdroj.
    var pendingLibrarySource by remember { mutableStateOf<HomeRowSourceType?>(null) }
    // Id se drží po dobu dialogu → dvoufázový výběr nevyrobí dvě různá id.
    val newId = remember { "custom_${System.currentTimeMillis()}" }
    val libraries = libraryState.rows

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (pendingLibrarySource == null) "Přidat řadu — vyber zdroj" else "Vyber knihovnu",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val librarySource = pendingLibrarySource
                if (librarySource == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                onPick(
                                    HomeRowConfig(
                                        id = newId,
                                        source = HomeRowSourceType.DISCOVER,
                                        title = "Objevovat",
                                        cardStyle = HomeCardStyle.POSTER,
                                        params = mapOf(
                                            HomeRowParams.TAB to "movies",
                                            HomeRowParams.FILTER to "trending",
                                        ),
                                    ),
                                )
                            },
                            label = { Text("Objevovat (Trakt)") },
                        )
                        SIMPLE_SOURCES.forEach { (source, style) ->
                            AssistChip(
                                onClick = {
                                    onPick(
                                        HomeRowConfig(
                                            id = newId, source = source, title = source.label, cardStyle = style,
                                        ),
                                    )
                                },
                                label = { Text(source.label) },
                            )
                        }
                        if (libraries.isNotEmpty()) {
                            AssistChip(
                                onClick = { pendingLibrarySource = HomeRowSourceType.RECENTLY_ADDED },
                                label = { Text("Nejnovější v knihovně") },
                            )
                            AssistChip(
                                onClick = { pendingLibrarySource = HomeRowSourceType.JELLYFIN_LIBRARY },
                                label = { Text("Konkrétní knihovna") },
                            )
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        libraries.forEach { lib ->
                            AssistChip(
                                onClick = {
                                    val title = if (librarySource == HomeRowSourceType.RECENTLY_ADDED) {
                                        "Nejnovější — ${lib.libraryName}"
                                    } else {
                                        lib.libraryName
                                    }
                                    onPick(
                                        HomeRowConfig(
                                            id = newId,
                                            source = librarySource,
                                            title = title,
                                            cardStyle = HomeCardStyle.POSTER,
                                            params = mapOf(
                                                HomeRowParams.LIBRARY_ID to lib.libraryId,
                                                HomeRowParams.COLLECTION_TYPE to (lib.collectionType ?: ""),
                                            ),
                                        ),
                                    )
                                },
                                label = { Text(lib.libraryName.ifBlank { "Knihovna" }) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pendingLibrarySource != null) {
                TextButton(onClick = { pendingLibrarySource = null }) { Text("Zpět") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}

/** Zdroje bez parametrů (vytvoří se rovnou) + jejich výchozí styl karty. Zrcadlí `TvAddRowPicker`. */
private val SIMPLE_SOURCES: List<Pair<HomeRowSourceType, HomeCardStyle>> = listOf(
    HomeRowSourceType.CONTINUE_WATCHING to HomeCardStyle.LANDSCAPE,
    HomeRowSourceType.NEXT_UP to HomeCardStyle.LANDSCAPE,
    HomeRowSourceType.CONTINUE_WATCHING_COMBINED to HomeCardStyle.LANDSCAPE,
    HomeRowSourceType.FILMOTEKA_RECENT to HomeCardStyle.POSTER,
    HomeRowSourceType.FAVORITES to HomeCardStyle.POSTER,
    HomeRowSourceType.SAVED_FOR_PLAYBACK to HomeCardStyle.POSTER,
    HomeRowSourceType.TRAKT_WATCHLIST to HomeCardStyle.POSTER,
    HomeRowSourceType.TRAKT_HISTORY to HomeCardStyle.LANDSCAPE,
    HomeRowSourceType.COUCHMONKEY_RECOMMENDATIONS to HomeCardStyle.POSTER,
    HomeRowSourceType.WEIGHTED_RECOMMENDATIONS to HomeCardStyle.POSTER,
    HomeRowSourceType.BRAIN_FOR_YOU to HomeCardStyle.POSTER,
)
