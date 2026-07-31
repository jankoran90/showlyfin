package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.feature.discover.foryou.ReferenceRecsViewModel

/**
 * Telefonní sekce „Podle filmu" — doporučení vázaná na RUČNĚ vybrané reference (user 2026-07-31:
 * „důležité je možnost volit referenci, na jaký film nebo filmy se doporučení váže").
 *
 * Nahoře vodorovná lišta filmů z historie (ťuk = vyber/odeber, vybrané drží chip nahoře), tlačítko
 * „Doporučit" spustí kurátora. Jeden vybraný titul = „co je podobné tomuhle", víc titulů = mozek hledá,
 * co je spojuje. Kurátor je LLM → první výpočet trvá desítky sekund, proto průběžný indikátor.
 */
@Composable
fun FilmyReferenceScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: ReferenceRecsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    val pickedKeys = state.picked.map { it.stableKey() }.toSet()

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                FilmyViewToggle(viewMode) { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID }
            },
        ) {
            Column {
                Text(
                    text = "Podle filmu",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = when {
                        state.picked.isEmpty() -> "Vyber film (klidně víc), na který se má doporučení vázat"
                        state.picked.size == 1 -> "Hledám něco podobného jako ${state.picked.first().displayTitle}"
                        else -> "${state.picked.size} vybrané tituly — mozek hledá, co je spojuje"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Vybrané reference + spouštěč.
        if (state.picked.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.picked, key = { it.stableKey() }) { item ->
                    AssistChip(
                        onClick = { vm.toggle(item) },
                        label = { Text(item.displayTitle, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Odebrat") },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = vm::run, enabled = !state.loadingResults) {
                    Text(if (state.ran) "Doporučit znovu" else "Doporučit")
                }
                TextButton(onClick = vm::clearPicked, enabled = !state.loadingResults) { Text("Zrušit výběr") }
                if (state.loadingResults) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }

        // Nabídka referencí = historie sledování.
        if (state.choices.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.choices.take(MAX_CHOICES), key = { it.stableKey() }) { item ->
                    FilterChip(
                        selected = item.stableKey() in pickedKeys,
                        onClick = { vm.toggle(item) },
                        label = { Text(item.displayTitle, maxLines = 1) },
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loadingChoices && state.choices.isEmpty() -> CircularProgressIndicator()
                state.loadingResults && state.results.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.MovieFilter,
                    title = "Kurátor přemýšlí",
                    text = "První výpočet pro nový výběr trvá i půl minuty — výsledek se pak drží v paměti a příště naskočí hned.",
                )
                state.results.isNotEmpty() ->
                    if (viewMode == ViewMode.LIST) FilmyMediaList(state.results, onOpenDetail)
                    else FilmyMediaGrid(state.results, onOpenDetail)
                state.ran -> FilmyEmpty(
                    icon = Icons.Rounded.MovieFilter,
                    title = "Nic nového nevypadlo",
                    text = "Zkus jinou kombinaci filmů — nebo míň titulů naráz, průnik pěti různých žánrů bývá prázdný.",
                )
                state.picked.isNotEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.MovieFilter,
                    title = "Máš vybráno",
                    text = "Ťukni na „Doporučit\" a kurátor najde tituly vázané na tvůj výběr.",
                )
                else -> FilmyEmpty(
                    icon = Icons.Rounded.MovieFilter,
                    title = "Vyber referenci",
                    text = "Nahoře je tvoje historie sledování. Ťukni na film (nebo na několik) a nech si doporučit tituly vázané právě na ně.",
                )
            }
        }
    }
}

private const val MAX_CHOICES = 40
