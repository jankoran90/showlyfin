package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * CONVERGE (SHW-97) — KÁNON hlavičky TV sekce: název sekce vlevo (`headlineMedium`/Bold) a volitelné
 * [actions] (přepínač Mřížka↔Immersive řada, osa Filmotéky…) v JEDNÉ řadě hned vedle názvu. Sdílené napříč
 * VŠEMI sekcemi (railové i mřížkové), aby nadpis + chipy vypadaly stejně a nezabíraly zbytečný vertikální
 * prostor (chipy pod názvem = odsazení obsahu pod obrazovku). Nové sekce používají tuto hlavičku, nikdy
 * vlastní `Text(headlineMedium)` + vlastní `Row` s chipy.
 */
@Composable
fun TvSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions?.invoke(this)
    }
}
