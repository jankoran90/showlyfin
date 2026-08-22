package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.PodcastVideoQuality
import com.github.jankoran90.showlyfin.feature.listen.PodcastVideoQualityViewModel
import com.github.jankoran90.showlyfin.feature.listen.YoutubeFeedPrefs

/**
 * CLARITY (SHW-75): kategorický blok „Kvalita videa (podcasty)" v Nastavení → Poslech.
 * Dvě nezávislé volby — kvalita STREAMU a kvalita STAHOVÁNÍ videa (360p / 720p / Nejlepší dostupná).
 * (2026-08-22) + počet epizod tažených z YouTube kanálu — dřív pevných 40, appka nešla vyhledat
 * mimo tenhle rozsah. Self-contained (vlastní VM). Parita Nastavení (HARD RULE).
 */
@Composable
fun PodcastVideoQualitySettingsSection(
    viewModel: PodcastVideoQualityViewModel = hiltViewModel(),
) {
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val feedLimit by viewModel.feedLimit.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Kvalita videa (podcasty)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Kvalita přehrávání video epizod (na telefonu i při odeslání na TV). 720p a Nejlepší jdou " +
                "přes plynulý HLS přenos; 360p šetří data. Stahování offline je vždy jen zvuk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PodcastVideoQuality.ALL.forEach { q ->
                FilterChip(
                    selected = stream == q,
                    onClick = { viewModel.setStream(q) },
                    label = { Text(PodcastVideoQuality.label(q)) },
                )
            }
        }

        Text(
            "Počet epizod na YouTube kanálu",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Kolik nejnovějších epizod appka z kanálu natáhne. Víc = jdou najít i starší díly, ale " +
                "kanál se déle načítá (hlavně poprvé).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            YoutubeFeedPrefs.ALL.forEach { n ->
                FilterChip(
                    selected = feedLimit == n,
                    onClick = { viewModel.setFeedLimit(n) },
                    label = { Text("$n") },
                )
            }
        }
    }
}
