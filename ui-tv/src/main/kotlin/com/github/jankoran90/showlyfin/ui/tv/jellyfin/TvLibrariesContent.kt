package com.github.jankoran90.showlyfin.ui.tv.jellyfin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinBrowserViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.TvLibraryCard

/**
 * TENFOOT (SHW-87) Fáze 2 — obsah záložky „Knihovna" na TV Home: mřížka Jellyfin knihoven.
 * Sdílí [JellyfinBrowserViewModel] s telefonem — ten se v `init` sám připojí z uložených creds aktivního
 * profilu (na TV už přihlášeného), takže žádné TV-nepohodlné přihlašování formulářem. Klik na knihovnu
 * deleguje nahoru do TV navigace (mřížka položek).
 */
@Composable
fun TvLibrariesContent(
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JellyfinBrowserViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    // TENFOOT: po OK ze sidebaru zaostři PRVNÍ knihovnu.
    val firstFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = ui.libraries.isNotEmpty(),
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )

    when {
        ui.isLoading && ui.libraries.isEmpty() -> Centered(modifier) { CircularProgressIndicator() }

        !ui.isConnected && ui.libraries.isEmpty() -> Centered(modifier) {
            Text(
                text = ui.error
                    ?: "Jellyfin není připojený. Přihlas se ve verzi appky na telefonu — profil se pak sesynchronizuje i na TV.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }

        ui.libraries.isEmpty() -> Centered(modifier) {
            Text("Žádné knihovny", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        else -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            itemsIndexed(ui.libraries, key = { _, lib -> lib.id }) { index, library ->
                TvLibraryCard(
                    library = library,
                    onClick = { onOpenLibrary(library.id, library.name, library.collectionType) },
                    focusRequester = if (index == 0) firstFocus else null,
                )
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
