package com.github.jankoran90.showlyfin.ui.phone.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.CuratorKind
import com.github.jankoran90.showlyfin.core.domain.CuratorSurprise
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorSettingsViewModel
import com.github.jankoran90.showlyfin.ui.phone.settings.ListenGroupTitle
import com.github.jankoran90.showlyfin.ui.phone.settings.ListenSwitchRow
import kotlin.math.roundToInt

/**
 * AUTEUR (SHW-91) Fáze C1 — sekce „Kurátor" v telefonním Nastavení (parita s [TvCuratorSettingsBlock]).
 * Sdílí [CuratorSettingsViewModel] s TV → volby jsou per profil a synchronizované TV↔telefon. Telefon
 * navíc nabízí textové jemnosti (nálada, žánry, model), na které je klávesnice — na TV chybí.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CuratorSettingsSection(vm: CuratorSettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth()) {
        ListenSwitchRow(
            title = "Kurátorská doporučení „Pro tebe“",
            subtitle = "AI mozek navrhuje z tvého vkusu; vypnuto = jen doporučení dle historie",
            checked = prefs.enabled,
            onCheckedChange = vm::setEnabled,
        )

        if (prefs.enabled) {
            Spacer(Modifier.height(12.dp))
            ListenGroupTitle("Míra objevování")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = discoveryLabel(prefs.discovery),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(prefs.discovery * 100).roundToInt()} %",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = prefs.discovery,
                onValueChange = vm::setDiscovery,
                valueRange = 0f..1f,
                steps = 19, // krok 0,05
            )
            Text(
                text = "Nižší = drž se vkusu (jistota) · vyšší = odvaž se a překvapuj",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            ListenGroupTitle("Druh obsahu")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuratorKind.entries.forEach { kind ->
                    val on = prefs.kind == kind
                    FilterChip(
                        selected = on,
                        onClick = { vm.setKind(kind) },
                        label = { Text(kindLabel(kind)) },
                        leadingIcon = { if (on) Icon(Icons.Filled.Check, null, Modifier.height(FilterChipDefaults.IconSize)) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            ListenGroupTitle("Kudy překvapovat")
            Text(
                text = "Nevybráno = kurátor volí sám (blízké tituly + sousední žánry)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuratorSurprise.entries.forEach { mode ->
                    val on = mode in prefs.surprise
                    FilterChip(
                        selected = on,
                        onClick = { vm.setSurprise(mode, !on) },
                        label = { Text(surpriseLabel(mode)) },
                        leadingIcon = { if (on) Icon(Icons.Filled.Check, null, Modifier.height(FilterChipDefaults.IconSize)) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            ListenGroupTitle("Nálada / přání teď")
            OutlinedTextField(
                value = prefs.mood,
                onValueChange = vm::setMood,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("např. něco odlehčeného na večer") },
            )

            Spacer(Modifier.height(12.dp))
            ListenGroupTitle("Držet se žánrů")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GENRE_CHOICES.forEach { (label, slug) ->
                    val on = slug in prefs.genres
                    FilterChip(
                        selected = on,
                        onClick = { vm.setGenres(if (on) prefs.genres - slug else prefs.genres + slug) },
                        label = { Text(label) },
                        leadingIcon = { if (on) Icon(Icons.Filled.Check, null, Modifier.height(FilterChipDefaults.IconSize)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            ListenGroupTitle("Pokročilé")
            OutlinedTextField(
                value = prefs.model.orEmpty(),
                onValueChange = { vm.setModel(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Model mozku") },
                placeholder = { Text("prázdné = výchozí") },
            )
            Text(
                text = "Který model AI kurátora použít. Prázdné = výchozí (doporučeno).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun discoveryLabel(d: Float): String = when {
    d <= 0.33f -> "Sázka na jistotu"
    d >= 0.66f -> "Odvážné objevování"
    else -> "Vyvážený mix"
}

private fun kindLabel(kind: CuratorKind): String = when (kind) {
    CuratorKind.BOTH -> "Filmy i seriály"
    CuratorKind.MOVIE -> "Filmy"
    CuratorKind.SHOW -> "Seriály"
}

private fun surpriseLabel(mode: CuratorSurprise): String = when (mode) {
    CuratorSurprise.NEAR -> "Blízké tituly"
    CuratorSurprise.GENRES -> "Sousední žánry"
    CuratorSurprise.UNKNOWN -> "Skryté klenoty"
    CuratorSurprise.ERA -> "Jiná éra"
}

/** Populární žánry jako chips (label → lowercase slug do promptu). Držet se = kurátor nevybočí. */
private val GENRE_CHOICES: List<Pair<String, String>> = listOf(
    "Akční" to "akční",
    "Komedie" to "komedie",
    "Drama" to "drama",
    "Sci-fi" to "sci-fi",
    "Horor" to "horor",
    "Thriller" to "thriller",
    "Romantika" to "romantika",
    "Dokument" to "dokument",
    "Animovaný" to "animovaný",
    "Fantasy" to "fantasy",
    "Krimi" to "krimi",
    "Rodinný" to "rodinný",
)
