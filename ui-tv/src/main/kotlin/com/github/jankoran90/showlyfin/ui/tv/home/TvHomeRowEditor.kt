package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType
import com.github.jankoran90.showlyfin.ui.tv.settings.TvActionChip
import com.github.jankoran90.showlyfin.ui.tv.settings.TvOptionStepperRow
import com.github.jankoran90.showlyfin.ui.tv.settings.TvToggleRow

/**
 * TENFOOT — TV DOMOV REDESIGN. Inline editor JEDNÉ řady (Kodi-like): dočasný overlay „Upravit řadu"
 * vyvolaný tlačítkem Menu na fokusované řadě. Styl karet / řazení / (u Objevovat) kategorie / skryj
 * zhlédnuté + Přesunout / Skrýt / Přidat řadu. Zápis rovnou do `HomeLayoutStore` → okamžitý náhled.
 * Vzdušné, progressive disclosure — domov zůstává čistý, editace je jen tady.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvHomeRowEditor(
    config: HomeRowConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    hiddenRows: List<HomeRowConfig>,
    onUpdate: (HomeRowConfig) -> Unit,
    onMove: (up: Boolean) -> Unit,
    onHide: () -> Unit,
    onUnhide: (id: String) -> Unit,
    onAddRow: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(config.id) {
        withFrameNanos { }
        runCatching { panelFocus.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .focusRequester(panelFocus)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Upravit řadu: ${config.title}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            TvOptionStepperRow(
                label = "Styl karet",
                options = HomeCardStyle.entries,
                selected = config.cardStyle,
                labelOf = { it.label },
                onSelect = { onUpdate(config.copy(cardStyle = it)) },
            )

            TvOptionStepperRow(
                label = "Řazení",
                options = HomeRowSort.entries,
                selected = config.sort,
                labelOf = { it.label },
                onSelect = { onUpdate(config.copy(sort = it)) },
            )

            // Progressive disclosure: kategorie/typ jen pro „Objevovat" (Trakt).
            if (config.source == HomeRowSourceType.DISCOVER) {
                val tab = config.params[HomeRowParams.TAB] ?: "movies"
                TvOptionStepperRow(
                    label = "Typ",
                    options = listOf("movies", "shows"),
                    selected = tab,
                    labelOf = { if (it == "shows") "Seriály" else "Filmy" },
                    onSelect = { onUpdate(config.withParam(HomeRowParams.TAB, it)) },
                )
                val filter = config.params[HomeRowParams.FILTER] ?: "trending"
                TvOptionStepperRow(
                    label = "Kategorie",
                    options = listOf("trending", "popular", "anticipated", "recommended"),
                    selected = filter,
                    labelOf = { filterLabel(it) },
                    onSelect = { onUpdate(config.withParam(HomeRowParams.FILTER, it)) },
                )
            }

            // Skrýt zhlédnuté — jen kde to dává smysl (Objevovat / Jellyfin knihovna / Nejnovější v knihovně).
            if (config.source == HomeRowSourceType.DISCOVER ||
                config.source == HomeRowSourceType.JELLYFIN_LIBRARY ||
                config.source == HomeRowSourceType.RECENTLY_ADDED
            ) {
                TvToggleRow(
                    label = "Skrýt zhlédnuté",
                    checked = config.params[HomeRowParams.HIDE_WATCHED] == "true",
                    onCheckedChange = { onUpdate(config.withParam(HomeRowParams.HIDE_WATCHED, it.toString())) },
                )
            }

            // Popisky na kartách — vypni pro čistý Netflix immersive (název nese hero nahoře).
            TvToggleRow(
                label = "Popisky na kartách",
                checked = config.showTitles,
                onCheckedChange = { onUpdate(config.copy(showTitles = it)) },
            )

            TvOptionStepperRow(
                label = "Počet položek",
                options = listOf(10, 20, 30, 40, 60),
                selected = config.limit.coerceIn(1, 60),
                labelOf = { "$it" },
                onSelect = { onUpdate(config.copy(limit = it)) },
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvActionChip(label = "↑ Nahoru", enabled = canMoveUp, onClick = { onMove(true) })
                TvActionChip(label = "↓ Dolů", enabled = canMoveDown, onClick = { onMove(false) })
                TvActionChip(label = "Skrýt řadu", enabled = true, danger = true, onClick = onHide)
                TvActionChip(label = "Přidat řadu", enabled = true, onClick = onAddRow)
                TvActionChip(label = "Hotovo", enabled = true, onClick = onDismiss)
            }

            // Správa viditelnosti z jednoho místa: vrátit dřív skryté řady (i knihovny).
            if (hiddenRows.isNotEmpty()) {
                Text(
                    text = "Vrátit skrytou řadu",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    hiddenRows.forEach { row ->
                        TvActionChip(
                            label = "＋ ${row.title.ifBlank { row.source.label }}",
                            enabled = true,
                            onClick = { onUnhide(row.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun HomeRowConfig.withParam(key: String, value: String): HomeRowConfig =
    copy(params = params + (key to value))

private fun filterLabel(filter: String): String = when (filter) {
    "popular" -> "Populární"
    "anticipated" -> "Očekávané"
    "recommended" -> "Doporučené"
    else -> "Trendy"
}
