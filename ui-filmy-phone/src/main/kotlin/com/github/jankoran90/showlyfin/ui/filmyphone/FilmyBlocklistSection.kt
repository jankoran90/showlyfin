package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * SPOTLIGHT+ (user 2026-08-27) — blok „Nenabízené tituly" v Nastavení.
 *
 * Parita nastavení = DoD: co jde zapnout, musí jít i vypnout. Bez tohohle seznamu by omylem
 * zablokovaný film zmizel navždy a nešel vrátit (user výslovně: „ale ukladejme historii").
 */
@Composable
fun FilmyBlocklistSection(vm: FilmyBlocklistViewModel = hiltViewModel()) {
    val blocked by vm.items.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingSectionTitle("Nenabízené tituly")
        Text(
            text = if (blocked.isEmpty())
                "Zatím nic. V sekci „Pro tebe\" můžeš u každého tipu ťuknout na křížek a kurátor ti " +
                    "ten titul přestane nabízet."
            else
                "Tyhle tituly ti kurátor nenabízí. Šipkou je vrátíš zpátky do hry.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Nejnovější blokace navrchu — člověk hledá typicky to, co právě omylem klikl.
        blocked.sortedByDescending { it.blockedAtMs }.forEach { b ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(b.title.takeIf { it.isNotBlank() } ?: "TMDB ${b.tmdbId}", b.year?.toString())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.unblock(b.tmdbId) }) {
                    Icon(Icons.Rounded.Undo, contentDescription = "Zase nabízet")
                }
            }
        }
    }
}
