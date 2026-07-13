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
    onPlayJellyfin: ((String) -> Unit)? = null,
    showPlot: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        // ── Popis (rozbalovací) — jen v CLASSIC_HERO layoutu; v immersim je popis v hero bloku (showPlot=false).
        if (showPlot && !plot.isNullOrBlank()) {
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

        // ── Sezóny / epizody seriálu (WS-C) ──
        // KOLO2 (G): epizoda vlastněného seriálu (v Jellyfin knihovně) → přímé přehrání z Jellyfinu
        // (episode id z mapy), místo stream/download flow. Neowned nebo bez mapy → stávající stream flow.
        if (uiState.showSeasons && displayItem.type == MediaType.SHOW && uiState.seasons.isNotEmpty()) {
            SeasonEpisodeSection(
                seasons = uiState.seasons,
                selectedSeason = uiState.selectedSeason,
                episodes = uiState.seasonEpisodes,
                isLoadingEpisodes = uiState.isLoadingEpisodes,
                onSelectSeason = { viewModel.selectSeason(it) },
                onPlayEpisode = { s, e, t ->
                    val jfEpisodeId = uiState.episodeJellyfinIds[s to e]
                    if (uiState.isOwnedInLibrary && jfEpisodeId != null && onPlayJellyfin != null) {
                        onPlayJellyfin(jfEpisodeId)
                    } else {
                        viewModel.playEpisode(s, e, t)
                    }
                },
                watched = uiState.episodeWatched,
                progress = uiState.episodeProgress,
                nextUp = uiState.nextUpEpisode,
                // KOLO2 (J): long-press na epizodě → přepni zhlédnuto (VM zapíše do Jellyfinu; no-op mimo knihovnu).
                onToggleWatched = { s, e -> viewModel.toggleEpisodeWatched(s, e) },
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
                        backdropUrl = part.backdrop_path?.let { "https://image.tmdb.org/t/p/w780$it" },
                        year = part.release_date?.take(4),
                        watched = uiState.watchedTmdbIds.contains(part.id),
                    )
                },
            )
        }
        val excludeKey = displayItem.tmdbId?.let { "tmdb_$it" }
        val sectionStyle = uiState.sectionStyle
        if (uiState.showCollections) {
            mergedCollection?.let { coll ->
                CollectionSection(collection = coll, excludeKey = excludeKey, onPartClick = { onCollectionPartClick?.invoke(it) }, style = sectionStyle)
            }
        }
        if (uiState.showDirector) {
            uiState.directorMovies?.let { coll ->
                CollectionSection(collection = coll, excludeKey = excludeKey, onPartClick = { onCollectionPartClick?.invoke(it) }, style = sectionStyle)
            }
        }
        if (uiState.showStudio) {
            uiState.studioMovies?.let { coll ->
                CollectionSection(collection = coll, excludeKey = excludeKey, onPartClick = { onCollectionPartClick?.invoke(it) }, style = sectionStyle)
            }
        }

        // ── Tvůrci — SAMOSTATNÁ POSLEDNÍ řada (OTA 299): odpojeno od rozbalení popisu (user 2026-07-13
        // „dáme je normálně do rows a budou úplně na konci jako poslední row"). Detaily (crew/žánry) vidět rovnou.
        if (uiState.showCreators) {
            CreatorsSection(
                cast = uiState.cast,
                directors = uiState.directors,
                writers = uiState.writers,
                cinematographers = uiState.cinematographers,
                onPersonClick = { person, kind -> viewModel.openPersonFilmography(person, kind) },
                genres = genres.orEmpty(),
                detailsVisible = true,
            )
        }

        // Spodní overscan — poslední sekce nesmí končit v TV overscan zóně u dolní hrany (jinak se ořízne
        // label/rok karet — user feedback OTA 295).
        Spacer(Modifier.height(56.dp))
    }
}
