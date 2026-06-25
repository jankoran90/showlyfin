package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource

/**
 * TWINE (SHW-74 / plán F7): výběr zdroje k PROPOJENÍ se [source] (= „tohle je týž pořad jako audio+video").
 * Auto-návrh ([suggested]) jde nahoru s odznakem „Možná stejný pořad" — ale propojení vždy POTVRDÍ uživatel
 * (brání špatnému spárování). Nedestruktivní: jde kdykoli odlinkovat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceLinkSheet(
    source: PodcastSource,
    candidates: List<PodcastSource>,
    suggested: PodcastSource?,
    onLink: (PodcastSource) -> Unit,
    onDismiss: () -> Unit,
) {
    // Návrh nahoru, pak zbytek (bez duplicity návrhu).
    val ordered = (listOfNotNull(suggested) + candidates.filterNot { it.id == suggested?.id })
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            "Propojit s druhou verzí",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            "Pořad ${source.title} se spojí s druhým zdrojem (audio + video) do jedné karty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        if (ordered.isEmpty()) {
            Text(
                "Nemáš žádný další sledovaný zdroj k propojení.\nPřidej druhou verzi pořadu v záložce Objev.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(ordered, key = { it.id }) { cand ->
                    LinkCandidateRow(
                        source = cand,
                        suggested = cand.id == suggested?.id,
                        onClick = { onLink(cand); onDismiss() },
                    )
                }
            }
        }
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun LinkCandidateRow(
    source: PodcastSource,
    suggested: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (source.thumbnail != null) {
                AsyncImage(
                    model = source.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    if (source.type == "youtube") Icons.Default.PlayArrow else Icons.Default.Podcasts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                source.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (source.type == "youtube") "YouTube (video)" else "Podcast (audio)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (suggested) {
            AssistChip(
                onClick = onClick,
                label = { Text("Možná stejný pořad") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        } else {
            Icon(Icons.Default.Link, contentDescription = "Propojit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
