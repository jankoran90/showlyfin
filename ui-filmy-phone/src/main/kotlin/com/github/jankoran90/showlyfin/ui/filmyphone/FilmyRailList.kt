package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/** Jedna řada telefonního domova (telefonní ekvivalent `TvRail` z ui-tv — varianta A, nesdílíme). */
data class FilmyRail(
    val id: String,
    val title: String,
    val style: HomeCardStyle,
    val items: List<HomeRowItem>,
    val showTitles: Boolean = true,
)

/**
 * CELLULOID (SHW-98) Fáze 2 M2.2 — vertikální seznam řad telefonního domova.
 *
 * Telefonní protějšek `TvRailList`: `LazyColumn` řad × `LazyRow` karet, BEZ D-pad aparátu
 * (focusGroup / FocusRequester / onPreviewKeyEvent) — čistý dotyk. Immersive header se přidá nad
 * seznam v M2.2b (slot `header`).
 */
@Composable
fun FilmyRailList(
    rails: List<FilmyRail>,
    onItemClick: (HomeRowItem) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (header != null) {
            item(key = "__header") { header() }
        }
        items(rails, key = { it.id }) { rail ->
            FilmyRailSection(rail, onItemClick)
        }
    }
}

@Composable
private fun FilmyRailSection(rail: FilmyRail, onItemClick: (HomeRowItem) -> Unit) {
    Column {
        if (rail.showTitles) {
            Text(
                text = rail.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rail.items, key = { it.key }) { item ->
                FilmyHomeCard(item = item, style = rail.style, onClick = { onItemClick(item) })
            }
        }
    }
}
