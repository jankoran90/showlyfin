package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.EpisodeUi
import com.github.jankoran90.showlyfin.core.ui.PhoneEpisodeRow
import com.github.jankoran90.showlyfin.core.ui.TvEpisodeStrip
import com.github.jankoran90.showlyfin.core.ui.isTvFormFactor
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbEpisode
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSeasonSummary

/**
 * TENFOOT WS-C (SHW-87) + TV DETAIL REDESIGN (OTA 299): sekce sezóny/epizody seriálu v detailu.
 * Sdílená telefon + TV. Chipy sezón → epizody. NA TV = HORIZONTÁLNÍ řada landscape karet (jako yellyfin)
 * s indikátorem zhlédnutí/progress + AUTO-SCROLL na první nezhlédnutou epizodu. Telefon = vertikální seznam.
 * Watched/progress/nextUp přichází z Jellyfinu ([DetailUiState.episodeWatched] atd.); klíč = (season, episode).
 *
 * Umísťuje se do vertikálně scrollované `Column` — proto je to prostá `Column`, ne vlastní `LazyColumn`
 * (jinak nested-scroll konflikt); horizontální `LazyRow` uvnitř je OK.
 */
@Composable
fun SeasonEpisodeSection(
    seasons: List<TmdbSeasonSummary>,
    selectedSeason: Int?,
    episodes: List<TmdbEpisode>,
    isLoadingEpisodes: Boolean,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (season: Int, episode: Int, title: String?) -> Unit,
    watched: Set<Pair<Int, Int>> = emptySet(),
    progress: Map<Pair<Int, Int>, Int> = emptyMap(),
    nextUp: Pair<Int, Int>? = null,
    onToggleWatched: ((season: Int, episode: Int) -> Unit)? = null,
    /** Označ/odznač CELOU sezónu (user 2026-07-28) — jinak se rozkoukaný seriál dohání po jednom dílu. */
    onMarkSeasonWatched: ((season: Int, watched: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (seasons.isEmpty()) return
    val isTv = isTvFormFactor()
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Epizody",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(seasons) { s ->
                FilterChip(
                    selected = s.season_number == selectedSeason,
                    onClick = { onSelectSeason(s.season_number) },
                    label = { Text(seasonLabel(s)) },
                    modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp)),
                )
            }
            // „Označit sezónu" na konci lišty — text se řídí stavem, ať je jasné, co klik udělá.
            if (onMarkSeasonWatched != null && episodes.isNotEmpty()) {
                item {
                    val season = selectedSeason ?: seasons.first().season_number
                    val allWatched = episodes.all { (it.season_number ?: season) to it.episode_number in watched }
                    FilterChip(
                        selected = false,
                        onClick = { onMarkSeasonWatched(season, !allWatched) },
                        label = { Text(if (allWatched) "Odznačit sezónu" else "Označit sezónu") },
                        modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (isLoadingEpisodes) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val season = selectedSeason ?: seasons.first().season_number
            // Jedna kostra pro seriály z Jellyfinu i pořady ČT (user 2026-07-29 „vezmi to 1:1"):
            // TV = vodorovná lišta karet, telefon = svislý seznam. Viz `core-ui/EpisodeStrip`.
            val ui = episodes.map { ep -> ep.toEpisodeUi(season, watched, progress) }
            val byKey = episodes.associateBy { episodeKey(it, season) }
            val play: (EpisodeUi) -> Unit = { e ->
                byKey[e.key]?.let { ep -> onPlayEpisode(ep.season_number ?: season, ep.episode_number, ep.name) }
            }
            val toggle: ((EpisodeUi) -> Unit)? = onToggleWatched?.let { cb ->
                { e -> byKey[e.key]?.let { ep -> cb(ep.season_number ?: season, ep.episode_number) } }
            }
            if (isTv) {
                TvEpisodeStrip(
                    episodes = ui,
                    onPlay = play,
                    nextUpKey = nextUp?.let { "s${it.first}e${it.second}" },
                    onLongPress = toggle,
                )
            } else {
                ui.forEach { e ->
                    PhoneEpisodeRow(
                        episode = e,
                        onClick = { play(e) },
                        // Parita s TV (tam long-press funguje od KOLO2) — telefon fajfku ani přepínač neměl.
                        onLongClick = toggle?.let { cb -> { cb(e) } },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun seasonLabel(s: TmdbSeasonSummary): String {
    val n = s.name
    return when {
        s.season_number == 0 -> "Speciály"
        !n.isNullOrBlank() && n.any { it.isLetter() } -> n
        else -> "Sezóna ${s.season_number}"
    }
}

private fun episodeKey(ep: TmdbEpisode, season: Int): String =
    "s${ep.season_number ?: season}e${ep.episode_number}"

/** TMDB epizoda → neutrální model sdílené kostry (`core-ui/EpisodeStrip`). */
private fun TmdbEpisode.toEpisodeUi(
    season: Int,
    watched: Set<Pair<Int, Int>>,
    progress: Map<Pair<Int, Int>, Int>,
): EpisodeUi {
    val s = season_number ?: season
    val key = s to episode_number
    return EpisodeUi(
        key = episodeKey(this, season),
        seasonNumber = s,
        episodeNumber = episode_number,
        title = name?.takeIf { it.isNotBlank() } ?: "Epizoda $episode_number",
        imageUrl = stillUrl(),
        meta = listOfNotNull(
            runtime?.takeIf { it > 0 }?.let { "$it min" },
            vote_average?.takeIf { it > 0f }?.let { "★ %.1f".format(it) },
            air_date?.take(4)?.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        overview = overview,
        watched = key in watched,
        progressPct = progress[key],
    )
}
