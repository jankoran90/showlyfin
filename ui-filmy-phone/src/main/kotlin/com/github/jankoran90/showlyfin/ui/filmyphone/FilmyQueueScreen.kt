package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.ViewMode

/**
 * RAMPA (SHW-121) — stránka „K přehrání" (telefon).
 *
 * User 2026-08-28 14:10: *„do phone app mi to das do filmoteky ale jako dalsi tab tzn horizontal
 * scroll jako dalsi obrazovka… a určitě dej razeni, zpusob view grid list"*.
 *
 * Sama nekreslí ani mřížku, ani seznam — obojí bere z `FilmotekaGrid`/`FilmotekaList`, takže vypadá
 * a ovládá se **stejně jako Filmotéka** (včetně rychloposuvníku). Prázdná fronta má vlastní výzvu;
 * jako PRUH se ale prázdná nikde neukazuje (zadání usera).
 */
@Composable
fun FilmyQueueScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    vm: FilmyQueueViewModel = hiltViewModel(),
) {
    val rails by vm.rails.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        // Táž jednopatrová lišta jako u Filmotéky: ☰ + názvy stránek + ovladače vpravo.
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                IconButton(onClick = { vm.toggleViewMode() }) {
                    if (viewMode == ViewMode.GRID) {
                        Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = "Zobrazit jako seznam")
                    } else {
                        Icon(Icons.Rounded.GridView, contentDescription = "Zobrazit jako mřížku")
                    }
                }
            },
            content = { titleContent() },
        )
        if (total > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QueueSort.entries.forEach { s ->
                    FilterChip(selected = sort == s, onClick = { vm.setSort(s) }, label = { Text(s.chipLabel) })
                }
            }
        }
        val items = rails.firstOrNull()?.items.orEmpty()
        when {
            items.isEmpty() -> QueueEmpty()
            viewMode == ViewMode.GRID -> FilmotekaGrid(
                rails = rails,
                onOpenDetail = onOpenDetail,
                onOpenCollection = {},
                scrollerLabel = { null },
            )
            else -> FilmotekaList(
                rails = rails,
                onOpenDetail = onOpenDetail,
                onOpenCollection = {},
                scrollerLabel = { null },
            )
        }
    }
}

/** Prázdná fronta — řekni, jak se do ní přidává, ať obrazovka nemlčí. */
@Composable
private fun QueueEmpty() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Fronta je prázdná",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Na kartě filmu nebo seriálu dej „Přidat k přehrání\" — objeví se tady a po dokoukání sám zmizí.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
