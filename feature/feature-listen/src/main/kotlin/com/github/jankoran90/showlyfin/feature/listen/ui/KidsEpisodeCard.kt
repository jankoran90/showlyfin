package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.ui.CoverCard

/**
 * Rodičovská správa R3 (2026-11-19) — jednotlivě schválený díl (klíč `typ:ref#ep:id|Název`) v Poslechu
 * dětí. Klepnutí díl rovnou pustí (celý pořad dítě otevřít nesmí, viz `KidsNavigationGuard`).
 */
@Composable
internal fun KidsEpisodeCard(
    title: String,
    showTitle: String?,
    imageUrl: String?,
    downloaded: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    CoverCard(
        title = title,
        subtitle = listOfNotNull("Díl", showTitle?.takeIf { it.isNotBlank() }, "v telefonu".takeIf { downloaded })
            .joinToString(" · "),
        imageUrl = imageUrl,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        placeholder = Icons.Rounded.PlayArrow,
    )
}
