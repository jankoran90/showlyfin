package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.PodcastDiscoverySettingsViewModel

/**
 * AGORA F4: blok „Objevování podcastů" v Nastavení → Poslech. Hráčka-friendly (víc voleb): výchozí
 * země/režim, trvale skryté CZ kategorie, min. počet epizod, počet karet na stránku, přepínače popisu
 * a počtu epizod v náhledech. Self-contained (vlastní VM), persistuje do AbsPreferences; objevovací
 * obrazovka tyto defaulty převezme při dalším vstupu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDiscoverySettingsSection(
    viewModel: PodcastDiscoverySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Objevování podcastů",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )

        SubHeader("Výchozí země")
        ChipChoices(
            options = COUNTRIES,
            selected = state.country,
            onSelect = viewModel::setCountry,
        )

        SubHeader("Výchozí režim")
        ChipChoices(
            options = MODES,
            selected = state.mode,
            onSelect = viewModel::setMode,
        )

        SubHeader("Min. počet epizod")
        IntChipChoices(
            options = MIN_EPISODE_OPTIONS,
            selected = state.minEpisodes,
            label = { if (it == 0) "Bez filtru" else "$it+" },
            onSelect = viewModel::setMinEpisodes,
        )

        SubHeader("Počet karet na stránku")
        IntChipChoices(
            options = PAGE_SIZE_OPTIONS,
            selected = state.pageSize,
            label = { "$it" },
            onSelect = viewModel::setPageSize,
        )

        SwitchRow(
            label = "Zobrazovat popis v náhledech",
            checked = state.showSummary,
            onChange = viewModel::setShowSummary,
        )
        SwitchRow(
            label = "Zobrazovat počet epizod",
            checked = state.showEpisodeCount,
            onChange = viewModel::setShowEpisodeCount,
        )

        SubHeader("Trvale skryté kategorie (ČR)")
        if (state.czCategories.isEmpty()) {
            Text(
                "Kategorie se načtou po připojení k serveru.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Vybrané kategorie se při otevření Objevit rovnou skryjí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.czCategories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = cat.id.toString() in state.hiddenCategories,
                        onClick = { viewModel.toggleHiddenCategory(cat.id) },
                        label = { Text(cat.name) },
                    )
                }
            }
        }
    }
}

private val COUNTRIES = listOf(
    "cz" to "ČR", "us" to "USA", "gb" to "UK", "au" to "Austrálie",
)
private val MODES = listOf(
    "popular" to "Populární", "active" to "Aktivní", "new" to "Nové", "az" to "A-Z",
)
private val MIN_EPISODE_OPTIONS = listOf(0, 5, 10, 25, 50)
private val PAGE_SIZE_OPTIONS = listOf(20, 30, 50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipChoices(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.first }) { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntChipChoices(
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it }) { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
}
