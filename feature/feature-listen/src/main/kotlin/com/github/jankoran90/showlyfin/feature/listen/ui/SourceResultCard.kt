package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult

/**
 * AGORA / PRESET: sdílená karta výsledku podcastového hledání (YouTube kanál / RSS podcast).
 * Reuse z [SourceManagerScreen] (správa zdrojů) i z univerzálního hledání (kontextové směrování v sekci
 * Poslech). Náhled + název + typ/autor; akce Přidat (toggle „Přidáno") a volitelně proklik na zdroj.
 */
@Composable
fun SourceResultCard(
    result: SourceSearchResult,
    added: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onOpen != null) it.clickable(onClick = onOpen) else it }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                sourceTypeLabel(result.type),
                result.subtitle?.takeIf { it != sourceTypeLabel(result.type) },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (added) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Přidáno") },
                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        } else {
            FilledTonalButton(onClick = onAdd, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Přidat", Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** type → čitelný český štítek. */
fun sourceTypeLabel(type: String): String = when (type) {
    "youtube" -> "YouTube kanál"
    "rss" -> "Podcast"
    else -> type
}
