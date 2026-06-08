package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Jednotné nastavení zobrazení epizody napříč sekcemi (detail, fronta, RSS sheet, přehrávač).
 * Threaduje se jako jeden objekt místo mnoha parametrů.
 */
data class EpisodeDisplaySettings(
    val titleLines: Int = 2,
    val descriptionLines: Int = 3,
    val highlightGuest: Boolean = true,
    val fontScale: Float = 1f,
)

/** Styl titulku epizody se zohledněním měřítka písma. */
@Composable
internal fun episodeTitleStyle(display: EpisodeDisplaySettings): TextStyle =
    MaterialTheme.typography.bodyMedium.let { it.copy(fontSize = it.fontSize * display.fontScale) }

/**
 * Vyparsovaný host (vč. profese) jako výrazný „poutač" NAD titulkem epizody — tučně, mírně
 * větší font, accent barva. Skryje se když [EpisodeDisplaySettings.highlightGuest] == false
 * nebo host není rozpoznán. Jednotné napříč sekcemi.
 */
@Composable
internal fun GuestBanner(guest: String?, display: EpisodeDisplaySettings, modifier: Modifier = Modifier) {
    if (!display.highlightGuest) return
    val g = guest?.takeIf { it.isNotBlank() } ?: return
    val size = MaterialTheme.typography.bodyMedium.fontSize * display.fontScale
    Text(
        text = g,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = size),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 2.dp),
    )
}

/** Popis epizody pod titulkem — ořez na [EpisodeDisplaySettings.descriptionLines] (0 = skrýt). */
@Composable
internal fun EpisodeDescriptionText(description: String?, display: EpisodeDisplaySettings, modifier: Modifier = Modifier) {
    if (display.descriptionLines <= 0) return
    val d = description?.takeIf { it.isNotBlank() } ?: return
    val size = MaterialTheme.typography.bodySmall.fontSize * display.fontScale
    Text(
        text = d,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = size),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        maxLines = display.descriptionLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = 4.dp),
    )
}
