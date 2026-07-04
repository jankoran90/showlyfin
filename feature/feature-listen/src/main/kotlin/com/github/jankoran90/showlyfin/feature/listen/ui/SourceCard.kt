package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.core.ui.CoverCardShape
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult

/**
 * PRESET (SHW-65): karta vlastního zdroje (YouTube kanál / RSS podcast) v sekci Poslech → Podcasty.
 * CHORUS Osa 2: delegát nad kanonickým [CoverCard]; odznak typu + prémiová hvězda v overlay.
 */
@Composable
fun SourceCard(
    source: PodcastSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // TWINE (SHW-74): dlouhý stisk → propojit s druhou verzí pořadu (audio+video).
    onLongClick: (() -> Unit)? = null,
) {
    val typeIcon: ImageVector = when (source.type) {
        "youtube" -> Icons.Default.PlayArrow
        "ctv" -> Icons.Default.LiveTv
        else -> Icons.Default.Podcasts
    }
    CoverCard(
        title = source.title,
        subtitle = when {
            source.premium -> "Prémiový podcast"
            source.type == "youtube" -> "YouTube kanál"
            source.type == "ctv" -> "ČT iVysílání"
            else -> "Podcast"
        },
        imageUrl = source.thumbnail,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        placeholder = typeIcon,
        overlay = {
            // Odznak typu (rozlišení od ABS podcastů).
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            ) {
                Icon(typeIcon, contentDescription = null, modifier = Modifier.padding(3.dp).size(16.dp))
            }
            // EXODUS (SHW-67): prémiový zdroj rodiny (NaVýbornou) — odznak hvězdy v rohu.
            if (source.premium) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Prémiový", modifier = Modifier.padding(3.dp).size(16.dp))
                }
            }
        },
    )
}

/**
 * TWINE (SHW-74 / plán F7): karta PROPOJENÉHO pořadu (audio RSS + video YouTube = 1 pořad). Místo dvou
 * karet jedna, s odznakem propojení; tap → sloučená obrazovka se spárovanými epizodami.
 * CHORUS Osa 2: delegát nad kanonickým [CoverCard].
 */
@Composable
fun MergedSourceCard(
    title: String,
    thumbnail: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CoverCard(
        title = title,
        subtitle = "Audio + video",
        subtitleColor = MaterialTheme.colorScheme.primary,
        imageUrl = thumbnail,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        placeholder = Icons.Default.Podcasts,
        overlay = {
            // Odznak propojení (audio + video pod jednou kartou).
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(13.dp))
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp))
                }
            }
        },
    )
}

/**
 * AGORA: bohatá objevovací karta podcastu/kanálu — obálka, název, kdo provází (subtitle),
 * popis (summary, 2-3 řádky), meta řádek (počet epizod + kategorie) a akce Přidat / „Přidáno".
 * F3: srdíčko ([favorite]/[onToggleFavorite]) = OSOBNÍ lokální záložka (≠ „Přidáno" = sdílené
 * rodinné zdroje). F4: [showSummary]/[showEpisodeCount] respektují přepínače z Nastavení.
 * Čte výhradně z [MaterialTheme] tokenů (UNISON). Celá je jeden řádek v gridu/listu.
 * (Horizontální rodina — mimo scope CoverCard; sdílí jen [CoverCardShape].)
 */
@Composable
fun DiscoveryCard(
    result: SourceSearchResult,
    added: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    showSummary: Boolean = true,
    showEpisodeCount: Boolean = true,
) {
    val typeIcon: ImageVector = when (result.type) {
        "youtube" -> Icons.Default.PlayArrow
        "ctv" -> Icons.Default.LiveTv
        else -> Icons.Default.Podcasts
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CoverCardShape,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            // Obálka
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (result.thumbnail != null) {
                    AsyncImage(
                        model = result.thumbnail,
                        contentDescription = result.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(typeIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // F3: srdíčko = osobní lokální záložka (oddělené od „Přidáno").
                    onToggleFavorite?.let { toggle ->
                        IconButton(onClick = toggle, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (favorite) "Odebrat z oblíbených" else "Přidat do oblíbených",
                                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                result.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                result.summary?.takeIf { showSummary && it.isNotBlank() }?.let { sum ->
                    Text(
                        text = sum,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // Meta řádek: počet epizod + kategorie (skryj, když chybí).
                val meta = buildList {
                    if (showEpisodeCount) result.episodeCount?.takeIf { it > 0 }?.let { add(epizodyLabel(it)) }
                    result.category?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (added) {
                        AssistChip(
                            onClick = {}, enabled = false,
                            label = { Text("Přidáno") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    } else {
                        FilledTonalButton(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Přidat", Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** „1 epizoda" / „2-4 epizody" / „5 epizod" — česká pluralizace. */
private fun epizodyLabel(n: Int): String = when {
    n == 1 -> "1 epizoda"
    n in 2..4 -> "$n epizody"
    else -> "$n epizod"
}
