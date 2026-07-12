package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType
import com.github.jankoran90.showlyfin.core.domain.home.LibrarySummary
import com.github.jankoran90.showlyfin.ui.tv.settings.TvActionChip

/**
 * TENFOOT — WS-A. Výběr ZDROJE nové řady domova (řeší hlavní mezeru: dřív šla přidat jen Trakt řada).
 * Dvoufázový: 1) typ zdroje; 2) u knihovních zdrojů výběr konkrétní knihovny. Vzdušné (progressive
 * disclosure) — chipy, žádná přeplácanost. Zdroje bez dat ve V1 (dlaždice/žánry/studia) se nenabízejí.
 *
 * `newId` musí být unikátní (dodá volající, typicky `System.currentTimeMillis()`), aby
 * [com.github.jankoran90.showlyfin.core.domain.home.HomeLayoutStore.addRow] řadu nezahodil jako duplikát.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvAddRowPicker(
    libraries: List<LibrarySummary>,
    newId: String,
    onPick: (HomeRowConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val panelFocus = remember { FocusRequester() }
    // null = fáze výběru typu; ne-null = fáze výběru knihovny pro daný zdroj.
    var pendingLibrarySource by remember { mutableStateOf<HomeRowSourceType?>(null) }
    LaunchedEffect(pendingLibrarySource) {
        withFrameNanos { }
        runCatching { panelFocus.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .focusRequester(panelFocus)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val librarySource = pendingLibrarySource
            if (librarySource == null) {
                Text(
                    text = "Přidat řadu — vyber zdroj",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Jednoduché zdroje bez parametru → vytvoř rovnou.
                    TvActionChip(
                        label = "Objevovat (Trakt)",
                        enabled = true,
                        onClick = {
                            onPick(
                                HomeRowConfig(
                                    id = newId, source = HomeRowSourceType.DISCOVER, title = "Objevovat",
                                    cardStyle = HomeCardStyle.POSTER,
                                    params = mapOf(HomeRowParams.TAB to "movies", HomeRowParams.FILTER to "trending"),
                                ),
                            )
                        },
                    )
                    simpleSource(newId, HomeRowSourceType.CONTINUE_WATCHING, HomeCardStyle.LANDSCAPE, onPick)
                    simpleSource(newId, HomeRowSourceType.NEXT_UP, HomeCardStyle.LANDSCAPE, onPick)
                    simpleSource(newId, HomeRowSourceType.CONTINUE_WATCHING_COMBINED, HomeCardStyle.LANDSCAPE, onPick)
                    simpleSource(newId, HomeRowSourceType.FAVORITES, HomeCardStyle.POSTER, onPick)
                    simpleSource(newId, HomeRowSourceType.SAVED_FOR_PLAYBACK, HomeCardStyle.POSTER, onPick)
                    // Knihovní zdroje → fáze 2 (výběr konkrétní knihovny), jen když nějaké knihovny známe.
                    if (libraries.isNotEmpty()) {
                        TvActionChip(
                            label = "Nejnovější v knihovně",
                            enabled = true,
                            onClick = { pendingLibrarySource = HomeRowSourceType.RECENTLY_ADDED },
                        )
                        TvActionChip(
                            label = "Konkrétní knihovna",
                            enabled = true,
                            onClick = { pendingLibrarySource = HomeRowSourceType.JELLYFIN_LIBRARY },
                        )
                    }
                }
                Box(Modifier.padding(top = 10.dp)) {
                    TvActionChip(label = "Zrušit", enabled = true, onClick = onDismiss)
                }
            } else {
                Text(
                    text = if (librarySource == HomeRowSourceType.RECENTLY_ADDED) {
                        "Nejnovější — vyber knihovnu"
                    } else {
                        "Vyber knihovnu"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    libraries.forEach { lib ->
                        TvActionChip(
                            label = lib.name.ifBlank { "Knihovna" },
                            enabled = true,
                            onClick = { onPick(libraryRowConfig(newId, librarySource, lib)) },
                        )
                    }
                }
                Box(Modifier.padding(top = 10.dp)) {
                    TvActionChip(
                        label = "← Zpět",
                        enabled = true,
                        onClick = { pendingLibrarySource = null },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.simpleSource(
    newId: String,
    source: HomeRowSourceType,
    style: HomeCardStyle,
    onPick: (HomeRowConfig) -> Unit,
) {
    TvActionChip(
        label = source.label,
        enabled = true,
        onClick = { onPick(HomeRowConfig(id = newId, source = source, title = source.label, cardStyle = style)) },
    )
}

private fun libraryRowConfig(
    newId: String,
    source: HomeRowSourceType,
    lib: LibrarySummary,
): HomeRowConfig {
    val title = if (source == HomeRowSourceType.RECENTLY_ADDED) "Nejnovější — ${lib.name}" else lib.name
    return HomeRowConfig(
        id = newId,
        source = source,
        title = title,
        cardStyle = HomeCardStyle.POSTER,
        params = mapOf(
            HomeRowParams.LIBRARY_ID to lib.id,
            HomeRowParams.COLLECTION_TYPE to (lib.collectionType ?: ""),
        ),
    )
}
