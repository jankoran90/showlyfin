package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first

/**
 * SDÍLENÁ kostra seznamu dílů — TV vodorovná lišta karet, telefon svislý seznam.
 *
 * Vzniklo z `SeasonEpisodeSection` (seriály z Jellyfinu) pro user zadání 2026-07-28: „udělej obrazovku
 * se sériemi a epizodami stejně jako máme u JF seriálů — takhle to budou mít i streamované seriály i ČT",
 * a 2026-07-29: „na TV máme horizontální lištu s díly a píšeme hlavně opravdu číslo dílu a série,
 * takže to fakt vezmi 1:1". Aby to bylo 1:1 STRUKTUROU (a ne kopií, která se rozejde), kreslí se
 * všechny zdroje přes [EpisodeUi] — neutrální model bez vazby na TMDB/ČT.
 */
data class EpisodeUi(
    /** Stabilní klíč položky v seznamu (TMDB: „s1e4", ČT: idec). */
    val key: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    /** Hotová URL náhledu 16:9 (žádné skládání cest — každý zdroj si ji připraví sám). */
    val imageUrl: String?,
    /** Řádek pod názvem: stopáž, hodnocení, datum… (už poskládaný). */
    val meta: String? = null,
    val overview: String? = null,
    val watched: Boolean = false,
    val progressPct: Int? = null,
    /** Kolečko místo náhledu, dokud se hledá odkaz na video (ČT resolvuje až zařízení). */
    val loading: Boolean = false,
)

/** „S01E04 · Název" — tvar čísel, na kterém user trvá (stejný u JF seriálu i ČT pořadu). */
fun episodeLabel(e: EpisodeUi): String =
    "S%02dE%02d · %s".format(e.seasonNumber, e.episodeNumber, e.title.ifBlank { "Díl ${e.episodeNumber}" })

/**
 * TV: vodorovná lišta dílů + auto-scroll a AUTOFOKUS na první nedokoukaný (D-pad pokračuje odtud).
 * [nextUpKey] = přesný díl „pokračovat" (zdroj ho může znát líp než my); jinak první nezhlédnutý.
 */
@Composable
fun TvEpisodeStrip(
    episodes: List<EpisodeUi>,
    onPlay: (EpisodeUi) -> Unit,
    modifier: Modifier = Modifier,
    nextUpKey: String? = null,
    onLongPress: ((EpisodeUi) -> Unit)? = null,
) {
    if (episodes.isEmpty()) return
    val listState = rememberLazyListState()
    val focusIdx = remember(episodes, nextUpKey) {
        val byKey = nextUpKey?.let { k -> episodes.indexOfFirst { it.key == k } } ?: -1
        if (byKey >= 0) byKey else episodes.indexOfFirst { !it.watched }
    }
    val nextUpFocus = remember { FocusRequester() }
    LaunchedEffect(focusIdx, episodes) {
        if (focusIdx < 0) return@LaunchedEffect
        if (focusIdx > 0) runCatching { listState.scrollToItem(focusIdx) }
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == focusIdx } }.first { it }
        runCatching { nextUpFocus.requestFocus() }
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(episodes, key = { _, e -> e.key }) { idx, e ->
            TvEpisodeCard(
                episode = e,
                isNextUp = idx == focusIdx,
                onClick = { onPlay(e) },
                onLongClick = onLongPress?.let { cb -> { cb(e) } },
                focusRequester = if (idx == focusIdx) nextUpFocus else null,
            )
        }
    }
}

/** TV landscape karta dílu: náhled 16:9 + fajfka / „Pokračovat" + progres + „S01E04 · název". */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TvEpisodeCard(
    episode: EpisodeUi,
    isNextUp: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    focusRequester: FocusRequester?,
) {
    Column(
        modifier = Modifier
            .width(236.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusable(shape = RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (episode.imageUrl != null) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (episode.watched) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Zhlédnuto",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            if (isNextUp && !episode.watched) {
                Text(
                    text = "▶ Pokračovat",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (episode.loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            val pct = episode.progressPct
            if (pct != null && pct in 1..99) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Box(Modifier.fillMaxWidth(pct / 100f).height(4.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = episodeLabel(episode),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        episode.meta?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }
}

/** Telefon: svislý řádek dílu — náhled, „S01E04 · název", fajfka, dlouhý stisk = nabídka/přepnutí. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneEpisodeRow(
    episode: EpisodeUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(shape = RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (episode.imageUrl != null) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (episode.watched) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Zhlédnuto", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (episode.loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
            val pct = episode.progressPct
            if (pct != null && pct in 1..99) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Box(Modifier.fillMaxWidth(pct / 100f).height(3.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = episodeLabel(episode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (episode.watched) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.meta?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Přehrát díl",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
