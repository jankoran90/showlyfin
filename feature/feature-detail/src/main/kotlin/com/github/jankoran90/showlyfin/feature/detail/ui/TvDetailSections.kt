package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.CollectionSection
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.feature.detail.DetailUiState
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel

/**
 * TENFOOT WS-B (SHW-87): obsah TV detailu POD immersive hero — plná parita s telefonem přes sdílené
 * komponenty ([CreatorsSection], [CollectionSection], [SeasonEpisodeSection]). Žije ve scrollované
 * `Column` uvnitř [TvDetailBody] (hero zůstává fixní nahoře, tohle scrolluje). Data plní `DetailViewModel`;
 * spouštěče jdou přímo přes [viewModel] (galerie, tvorba osoby) nebo přes [onCollectionPartClick]
 * ([TvNavigator] otevře další detail). Karty reuse `PosterCard.tvFocusable()` → D-pad glow „zdarma".
 */
@Composable
internal fun TvDetailSections(
    displayItem: MediaItem,
    uiState: DetailUiState,
    viewModel: DetailViewModel,
    genres: List<String>?,
    plot: String?,
    plotExpanded: Boolean,
    onTogglePlot: () -> Unit,
    onCollectionPartClick: ((CollectionPart) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        // ── Popis (rozbalovací) ──
        if (!plot.isNullOrBlank()) {
            val collapsedLines = uiState.plotCollapsedLines.takeIf { it > 0 } ?: 3
            Column(Modifier.padding(horizontal = 48.dp, vertical = 8.dp)) {
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (plotExpanded) Int.MAX_VALUE else collapsedLines,
                    overflow = if (plotExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Icon(
                    imageVector = if (plotExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (plotExpanded) "Sbalit" else "Zobrazit víc",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlot)
                        .tvFocusBorder(shape = CircleShape)
                        .padding(6.dp)
                        .size(28.dp),
                )
            }
        }

        // ── Tvůrci (pás herců + režie, Scénář/Kamera/Žánry po rozbalení popisu) ──
        if (uiState.showCreators) {
            CreatorsSection(
                cast = uiState.cast,
                directors = uiState.directors,
                writers = uiState.writers,
                cinematographers = uiState.cinematographers,
                onPersonClick = { person, kind -> viewModel.openPersonFilmography(person, kind) },
                genres = genres.orEmpty(),
                detailsVisible = plotExpanded,
            )
        }

        // ── Sezóny / epizody seriálu (WS-C) — klik na epizodu spustí stream flow ──
        if (uiState.showSeasons && displayItem.type == MediaType.SHOW && uiState.seasons.isNotEmpty()) {
            SeasonEpisodeSection(
                seasons = uiState.seasons,
                selectedSeason = uiState.selectedSeason,
                episodes = uiState.seasonEpisodes,
                isLoadingEpisodes = uiState.isLoadingEpisodes,
                onSelectSeason = { viewModel.selectSeason(it) },
                onPlayEpisode = { s, e, t -> viewModel.playEpisode(s, e, t) },
            )
        }

        // ── Kolekce / od stejného režiséra / studia (film) ──
        val mergedCollection = uiState.mergedCollection ?: uiState.collection?.let { coll ->
            MediaCollection(
                name = coll.name ?: "Kolekce",
                parts = coll.parts.orEmpty().map { part ->
                    CollectionPart(
                        key = "tmdb_${part.id}",
                        tmdbId = part.id,
                        jellyfinId = uiState.ownedTmdbToJellyfin[part.id],
                        title = part.title ?: "",
                        posterUrl = part.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                        year = part.release_date?.take(4),
                        watched = uiState.watchedTmdbIds.contains(part.id),
                    )
                },
            )
        }
        val excludeKey = displayItem.tmdbId?.let { "tmdb_$it" }
        if (uiState.showCollections) {
            mergedCollection?.let { coll ->
                CollectionSection(collection = coll, excludeKey = excludeKey, onPartClick = { onCollectionPartClick?.invoke(it) })
            }
        }
        if (uiState.showDirector) {
            uiState.directorMovies?.let { coll ->
                CollectionSection(collection = coll, excludeKey = excludeKey, onPartClick = { onCollectionPartClick?.invoke(it) })
            }
        }
        if (uiState.showStudio) {
            uiState.studioMovies?.let { coll ->
                CollectionSection(collection = coll, excludeKey = excludeKey, onPartClick = { onCollectionPartClick?.invoke(it) })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
