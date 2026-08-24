package com.github.jankoran90.showlyfin.ui.tv.filmoteka

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaCollectionGroup
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.collectionCardTitle
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.dilyLabel
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.TvHomeCard
import kotlin.math.roundToInt

/**
 * ATRIUM (SHW-118, user 2026-08-24 „udělej nejen telefon, ale i TV, to musí být automatické") —
 * OBSAH sdružené kolekce na TV. Stejná data i pravidla jako na telefonu ([FilmyCollectionOverlay]),
 * jen dálkovým ovladačem: mřížka dílů s TV fokusem, zpět zavírá překryv.
 *
 * Proč vlastní překryv a ne otevření BoxSetu v Jellyfin prohlížeči: kolekce může být SMÍŠENÁ (díl
 * z Jellyfinu + díl ze sdilej.cz). Jellyfin o tom druhém neví, takže by ho jeho vlastní obrazovka
 * zamlčela — a přesně kvůli tomu se sdružování stavělo.
 */
@Composable
fun TvFilmotekaCollectionOverlay(
    group: FilmotekaCollectionGroup,
    onDismiss: () -> Unit,
    onItemClick: (HomeRowItem) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val cardScale = LocalTvCardScale.current
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val items = remember(group) { group.toRowItems() }
    // 🔒 2026-08-24 (user: „pokud rozbalím kartu kolekce tak focus není na podkarty kolekce nemůžu je
    // vybrat, dpad není fokusovaný na filmy uvnitř ale pod" + „měl by fokusovat hned na první
    // položku") — překryv si musí fokus AKTIVNĚ vzít, jinak zůstane na mřížce pod ním a dálkový
    // ovladač ovládá něco, co není vidět. Stejný vzor jako mřížka Filmotéky.
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = items.isNotEmpty(),
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize().tvOverscan()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    text = collectionCardTitle(group.name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.year?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = dilyLabel(group.members.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed((6f / cardScale.widthScale).roundToInt().coerceIn(3, 9)),
                horizontalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp)),
                verticalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp)),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                    TvHomeCard(
                        item = item,
                        style = HomeCardStyle.POSTER,
                        onClick = { onItemClick(item) },
                        focusRequester = if (index == 0) firstFocus else null,
                    )
                }
            }
        }
    }
}

/** Díly kolekce jako karty řady — stejný model jako zbytek Filmotéky, takže se chovají identicky. */
private fun FilmotekaCollectionGroup.toRowItems(): List<HomeRowItem> = members.map { item ->
    HomeRowItem(
        key = "filmo_coll_${id}_${item.tmdbId ?: item.imdbId ?: item.title}",
        title = item.displayTitle,
        year = item.year,
        posterUrl = item.posterUrl("w342"),
        landscapeUrl = item.backdropUrl("w780"),
        mediaItem = item,
    )
}
